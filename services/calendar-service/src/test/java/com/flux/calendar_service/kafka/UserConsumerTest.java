package com.flux.calendar_service.kafka;

import com.flux.calendar_service.calendar.Calendar;
import com.flux.calendar_service.calendar.CalendarRepository;
import com.flux.calendar_service.user.UserSyncRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserConsumerTest {

    @Mock
    private UserSyncRepository userSyncRepository;

    @Mock
    private CalendarRepository calendarRepository;

    @Mock
    private com.flux.calendar_service.connects.ConnectService connectService;

    @InjectMocks
    private UserConsumer userConsumer;

    @Test
    void consumeUserEvent_ShouldCreateDefaultCalendar_WhenCreatedEventAndNoDefaultCalendar() {
        UserEvent event = UserEvent.builder()
                .userId("user-1")
                .email("test@example.com")
                .firstname("Test")
                .lastname("User")
                .type("CREATED")
                .build();

        when(calendarRepository.existsByUserIdAndTitleAndIsDeletedFalse("user-1", "Default")).thenReturn(false);
        when(calendarRepository.findCalendarByUserIdAndIsDeletedFalse("user-1")).thenReturn(Collections.emptyList());

        userConsumer.consumeUserEvent(event);

        ArgumentCaptor<Calendar> calendarCaptor = ArgumentCaptor.forClass(Calendar.class);
        verify(calendarRepository).save(calendarCaptor.capture());
        Calendar saved = calendarCaptor.getValue();
        assertEquals("user-1", saved.getUserId());
        assertEquals("Default", saved.getTitle());
        assertEquals("#808080", saved.getColorHex());
        assertTrue(saved.isPrimary());
        verify(userSyncRepository).save(any());
    }

    @Test
    void consumeUserEvent_ShouldNotCreateDefaultCalendar_WhenCreatedEventAndDefaultAlreadyExists() {
        UserEvent event = UserEvent.builder()
                .userId("user-1")
                .type("CREATED")
                .build();

        when(calendarRepository.existsByUserIdAndTitleAndIsDeletedFalse("user-1", "Default")).thenReturn(true);

        userConsumer.consumeUserEvent(event);

        verify(calendarRepository, never()).save(any(Calendar.class));
        verify(userSyncRepository).save(any());
    }

    @Test
    void consumeUserEvent_ShouldDeleteUserSync_WhenDeletedEvent() {
        UserEvent event = UserEvent.builder()
                .userId("user-1")
                .type("DELETED")
                .build();

        userConsumer.consumeUserEvent(event);

        verify(userSyncRepository).deleteById("user-1");
        verify(userSyncRepository, never()).save(any());
        verify(calendarRepository, never()).save(any(Calendar.class));
    }
}
