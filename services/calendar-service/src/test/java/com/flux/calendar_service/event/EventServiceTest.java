package com.flux.calendar_service.event;

import com.flux.calendar_service.calendar.Calendar;
import com.flux.calendar_service.calendar.CalendarRepository;
import com.flux.calendar_service.conference.ConferenceMapper;
import com.flux.calendar_service.conference.ConferenceRepository;
import com.flux.calendar_service.event.dto.EventRequest;
import com.flux.calendar_service.event.dto.EventResponse;
import com.flux.calendar_service.google.GoogleCalendarApiService;
import com.flux.calendar_service.location.LocationMapper;
import com.flux.calendar_service.location.LocationRepository;
import com.flux.calendar_service.member.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
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
    private ConferenceRepository conferenceRepository;
    @Mock
    private ConferenceMapper conferenceMapper;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private GoogleCalendarApiService googleCalendarApiService;

    @InjectMocks
    private EventService eventService;

    @Test
    void createEvent_ShouldReturnId() {
        String calendarId = "cal1";
        EventRequest request = new EventRequest(
                "New Event", "Description", "#000000", EventType.MEETING, null,
                LocalDateTime.now(), LocalDateTime.now().plusHours(1), false, EventStatus.NOTSTARTED,
                Collections.emptyList(), Collections.emptyList());
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

        String id = eventService.createEvent(calendarId, request);

        assertEquals("event1", id);
        verify(memberRepository).save(any());
    }

    @Test
    void getEventsByCalendarId_ShouldReturnList() {
        String calendarId = "cal1";
        Calendar calendar = new Calendar();
        Event event = new Event();
        EventResponse response = new EventResponse(
                "1", null, "Event 1", "Desc", "#000", null, null,
                EventType.MEETING, LocalDateTime.now(), LocalDateTime.now().plusHours(1),
                false, SyncStatus.SYNCED, EventStatus.NOTSTARTED,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(),
                LocalDateTime.now(), LocalDateTime.now());

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
        Event event = new Event();
        EventResponse response = new EventResponse(
                "1", null, "Event 1", "Desc", "#000", null, null,
                EventType.MEETING, LocalDateTime.now(), LocalDateTime.now().plusHours(1),
                false, SyncStatus.SYNCED, EventStatus.NOTSTARTED,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(),
                LocalDateTime.now(), LocalDateTime.now());

        when(eventRepository.findById(id)).thenReturn(Optional.of(event));
        when(eventMapper.toEventResponse(event)).thenReturn(response);

        EventResponse result = eventService.getEventById(id);

        assertNotNull(result);
        assertEquals("1", result.id());
    }
}
