package com.flux.notification_service.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import com.flux.notification_service.notification.NotificationPayload;
import com.flux.notification_service.notification.NotificationSender;
import com.flux.notification_service.notification.NotificationSenderFactory;
import com.flux.notification_service.reminder.Reminder;
import com.flux.notification_service.reminder.ReminderRepository;
import com.flux.notification_service.reminder.ReminderStatus;
import com.flux.notification_service.reminder.ReminderType;

@ExtendWith(MockitoExtension.class)
class ReminderProcessorTest {

    @Mock private ReminderRepository reminderRepository;
    @Mock private NotificationSenderFactory senderFactory;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private RestTemplate restTemplate;
    @Mock private NotificationSender mockSender;
    @Mock private ValueOperations<String, String> valueOps;

    @InjectMocks
    private ReminderProcessor reminderProcessor;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(reminderProcessor, "maxRetryAttempts", 3);
        ReflectionTestUtils.setField(reminderProcessor, "dedupTtlHours", 24L);
        ReflectionTestUtils.setField(reminderProcessor, "userServiceUrl", "http://localhost:8081");
    }

    @Test
    void processReminders_shouldSkipWhenNoPending() {
        when(reminderRepository.findTop100ByStatusAndTriggerTimeBeforeOrderByTriggerTimeAsc(
                eq(ReminderStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of());

        reminderProcessor.processReminders();

        verify(reminderRepository).findTop100ByStatusAndTriggerTimeBeforeOrderByTriggerTimeAsc(
                eq(ReminderStatus.PENDING), any(Instant.class));
    }

    @Test
    void processOne_shouldMarkSentOnSuccess() throws Exception {
        Reminder reminder = buildReminder("rem-1", ReminderType.EMAIL);

        when(redisTemplate.hasKey("reminder:sent:rem-1")).thenReturn(false);
        when(senderFactory.getSender(ReminderType.EMAIL)).thenReturn(mockSender);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        reminderProcessor.processOne(reminder);

        assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.SENT);
        verify(reminderRepository).save(reminder);
        verify(mockSender).send(any(NotificationPayload.class));
    }

    @Test
    void processOne_shouldSkipIfDedupHit() {
        Reminder reminder = buildReminder("rem-1", ReminderType.EMAIL);

        when(redisTemplate.hasKey("reminder:sent:rem-1")).thenReturn(true);

        reminderProcessor.processOne(reminder);

        assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.SENT);
        verify(reminderRepository).save(reminder);
    }

    @Test
    void processOne_shouldIncrementRetryOnFailure() throws Exception {
        Reminder reminder = buildReminder("rem-1", ReminderType.EMAIL);

        when(redisTemplate.hasKey("reminder:sent:rem-1")).thenReturn(false);
        when(senderFactory.getSender(ReminderType.EMAIL)).thenReturn(mockSender);
        doThrow(new RuntimeException("SMTP error")).when(mockSender).send(any());

        reminderProcessor.processOne(reminder);

        assertThat(reminder.getRetryCount()).isEqualTo(1);
        assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.PENDING);
        verify(reminderRepository).save(reminder);
    }

    @Test
    void processOne_shouldMarkFailedAfterMaxRetries() throws Exception {
        Reminder reminder = buildReminder("rem-1", ReminderType.EMAIL);
        reminder.setRetryCount(2); // Already retried twice

        when(redisTemplate.hasKey("reminder:sent:rem-1")).thenReturn(false);
        when(senderFactory.getSender(ReminderType.EMAIL)).thenReturn(mockSender);
        doThrow(new RuntimeException("SMTP error")).when(mockSender).send(any());

        reminderProcessor.processOne(reminder);

        assertThat(reminder.getRetryCount()).isEqualTo(3);
        assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.FAILED);
        verify(reminderRepository).save(reminder);
    }

    private Reminder buildReminder(String id, ReminderType type) {
        return Reminder.builder()
                .id(id)
                .eventId("event-1")
                .userId("user-1")
                .triggerTime(Instant.now().minusSeconds(60))
                .type(type)
                .status(ReminderStatus.PENDING)
                .retryCount(0)
                .build();
    }
}
