package com.flux.calendar_service.user;

import com.flux.calendar_service.calendar.Calendar;
import com.flux.calendar_service.calendar.CalendarRepository;
import com.flux.calendar_service.connects.Connect;
import com.flux.calendar_service.connects.ConnectRepository;
import com.flux.calendar_service.event.Event;
import com.flux.calendar_service.event.EventRepository;
import com.flux.calendar_service.google.GoogleOAuthToken;
import com.flux.calendar_service.google.GoogleOAuthTokenRepository;
import com.flux.calendar_service.location.Location;
import com.flux.calendar_service.location.LocationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDataCleanupServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private CalendarRepository calendarRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private UserSyncRepository userSyncRepository;

    @Mock
    private ConnectRepository connectRepository;

    @Mock
    private GoogleOAuthTokenRepository googleOAuthTokenRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private UserDataCleanupService service;

    @Test
    void cleanupDeletedUser_ShouldDeleteCalendarsEventsAndLocalUserData() {
        Calendar calendar = Calendar.builder().userId("user-1").build();
        calendar.setId("calendar-1");

        Location location = Location.builder().placeName("Office").build();
        location.setId("location-1");

        Event event = Event.builder().calendar(calendar).location(location).build();
        event.setId("event-1");

        Connect connect = Connect.builder().userId("user-1").build();
        GoogleOAuthToken token = GoogleOAuthToken.builder().userId("user-1").build();

        when(eventRepository.findByCalendar_UserId("user-1")).thenReturn(List.of(event));
        when(calendarRepository.findCalendarByUserId("user-1")).thenReturn(List.of(calendar));
        when(eventRepository.findEventByLocationId("location-1")).thenReturn(List.of());
        when(connectRepository.findByUserId("user-1")).thenReturn(Optional.of(connect));
        when(googleOAuthTokenRepository.findByUserId("user-1")).thenReturn(Optional.of(token));

        service.cleanupDeletedUser("user-1");

        verify(calendarRepository).deleteAll(List.of(calendar));
        verify(calendarRepository).flush();
        verify(locationRepository).delete(location);
        verify(userSyncRepository).deleteById("user-1");
        verify(connectRepository).delete(connect);
        verify(googleOAuthTokenRepository).delete(token);
        verify(kafkaTemplate).send(eq("reminder-events"), eq("event-1"), argThat(deleteReminderMessage("event-1")));
    }

    private ArgumentMatcher<Object> deleteReminderMessage(String eventId) {
        return message -> message instanceof Map<?, ?> map
                && eventId.equals(map.get("eventId"))
                && "DELETE".equals(map.get("action"));
    }
}
