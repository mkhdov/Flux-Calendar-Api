package com.flux.calendar_service.event;

import com.flux.calendar_service.calendar.Calendar;
import com.flux.calendar_service.calendar.CalendarRepository;
import com.flux.calendar_service.conference.ConferenceMapper;
import com.flux.calendar_service.conference.ConferenceRepository;
import com.flux.calendar_service.event.dto.EventRequest;
import com.flux.calendar_service.event.dto.EventResponse;
import com.flux.calendar_service.google.GoogleCalendarApiService;
import com.flux.calendar_service.google.GoogleCalendarApiService.EventsResult;
import com.flux.calendar_service.location.LocationMapper;
import com.flux.calendar_service.location.LocationRepository;
import com.flux.calendar_service.location.LocationService;
import com.flux.calendar_service.member.MemberRepository;
import com.flux.calendar_service.event.dto.EventMonthResponse;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.EventDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

        @Mock
        private EventRepository eventRepository;
        @Mock
        private EventMapper eventMapper;
        @Mock
        private CalendarRepository calendarRepository;
        @Mock
        private LocationRepository locationRepository;
        @Mock
        private LocationMapper locationMapper;
        @Mock
        private LocationService locationService;
        @Mock
        private ConferenceRepository conferenceRepository;
        @Mock
        private ConferenceMapper conferenceMapper;
        @Mock
        private MemberRepository memberRepository;
        @Mock
        private GoogleCalendarApiService googleCalendarApiService;
        @Mock
        private com.flux.calendar_service.subtask.SubtaskRepository subtaskRepository;
        @Mock
        private com.flux.calendar_service.attachment.AttachmentRepository attachmentRepository;
        @Mock
        private com.flux.calendar_service.note.NoteRepository noteRepository;
        @Mock
        private EventRangeValidator rangeValidator;
        @Mock
        private KafkaTemplate<String, Object> kafkaTemplate;

        @InjectMocks
        private EventService eventService;

        @Test
        void createEvent_ShouldReturnId() {
                String calendarId = "cal1";
                Instant now = Instant.now();
                EventRequest request = new EventRequest(
                                "New Event", "Description", "#000000", EventType.MEETING, null,
                                now, now.plusSeconds(3600), false, EventStatus.NOTSTARTED,
                                Collections.emptyList(), Collections.emptyList(), null, null, null);
                Calendar calendar = new Calendar();
                calendar.setId("cal1");
                calendar.setUserId("user1");

                Event event = new Event();
                event.setId("event1");
                event.setStartTime(request.startTime());
                event.setEndTime(request.endTime());
                event.setTitle(request.title());
                event.setDescription(request.description());
                event.setType(EventType.MEETING);

                when(calendarRepository.findById(calendarId)).thenReturn(Optional.of(calendar));
                when(eventMapper.toEvent(request, calendar)).thenReturn(event);
                when(eventRepository.save(event)).thenReturn(event);

                List<String> ids = eventService.createEvent(calendarId, request);

                assertFalse(ids.isEmpty());
                assertEquals("event1", ids.get(0));
                verify(memberRepository).save(any());
        }

        @Test
        void getEventsByCalendarId_ShouldReturnList() {
                String calendarId = "cal1";
                Instant now = Instant.now();
                Calendar calendar = new Calendar();
                Event event = new Event();
                EventResponse response = new EventResponse(
                                "1", null, "Event 1", "Desc", "#000", null, null,
                                EventType.MEETING, now, now.plusSeconds(3600),
                                false, SyncStatus.SYNCED, EventStatus.NOTSTARTED,
                                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                                Collections.emptyList(),
                                now, now,
                                null, false);

                when(calendarRepository.findById(calendarId)).thenReturn(Optional.of(calendar));
                when(eventRepository.findByCalendarId(calendarId)).thenReturn(List.of(event));
                when(eventMapper.toEventResponse(event)).thenReturn(response);

                List<EventResponse> results = eventService.getEventsByCalendarId(calendarId);

                assertEquals(1, results.size());
                assertEquals("1", results.get(0).id());
        }

        @Test
        void getEventById_ShouldReturnEvent() {
                String id = "1";
                Instant now = Instant.now();
                Event event = new Event();
                EventResponse response = new EventResponse(
                                "1", null, "Event 1", "Desc", "#000", null, null,
                                EventType.MEETING, now, now.plusSeconds(3600),
                                false, SyncStatus.SYNCED, EventStatus.NOTSTARTED,
                                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                                Collections.emptyList(),
                                now, now,
                                null, false);

                when(eventRepository.findById(id)).thenReturn(Optional.of(event));
                when(eventMapper.toEventResponse(event)).thenReturn(response);

                EventResponse result = eventService.getEventById(id);

                assertNotNull(result);
                assertEquals("1", result.id());
        }

        @Test
        void getEventsByMonth_ShouldReturnList() {
                String calendarId = "cal1";
                int year = 2023;
                int month = 10;
                Instant now = Instant.now();
                Event event = new Event();
                event.setId("1");

                EventMonthResponse response = new EventMonthResponse(
                                "1", "cal1", "Event 1", "#000", now, now.plusSeconds(3600), false);

                when(eventRepository.findRangeEventsWithCoreRelations(eq(calendarId), any(Instant.class),
                                any(Instant.class)))
                                .thenReturn(List.of(event));
                when(subtaskRepository.findAllByEventIdIn(anyList())).thenReturn(Collections.emptyList());
                when(attachmentRepository.findByEventIdIn(anyList())).thenReturn(Collections.emptyList());
                when(memberRepository.findAllMembersByEventIdIn(anyList())).thenReturn(Collections.emptyList());
                when(noteRepository.findAllByEventIdIn(anyList())).thenReturn(Collections.emptyList());
                when(eventMapper.toEventMonthResponse(event)).thenReturn(response);

                List<EventMonthResponse> results = eventService.getEventsByMonth(calendarId, year, month);

                assertEquals(1, results.size());
                assertEquals("1", results.get(0).id());
        }

        // ── Range validation tests ──────────────────────────────────

        @Test
        void getEventsByDateRange_DelegatesToRangeValidator() {
                Instant start = Instant.now();
                Instant end = start.plus(3, ChronoUnit.DAYS);

                when(eventRepository.findRangeEventsWithCoreRelations(eq("cal1"), any(), any()))
                                .thenReturn(Collections.emptyList());

                eventService.getEventsByDateRange("cal1", start, end);

                verify(rangeValidator).validate(ViewType.WEEK, start, end);
        }

        @Test
        void getEventsByDateRange_ExceedingSevenDays_ShouldThrowBadRequest() {
                Instant start = Instant.now();
                Instant end = start.plus(10, ChronoUnit.DAYS);

                doThrow(new ResponseStatusException(
                                org.springframework.http.HttpStatus.BAD_REQUEST, "Week view supports max 7 days"))
                                .when(rangeValidator).validate(ViewType.WEEK, start, end);

                ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                                () -> eventService.getEventsByDateRange("cal1", start, end));

                assertEquals(400, ex.getStatusCode().value());
                assertTrue(ex.getReason().contains("Week view supports max 7 days"));
        }

        @Test
        void getEventsByDateRange_ZeroDayRange_ShouldThrowBadRequest() {
                Instant start = Instant.now();
                Instant end = start.plusSeconds(30);

                doThrow(new ResponseStatusException(
                                org.springframework.http.HttpStatus.BAD_REQUEST,
                                "Invalid date range: must span at least 1 day"))
                                .when(rangeValidator).validate(ViewType.WEEK, start, end);

                ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                                () -> eventService.getEventsByDateRange("cal1", start, end));

                assertEquals(400, ex.getStatusCode().value());
                assertTrue(ex.getReason().contains("Invalid date range"));
        }

        // ── Month validation tests ──────────────────────────────────

        @Test
        void getEventsByMonth_DelegatesToRangeValidator() {
                String calendarId = "cal1";
                Event event = new Event();
                event.setId("1");
                Instant now = Instant.now();
                EventMonthResponse response = new EventMonthResponse(
                                "1", "cal1", "Event 1", "#000", now, now.plusSeconds(3600), false);

                when(eventRepository.findRangeEventsWithCoreRelations(eq(calendarId), any(Instant.class),
                                any(Instant.class)))
                                .thenReturn(List.of(event));
                when(subtaskRepository.findAllByEventIdIn(anyList())).thenReturn(Collections.emptyList());
                when(attachmentRepository.findByEventIdIn(anyList())).thenReturn(Collections.emptyList());
                when(memberRepository.findAllMembersByEventIdIn(anyList())).thenReturn(Collections.emptyList());
                when(noteRepository.findAllByEventIdIn(anyList())).thenReturn(Collections.emptyList());
                when(eventMapper.toEventMonthResponse(event)).thenReturn(response);

                eventService.getEventsByMonth(calendarId, 2023, 10);

                verify(rangeValidator).validate(eq(ViewType.MONTH), any(Instant.class), any(Instant.class));
        }

        @Test
        void getEventsByMonth_WithInvalidMonth_ShouldThrowBadRequest() {
                ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                                () -> eventService.getEventsByMonth("cal1", 2023, 13));

                assertEquals(400, ex.getStatusCode().value());
                assertTrue(ex.getReason().contains("Month must be between 1 and 12"));
        }

        @Test
        void getEventsByMonth_WithZeroMonth_ShouldThrowBadRequest() {
                ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                                () -> eventService.getEventsByMonth("cal1", 2023, 0));

                assertEquals(400, ex.getStatusCode().value());
                assertTrue(ex.getReason().contains("Month must be between 1 and 12"));
        }

        @Test
        void syncEventsFromGoogle_WhenEventPersistenceFails_ShouldNotAdvanceSyncToken() throws Exception {
                ReflectionTestUtils.setField(eventService, "googleCalendarApiService", googleCalendarApiService);

                Calendar calendar = new Calendar();
                calendar.setId("cal1");
                calendar.setUserId("user1");
                calendar.setGoogleSyncToken("old-token");

                com.google.api.services.calendar.model.Event googleEvent =
                                new com.google.api.services.calendar.model.Event()
                                                .setId("google-event-1")
                                                .setSummary("Imported Event")
                                                .setStart(new EventDateTime()
                                                                .setDateTime(new DateTime("2026-05-10T10:00:00Z")))
                                                .setEnd(new EventDateTime()
                                                                .setDateTime(new DateTime("2026-05-10T11:00:00Z")));

                when(calendarRepository.findById("cal1")).thenReturn(Optional.of(calendar));
                when(googleCalendarApiService.getEventsWithSyncToken("user1", "old-token"))
                                .thenReturn(new EventsResult(List.of(googleEvent), "new-token"));
                when(eventRepository.findByGoogleCalendarId("google-event-1")).thenReturn(Optional.empty());
                when(eventRepository.save(any(Event.class))).thenThrow(new RuntimeException("database rejected event"));

                RuntimeException ex = assertThrows(RuntimeException.class,
                                () -> eventService.syncEventsFromGoogle("cal1"));

                assertEquals("database rejected event", ex.getMessage());
                verify(calendarRepository, never()).save(calendar);
                assertEquals("old-token", calendar.getGoogleSyncToken());
                assertNull(calendar.getLastGoogleSync());
        }

        @Test
        void getEventsByMonth_WithInvalidYear_ShouldThrowBadRequest() {
                ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                                () -> eventService.getEventsByMonth("cal1", -1, 6));

                assertEquals(400, ex.getStatusCode().value());
                assertTrue(ex.getReason().contains("Year must be between 1 and 9999"));
        }
}
