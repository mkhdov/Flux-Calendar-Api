package com.flux.calendar_service.event;

import com.flux.calendar_service.calendar.Calendar;
import com.flux.calendar_service.calendar.CalendarRepository;
import com.flux.calendar_service.conference.Conference;
import com.flux.calendar_service.conference.ConferenceMapper;
import com.flux.calendar_service.conference.ConferenceRepository;
import com.flux.calendar_service.event.dto.EventRequest;
import com.flux.calendar_service.event.dto.EventResponse;
import com.flux.calendar_service.event.dto.EventUpdateRequest;
import com.flux.calendar_service.google.GoogleCalendarApiService;
import com.flux.calendar_service.location.Location;
import com.flux.calendar_service.location.LocationMapper;
import com.flux.calendar_service.location.LocationRepository;
import com.flux.calendar_service.exception.*;
import com.flux.calendar_service.member.Member;
import com.flux.calendar_service.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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

    @Autowired(required = false)
    private GoogleCalendarApiService googleCalendarApiService;

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

        // Save event first to get the ID
        if (event.getSubtasks() != null) {
            event.getSubtasks().forEach(subtask -> subtask.setEvent(event));
        }
        if (event.getAttachments() != null) {
            event.getAttachments().forEach(attachment -> attachment.setEvent(event));
        }
        Event savedEvent = eventRepository.save(event);

        // Sync with Google Calendar if enabled
        if (googleCalendarApiService != null) {
            try {
                com.google.api.services.calendar.model.Event googleEvent;
                String userId = calendar.getUserId();

                if (savedEvent.isAllDay()) {
                    // Create all-day event
                    googleEvent = googleCalendarApiService.createAllDayEvent(
                            userId,
                            savedEvent.getTitle(),
                            savedEvent.getDescription(),
                            savedEvent.getStartTime());
                } else {
                    // Create simple event
                    googleEvent = googleCalendarApiService.createEvent(
                            userId,
                            savedEvent.getTitle(),
                            savedEvent.getDescription(),
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
                log.error("Failed to sync event to Google Calendar. Event ID: {}", savedEvent.getId(), e);
                savedEvent.setSyncStatus(SyncStatus.PENDING);
                eventRepository.save(savedEvent);
            }
        } else {
            log.debug("Google Calendar integration is disabled. Event created locally only.");
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
        if (event.getSubtasks() != null) {
            event.getSubtasks().forEach(subtask -> subtask.setEvent(event));
        }
        if (event.getAttachments() != null) {
            event.getAttachments().forEach(attachment -> attachment.setEvent(event));
        }
        Event savedEvent = eventRepository.save(event);

        // 4️⃣ Google sync (optional)
        if (googleCalendarApiService == null) {
            log.debug("Google Calendar integration disabled. Event created locally only.");
            return savedEvent.getId();
        }

        try {
            String userId = calendar.getUserId();
            com.google.api.services.calendar.model.Event googleEvent;

            if (savedEvent.isAllDay()) {
                // ✔ All-day → NO Meet
                googleEvent = googleCalendarApiService.createAllDayEvent(
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
                        savedEvent.getStartTime(),
                        savedEvent.getEndTime(),
                        savedEvent.getType().toString());
            }

            // 5️⃣ Extract Meet details (timed events only)
            String googleConferenceId = null;
            String meetLink = null;
            String meetingCode = null;
            String phoneNumber = null;
            String pin = null;

            if (!savedEvent.isAllDay() && googleEvent.getConferenceData() != null) {
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
                    .event(savedEvent)
                    .build();

            // 6️⃣ Update and save local event (cascade will save conference)
            savedEvent.setGoogleCalendarId(googleEvent.getId());
            savedEvent.setSyncStatus(SyncStatus.SYNCED);
            savedEvent.setConference(conference);

            eventRepository.save(savedEvent);

            log.info(
                    "Event synced. Local ID: {}, Google ID: {}, Meet: {}",
                    savedEvent.getId(),
                    googleEvent.getId(),
                    meetLink);

        } catch (IOException e) {
            log.error("Failed to sync event to Google Calendar. Event ID: {}",
                    savedEvent.getId(), e);

            savedEvent.setSyncStatus(SyncStatus.PENDING);
            eventRepository.save(savedEvent);
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

        if (googleCalendarApiService != null && event.getGoogleCalendarId() != null) {
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
        Event savedEvent = eventRepository.save(event);

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
        if (googleCalendarApiService != null) {
            try {
                String description = savedEvent.getDescription();
                if (zoomConference != null) {
                    description = (description != null ? description : "") + "\n\nZoom Meeting: "
                            + zoomConference.getConferenceLink();
                }

                com.google.api.services.calendar.model.Event googleEvent = googleCalendarApiService.createEvent(
                        userId,
                        savedEvent.getTitle(),
                        description,
                        savedEvent.getStartTime(),
                        savedEvent.getEndTime(),
                        savedEvent.getType().toString());

                savedEvent.setGoogleCalendarId(googleEvent.getId());
                savedEvent.setSyncStatus(SyncStatus.SYNCED);
                eventRepository.save(savedEvent);

                log.info("Event synced to Google Calendar. Event ID: {}, Google ID: {}", savedEvent.getId(),
                        googleEvent.getId());
            } catch (IOException e) {
                log.error("Failed to sync event to Google Calendar for event {}: {}", savedEvent.getId(),
                        e.getMessage());
                savedEvent.setSyncStatus(SyncStatus.PENDING);
                eventRepository.save(savedEvent);
            }
        } else {
            log.debug("Google Calendar integration is disabled.");
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
            eventRepository.save(event);

            log.info("Zoom meeting added to event. Event ID: {}, Zoom Meeting ID: {}",
                    event.getId(), conference.getZoomMeetingId());

            // Sync with Google Calendar if enabled
            if (googleCalendarApiService != null && event.getGoogleCalendarId() != null) {
                String description = event.getDescription() + "\n\nZoom Meeting: " + conference.getConferenceLink();
                googleCalendarApiService.updateEvent(
                        event.getCalendar().getUserId(),
                        event.getGoogleCalendarId(),
                        event.getTitle(),
                        description);
            }

        } catch (Exception e) {
            log.error("Failed to add Zoom meeting to event. Event ID: {}", event.getId(), e);
            throw new ServiceUnavailableException("Failed to add Zoom meeting", e);
        }
    }

    @Transactional
    public void removeZoomFromEvent(String id, String userId) {
        if (id == null || id.isBlank()) {
            throw new EmptyFieldException("Event ID cannot be empty");
        }

        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + id));

        if (event.getConference() == null) {
            return; // No conference to remove
        }

        if (!Conference.ConferenceType.ZOOM.equals(event.getConference().getType())) {
            throw new InvalidOperationException("Event does not have a Zoom meeting");
        }

        if (zoomMeetingService != null) {
            try {
                zoomMeetingService.deleteZoomMeeting(event.getId(), userId);
                log.info("Zoom meeting removed from event. Event ID: {}", id);
            } catch (Exception e) {
                log.error("Failed to remove Zoom meeting from Zoom servers. Event ID: {}", event.getId(), e);
                // Continue with local deletion even if Zoom deletion fails
            }
        }

        // Sync with Google Calendar to remove Zoom details
        if (googleCalendarApiService != null && event.getGoogleCalendarId() != null) {
            try {
                // Restore original description (without Zoom link)
                googleCalendarApiService.updateEvent(
                        event.getCalendar().getUserId(),
                        event.getGoogleCalendarId(),
                        event.getTitle(),
                        event.getDescription());
            } catch (IOException e) {
                log.error("Failed to update Google Calendar event after removing Zoom. Event ID: {}", event.getId(), e);
            }
        }

        // Remove conference locally (already deleted by ZoomMeetingService)
        // Just log the completion
        log.info("Zoom meeting removed from event locally. Event ID: {}", id);
    }

    public List<EventResponse> getAllEvents() {
        return eventRepository.findAll()
                .stream()
                .map(eventMapper::toEventResponse)
                .collect(Collectors.toList());
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

    public List<EventResponse> getEventsByDateRange(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            throw new EmptyFieldException("Start and end dates cannot be null");
        }
        if (end.isBefore(start)) {
            throw new InvalidTimeRangeException("End date cannot be before start date");
        }
        return eventRepository.findByStartTimeBetween(start, end)
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
        if (googleCalendarApiService != null && event.getGoogleCalendarId() != null) {
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
        if (googleCalendarApiService != null && event.getGoogleCalendarId() != null) {
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
            log.error("Failed to sync events from Google Calendar for user: {}", userId, e);
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
        event.setColorHex("#3788d8"); // Google Blue default
        event.setType(EventType.OTHERS); // Default

        // Try to map color ID to type if possible, or just leave as OTHERS
        // 1=Lavender, 2=Sage, 3=Grape, 4=Flamingo, 5=Banana,
        // 6=Tangerine, 7=Peacock, 8=Graphite, 9=Blueberry, 10=Basil, 11=Tomato
        // Types: MEETING(1), WORK(2), APPOINTMENT(3), REMINDER(4), TASK(5),
        // BIRTHDAY(6), HOLIDAY(7), ROUTE(8), OTHERS(9), STUDY(11)
        if (googleEvent.getColorId() != null) {
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
        // Clear existing members if it's an update to avoid duplicates
        // (Alternatively, perform a merge, but clearing is safer for simple sync)
        if (event.getMembers() != null && !event.getMembers().isEmpty()) {
            memberRepository.deleteAll(event.getMembers());
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

        memberRepository.saveAll(members);
        event.setMembers(members);
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
}