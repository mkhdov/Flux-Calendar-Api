package com.flux.calendar_service.event;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.flux.calendar_service.attachment.Attachment;
import com.flux.calendar_service.attachment.AttachmentRepository;
import com.flux.calendar_service.calendar.Calendar;
import com.flux.calendar_service.calendar.CalendarRepository;
import com.flux.calendar_service.conference.Conference;
import com.flux.calendar_service.conference.ConferenceMapper;
import com.flux.calendar_service.conference.ConferenceRepository;
import com.flux.calendar_service.event.dto.*;
import com.flux.calendar_service.exception.ConflictException;
import org.springframework.dao.DataIntegrityViolationException;
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
import com.flux.calendar_service.note.Note;
import com.flux.calendar_service.note.NoteRepository;
import com.flux.calendar_service.subtask.Subtask;
import com.flux.calendar_service.subtask.SubtaskRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventService {
    static final String EVENTS_BY_RANGE_CACHE = "events:by-range";
    static final String EVENTS_BY_MONTH_CACHE = "events:month";
    static final String EVENTS_DETAIL_CACHE = "events:detail";

    private final EventRepository eventRepository;
    private final EventMapper eventMapper;
    private final AttachmentRepository attachmentRepository;
    private final CalendarRepository calendarRepository;
    private final LocationRepository locationRepository;
    private final LocationMapper locationMapper;
    private final ConferenceRepository conferenceRepository;
    private final ConferenceMapper conferenceMapper;
    private final MemberRepository memberRepository;
    private final LocationService locationService;
    private final NoteRepository noteRepository;
    private final SubtaskRepository subtaskRepository;
    private final EventRangeValidator rangeValidator;

    @Autowired(required = false)
    private GoogleCalendarApiService googleCalendarApiService;

    @Autowired(required = false)
    private GoogleAuthService googleAuthService;

    @Autowired(required = false)
    private com.flux.calendar_service.zoom.ZoomMeetingService zoomMeetingService;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    // private String currentUserId() {
    // return SecurityContextHolder.getContext().getAuthentication().getName();
    // }

    @Transactional
    @CacheEvict(cacheNames = { EVENTS_BY_RANGE_CACHE, EVENTS_BY_MONTH_CACHE }, allEntries = true)
    public List<String> createEvent(String calendarId, EventRequest request) {
        if (calendarId == null || calendarId.isBlank()) {
            throw new EmptyFieldException("Calendar ID cannot be empty");
        }

        Calendar calendar = calendarRepository.findById(calendarId)
                .orElseThrow(() -> new ResourceNotFoundException("Calendar not found with ID: " + calendarId));

        if (request.planner() != null) {
            return switchToPlannedMode(request, calendar);
        }

        if (!request.allDay() && request.startTime() != null && request.endTime() != null) {
            if (request.endTime().isBefore(request.startTime())) {
                throw new InvalidTimeRangeException("End time cannot be before start time");
            }
        }

        Event event = eventMapper.toEvent(request, calendar);
        normalizeAllDayEventTimes(event, request);

        return List.of(saveSingleEventInternal(calendar, event, request));
    }

    private String saveSingleEventInternal(Calendar calendar, Event event, EventRequest request) {
        if (event.getSubtasks() != null) {
            event.getSubtasks().forEach(subtask -> subtask.setEvent(event));
        }
        if (event.getAttachments() != null) {
            event.getAttachments().forEach(attachment -> attachment.setEvent(event));
        }

        Event savedEvent = eventRepository.save(event);

        if (request.location() != null) {
            Location location = locationService.create(savedEvent.getId(), request.location());
            savedEvent.setLocation(location);
            savedEvent = eventRepository.save(savedEvent);
        }

        if (shouldSyncToGoogle(calendar)) {
            try {
                com.google.api.services.calendar.model.Event googleEvent;
                String userId = calendar.getUserId();

                if (savedEvent.isAllDay()) {
                    googleEvent = googleCalendarApiService.createAllDayEvent(
                            userId,
                            savedEvent.getTitle(),
                            savedEvent.getDescription(),
                            savedEvent.getLocation(),
                            savedEvent.getStartTime());
                } else {
                    googleEvent = googleCalendarApiService.createEvent(
                            userId,
                            savedEvent.getTitle(),
                            savedEvent.getDescription(),
                            savedEvent.getLocation(),
                            savedEvent.getStartTime(),
                            savedEvent.getEndTime(),
                            savedEvent.getType().toString());
                }

                savedEvent.setGoogleCalendarId(googleEvent.getId());
                savedEvent.setSyncStatus(SyncStatus.SYNCED);
                eventRepository.save(savedEvent);

                log.info("Event created and synced to Google Calendar. Event ID: {}, Google Calendar ID: {}",
                        savedEvent.getId(), googleEvent.getId());
            } catch (IOException e) {
                if (GoogleAuthService.isInvalidGrant(e)) {
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

        if (!shouldSyncToGoogle(calendar)) {
            Member member = Member.builder()
                    .fullName("Owner")
                    .email("Owner")
                    .isOwner(true)
                    .event(savedEvent)
                    .build();
            memberRepository.save(member);
        }

        // ─── Notification / Reminder Creation (Kafka) ───
        // Run AFTER commit to avoid race conditions with notification-service
        final Event finalSavedEvent = savedEvent;
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendReminderNotification(finalSavedEvent, calendar, request);
                }
            });
        } else {
            sendReminderNotification(savedEvent, calendar, request);
        }

        return savedEvent.getId();
    }

    private List<String> switchToPlannedMode(EventRequest request, Calendar calendar) {
        PlannerRequest planner = request.planner();
        List<String> createdIds = new ArrayList<>();

        LocalDate startDate = request.date();
        if (startDate == null && request.startTime() != null) {
            startDate = request.startTime().atZone(ZoneId.of("UTC")).toLocalDate();
        }

        if (startDate == null) {
            throw new EmptyFieldException("Start date or start time is required for planner mode");
        }

        List<DayOfWeek> selectedDays = getTargetDays(planner);

        for (int w = 0; w < planner.numOfWeeks(); w++) {
            for (DayOfWeek day : selectedDays) {
                LocalDate targetDate = startDate.plusWeeks(w).with(TemporalAdjusters.nextOrSame(day));

                Instant newStart = null;
                Instant newEnd = null;

                if (request.startTime() != null) {
                    ZonedDateTime zdtStart = request.startTime().atZone(ZoneId.of("UTC"));
                    newStart = zdtStart.withYear(targetDate.getYear())
                            .withMonth(targetDate.getMonthValue())
                            .withDayOfMonth(targetDate.getDayOfMonth())
                            .toInstant();
                }

                if (request.endTime() != null && request.startTime() != null) {
                    long durationSeconds = java.time.Duration.between(request.startTime(), request.endTime())
                            .getSeconds();
                    newEnd = newStart.plusSeconds(durationSeconds);
                }

                EventRequest subRequest = new EventRequest(
                        request.title(),
                        request.description(),
                        request.colorHex(),
                        request.type(),
                        targetDate,
                        newStart,
                        newEnd,
                        request.allDay(),
                        request.status(),
                        request.subtasks(),
                        request.attachments(),
                        request.location(),
                        null,
                        request.notification());

                Event event = eventMapper.toEvent(subRequest, calendar);
                normalizeAllDayEventTimes(event, subRequest);

                createdIds.add(saveSingleEventInternal(calendar, event, subRequest));
            }
        }
        return createdIds;
    }

    private List<String> switchToPlannedModeWithMeet(EventRequest request, Calendar calendar) {
        List<String> createdIds = new ArrayList<>();
        PlannerRequest planner = request.planner();
        List<DayOfWeek> targetDays = getTargetDays(planner);

        Instant baseStart = request.startTime();
        Instant baseEnd = request.endTime();
        long durationMillis = java.time.Duration.between(baseStart, baseEnd).toMillis();

        for (int week = 0; week < planner.numOfWeeks(); week++) {
            for (DayOfWeek day : targetDays) {
                Instant currentStart = baseStart.atZone(ZoneOffset.UTC)
                        .with(TemporalAdjusters.nextOrSame(day))
                        .plusWeeks(week)
                        .toInstant();
                Instant currentEnd = currentStart.plusMillis(durationMillis);

                EventRequest newReq = new EventRequest(
                        request.title(), request.description(), request.colorHex(),
                        request.type(), request.date(),
                        currentStart, currentEnd, request.allDay(), request.status(),
                        request.subtasks(), request.attachments(), request.location(), null, request.notification());

                Event event = eventMapper.toEvent(newReq, calendar);
                createdIds.add(saveSingleEventWithMeetInternal(calendar, event, newReq));
            }
        }
        return createdIds;
    }

    private List<String> switchToPlannedModeWithZoom(EventRequest request, Calendar calendar, String userId) {
        List<String> createdIds = new ArrayList<>();
        PlannerRequest planner = request.planner();
        List<DayOfWeek> targetDays = getTargetDays(planner);

        Instant baseStart = request.startTime();
        Instant baseEnd = request.endTime();
        long durationMillis = java.time.Duration.between(baseStart, baseEnd).toMillis();

        for (int week = 0; week < planner.numOfWeeks(); week++) {
            for (DayOfWeek day : targetDays) {
                Instant currentStart = baseStart.atZone(ZoneOffset.UTC)
                        .with(TemporalAdjusters.nextOrSame(day))
                        .plusWeeks(week)
                        .toInstant();
                Instant currentEnd = currentStart.plusMillis(durationMillis);

                EventRequest newReq = new EventRequest(
                        request.title(), request.description(), request.colorHex(),
                        request.type(), request.date(),
                        currentStart, currentEnd, request.allDay(), request.status(),
                        request.subtasks(), request.attachments(), request.location(), null, request.notification());

                Event event = eventMapper.toEvent(newReq, calendar);
                createdIds.add(saveSingleEventWithZoomInternal(calendar, event, newReq, userId));
            }
        }
        return createdIds;
    }

    @Transactional
    @CacheEvict(cacheNames = { EVENTS_BY_RANGE_CACHE, EVENTS_BY_MONTH_CACHE }, allEntries = true)
    public List<String> createEventWithGoogleMeet(String calendarId, EventRequest request) {

        if (calendarId == null || calendarId.isBlank()) {
            throw new EmptyFieldException("Calendar ID cannot be empty");
        }

        Calendar calendar = calendarRepository.findById(calendarId)
                .orElseThrow(() -> new ResourceNotFoundException("Calendar not found with ID: " + calendarId));

        if (request.planner() != null) {
            // For now, if planner is used with Meet, we could either:
            // 1. Create multiple events but each with its own Meet link (simple but many
            // links)
            // 2. Not support it.
            // Let's implement it with multiple Meet links for now to be consistent with
            // individual creation.
            return switchToPlannedModeWithMeet(request, calendar);
        }
        if (!request.allDay() && request.startTime() != null && request.endTime() != null
                && request.endTime().isBefore(request.startTime())) {
            throw new InvalidTimeRangeException("End time cannot be before start time");
        }

        Event event = eventMapper.toEvent(request, calendar);
        return List.of(saveSingleEventWithMeetInternal(calendar, event, request));
    }

    private String saveSingleEventWithMeetInternal(Calendar calendar, Event event, EventRequest request) {
        if (event.getSubtasks() != null) {
            event.getSubtasks().forEach(subtask -> subtask.setEvent(event));
        }
        if (event.getAttachments() != null) {
            event.getAttachments().forEach(attachment -> attachment.setEvent(event));
        }
        Event savedEvent = eventRepository.save(event);

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
                    googleEvent = googleCalendarApiService.createEventWithGoogleMeet(
                            userId,
                            savedEvent.getTitle(),
                            savedEvent.getDescription(),
                            savedEvent.getLocation(),
                            savedEvent.getStartTime(),
                            savedEvent.getEndTime(),
                            savedEvent.getType().toString());
                }

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
                            .lastSynced(Instant.now())
                            .event(savedEvent)
                            .build();
                    savedEvent.setConference(conference);
                } else {
                    savedEvent.setConference(null);
                }

                eventRepository.save(savedEvent);

                log.info("Event synced. Local ID: {}, Google ID: {}, Meet: {}",
                        savedEvent.getId(), googleEvent.getId(), meetLink);

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

        if (!shouldSyncToGoogle(calendar)) {
            Member member = Member.builder()
                    .fullName("Owner")
                    .email("Owner")
                    .isOwner(true)
                    .event(savedEvent)
                    .build();
            memberRepository.save(member);
        }

        final Event finalEvent = savedEvent;
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendReminderNotification(finalEvent, calendar, request);
                }
            });
        } else {
            sendReminderNotification(savedEvent, calendar, request);
        }

        return savedEvent.getId();
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = { EVENTS_BY_RANGE_CACHE, EVENTS_BY_MONTH_CACHE }, allEntries = true),
            @CacheEvict(cacheNames = EVENTS_DETAIL_CACHE, key = "#id")
    })
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
                    .addGoogleMeetToExistingEvent(userId, getGoogleEventId(event));

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
                    .lastSynced(Instant.now())
                    .event(event)
                    .build();

            event.setConference(conference);
            event.setSyncStatus(SyncStatus.SYNCED);
            eventRepository.save(event);
            publishReminderSnapshotUpdate(event);

            log.info("Google Meet added to event. Local ID: {}, Google ID: {}, Meet: {}",
                    event.getId(), googleEvent.getId(), meetLink);

        } catch (IOException e) {
            log.error("Failed to add Google Meet to event. Event ID: {}", event.getId(), e);
            throw new ServiceUnavailableException("Failed to add Google Meet", e);
        }
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = { EVENTS_BY_RANGE_CACHE, EVENTS_BY_MONTH_CACHE }, allEntries = true),
            @CacheEvict(cacheNames = EVENTS_DETAIL_CACHE, key = "#id")
    })
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
                String userId = event.getCalendar().getUserId();
                com.google.api.services.calendar.model.Event googleEvent = googleCalendarApiService.getEventById(userId,
                        getGoogleEventId(event));
                googleEvent.setConferenceData(null);

                googleCalendarApiService.removeConfrenceFromEvent(userId, getGoogleEventId(event));
            } catch (IOException e) {
                log.error("Failed to remove Google Meet from Google Calendar. Event ID: {}", event.getId(), e);
            }
        }

        Conference conference = event.getConference();
        event.setConference(null);
        eventRepository.save(event);
        publishReminderSnapshotUpdate(event);
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
    @CacheEvict(cacheNames = { EVENTS_BY_RANGE_CACHE, EVENTS_BY_MONTH_CACHE }, allEntries = true)
    public List<String> createEventWithZoom(String calendarId, EventRequest request, String userId) {
        if (calendarId == null || calendarId.isBlank()) {
            throw new EmptyFieldException("Calendar ID cannot be empty");
        }

        Calendar calendar = calendarRepository.findById(calendarId)
                .orElseThrow(() -> new ResourceNotFoundException("Calendar not found with ID: " + calendarId));

        if (request.planner() != null) {
            return switchToPlannedModeWithZoom(request, calendar, userId);
        }

        if (!request.allDay()
                && request.startTime() != null
                && request.endTime() != null
                && request.endTime().isBefore(request.startTime())) {
            throw new InvalidTimeRangeException("End time cannot be before start time");
        }

        Event event = eventMapper.toEvent(request, calendar);
        return List.of(saveSingleEventWithZoomInternal(calendar, event, request, userId));
    }

    private String saveSingleEventWithZoomInternal(Calendar calendar, Event event, EventRequest request,
            String userId) {
        if (event.getSubtasks() != null) {
            event.getSubtasks().forEach(subtask -> subtask.setEvent(event));
        }
        if (event.getAttachments() != null) {
            event.getAttachments().forEach(attachment -> attachment.setEvent(event));
        }

        Event savedEvent = eventRepository.save(event);

        if (request.location() != null) {
            Location location = locationService.create(savedEvent.getId(), request.location());
            savedEvent.setLocation(location);
            savedEvent = eventRepository.save(savedEvent);
        }

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
            }
        }

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

        // FIX #3: Skip dummy Owner for Google-synced events.
        if (!shouldSyncToGoogle(calendar)) {
            Member member = Member.builder()
                    .fullName("Owner")
                    .email("Owner")
                    .isOwner(true)
                    .event(savedEvent)
                    .build();
            memberRepository.save(member);
        }

        final Event finalZoomEvent = savedEvent;
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendReminderNotification(finalZoomEvent, calendar, request);
                }
            });
        } else {
            sendReminderNotification(savedEvent, calendar, request);
        }

        return savedEvent.getId();
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = { EVENTS_BY_RANGE_CACHE, EVENTS_BY_MONTH_CACHE }, allEntries = true),
            @CacheEvict(cacheNames = EVENTS_DETAIL_CACHE, key = "#id")
    })
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

            if (shouldSyncExistingGoogleEvent(event)) {
                StringBuilder description = new StringBuilder();
                description.append(event.getDescription()).append("\n\nZoom Meeting: ")
                        .append(conference.getConferenceLink());
                googleCalendarApiService.updateEvent(
                        event.getCalendar().getUserId(),
                        event.getGoogleCalendarId(),
                        event.getTitle(),
                        description.toString());
                event.setDescription(description.toString());
            }
            eventRepository.save(event);
            publishReminderSnapshotUpdate(event);

        } catch (Exception e) {
            log.error("Failed to add Zoom meeting to event. Event ID: {}", event.getId(), e);
            throw new ServiceUnavailableException("Failed to add Zoom meeting", e);
        }
    }

    private String removeZoomLinkFromDescription(String description) {
        if (description == null || description.isBlank()) {
            return description;
        }
        return description.replaceAll("\\n\\nZoom Meeting: https?://\\S+", "").stripTrailing();
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = { EVENTS_BY_RANGE_CACHE, EVENTS_BY_MONTH_CACHE }, allEntries = true),
            @CacheEvict(cacheNames = EVENTS_DETAIL_CACHE, key = "#id")
    })
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

        if (zoomMeetingService != null) {
            try {
                zoomMeetingService.deleteZoomMeeting(event.getId(), userId);
                log.info("Zoom meeting removed from Zoom servers. Event ID: {}", id);
            } catch (Exception e) {
                log.error("Failed to remove Zoom meeting from Zoom servers. Event ID: {}", event.getId(), e);
                throw new ZoomException(e.getMessage());
            }
        }

        String cleanedDescription = removeZoomLinkFromDescription(event.getDescription());

        event.setConference(null);
        event.setDescription(cleanedDescription);
        eventRepository.save(event);
        publishReminderSnapshotUpdate(event);

        if (shouldSyncExistingGoogleEvent(event)) {
            try {
                googleCalendarApiService.updateEvent(
                        event.getCalendar().getUserId(),
                        event.getGoogleCalendarId(),
                        event.getTitle(),
                        cleanedDescription);
                log.info("Google Calendar updated after Zoom removal. Event ID: {}", id);
            } catch (IOException e) {
                if (GoogleAuthService.isInvalidGrant(e)) {
                    log.error("Google token revoked while removing Zoom link for user {}", userId);
                    if (googleAuthService != null)
                        googleAuthService.markUserAsDisconnected(userId);
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

    @Cacheable(cacheNames = EVENTS_DETAIL_CACHE, key = "#id")
    public EventResponse getEventById(String id) {
        if (id == null || id.isBlank()) {
            throw new EmptyFieldException("Event ID cannot be empty");
        }

        return eventRepository.findById(id)
                .map(eventMapper::toEventResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + id));
    }

    @Cacheable(cacheNames = EVENTS_DETAIL_CACHE, key = "'notification:' + #id")
    public EventNotificationResponse getEventNotificationById(String id) {
        if (id == null || id.isBlank()) {
            throw new EmptyFieldException("Event ID cannot be empty");
        }

        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + id));

        String joinUrl = null;
        if (event.getConference() != null) {
            var conf = event.getConference();
            if (conf.getType() == com.flux.calendar_service.conference.Conference.ConferenceType.GOOGLE_MEET) {
                if (conf.getMeetLink() != null && !conf.getMeetLink().isBlank()) {
                    joinUrl = conf.getMeetLink();
                } else if (conf.getConferenceLink() != null && !conf.getConferenceLink().isBlank()) {
                    joinUrl = conf.getConferenceLink();
                }
            } else if (conf.getType() == com.flux.calendar_service.conference.Conference.ConferenceType.ZOOM) {
                if (conf.getConferenceLink() != null && !conf.getConferenceLink().isBlank()) {
                    joinUrl = conf.getConferenceLink();
                } else if (conf.getMeetLink() != null && !conf.getMeetLink().isBlank()) {
                    joinUrl = conf.getMeetLink();
                }
            } else {
                if (conf.getConferenceLink() != null && !conf.getConferenceLink().isBlank()) {
                    joinUrl = conf.getConferenceLink();
                } else if (conf.getMeetLink() != null && !conf.getMeetLink().isBlank()) {
                    joinUrl = conf.getMeetLink();
                }
            }
        }

        String locationText = null;
        if (event.getLocation() != null) {
            List<String> parts = new ArrayList<>();
            if (event.getLocation().getPlaceName() != null && !event.getLocation().getPlaceName().isBlank()) {
                parts.add(event.getLocation().getPlaceName());
            }
            if (event.getLocation().getStreetAddress() != null && !event.getLocation().getStreetAddress().isBlank()) {
                parts.add(event.getLocation().getStreetAddress());
            }
            if (event.getLocation().getCity() != null && !event.getLocation().getCity().isBlank()) {
                parts.add(event.getLocation().getCity());
            }
            if (event.getLocation().getCountry() != null && !event.getLocation().getCountry().isBlank()) {
                parts.add(event.getLocation().getCountry());
            }
            if (event.getLocation().getBuildingName() != null && !event.getLocation().getBuildingName().isBlank()) {
                parts.add(event.getLocation().getBuildingName());
            }
            if (event.getLocation().getFloor() != null) {
                parts.add("Floor " + event.getLocation().getFloor());
            }
            if (event.getLocation().getRoom() != null && !event.getLocation().getRoom().isBlank()) {
                parts.add("Room " + event.getLocation().getRoom());
            }

            if (!parts.isEmpty()) {
                locationText = String.join(" · ", parts);
            }
        }

        return eventMapper.toEventNotificationResponse(event, joinUrl, locationText);
    }

    public Event getEventByIdTypeEvent(String id) {
        if (id == null || id.isBlank()) {
            throw new EmptyFieldException("Event ID cannot be empty");
        }

        return eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + id));
    }

    public List<EventResponse> getMeetingsByCalendarId(String calendarId, String filter) {
        if (calendarId == null || calendarId.isBlank()) {
            throw new EmptyFieldException("Calendar ID cannot be empty");
        }

        calendarRepository.findById(calendarId)
                .orElseThrow(() -> new ResourceNotFoundException("Calendar not found with ID: " + calendarId));

        Instant now = Instant.now();

        return eventRepository.findByCalendarId(calendarId)
                .stream()
                .filter(e -> e.getType() != null && e.getType() == EventType.MEETING)
                .filter(e -> {
                    if ("all".equalsIgnoreCase(filter))
                        return true;
                    if (e.getStartTime() == null)
                        return false;
                    if ("today".equalsIgnoreCase(filter)) {
                        Instant dayStart = now.truncatedTo(java.time.temporal.ChronoUnit.DAYS);
                        Instant dayEnd = dayStart.plus(1, java.time.temporal.ChronoUnit.DAYS);
                        return !e.getStartTime().isBefore(dayStart) && e.getStartTime().isBefore(dayEnd);
                    }
                    if ("upcoming".equalsIgnoreCase(filter)) {
                        Instant dayEnd = now.truncatedTo(java.time.temporal.ChronoUnit.DAYS)
                                .plus(1, java.time.temporal.ChronoUnit.DAYS);
                        return !e.getStartTime().isBefore(dayEnd);
                    }
                    if ("past".equalsIgnoreCase(filter)) {
                        Instant dayStart = now.truncatedTo(java.time.temporal.ChronoUnit.DAYS);
                        return e.getStartTime().isBefore(dayStart);
                    }
                    return true;
                })
                .sorted(java.util.Comparator.comparing(Event::getStartTime,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .map(eventMapper::toEventResponse)
                .collect(Collectors.toList());
    }

    public List<EventResponse> getEventsByCalendarId(String calendarId) {
        if (calendarId == null || calendarId.isBlank()) {
            throw new EmptyFieldException("Calendar ID cannot be empty");
        }

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

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = EVENTS_BY_RANGE_CACHE, key = "#calendarId + ':' + #start.toEpochMilli() + ':' + #end.toEpochMilli()", sync = true)
    public List<EventResponse> getEventsByDateRange(String calendarId, Instant start, Instant end) {
        if (start == null || end == null) {
            throw new EmptyFieldException("Start and end dates cannot be null");
        }
        if (end.isBefore(start)) {
            throw new InvalidTimeRangeException("End date cannot be before start date");
        }
        rangeValidator.validate(ViewType.WEEK, start, end);

        return eventRepository.findRangeEventsWithCoreRelations(calendarId, start, end)
                .stream()
                .map(eventMapper::toEventResponse)
                .collect(Collectors.toList());
    }

    @Cacheable(cacheNames = EVENTS_BY_MONTH_CACHE, key = "#calendarId + ':' + #year + ':' + #month")
    public List<EventMonthResponse> getEventsByMonth(String calendarId, int year, int month) {
        if (month < 1 || month > 12) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Month must be between 1 and 12");
        }
        if (year < 1 || year > 9999) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Year must be between 1 and 9999");
        }
        java.time.YearMonth yearMonth = java.time.YearMonth.of(year, month);
        Instant start = yearMonth.atDay(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        Instant end = yearMonth.atEndOfMonth().atTime(23, 59, 59, 999999999).toInstant(java.time.ZoneOffset.UTC);

        rangeValidator.validate(ViewType.MONTH, start, end);

        List<Event> events = loadRangeEvents(calendarId, start, end);
        return events.stream()
                .map(eventMapper::toEventMonthResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = { EVENTS_BY_RANGE_CACHE, EVENTS_BY_MONTH_CACHE }, allEntries = true),
            @CacheEvict(cacheNames = EVENTS_DETAIL_CACHE, key = "#id")
    })
    public void updateEvent(String id, EventUpdateRequest request) {
        if (id == null || id.isBlank()) {
            throw new EmptyFieldException("Event ID cannot be empty");
        }

        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + id));

        Instant newStartTime = request.startTime() != null ? request.startTime() : event.getStartTime();
        Instant newEndTime = request.endTime() != null ? request.endTime() : event.getEndTime();
        boolean isAllDay = request.allDay() != null ? request.allDay() : event.isAllDay();

        if (!isAllDay && newStartTime != null && newEndTime != null) {
            if (newEndTime.isBefore(newStartTime)) {
                throw new InvalidTimeRangeException("End time cannot be before start time");
            }
        }

        eventMapper.updateEventFromRequest(event, request);

        if (request.location() != null) {
            Location location;
            if (event.getLocation() != null) {
                location = event.getLocation();
                locationMapper.updateLocationFromRequest(location, request.location());
                locationRepository.save(location);
            } else {
                location = locationMapper.toLocation(request.location());
                location = locationRepository.save(location);
                event.setLocation(location);
            }
        }

        if (request.conference() != null) {
            Conference conference;
            if (event.getConference() != null) {
                conference = event.getConference();
                conferenceMapper.updateConferenceFromRequest(conference, request.conference());
                conferenceRepository.save(conference);
            } else {
                conference = conferenceMapper.toConference(request.conference(), event);
                conferenceRepository.save(conference);
            }
        }

        eventRepository.save(event);
        publishReminderSnapshotUpdate(event);

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
                        event.isAllDay(),
                        locationName,
                        java.util.Collections.emptyList());

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
    @Caching(evict = {
            @CacheEvict(cacheNames = { EVENTS_BY_RANGE_CACHE, EVENTS_BY_MONTH_CACHE }, allEntries = true),
            @CacheEvict(cacheNames = EVENTS_DETAIL_CACHE, key = "#id")
    })
    public void deleteEvent(String id) {
        if (id == null || id.isBlank()) {
            throw new EmptyFieldException("Event ID cannot be empty");
        }

        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + id));

        if (shouldSyncExistingGoogleEvent(event)) {
            try {
                String userId = event.getCalendar().getUserId();
                googleCalendarApiService.deleteEvent(userId, event.getGoogleCalendarId());
                log.info("Event deleted from Google Calendar. Event ID: {}, Google Calendar ID: {}",
                        event.getId(), event.getGoogleCalendarId());
            } catch (IOException e) {
                log.error("Failed to delete event from Google Calendar. Event ID: {}", event.getId(), e);
            }
        }

        eventRepository.delete(event);
        // In deleteEvent():
        try {
            kafkaTemplate.send("reminder-events", id, Map.of(
                    "eventId", id,
                    "action", "DELETE"));
        } catch (Exception e) {
            log.error("Failed to send deletion notification to Kafka for event {}: {}", id, e.getMessage());
        }
    }

    @Transactional
    @CacheEvict(cacheNames = { EVENTS_BY_RANGE_CACHE, EVENTS_BY_MONTH_CACHE }, allEntries = true)
    public List<String> createEventWithLocation(String calendarId, EventRequest request) {
        return createEvent(calendarId, request);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = { EVENTS_BY_RANGE_CACHE, EVENTS_BY_MONTH_CACHE }, allEntries = true),
            @CacheEvict(cacheNames = EVENTS_DETAIL_CACHE, key = "#eventId")
    })
    public void attachLocation(String eventId, String locationId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + eventId));

        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new ResourceNotFoundException("Location not found with ID: " + locationId));

        event.setLocation(location);
        eventRepository.save(event);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = { EVENTS_BY_RANGE_CACHE, EVENTS_BY_MONTH_CACHE }, allEntries = true),
            @CacheEvict(cacheNames = EVENTS_DETAIL_CACHE, key = "#eventId")
    })
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

    private List<Event> loadRangeEvents(String calendarId, Instant start, Instant end) {
        List<Event> events = eventRepository.findRangeEventsWithCoreRelations(calendarId, start, end);
        if (events.isEmpty()) {
            return events;
        }

        List<String> eventIds = events.stream()
                .map(Event::getId)
                .toList();

        Map<String, List<Subtask>> subtasksByEvent = subtaskRepository.findAllByEventIdIn(eventIds).stream()
                .collect(Collectors.groupingBy(subtask -> subtask.getEvent().getId(), LinkedHashMap::new,
                        Collectors.toList()));
        Map<String, List<Attachment>> attachmentsByEvent = attachmentRepository.findByEventIdIn(eventIds).stream()
                .collect(Collectors.groupingBy(attachment -> attachment.getEvent().getId(), LinkedHashMap::new,
                        Collectors.toList()));
        Map<String, List<Member>> membersByEvent = memberRepository.findAllMembersByEventIdIn(eventIds).stream()
                .collect(Collectors.groupingBy(member -> member.getEvent().getId(), LinkedHashMap::new,
                        Collectors.toList()));
        Map<String, List<Note>> notesByEvent = noteRepository.findAllByEventIdIn(eventIds).stream()
                .collect(Collectors.groupingBy(note -> note.getEvent().getId(), LinkedHashMap::new,
                        Collectors.toList()));

        for (Event event : events) {
            event.setSubtasks(
                    new LinkedHashSet<>(subtasksByEvent.getOrDefault(event.getId(), Collections.emptyList())));
            event.setAttachments(
                    new LinkedHashSet<>(attachmentsByEvent.getOrDefault(event.getId(), Collections.emptyList())));
            event.setMembers(new LinkedHashSet<>(membersByEvent.getOrDefault(event.getId(), Collections.emptyList())));
            event.setNotes(new LinkedHashSet<>(notesByEvent.getOrDefault(event.getId(), Collections.emptyList())));
        }

        return events;
    }

    @Transactional
    @CacheEvict(cacheNames = { EVENTS_BY_RANGE_CACHE, EVENTS_BY_MONTH_CACHE }, allEntries = true)
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
                    try {
                        log.info("Creating new local event from Google event: {}", googleEvent.getId());
                        createLocalEventFromGoogle(calendar, googleEvent);
                    } catch (DataIntegrityViolationException e) {
                        log.warn("Concurrent sync conflict: Event with Google ID {} was likely created by another process. Retrying lookup.", googleEvent.getId());
                        // Final attempt to find the record that caused the constraint violation
                        eventRepository.findByGoogleCalendarId(googleEvent.getId())
                                .ifPresentOrElse(
                                        existing -> updateLocalEventFromGoogle(existing, googleEvent),
                                        () -> log.error("Failing to resolve sync conflict for Google ID: {}", googleEvent.getId())
                                );
                    }
                }
            }

            calendar.setGoogleSyncToken(syncResult.getNextSyncToken());
            calendar.setLastGoogleSync(Instant.now());
            calendarRepository.save(calendar);

        } catch (IOException e) {
            if (GoogleAuthService.isInvalidGrant(e)) {
                log.error("Google token permanently revoked for user {} during sync. "
                        + "User must re-authorize. Calendar ID: {}", userId, calendarId);
                if (googleAuthService != null) {
                    googleAuthService.markUserAsDisconnected(userId);
                }
                throw new GoogleTokenRevokedException(userId, e);
            }
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
            event.setGoogleUpdatedAt(convertToInstant(googleEvent.getUpdated()));
        }
        updateEventFieldsFromGoogle(event, googleEvent);
        event.setCreatedAt(Instant.now());
        event.setStatus(EventStatus.NOTSTARTED);

        // FIX #2: Save event first so members have a valid event FK,
        // then sync attendees, then save once more with complete state.
        eventRepository.save(event);
        syncAttendeesFromGoogle(event, googleEvent);
        eventRepository.save(event); // persist member changes
    }

    private void updateLocalEventFromGoogle(Event event, com.google.api.services.calendar.model.Event googleEvent) {
        if (googleEvent.getUpdated() != null) {
            Instant googleUpdated = convertToInstant(googleEvent.getUpdated());
            if (event.getGoogleUpdatedAt() != null && !googleUpdated.isAfter(event.getGoogleUpdatedAt())) {
                log.debug("Skipping update for event {} - no changes on Google side", event.getId());
                return;
            }
            event.setGoogleUpdatedAt(googleUpdated);
        }

        log.info("Updating existing event from Google: {}", event.getId());

        // FIX #2: Update fields and members BEFORE saving so they persist together
        // in a single flush. Previously save() was called before
        // syncAttendeesFromGoogle(),
        // meaning member changes were not always flushed with the event update.
        updateEventFieldsFromGoogle(event, googleEvent);
        syncAttendeesFromGoogle(event, googleEvent);
        eventRepository.save(event); // single save covers both field + member updates
    }

    private void updateEventFieldsFromGoogle(Event event, com.google.api.services.calendar.model.Event googleEvent) {
        event.setTitle(googleEvent.getSummary() != null ? googleEvent.getSummary() : "(No Title)");
        event.setDescription(googleEvent.getDescription() != null ? googleEvent.getDescription() : "");
        event.setColorHex("#3788d8");
        event.setType(EventType.OTHERS);

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

        if (googleEvent.getLocation() != null && !googleEvent.getLocation().isBlank()) {
            String address = googleEvent.getLocation();
            Location newLocation = new Location();
            newLocation.setPlaceName(address);
            newLocation.setStreetAddress(address);
            Location savedLocation = locationRepository.save(newLocation);
            event.setLocation(savedLocation);
        } else {
            event.setLocation(null);
        }

        if (googleEvent.getConferenceData() != null || googleEvent.getHangoutLink() != null) {
            Conference conference = event.getConference();
            if (conference == null) {
                conference = new Conference();
                // FIX #5: Always set the event FK before saving Conference to avoid
                // NOT NULL constraint violations on the foreign key column.
                conference.setEvent(event);
                conference.setSyncStatus(Conference.SyncStatus.SYNCED);
                conference.setLastSynced(Instant.now());
            }

            if (googleEvent.getConferenceData() != null) {
                conference.setType(Conference.ConferenceType.GOOGLE_MEET);
                conference.setPlatformName("Google Meet");
                conference.setGoogleConferenceId(googleEvent.getConferenceData().getConferenceId());

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

        if (googleEvent.getStart().getDate() != null) {
            event.setAllDay(true);
            event.setStartTime(convertToInstant(googleEvent.getStart().getDate()));
            event.setEndTime(
                    convertToInstant(googleEvent.getEnd().getDate()).minus(1, java.time.temporal.ChronoUnit.DAYS));
        } else {
            event.setAllDay(false);
            event.setStartTime(convertToInstant(googleEvent.getStart().getDateTime()));
            event.setEndTime(convertToInstant(googleEvent.getEnd().getDateTime()));
        }

        event.setSyncStatus(SyncStatus.SYNCED);
    }

    private void syncAttendeesFromGoogle(Event event, com.google.api.services.calendar.model.Event googleEvent) {
        // FIX #1: Keep the Hibernate-managed collection reference and mutate it
        // in-place. Previously the method mutated members but never called save(),
        // so concurrent pods would each try to delete the same Member rows causing
        // StaleObjectStateException. The caller is now responsible for saving once
        // after this method returns.
        if (event.getMembers() != null) {
            event.getMembers().clear();
        }

        List<Member> members = new ArrayList<>();

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
            members.add(Member.builder()
                    .fullName("Owner")
                    .email("Owner")
                    .isOwner(true)
                    .event(event)
                    .build());
        }

        if (googleEvent.getAttendees() != null) {
            for (com.google.api.services.calendar.model.EventAttendee attendee : googleEvent.getAttendees()) {
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
        // NOTE: caller must call eventRepository.save(event) after this method.
    }

    private Instant convertToInstant(com.google.api.client.util.DateTime dateTime) {
        return Instant.ofEpochMilli(dateTime.getValue());
    }

    private void normalizeAllDayEventTimes(Event event, EventRequest request) {
        if (event == null || request == null || !request.allDay()) {
            return;
        }

        Instant start = event.getStartTime();
        if (start == null) {
            if (request.date() == null) {
                throw new EmptyFieldException("All-day events require either date or startTime");
            }
            start = request.date().atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
            event.setStartTime(start);
        }

        if (event.getEndTime() == null || !event.getEndTime().isAfter(start)) {
            event.setEndTime(start.plus(1, java.time.temporal.ChronoUnit.DAYS));
        }
    }

    private boolean shouldSyncToGoogle(Calendar calendar) {
        if (googleCalendarApiService == null || calendar == null || calendar.getUserId() == null) {
            return false;
        }

        if (googleAuthService != null && !googleAuthService.isGoogleConnected(calendar.getUserId())) {
            return false;
        }

        if (calendar.getGoogleCalendarId() != null && !calendar.getGoogleCalendarId().isBlank()) {
            // Already identified as a Google-bound calendar — trust it and skip network
            // call.
            return true;
        }

        try {
            String primaryGoogleCalendarId = googleCalendarApiService.getPrimaryCalendarId(calendar.getUserId());
            if (primaryGoogleCalendarId == null || primaryGoogleCalendarId.isBlank()) {
                return false;
            }

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
                // FIX #4: The DB write (setGoogleCalendarId + save) is kept here but now only
                // executes when the designated calendar is missing its googleCalendarId,
                // which is a one-time migration path. This avoids repeated writes on every
                // event creation in steady state.
                if (designatedSyncCalendar.getGoogleCalendarId() == null
                        || designatedSyncCalendar.getGoogleCalendarId().isBlank()) {
                    designatedSyncCalendar.setGoogleCalendarId(primaryGoogleCalendarId);
                    calendarRepository.save(designatedSyncCalendar);
                }
                return true;
            }

            // No designated sync calendar found — auto-bind current calendar once.
            if (calendar.getGoogleCalendarId() == null || calendar.getGoogleCalendarId().isBlank()) {
                calendar.setGoogleCalendarId(primaryGoogleCalendarId);
                calendarRepository.save(calendar);
            }
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

    @Transactional
    @CacheEvict(cacheNames = { EVENTS_BY_RANGE_CACHE, EVENTS_BY_MONTH_CACHE }, allEntries = true)
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
                boolean isUpdate = event.getGoogleCalendarId() != null && !event.getGoogleCalendarId().isBlank();

                if (isUpdate) {
                    String locationName = event.getLocation() != null ? event.getLocation().getPlaceName() : null;
                    googleEvent = googleCalendarApiService.updateCompleteEvent(
                            userId,
                            event.getGoogleCalendarId(),
                            event.getTitle(),
                            event.getDescription(),
                            event.getStartTime(),
                            event.getEndTime(),
                            event.isAllDay(),
                            locationName,
                            java.util.Collections.emptyList());
                } else {
                    if (event.isAllDay()) {
                        googleEvent = googleCalendarApiService.createAllDayEvent(
                                userId,
                                event.getTitle(),
                                event.getDescription(),
                                event.getLocation(),
                                event.getStartTime());
                    } else {
                        googleEvent = googleCalendarApiService.createEvent(
                                userId,
                                event.getTitle(),
                                event.getDescription(),
                                event.getLocation(),
                                event.getStartTime(),
                                event.getEndTime(),
                                event.getType().toString());
                    }
                    event.setGoogleCalendarId(googleEvent.getId());
                }
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
                    return;
                } else {
                    log.error("Transient error pushing unsynced event {} to Google Calendar: {}. Keeping as PENDING.",
                            event.getId(), e.getMessage());
                    event.setSyncStatus(SyncStatus.PENDING);
                    eventRepository.save(event);
                }
            }
        }
    }

    private List<DayOfWeek> getTargetDays(PlannerRequest planner) {
        List<DayOfWeek> selectedDays = new ArrayList<>();
        if (planner.monday())
            selectedDays.add(DayOfWeek.MONDAY);
        if (planner.tuesday())
            selectedDays.add(DayOfWeek.TUESDAY);
        if (planner.wednesday())
            selectedDays.add(DayOfWeek.WEDNESDAY);
        if (planner.thursday())
            selectedDays.add(DayOfWeek.THURSDAY);
        if (planner.friday())
            selectedDays.add(DayOfWeek.FRIDAY);
        if (planner.saturday())
            selectedDays.add(DayOfWeek.SATURDAY);
        if (planner.sunday())
            selectedDays.add(DayOfWeek.SUNDAY);
        if (selectedDays.isEmpty()) {
            throw new EmptyFieldException("At least one day of the week must be selected");
        }
        return selectedDays;
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

    private void sendReminderNotification(Event savedEvent, Calendar calendar, EventRequest request) {
        if (request.notification() == null || !request.notification().isEnabled()) return;

        try {
            if (savedEvent.getStartTime() == null) {
                log.warn("Skipping reminder event publish: event {} has null startTime", savedEvent.getId());
                return;
            }

            int mins = request.notification().getMinutesBefore();
            String type = request.notification().getType();

            // ── Conference fields ──────────────────────────────────────────
            String joinUrl = null, meetingType = null, meetingCode = null,
                    dialInPhone = null, dialInPin = null, meetingPassword = null;

            if (savedEvent.getConference() != null) {
                var conf = savedEvent.getConference();
                meetingType    = conf.getType() != null ? conf.getType().name() : null;
                meetingCode    = conf.getMeetingCode();
                dialInPhone    = conf.getPhoneNumber();
                dialInPin      = conf.getPin();
                meetingPassword = conf.getConferencePassword();

                if (conf.getConferenceLink() != null && !conf.getConferenceLink().isBlank()) {
                    joinUrl = conf.getConferenceLink();
                } else if (conf.getMeetLink() != null && !conf.getMeetLink().isBlank()) {
                    joinUrl = conf.getMeetLink();
                }
            }

            // ── Location text ──────────────────────────────────────────────
            String locationText = null;
            if (savedEvent.getLocation() != null) {
                List<String> parts = new ArrayList<>();
                var loc = savedEvent.getLocation();
                if (loc.getPlaceName()     != null && !loc.getPlaceName().isBlank())     parts.add(loc.getPlaceName());
                if (loc.getStreetAddress() != null && !loc.getStreetAddress().isBlank()) parts.add(loc.getStreetAddress());
                if (loc.getCity()          != null && !loc.getCity().isBlank())          parts.add(loc.getCity());
                if (loc.getCountry()       != null && !loc.getCountry().isBlank())       parts.add(loc.getCountry());
                if (loc.getBuildingName()  != null && !loc.getBuildingName().isBlank())  parts.add(loc.getBuildingName());
                if (loc.getFloor()         != null)                                      parts.add("Floor " + loc.getFloor());
                if (loc.getRoom()          != null && !loc.getRoom().isBlank())          parts.add("Room " + loc.getRoom());
                if (!parts.isEmpty()) locationText = String.join(" · ", parts);
            }

            // ── Subtasks ───────────────────────────────────────────────────
            List<Map<String, Object>> subtasksList = savedEvent.getSubtasks() != null
                    ? savedEvent.getSubtasks().stream()
                      .map(st -> {
                          Map<String, Object> m = new LinkedHashMap<>();
                          m.put("id",      st.getId());
                          m.put("subtask", st.getSubtask());
                          m.put("isDone",  st.isDone());
                          return m;
                      }).toList()
                    : List.of();

            // ── Trigger time ───────────────────────────────────────────────
            Instant triggerTime = savedEvent.getStartTime().minus(mins, ChronoUnit.MINUTES);

            // ── Build message ──────────────────────────────────────────────
            // ✅ Instants sent as epoch milliseconds so Jackson deserializes
            //    them correctly into Instant on the notification-service side
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("eventId",          savedEvent.getId());
            message.put("userId",           calendar.getUserId());
            message.put("eventTitle",       savedEvent.getTitle());
            message.put("eventDescription", savedEvent.getDescription());
            message.put("eventStartTime",   savedEvent.getStartTime().toEpochMilli());   // ✅ was .toString()
            message.put("eventEndTime",     savedEvent.getEndTime() != null
                    ? savedEvent.getEndTime().toEpochMilli() // ✅ was .toString()
                    : null);
            message.put("allDay",           savedEvent.isAllDay());
            message.put("locationText",     locationText);
            message.put("meetingType",      meetingType);
            message.put("joinUrl",          joinUrl);
            message.put("meetingCode",      meetingCode);
            message.put("dialInPhone",      dialInPhone);
            message.put("dialInPin",        dialInPin);
            message.put("meetingPassword",  meetingPassword);
            message.put("subtasks",         subtasksList);
            message.put("action",           "CREATE");
            message.put("reminders",        List.of(Map.of(
                    "triggerTime", triggerTime.toEpochMilli(),   // ✅ was .toString()
                    "type",        type.toUpperCase()
            )));

            kafkaTemplate.send("reminder-events", savedEvent.getId(), message);
            log.info("Published reminder event to Kafka for event {}", savedEvent.getId());

        } catch (Exception e) {
            log.error("Failed to send creation notification to Kafka for event {}: {}",
                    savedEvent.getId(), e.getMessage());
        }
    }

    /**
     * Publish an event snapshot update so reminder emails can render up-to-date
     * details
     * without calling back into calendar-service.
     *
     * This does not create/cancel reminders; it only updates the stored snapshot.
     */
    private void publishReminderSnapshotUpdate(Event event) {
        try {
            if (event == null || event.getId() == null || event.getId().isBlank())
                return;

            String joinUrl = null;
            String meetingType = null;
            String meetingCode = null;
            String dialInPhone = null;
            String dialInPin = null;
            String meetingPassword = null;

            if (event.getConference() != null) {
                var conf = event.getConference();
                meetingType = conf.getType() != null ? conf.getType().name() : null;
                meetingCode = conf.getMeetingCode();
                dialInPhone = conf.getPhoneNumber();
                dialInPin = conf.getPin();
                meetingPassword = conf.getConferencePassword();

                if (conf.getConferenceLink() != null && !conf.getConferenceLink().isBlank()) {
                    joinUrl = conf.getConferenceLink();
                } else if (conf.getMeetLink() != null && !conf.getMeetLink().isBlank()) {
                    joinUrl = conf.getMeetLink();
                }
            }

            String locationText = null;
            if (event.getLocation() != null) {
                List<String> parts = new ArrayList<>();
                var loc = event.getLocation();
                if (loc.getPlaceName() != null && !loc.getPlaceName().isBlank())
                    parts.add(loc.getPlaceName());
                if (loc.getStreetAddress() != null && !loc.getStreetAddress().isBlank())
                    parts.add(loc.getStreetAddress());
                if (loc.getCity() != null && !loc.getCity().isBlank())
                    parts.add(loc.getCity());
                if (loc.getCountry() != null && !loc.getCountry().isBlank())
                    parts.add(loc.getCountry());
                if (loc.getBuildingName() != null && !loc.getBuildingName().isBlank())
                    parts.add(loc.getBuildingName());
                if (loc.getFloor() != null)
                    parts.add("Floor " + loc.getFloor());
                if (loc.getRoom() != null && !loc.getRoom().isBlank())
                    parts.add("Room " + loc.getRoom());
                if (!parts.isEmpty())
                    locationText = String.join(" · ", parts);
            }

            Map<String, Object> message = new LinkedHashMap<>();
            message.put("eventId", event.getId());
            message.put("userId", event.getCalendar() != null ? event.getCalendar().getUserId() : null);
            message.put("eventTitle", event.getTitle());
            message.put("eventDescription", event.getDescription());
            message.put("eventStartTime", event.getStartTime() != null ? event.getStartTime().toString() : null);
            message.put("eventEndTime", event.getEndTime() != null ? event.getEndTime().toString() : null);
            message.put("allDay", event.isAllDay());
            message.put("locationText", locationText);
            message.put("meetingType", meetingType);
            message.put("joinUrl", joinUrl);
            message.put("meetingCode", meetingCode);
            message.put("dialInPhone", dialInPhone);
            message.put("dialInPin", dialInPin);
            message.put("meetingPassword", meetingPassword);
            message.put("subtasks", event.getSubtasks() != null
                    ? event.getSubtasks().stream()
                            .map(st -> {
                                Map<String, Object> m = new LinkedHashMap<>();
                                m.put("id", st.getId());
                                m.put("subtask", st.getSubtask());
                                m.put("isDone", st.isDone());
                                return m;
                            })
                            .toList()
                    : List.of());
            message.put("action", "SNAPSHOT_UPDATE");

            kafkaTemplate.send("reminder-events", event.getId(), message);
        } catch (Exception e) {
            log.warn("Failed to publish reminder snapshot update for event {}: {}",
                    event != null ? event.getId() : null, e.getMessage());
        }
    }
}
