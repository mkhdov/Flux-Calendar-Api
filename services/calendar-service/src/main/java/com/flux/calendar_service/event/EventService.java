package com.flux.calendar_service.event;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import com.flux.calendar_service.calendar.Calendar;
import com.flux.calendar_service.calendar.CalendarRepository;
import com.flux.calendar_service.conference.Conference;
import com.flux.calendar_service.conference.ConferenceMapper;
import com.flux.calendar_service.conference.ConferenceRepository;
import com.flux.calendar_service.event.dto.EventRequest;
import com.flux.calendar_service.event.dto.EventResponse;
import com.flux.calendar_service.event.dto.EventUpdateRequest;
import com.flux.calendar_service.exception.ConflictException;
import com.flux.calendar_service.exception.EmptyFieldException;
import com.flux.calendar_service.exception.GoogleTokenRevokedException;
import com.flux.calendar_service.exception.InvalidOperationException;
import com.flux.calendar_service.exception.InvalidTimeRangeException;
import com.flux.calendar_service.exception.ResourceNotFoundException;
import com.flux.calendar_service.exception.ServiceUnavailableException;
import com.flux.calendar_service.exception.ZoomException;
import com.flux.calendar_service.google.GoogleAuthService;
import com.flux.calendar_service.google.GoogleCalendarApiService;
import com.flux.calendar_service.location.Location;
import com.flux.calendar_service.location.LocationMapper;
import com.flux.calendar_service.location.LocationRepository;
import com.flux.calendar_service.location.LocationService;
import com.flux.calendar_service.member.Member;
import com.flux.calendar_service.member.MemberRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventService {
    private final EventRepository eventRepository;
    private final EventMapper eventMapper;
    private final CalendarRepository calendarRepository;
    private final LocationRepository locationRepository;
    private final LocationMapper locationMapper;
    private final ConferenceRepository conferenceRepository;
    private final ConferenceMapper conferenceMapper;
    private final MemberRepository memberRepository;
    private final LocationService locationService;

    @Autowired(required = false)
    private GoogleCalendarApiService googleCalendarApiService;

    @Autowired(required = false)
    private GoogleAuthService googleAuthService;

    @Autowired(required = false)
    private com.flux.calendar_service.zoom.ZoomMeetingService zoomMeetingService;

    private String currentUserId() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    @Transactional
    public String createEvent(String calendarId, EventRequest request) {
        if (calendarId == null || calendarId.isBlank()) {
            throw new EmptyFieldException("Calendar ID cannot be empty");
        }

        // Validate calendar exists
        Calendar calendar = calendarRepository.findById(calendarId)
                .orElseThrow(() -> new ResourceNotFoundException("Calendar not found with ID: " + calendarId));

        // Validate time if not all-day event
        if (!request.allDay() && request.startTime() != null && request.endTime() != null) {
            if (request.endTime().isBefore(request.startTime())) {
                throw new InvalidTimeRangeException("End time cannot be before start time");
            }
        }

        // Create event
        Event event = eventMapper.toEvent(request, calendar);
        normalizeAllDayEventTimes(event, request);

        if (event.getSubtasks() != null) {
            event.getSubtasks().forEach(subtask -> subtask.setEvent(event));
        }
        if (event.getAttachments() != null) {
            event.getAttachments().forEach(attachment -> attachment.setEvent(event));
        }

        // Save event first so it gets a generated UUID before we reference
        // event.getId()
        Event savedEvent = eventRepository.save(event);

        // Attach location after save (locationService.create needs a valid event ID)
        if (request.location() != null) {
            Location location = locationService.create(savedEvent.getId(), request.location());
            savedEvent.setLocation(location);
            savedEvent = eventRepository.save(savedEvent);
        }

        // Sync with Google Calendar if enabled
        if (shouldSyncToGoogle(calendar)) {
            try {
                com.google.api.services.calendar.model.Event googleEvent;
                String userId = calendar.getUserId();

                if (savedEvent.isAllDay()) {
                    // Create all-day event
                    googleEvent = googleCalendarApiService.createAllDayEvent(
                            userId,
                            savedEvent.getTitle(),
                            savedEvent.getDescription(),
                            savedEvent.getLocation(),
                            savedEvent.getStartTime());
                } else {
                    // Create simple event
                    googleEvent = googleCalendarApiService.createEvent(
                            userId,
                            savedEvent.getTitle(),
                            savedEvent.getDescription(),
                            savedEvent.getLocation(),
                            savedEvent.getStartTime(),
                            savedEvent.getEndTime(),
                            savedEvent.getType().toString());
                }

                // Update event with Google Calendar ID
                savedEvent.setGoogleCalendarId(googleEvent.getId());
                savedEvent.setSyncStatus(SyncStatus.SYNCED);
                eventRepository.save(savedEvent);

                log.info("Event created and synced to Google Calendar. Event ID: {}, Google Calendar ID: {}",
                        savedEvent.getId(), googleEvent.getId());
            } catch (IOException e) {
                if (GoogleAuthService.isInvalidGrant(e)) {
                    // User's token is permanently revoked — mark disconnected and tag event
                    log.error("Google token revoked while creating event for user {}. "
                            + "Event saved locally. Event ID: {}", calendar.getUserId(), savedEvent.getId());
                    if (googleAuthService != null) {
                        googleAuthService.markUserAsDisconnected(calendar.getUserId());
                    }
                    savedEvent.setSyncStatus(SyncStatus.AUTH_ERROR);
                } else {
                    log.error("Transient Google sync failure for event: {}. Will retry.", savedEvent.getId(), e);
                    savedEvent.setSyncStatus(SyncStatus.PENDING);
                }
                eventRepository.save(savedEvent);
            }
        } else {
            if (googleAuthService != null && !googleAuthService.isGoogleConnected(calendar.getUserId())
                    && calendar.getGoogleCalendarId() != null && !calendar.getGoogleCalendarId().isBlank()) {
                log.warn(
                        "Event {} not synced due to AUTH_ERROR. Google auth is disconnected for user {}. Calendar ID: {}",
                        savedEvent.getId(), calendar.getUserId(), calendar.getId());
                savedEvent.setSyncStatus(SyncStatus.AUTH_ERROR);
                eventRepository.save(savedEvent);
            } else {
                log.debug("Event created locally without Google sync. Calendar ID: {}", calendar.getId());
            }
        }
        Member member = Member.builder()
                .fullName("Owner")
                .email("Owner")
                .isOwner(true)
                .event(savedEvent)
                .build();

        memberRepository.save(member);

        return savedEvent.getId();
    }

    @Transactional
    public String createEventWithGoogleMeet(String calendarId, EventRequest request) {

        if (calendarId == null || calendarId.isBlank()) {
            throw new EmptyFieldException("Calendar ID cannot be empty");
        }

        // 1️⃣ Validate calendar
        Calendar calendar = calendarRepository.findById(calendarId)
                .orElseThrow(() -> new ResourceNotFoundException("Calendar not found with ID: " + calendarId));

        // 2️⃣ Validate time
        if (!request.allDay()
                && request.startTime() != null
                && request.endTime() != null
                && request.endTime().isBefore(request.startTime())) {
            throw new InvalidTimeRangeException("End time cannot be before start time");
        }

        // 3️⃣ Create & save local event
        Event event = eventMapper.toEvent(request, calendar);
        normalizeAllDayEventTimes(event, request);
        if (event.getSubtasks() != null) {
            event.getSubtasks().forEach(subtask -> subtask.setEvent(event));
        }
        if (event.getAttachments() != null) {
            event.getAttachments().forEach(attachment -> attachment.setEvent(event));
        }
        Event savedEvent = eventRepository.save(event);

        // 4️⃣ Google sync (optional)
        // Google Meet events should sync even when created from a non-dedicated
        // calendar.
        if (googleCalendarApiService == null) {
            log.debug("Event created locally without Google sync. Calendar ID: {}", calendar.getId());
            return savedEvent.getId();
        }

        if (googleAuthService != null && !googleAuthService.isGoogleConnected(calendar.getUserId())) {
            log.warn("Event {} not synced due to AUTH_ERROR. Google auth is disconnected for user {}.",
                    savedEvent.getId(), calendar.getUserId());
            savedEvent.setSyncStatus(SyncStatus.AUTH_ERROR);
            eventRepository.save(savedEvent);
        } else {
            try {
                String userId = calendar.getUserId();
                com.google.api.services.calendar.model.Event googleEvent;

                if (savedEvent.isAllDay()) {
                    googleEvent = googleCalendarApiService.createAllDayEventWithGoogleMeet(
                            userId,
                            savedEvent.getTitle(),
                            savedEvent.getDescription(),
                            savedEvent.getStartTime());
                } else {
                    // ✔ Timed → WITH Meet
                    googleEvent = googleCalendarApiService.createEventWithGoogleMeet(
                            userId,
                            savedEvent.getTitle(),
                            savedEvent.getDescription(),
                            savedEvent.getLocation(),
                            savedEvent.getStartTime(),
                            savedEvent.getEndTime(),
                            savedEvent.getType().toString());
                }

                // 5️⃣ Extract Meet details if present
                String googleConferenceId = null;
                String meetLink = null;
                String meetingCode = null;
                String phoneNumber = null;
                String pin = null;

                if (googleEvent.getConferenceData() != null) {
                    com.google.api.services.calendar.model.ConferenceData confData = googleEvent.getConferenceData();
                    googleConferenceId = confData.getConferenceId();

                    if (confData.getEntryPoints() != null) {
                        for (com.google.api.services.calendar.model.EntryPoint entry : confData.getEntryPoints()) {
                            if ("video".equals(entry.getEntryPointType())) {
                                meetLink = entry.getUri();
                                if (meetLink != null && meetLink.contains("/")) {
                                    meetingCode = meetLink.substring(meetLink.lastIndexOf("/") + 1);
                                }
                            } else if ("phone".equals(entry.getEntryPointType())) {
                                phoneNumber = entry.getUri();
                                pin = entry.getPin();
                            }
                        }
                    }
                }

                // 6️⃣ Update and save local event (cascade will save conference)
                savedEvent.setGoogleCalendarId(googleEvent.getId());
                savedEvent.setSyncStatus(SyncStatus.SYNCED);
                if (googleConferenceId != null || meetLink != null || meetingCode != null || phoneNumber != null
                        || pin != null) {
                    Conference conference = Conference.builder()
                            .type(Conference.ConferenceType.GOOGLE_MEET)
                            .googleConferenceId(googleConferenceId)
                            .meetLink(meetLink)
                            .meetingCode(meetingCode)
                            .phoneNumber(phoneNumber)
                            .pin(pin)
                            .conferenceLink(meetLink)
                            .syncStatus(Conference.SyncStatus.SYNCED)
                            .lastSynced(LocalDateTime.now())
                            .event(savedEvent)
                            .build();
                    savedEvent.setConference(conference);
                } else {
                    savedEvent.setConference(null);
                }

                eventRepository.save(savedEvent);

                log.info(
                        "Event synced. Local ID: {}, Google ID: {}, Meet: {}",
                        savedEvent.getId(),
                        googleEvent.getId(),
                        meetLink);

            } catch (IOException e) {
                if (GoogleAuthService.isInvalidGrant(e)) {
                    log.error("Google token revoked while creating meet event for user {}. Event ID: {}",
                            calendar.getUserId(), savedEvent.getId());
                    if (googleAuthService != null) {
                        googleAuthService.markUserAsDisconnected(calendar.getUserId());
                    }
                    savedEvent.setSyncStatus(SyncStatus.AUTH_ERROR);
                } else {
                    log.error("Failed to sync event to Google Calendar. Event ID: {}",
                            savedEvent.getId(), e);
                    savedEvent.setSyncStatus(SyncStatus.PENDING);
                }
                eventRepository.save(savedEvent);
            }
        }

        Member member = Member.builder()
                .fullName("Owner")
                .email("Owner")
                .isOwner(true)
                .event(savedEvent)
                .build();

        memberRepository.save(member);

        return savedEvent.getId();
    }

    @Transactional
    public void addGoogleMeetToExistingEvent(String id) {
        if (id == null || id.isBlank()) {
            throw new EmptyFieldException("Event ID cannot be empty");
        }

        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + id));

        if (googleCalendarApiService == null) {
            throw new ServiceUnavailableException("Google Calendar integration is disabled");
        }

        try {
            String userId = event.getCalendar().getUserId();
            com.google.api.services.calendar.model.Event googleEvent = googleCalendarApiService
                    .addGoogleMeetToExistingEvent(
                            userId,
                            getGoogleEventId(event));

            // Extract Meet details
            String googleConferenceId = null;
            String meetLink = null;
            String meetingCode = null;
            String phoneNumber = null;
            String pin = null;

            if (googleEvent.getConferenceData() != null) {
                com.google.api.services.calendar.model.ConferenceData confData = googleEvent.getConferenceData();
                googleConferenceId = confData.getConferenceId();

                if (confData.getEntryPoints() != null) {
                    for (com.google.api.services.calendar.model.EntryPoint entry : confData.getEntryPoints()) {
                        if ("video".equals(entry.getEntryPointType())) {
                            meetLink = entry.getUri();
                            if (meetLink != null && meetLink.contains("/")) {
                                meetingCode = meetLink.substring(meetLink.lastIndexOf("/") + 1);
                            }
                        } else if ("phone".equals(entry.getEntryPointType())) {
                            phoneNumber = entry.getUri();
                            pin = entry.getPin();
                        }
                    }
                }
            }

            Conference conference = Conference.builder()
                    .type(Conference.ConferenceType.GOOGLE_MEET)
                    .googleConferenceId(googleConferenceId)
                    .meetLink(meetLink)
                    .meetingCode(meetingCode)
                    .phoneNumber(phoneNumber)
                    .pin(pin)
                    .conferenceLink(meetLink)
                    .syncStatus(Conference.SyncStatus.SYNCED)
                    .lastSynced(LocalDateTime.now())
                    .event(event)
                    .build();

            event.setConference(conference);
            event.setSyncStatus(SyncStatus.SYNCED);
            eventRepository.save(event);

            log.info("Google Meet added to event. Local ID: {}, Google ID: {}, Meet: {}",
                    event.getId(), googleEvent.getId(), meetLink);

        } catch (IOException e) {
            log.error("Failed to add Google Meet to event. Event ID: {}", event.getId(), e);
            throw new ServiceUnavailableException("Failed to add Google Meet", e);
        }

        Member member = Member.builder()
                .fullName("Owner")
                .email("Owner")
                .isOwner(true)
                .event(event)
                .build();

        memberRepository.save(member);
    }

    @Transactional
    public void removeGoogleMeetFromEvent(String id) {
        if (id == null || id.isBlank()) {
            throw new EmptyFieldException("Event ID cannot be empty");
        }

        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + id));

        if (event.getConference() == null) {
            return;
        }

        if (shouldSyncExistingGoogleEvent(event)) {
            try {
                // We don't have a direct 'removeMeet' in ApiService, but we can update the
                // event with null conferenceData
                // For now, let's assume we want to clear it locally and potentially update
                // Google
                String userId = event.getCalendar().getUserId();
                com.google.api.services.calendar.model.Event googleEvent = googleCalendarApiService.getEventById(userId,
                        getGoogleEventId(event));
                googleEvent.setConferenceData(null);

                // Update Google
                googleCalendarApiService.removeConfrenceFromEvent(
                        userId,
                        getGoogleEventId(event));
            } catch (IOException e) {
                log.error("Failed to remove Google Meet from Google Calendar. Event ID: {}", event.getId(), e);
            }
        }

        Conference conference = event.getConference();
        event.setConference(null);
        eventRepository.save(event);
        conferenceRepository.delete(conference);

        log.info("Google Meet removed from event. Local ID: {}", id);
    }

    private String getGoogleEventId(Event event) {
        if (event.getGoogleCalendarId() != null && !event.getGoogleCalendarId().isBlank()) {
            return event.getGoogleCalendarId();
        }
        return event.getId();
    }

    // ========================================
    // ZOOM MEETING METHODS
    // ========================================

    @Transactional
    public String createEventWithZoom(String calendarId, EventRequest request, String userId) {
        if (calendarId == null || calendarId.isBlank()) {
            throw new EmptyFieldException("Calendar ID cannot be empty");
        }

        // 1️⃣ Validate calendar
        Calendar calendar = calendarRepository.findById(calendarId)
                .orElseThrow(() -> new ResourceNotFoundException("Calendar not found with ID: " + calendarId));

        // 2️⃣ Validate time
        if (!request.allDay()
                && request.startTime() != null
                && request.endTime() != null
                && request.endTime().isBefore(request.startTime())) {
            throw new InvalidTimeRangeException("End time cannot be before start time");
        }

        // 3️⃣ Create & save local event
        Event event = eventMapper.toEvent(request, calendar);
        if (event.getSubtasks() != null) {
            event.getSubtasks().forEach(subtask -> subtask.setEvent(event));
        }
        if (event.getAttachments() != null) {
            event.getAttachments().forEach(attachment -> attachment.setEvent(event));
        }

        // Save event first so it gets a generated UUID before we reference
        // event.getId()
        Event savedEvent = eventRepository.save(event);

        // Attach location after save (locationService.create needs a valid event ID)
        if (request.location() != null) {
            Location location = locationService.create(savedEvent.getId(), request.location());
            savedEvent.setLocation(location);
            savedEvent = eventRepository.save(savedEvent);
        }

        // 4️⃣ Create Zoom meeting (Best effort)
        Conference zoomConference = null;
        if (zoomMeetingService != null && !savedEvent.isAllDay()) {
            try {
                zoomConference = zoomMeetingService.createZoomMeeting(savedEvent.getId(), userId);
                savedEvent.setConference(zoomConference);
                savedEvent = eventRepository.save(savedEvent);

                log.info("Zoom meeting created for event: {}. ID: {}", savedEvent.getId(),
                        zoomConference.getZoomMeetingId());
            } catch (ZoomException e) {
                log.error("Failed to create Zoom meeting for event {}: {}", savedEvent.getId(), e.getMessage());
                // Continue without Zoom
            }
        }

        // 5️⃣ Sync with Google Calendar (if enabled)
        if (shouldSyncToGoogle(calendar)) {
            try {
                StringBuilder description = new StringBuilder();
                description.append(savedEvent.getDescription());
                if (zoomConference != null) {
                    description.append("\n\nZoom Meeting: ").append(zoomConference.getConferenceLink());
                }

                com.google.api.services.calendar.model.Event googleEvent = googleCalendarApiService.createEvent(
                        userId,
                        savedEvent.getTitle(),
                        description.toString(),
                        event.getLocation(),
                        savedEvent.getStartTime(),
                        savedEvent.getEndTime(),
                        savedEvent.getType().toString());

                savedEvent.setGoogleCalendarId(googleEvent.getId());
                savedEvent.setSyncStatus(SyncStatus.SYNCED);
                savedEvent.setDescription(description.toString());
                eventRepository.save(savedEvent);

                log.info("Event synced to Google Calendar. Event ID: {}, Google ID: {}", savedEvent.getId(),
                        googleEvent.getId());
            } catch (IOException e) {
                if (GoogleAuthService.isInvalidGrant(e)) {
                    log.error("Google token revoked while creating zoom event for user {}. Event ID: {}", userId,
                            savedEvent.getId());
                    if (googleAuthService != null) {
                        googleAuthService.markUserAsDisconnected(userId);
                    }
                    savedEvent.setSyncStatus(SyncStatus.AUTH_ERROR);
                } else {
                    log.error("Failed to sync event to Google Calendar for event {}: {}", savedEvent.getId(),
                            e.getMessage());
                    savedEvent.setSyncStatus(SyncStatus.PENDING);
                }
                eventRepository.save(savedEvent);
            }
        } else {
            if (googleAuthService != null && !googleAuthService.isGoogleConnected(calendar.getUserId())
                    && calendar.getGoogleCalendarId() != null && !calendar.getGoogleCalendarId().isBlank()) {
                log.warn(
                        "Event {} not synced due to AUTH_ERROR. Google auth is disconnected for user {}. Calendar ID: {}",
                        savedEvent.getId(), calendar.getUserId(), calendar.getId());
                savedEvent.setSyncStatus(SyncStatus.AUTH_ERROR);
                eventRepository.save(savedEvent);
            } else {
                log.debug("Event created locally without Google sync. Calendar ID: {}", calendar.getId());
            }
        }

        // 6️⃣ Create owner member
        Member member = Member.builder()
                .fullName("Owner")
                .email("Owner")
                .isOwner(true)
                .event(savedEvent)
                .build();
        memberRepository.save(member);

        return savedEvent.getId();
    }

    @Transactional
    public void addZoomToExistingEvent(String id, String userId) {
        if (id == null || id.isBlank()) {
            throw new EmptyFieldException("Event ID cannot be empty");
        }

        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + id));

        if (event.isAllDay()) {
            throw new InvalidOperationException("Cannot add Zoom meeting to all-day events");
        }

        if (event.getConference() != null && Conference.ConferenceType.ZOOM.equals(event.getConference().getType())) {
            throw new ConflictException("Event already has a Zoom meeting");
        }

        if (zoomMeetingService == null) {
            throw new ServiceUnavailableException("Zoom integration is disabled");
        }

        try {
            Conference conference = zoomMeetingService.createZoomMeeting(event.getId(), userId);
            event.setConference(conference);

            log.info("Zoom meeting added to event. Event ID: {}, Zoom Meeting ID: {}",
                    event.getId(), conference.getZoomMeetingId());

            // Sync with Google Calendar if enabled
            if (shouldSyncExistingGoogleEvent(event)) {
                StringBuilder description = new StringBuilder();
                description.append(event.getDescription()).append("\n\nZoom Meeting: ").append(conference.getConferenceLink());
                googleCalendarApiService.updateEvent(
                        event.getCalendar().getUserId(),
                        event.getGoogleCalendarId(),
                        event.getTitle(),
                        description.toString());
                event.setDescription(description.toString());
            }
            eventRepository.save(event);

        } catch (Exception e) {
            log.error("Failed to add Zoom meeting to event. Event ID: {}", event.getId(), e);
            throw new ServiceUnavailableException("Failed to add Zoom meeting", e);
        }
    }
    /**
     * Removes only the Zoom meeting link portion appended during creation.
     * Leaves the rest of the user's original description intact.
     *
     * Pattern matched: "\n\nZoom Meeting: <link>"
     */
    private String removeZoomLinkFromDescription(String description) {
        if (description == null || description.isBlank()) {
            return description;
        }
        // Removes the exact block appended in createEventWithZoom()
        return description.replaceAll("\\n\\nZoom Meeting: https?://\\S+", "").stripTrailing();
    }

    @Transactional
    public void removeZoomFromEvent(String id, String userId) {
        if (id == null || id.isBlank()) {
            throw new EmptyFieldException("Event ID cannot be empty");
        }

        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + id));

        if (event.getConference() == null) {
            return;
        }

        if (!Conference.ConferenceType.ZOOM.equals(event.getConference().getType())) {
            throw new InvalidOperationException("Event does not have a Zoom meeting");
        }

        // 1️⃣ Delete from Zoom servers
        if (zoomMeetingService != null) {
            try {
                zoomMeetingService.deleteZoomMeeting(event.getId(), userId);
                log.info("Zoom meeting removed from Zoom servers. Event ID: {}", id);
            } catch (Exception e) {
                log.error("Failed to remove Zoom meeting from Zoom servers. Event ID: {}", event.getId(), e);
                throw new ZoomException(e.getMessage());
            }
        }

        // 2️⃣ Clean Zoom link from description, preserve user's original text
        String cleanedDescription = removeZoomLinkFromDescription(event.getDescription());

        // 3️⃣ Update local event — clear conference and fix description
        event.setConference(null);
        event.setDescription(cleanedDescription);
        eventRepository.save(event);

        // 4️⃣ Sync cleaned description to Google Calendar
        if (shouldSyncExistingGoogleEvent(event)) {
            try {
                googleCalendarApiService.updateEvent(
                        event.getCalendar().getUserId(),
                        event.getGoogleCalendarId(),
                        event.getTitle(),
                        cleanedDescription);  // ✅ cleaned, not raw event.getDescription()
                log.info("Google Calendar updated after Zoom removal. Event ID: {}", id);
            } catch (IOException e) {
                if (GoogleAuthService.isInvalidGrant(e)) {
                    log.error("Google token revoked while removing Zoom link for user {}", userId);
                    if (googleAuthService != null) googleAuthService.markUserAsDisconnected(userId);
                    event.setSyncStatus(SyncStatus.AUTH_ERROR);
                } else {
                    log.error("Failed to update Google Calendar after removing Zoom. Event ID: {}", event.getId(), e);
                    event.setSyncStatus(SyncStatus.PENDING);
                }
                eventRepository.save(event);
            }
        }

        log.info("Zoom meeting fully removed from event. Event ID: {}", id);
    }

    public EventResponse getEventById(String id) {
        if (id == null || id.isBlank()) {
            throw new EmptyFieldException("Event ID cannot be empty");
        }

        return eventRepository.findById(id)
                .map(eventMapper::toEventResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + id));
    }

    public Event getEventByIdTypeEvent(String id) {
        if (id == null || id.isBlank()) {
            throw new EmptyFieldException("Event ID cannot be empty");
        }

        return eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + id));
    }

    public List<EventResponse> getEventsByCalendarId(String calendarId) {
        if (calendarId == null || calendarId.isBlank()) {
            throw new EmptyFieldException("Calendar ID cannot be empty");
        }

        // Validate calendar exists
        calendarRepository.findById(calendarId)
                .orElseThrow(() -> new ResourceNotFoundException("Calendar not found with ID: " + calendarId));

        return eventRepository.findByCalendarId(calendarId)
                .stream()
                .map(eventMapper::toEventResponse)
                .collect(Collectors.toList());
    }

    public List<EventResponse> getEventsByUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new EmptyFieldException("User ID cannot be empty");
        }

        List<EventResponse> userEvents = eventRepository.findByCalendar_UserId(userId)
                .stream()
                .map(eventMapper::toEventResponse)
                .toList();

        if (userEvents.isEmpty()) {
            throw new ResourceNotFoundException("No events found for user: " + userId);
        }

        return userEvents;
    }

    public List<EventResponse> getEventsByDateRange(String calendarId, LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            throw new EmptyFieldException("Start and end dates cannot be null");
        }
        if (end.isBefore(start)) {
            throw new InvalidTimeRangeException("End date cannot be before start date");
        }
        return eventRepository.findEventsWithRelations(calendarId, start, end)
                .stream()
                .map(eventMapper::toEventResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void updateEvent(String id, EventUpdateRequest request) {
        if (id == null || id.isBlank()) {
            throw new EmptyFieldException("Event ID cannot be empty");
        }

        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + id));

        // Validate time if being updated
        LocalDateTime newStartTime = request.startTime() != null ? request.startTime() : event.getStartTime();
        LocalDateTime newEndTime = request.endTime() != null ? request.endTime() : event.getEndTime();
        boolean isAllDay = request.allDay() != null ? request.allDay() : event.isAllDay();

        if (!isAllDay && newStartTime != null && newEndTime != null) {
            if (newEndTime.isBefore(newStartTime)) {
                throw new InvalidTimeRangeException("End time cannot be before start time");
            }
        }

        // Update basic event fields
        eventMapper.updateEventFromRequest(event, request);

        // Handle location update
        if (request.location() != null) {
            Location location;
            if (event.getLocation() != null) {
                // Update existing location
                location = event.getLocation();
                locationMapper.updateLocationFromRequest(location, request.location());
                locationRepository.save(location);
            } else {
                // Create new location
                location = locationMapper.toLocation(request.location());
                location = locationRepository.save(location);
                event.setLocation(location);
            }
        }

        // Handle conference update
        if (request.conference() != null) {
            Conference conference;
            if (event.getConference() != null) {
                // Update existing conference
                conference = event.getConference();
                conferenceMapper.updateConferenceFromRequest(conference, request.conference());
                conferenceRepository.save(conference);
            } else {
                // Create new conference
                conference = conferenceMapper.toConference(request.conference(), event);
                conferenceRepository.save(conference);
            }
        }

        eventRepository.save(event);

        // Sync with Google Calendar if event is already synced and service is available
        if (shouldSyncExistingGoogleEvent(event)) {
            try {
                String locationName = event.getLocation() != null ? event.getLocation().getPlaceName() : null;
                String userId = event.getCalendar().getUserId();

                googleCalendarApiService.updateCompleteEvent(
                        userId,
                        event.getGoogleCalendarId(),
                        event.getTitle(),
                        event.getDescription(),
                        event.getStartTime(),
                        event.getEndTime(),
                        locationName,
                        List.of() // No attendees support in current DTO
                );

                event.setSyncStatus(SyncStatus.SYNCED);
                eventRepository.save(event);

                log.info("Event updated and synced to Google Calendar. Event ID: {}, Google Calendar ID: {}",
                        event.getId(), event.getGoogleCalendarId());
            } catch (IOException e) {
                log.error("Failed to sync event update to Google Calendar. Event ID: {}", event.getId(), e);
                event.setSyncStatus(SyncStatus.PENDING);
                eventRepository.save(event);
            }
        }
    }

    @Transactional
    public void deleteEvent(String id) {
        if (id == null || id.isBlank()) {
            throw new EmptyFieldException("Event ID cannot be empty");
        }

        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + id));

        // Delete from Google Calendar if synced and service is available
        if (shouldSyncExistingGoogleEvent(event)) {
            try {
                String userId = event.getCalendar().getUserId();
                googleCalendarApiService.deleteEvent(userId, event.getGoogleCalendarId());
                log.info("Event deleted from Google Calendar. Event ID: {}, Google Calendar ID: {}",
                        event.getId(), event.getGoogleCalendarId());
            } catch (IOException e) {
                log.error("Failed to delete event from Google Calendar. Event ID: {}", event.getId(), e);
                // Continue with local deletion even if Google Calendar deletion fails
            }
        }

        eventRepository.delete(event);
    }

    @Transactional
    public String createEventWithLocation(String calendarId, EventRequest request) {
        String eventId = createEvent(calendarId, request);
        return eventId;
    }

    @Transactional
    public void attachLocation(String eventId, String locationId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + eventId));

        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new ResourceNotFoundException("Location not found with ID: " + locationId));

        event.setLocation(location);
        eventRepository.save(event);
    }

    @Transactional
    public void attachConference(String eventId, String conferenceId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + eventId));

        Conference conference = conferenceRepository.findById(conferenceId)
                .orElseThrow(() -> new ResourceNotFoundException("Conference not found with ID: " + conferenceId));

        if (conference.getEvent() != null && !conference.getEvent().getId().equals(eventId)) {
            throw new ConflictException("Conference is already attached to another event");
        }

        conference.setEvent(event);
        conferenceRepository.save(conference);
    }

    @Transactional
    public void syncEventsFromGoogle(String calendarId) {
        if (calendarId == null || calendarId.isBlank()) {
            throw new EmptyFieldException("Calendar ID cannot be empty");
        }

        Calendar calendar = calendarRepository.findById(calendarId)
                .orElseThrow(() -> new ResourceNotFoundException("Calendar not found with ID: " + calendarId));
        String userId = calendar.getUserId();

        if (googleCalendarApiService == null) {
            throw new ServiceUnavailableException("Google Calendar integration is disabled");
        }

        try {
            log.info("Starting incremental sync for calendar {} (user {}) using token: {}",
                    calendarId, userId, calendar.getGoogleSyncToken());

            GoogleCalendarApiService.EventsResult syncResult = googleCalendarApiService
                    .getEventsWithSyncToken(userId, calendar.getGoogleSyncToken());

            List<com.google.api.services.calendar.model.Event> googleEvents = syncResult.getEvents();
            log.info("Fetched {} changed events from Google Calendar for user {}", googleEvents.size(), userId);

            for (com.google.api.services.calendar.model.Event googleEvent : googleEvents) {
                try {
                    if ("cancelled".equals(googleEvent.getStatus())) {
                        eventRepository.findByGoogleCalendarId(googleEvent.getId())
                                .ifPresent(event -> {
                                    log.info("Deleting cancelled Google event: {}", event.getId());
                                    eventRepository.delete(event);
                                });
                        continue;
                    }

                    if (googleEvent.getStart() == null) {
                        log.debug("Skipping Google event with no start time: {}", googleEvent.getId());
                        continue;
                    }

                    java.util.Optional<Event> existingEventOpt = eventRepository
                            .findByGoogleCalendarId(googleEvent.getId());

                    if (existingEventOpt.isPresent()) {
                        updateLocalEventFromGoogle(existingEventOpt.get(), googleEvent);
                    } else {
                        log.info("Creating new local event from Google event: {}", googleEvent.getId());
                        createLocalEventFromGoogle(calendar, googleEvent);
                    }
                } catch (Exception e) {
                    log.error("Failed to process Google event {}: {}", googleEvent.getId(), e.getMessage());
                }
            }

            // Update sync metadata
            calendar.setGoogleSyncToken(syncResult.getNextSyncToken());
            calendar.setLastGoogleSync(LocalDateTime.now());
            calendarRepository.save(calendar);

        } catch (IOException e) {
            if (GoogleAuthService.isInvalidGrant(e)) {
                // Terminal — the refresh token was permanently revoked.
                // Mark the user disconnected so the scheduler stops retrying.
                log.error("Google token permanently revoked for user {} during sync. "
                        + "User must re-authorize. Calendar ID: {}", userId, calendarId);
                if (googleAuthService != null) {
                    googleAuthService.markUserAsDisconnected(userId);
                }
                // Propagate as GoogleTokenRevokedException so the scheduler
                // can catch it specifically and continue with other users.
                throw new GoogleTokenRevokedException(userId, e);
            }
            // Transient failure — let the scheduler log it and retry next cycle.
            log.error("Transient Google Calendar sync failure for user: {}, calendar: {}",
                    userId, calendarId, e);
            throw new ServiceUnavailableException("Failed to sync from Google Calendar", e);
        }
    }

    private void createLocalEventFromGoogle(Calendar calendar,
            com.google.api.services.calendar.model.Event googleEvent) {
        Event event = new Event();
        event.setCalendar(calendar);
        event.setGoogleCalendarId(googleEvent.getId());
        if (googleEvent.getUpdated() != null) {
            event.setGoogleUpdatedAt(convertGoogleDateTimeToLocalDateTime(googleEvent.getUpdated()));
        }
        updateEventFieldsFromGoogle(event, googleEvent);
        event.setCreatedAt(LocalDateTime.now());
        event.setStatus(EventStatus.NOTSTARTED);

        eventRepository.save(event);

        // Sync attendees (including organizers) as members
        syncAttendeesFromGoogle(event, googleEvent);
    }

    private void updateLocalEventFromGoogle(Event event, com.google.api.services.calendar.model.Event googleEvent) {
        if (googleEvent.getUpdated() != null) {
            LocalDateTime googleUpdated = convertGoogleDateTimeToLocalDateTime(googleEvent.getUpdated());
            if (event.getGoogleUpdatedAt() != null && !googleUpdated.isAfter(event.getGoogleUpdatedAt())) {
                log.debug("Skipping update for event {} - no changes on Google side", event.getId());
                return;
            }
            event.setGoogleUpdatedAt(googleUpdated);
        }

        log.info("Updating existing event from Google: {}", event.getId());
        updateEventFieldsFromGoogle(event, googleEvent);
        eventRepository.save(event);

        // Sync attendees
        syncAttendeesFromGoogle(event, googleEvent);
    }

    private void updateEventFieldsFromGoogle(Event event, com.google.api.services.calendar.model.Event googleEvent) {
        event.setTitle(googleEvent.getSummary() != null ? googleEvent.getSummary() : "(No Title)");
        event.setDescription(googleEvent.getDescription() != null ? googleEvent.getDescription() : "");
        event.setColorHex("#3788d8"); // fallback when no Google color is available
        event.setType(EventType.OTHERS); // Default

        // Try to map color ID to type if possible, or just leave as OTHERS
        // 1=Lavender, 2=Sage, 3=Grape, 4=Flamingo, 5=Banana,
        // 6=Tangerine, 7=Peacock, 8=Graphite, 9=Blueberry, 10=Basil, 11=Tomato
        // Types: MEETING(1), WORK(2), APPOINTMENT(3), REMINDER(4), TASK(5),
        // BIRTHDAY(6), HOLIDAY(7), ROUTE(8), OTHERS(9), STUDY(11)
        if (googleEvent.getColorId() != null) {
            event.setColorHex(mapGoogleColorIdToHex(googleEvent.getColorId()));
            switch (googleEvent.getColorId()) {
                case "1" -> event.setType(EventType.MEETING);
                case "2" -> event.setType(EventType.WORK);
                case "3" -> event.setType(EventType.APPOINTMENT);
                case "4" -> event.setType(EventType.REMINDER);
                case "5" -> event.setType(EventType.TASK);
                case "6" -> event.setType(EventType.BIRTHDAY);
                case "7" -> event.setType(EventType.HOLIDAY);
                case "8" -> event.setType(EventType.ROUTE);
                case "9" -> event.setType(EventType.OTHERS);
                case "11" -> event.setType(EventType.STUDY);
            }
        }

        // Location
        if (googleEvent.getLocation() != null && !googleEvent.getLocation().isBlank()) {
            String address = googleEvent.getLocation();
            Location location = locationRepository.findByStreetAddress(address)
                    .orElseGet(() -> {
                        Location newLocation = new Location();
                        newLocation.setPlaceName(address);
                        newLocation.setStreetAddress(address);
                        return locationRepository.save(newLocation);
                    });
            event.setLocation(location);
        } else {
            event.setLocation(null);
        }

        // Conference / Meeting details
        if (googleEvent.getConferenceData() != null || googleEvent.getHangoutLink() != null) {
            Conference conference = event.getConference();
            if (conference == null) {
                conference = new Conference();
                conference.setSyncStatus(Conference.SyncStatus.SYNCED);
                conference.setLastSynced(LocalDateTime.now());
            }

            if (googleEvent.getConferenceData() != null) {
                conference.setType(Conference.ConferenceType.GOOGLE_MEET);
                conference.setPlatformName("Google Meet");
                conference.setGoogleConferenceId(googleEvent.getConferenceData().getConferenceId());

                // Get Meet Link from entry points
                if (googleEvent.getConferenceData().getEntryPoints() != null) {
                    for (com.google.api.services.calendar.model.EntryPoint entryPoint : googleEvent.getConferenceData()
                            .getEntryPoints()) {
                        if ("video".equals(entryPoint.getEntryPointType())) {
                            conference.setMeetLink(entryPoint.getUri());
                            conference.setConferenceLink(entryPoint.getUri());
                        } else if ("phone".equals(entryPoint.getEntryPointType())) {
                            conference.setPhoneNumber(entryPoint.getLabel());
                            conference.setPin(entryPoint.getPin());
                        }
                    }
                }
            } else if (googleEvent.getHangoutLink() != null) {
                conference.setType(Conference.ConferenceType.GOOGLE_MEET);
                conference.setPlatformName("Google Meet");
                conference.setMeetLink(googleEvent.getHangoutLink());
                conference.setConferenceLink(googleEvent.getHangoutLink());
            }

            conference = conferenceRepository.save(conference);
            event.setConference(conference);
        }

        // Time
        if (googleEvent.getStart().getDate() != null) {
            // All day
            event.setAllDay(true);
            event.setStartTime(convertGoogleDateToLocalDateTime(googleEvent.getStart().getDate()));
            event.setEndTime(convertGoogleDateToLocalDateTime(googleEvent.getEnd().getDate()).minusDays(1));
        } else {
            // Timed
            event.setAllDay(false);
            event.setStartTime(convertGoogleDateTimeToLocalDateTime(googleEvent.getStart().getDateTime()));
            event.setEndTime(convertGoogleDateTimeToLocalDateTime(googleEvent.getEnd().getDateTime()));
        }

        event.setSyncStatus(SyncStatus.SYNCED);
    }

    private void syncAttendeesFromGoogle(Event event, com.google.api.services.calendar.model.Event googleEvent) {
        // Keep Hibernate-managed collection instance; orphanRemoval expects in-place
        // mutation.
        if (event.getMembers() != null) {
            event.getMembers().clear();
        }

        List<Member> members = new ArrayList<>();

        // Add Organizer as owner if applicable
        if (googleEvent.getOrganizer() != null) {
            members.add(Member.builder()
                    .fullName(googleEvent.getOrganizer().getDisplayName() != null
                            ? googleEvent.getOrganizer().getDisplayName()
                            : "Organizer")
                    .email(googleEvent.getOrganizer().getEmail())
                    .isOwner(true)
                    .event(event)
                    .build());
        } else {
            // Fallback owner
            members.add(Member.builder()
                    .fullName("Owner")
                    .email("Owner")
                    .isOwner(true)
                    .event(event)
                    .build());
        }

        // Add Attendees
        if (googleEvent.getAttendees() != null) {
            for (com.google.api.services.calendar.model.EventAttendee attendee : googleEvent.getAttendees()) {
                // Skip if attendee is the organizer (already added as owner)
                if (googleEvent.getOrganizer() != null &&
                        attendee.getEmail().equals(googleEvent.getOrganizer().getEmail())) {
                    continue;
                }

                members.add(Member.builder()
                        .fullName(attendee.getDisplayName() != null ? attendee.getDisplayName() : attendee.getEmail())
                        .email(attendee.getEmail())
                        .isOwner(Boolean.TRUE.equals(attendee.getOrganizer()))
                        .event(event)
                        .build());
            }
        }

        if (event.getMembers() == null) {
            event.setMembers(new HashSet<>());
        }
        event.getMembers().addAll(members);
    }

    private LocalDateTime convertGoogleDateTimeToLocalDateTime(com.google.api.client.util.DateTime dateTime) {
        return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(dateTime.getValue()),
                java.time.ZoneId.systemDefault());
    }

    private LocalDateTime convertGoogleDateToLocalDateTime(com.google.api.client.util.DateTime dateTime) {
        // Date only, treat as start of day system default
        // dateTime.getValue() returns millis.
        // Google client DateTime(true, value, tz) for date-only sometimes has specific
        // parsing.
        // Safest is to use value.
        // Notes: Google Calendar API 'date' field is yyyy-mm-dd. DateTime parses it to
        // midnight UTC or similar.
        // But getValue() gives millis.
        // We use systemDefault() to be consistent with other parts of the app.
        return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(dateTime.getValue()),
                java.time.ZoneId.systemDefault());
    }

    private void normalizeAllDayEventTimes(Event event, EventRequest request) {
        if (event == null || request == null || !request.allDay()) {
            return;
        }

        LocalDateTime start = event.getStartTime();
        if (start == null) {
            if (request.date() == null) {
                throw new EmptyFieldException("All-day events require either date or startTime");
            }
            start = request.date().atStartOfDay();
            event.setStartTime(start);
        }

        if (event.getEndTime() == null || !event.getEndTime().isAfter(start)) {
            event.setEndTime(start.plusDays(1));
        }
    }

    private boolean shouldSyncToGoogle(Calendar calendar) {
        if (googleCalendarApiService == null || calendar == null || calendar.getUserId() == null) {
            return false;
        }

        if (googleAuthService != null && !googleAuthService.isGoogleConnected(calendar.getUserId())) {
            return false;
        }

        try {
            String primaryGoogleCalendarId = googleCalendarApiService.getPrimaryCalendarId(calendar.getUserId());
            if (primaryGoogleCalendarId == null || primaryGoogleCalendarId.isBlank()) {
                return false;
            }

            if (calendar.getGoogleCalendarId() != null && !calendar.getGoogleCalendarId().isBlank()) {
                return primaryGoogleCalendarId.equalsIgnoreCase(calendar.getGoogleCalendarId());
            }

            // Resolve any existing designated Google sync calendar for this user.
            java.util.List<Calendar> userCalendars = calendarRepository
                    .findCalendarByUserIdAndIsDeletedFalse(calendar.getUserId());
            Calendar designatedSyncCalendar = userCalendars.stream()
                    .filter(c -> c.getGoogleCalendarId() != null
                            && !c.getGoogleCalendarId().isBlank()
                            && primaryGoogleCalendarId.equalsIgnoreCase(c.getGoogleCalendarId()))
                    .findFirst()
                    .orElseGet(() -> userCalendars.stream()
                            .filter(c -> c.getTitle() != null && primaryGoogleCalendarId.equalsIgnoreCase(c.getTitle()))
                            .findFirst()
                            .orElseGet(() -> userCalendars.stream()
                                    .filter(c -> c.getDescription() != null
                                            && c.getDescription().startsWith("Google sync calendar for "))
                                    .findFirst()
                                    .orElse(null)));

            if (designatedSyncCalendar != null) {
                if (!designatedSyncCalendar.getId().equals(calendar.getId())) {
                    return false;
                }
                designatedSyncCalendar.setGoogleCalendarId(primaryGoogleCalendarId);
                calendarRepository.save(designatedSyncCalendar);
                return true;
            }

            // No designated sync calendar found. Auto-bind current calendar to avoid silent
            // local-only event creation.
            calendar.setGoogleCalendarId(primaryGoogleCalendarId);
            calendarRepository.save(calendar);
            return true;

        } catch (IOException e) {
            if (GoogleAuthService.isInvalidGrant(e)) {
                log.error("Google token revoked while resolving sync calendar for user {}.", calendar.getUserId());
                if (googleAuthService != null) {
                    googleAuthService.markUserAsDisconnected(calendar.getUserId());
                }
            } else {
                log.warn("Skipping Google sync for calendar {}. Could not resolve Google sync calendar: {}",
                        calendar.getId(), e.getMessage());
            }
            return false;
        }
    }

    private boolean shouldSyncExistingGoogleEvent(Event event) {
        if (googleCalendarApiService == null
                || event == null
                || event.getGoogleCalendarId() == null
                || event.getGoogleCalendarId().isBlank()) {
            return false;
        }
        return googleAuthService == null || googleAuthService.isGoogleConnected(event.getCalendar().getUserId());
    }

    public void pushUnsyncedLocalEventsToGoogle(Calendar calendar) {
        if (googleCalendarApiService == null || calendar == null || calendar.getUserId() == null) {
            return;
        }

        List<Event> unsyncedEvents = eventRepository.findByCalendarIdAndSyncStatusIn(
                calendar.getId(),
                List.of(SyncStatus.AUTH_ERROR, SyncStatus.PENDING));

        if (unsyncedEvents.isEmpty()) {
            return;
        }

        log.info("Found {} unsynced events (AUTH_ERROR / PENDING) for calendar {}. Attempting to push to Google.",
                unsyncedEvents.size(), calendar.getId());

        String userId = calendar.getUserId();

        for (Event event : unsyncedEvents) {
            try {
                com.google.api.services.calendar.model.Event googleEvent;

                if (event.isAllDay()) {
                    // Create all-day event
                    googleEvent = googleCalendarApiService.createAllDayEvent(
                            userId,
                            event.getTitle(),
                            event.getDescription(),
                            event.getLocation(),
                            event.getStartTime());
                } else {
                    // Create simple event
                    googleEvent = googleCalendarApiService.createEvent(
                            userId,
                            event.getTitle(),
                            event.getDescription(),
                            event.getLocation(),
                            event.getStartTime(),
                            event.getEndTime(),
                            event.getType().toString());
                }

                // If event had a local zoom conference, append it to description in Google side
                // Update event with Google Calendar ID
                event.setGoogleCalendarId(googleEvent.getId());
                event.setSyncStatus(SyncStatus.SYNCED);
                eventRepository.save(event);

                log.info("Successfully pushed previously unsynced event {} to Google Calendar. Google ID: {}",
                        event.getId(), googleEvent.getId());

            } catch (IOException e) {
                if (GoogleAuthService.isInvalidGrant(e)) {
                    log.error("Google token revoked while pushing unsynced event {} for user {}.",
                            event.getId(), userId);
                    if (googleAuthService != null) {
                        googleAuthService.markUserAsDisconnected(userId);
                    }
                    event.setSyncStatus(SyncStatus.AUTH_ERROR);
                    eventRepository.save(event);
                    return; // Stop trying this user's events immediately to avoid spam
                } else {
                    log.error("Transient error pushing unsynced event {} to Google Calendar: {}. Keeping as PENDING.",
                            event.getId(), e.getMessage());
                    event.setSyncStatus(SyncStatus.PENDING);
                    eventRepository.save(event);
                }
            }
        }
    }

    private String mapGoogleColorIdToHex(String colorId) {
        return switch (colorId) {
            case "1" -> "#a4bdfc";
            case "2" -> "#7ae7bf";
            case "3" -> "#dbadff";
            case "4" -> "#ff887c";
            case "5" -> "#fbd75b";
            case "6" -> "#ffb878";
            case "7" -> "#46d6db";
            case "8" -> "#e1e1e1";
            case "9" -> "#5484ed";
            case "10" -> "#51b749";
            case "11" -> "#dc2127";
            default -> "#3788d8";
        };
    }
}
