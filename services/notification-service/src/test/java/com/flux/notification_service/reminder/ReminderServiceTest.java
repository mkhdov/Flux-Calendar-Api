package com.flux.notification_service.reminder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.flux.notification_service.exception.DuplicateReminderException;
import com.flux.notification_service.exception.ReminderNotFoundException;
import com.flux.notification_service.reminder.dto.ReminderRequest;
import com.flux.notification_service.reminder.dto.ReminderResponse;

@ExtendWith(MockitoExtension.class)
class ReminderServiceTest {

    @Mock
    private ReminderRepository reminderRepository;

    @Mock
    private ReminderMapper reminderMapper;

    @InjectMocks
    private ReminderService reminderService;

    private final Instant triggerTime = Instant.parse("2026-04-01T10:00:00Z");

    @Test
    void createReminder_shouldCreateSuccessfully() {
        var request = new ReminderRequest("event-1", "user-1", triggerTime, ReminderType.EMAIL);
        var entity = Reminder.builder()
                .id("rem-1")
                .eventId("event-1")
                .userId("user-1")
                .triggerTime(triggerTime)
                .type(ReminderType.EMAIL)
                .status(ReminderStatus.PENDING)
                .build();
        var response = new ReminderResponse("rem-1", "event-1", "user-1", triggerTime,
                ReminderType.EMAIL, ReminderStatus.PENDING, 0, null, null);

        when(reminderRepository.existsByEventIdAndTriggerTimeAndType("event-1", triggerTime, ReminderType.EMAIL))
                .thenReturn(false);
        when(reminderMapper.toEntity(request)).thenReturn(entity);
        when(reminderRepository.save(entity)).thenReturn(entity);
        when(reminderMapper.toResponse(entity)).thenReturn(response);

        ReminderResponse result = reminderService.createReminder(request);

        assertThat(result.id()).isEqualTo("rem-1");
        assertThat(result.status()).isEqualTo(ReminderStatus.PENDING);
        verify(reminderRepository).save(entity);
    }

    @Test
    void createReminder_shouldThrowOnDuplicate() {
        var request = new ReminderRequest("event-1", "user-1", triggerTime, ReminderType.EMAIL);

        when(reminderRepository.existsByEventIdAndTriggerTimeAndType("event-1", triggerTime, ReminderType.EMAIL))
                .thenReturn(true);

        assertThatThrownBy(() -> reminderService.createReminder(request))
                .isInstanceOf(DuplicateReminderException.class);

        verify(reminderRepository, never()).save(any());
    }

    @Test
    void getByEventId_shouldReturnReminders() {
        var reminder = Reminder.builder()
                .id("rem-1")
                .eventId("event-1")
                .build();
        var response = new ReminderResponse("rem-1", "event-1", "user-1", triggerTime,
                ReminderType.EMAIL, ReminderStatus.PENDING, 0, null, null);

        when(reminderRepository.findByEventIdOrderByTriggerTimeAsc("event-1"))
                .thenReturn(List.of(reminder));
        when(reminderMapper.toResponse(reminder)).thenReturn(response);

        List<ReminderResponse> result = reminderService.getByEventId("event-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).eventId()).isEqualTo("event-1");
    }

    @Test
    void deleteReminder_shouldThrowIfNotFound() {
        when(reminderRepository.existsById("non-existent")).thenReturn(false);

        assertThatThrownBy(() -> reminderService.deleteReminder("non-existent"))
                .isInstanceOf(ReminderNotFoundException.class);
    }

    @Test
    void getById_shouldReturnReminder() {
        var reminder = Reminder.builder()
                .id("rem-1")
                .eventId("event-1")
                .build();
        var response = new ReminderResponse("rem-1", "event-1", "user-1", triggerTime,
                ReminderType.EMAIL, ReminderStatus.PENDING, 0, null, null);

        when(reminderRepository.findById("rem-1")).thenReturn(Optional.of(reminder));
        when(reminderMapper.toResponse(reminder)).thenReturn(response);

        ReminderResponse result = reminderService.getById("rem-1");

        assertThat(result.id()).isEqualTo("rem-1");
    }

    @Test
    void getById_shouldThrowIfNotFound() {
        when(reminderRepository.findById("non-existent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reminderService.getById("non-existent"))
                .isInstanceOf(ReminderNotFoundException.class);
    }
}
