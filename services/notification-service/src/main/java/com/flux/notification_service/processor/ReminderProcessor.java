package com.flux.notification_service.processor;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import com.flux.notification_service.notification.NotificationPayload;
import com.flux.notification_service.notification.NotificationSender;
import com.flux.notification_service.notification.NotificationSenderFactory;
import com.flux.notification_service.reminder.Reminder;
import com.flux.notification_service.reminder.ReminderRepository;
import com.flux.notification_service.reminder.ReminderStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Core processing engine: fetches pending reminders, resolves user info,
 * dispatches notifications, and handles retries.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReminderProcessor {

    private final ReminderRepository reminderRepository;
    private final NotificationSenderFactory senderFactory;
    private final RedisTemplate<String, String> redisTemplate;
    private final RestTemplate restTemplate;

    @Value("${notification.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${notification.dedup.ttl-hours:24}")
    private long dedupTtlHours;

    @Value("${user-management-service.url:http://localhost:8081}")
    private String userServiceUrl;

    private static final String DEDUP_KEY_PREFIX = "reminder:sent:";

    /**
     * Main processing method called by the scheduler.
     * Fetches pending reminders where triggerTime <= now and processes them in batch.
     */
    public void processReminders() {
        Instant now = Instant.now();

        List<Reminder> pendingReminders = reminderRepository
                .findTop100ByStatusAndTriggerTimeBeforeOrderByTriggerTimeAsc(ReminderStatus.PENDING, now);

        if (pendingReminders.isEmpty()) {
            return;
        }

        log.info("Processing {} pending reminder(s)", pendingReminders.size());

        for (Reminder reminder : pendingReminders) {
            processOne(reminder);
        }

        log.info("Batch processing complete. Processed {} reminder(s)", pendingReminders.size());
    }

    /**
     * Process a single reminder: dedup check → resolve user → send → update status.
     */
    @Transactional
    public void processOne(Reminder reminder) {
        String dedupKey = DEDUP_KEY_PREFIX + reminder.getId();

        try {
            // Idempotency: check if already sent via Redis
            if (Boolean.TRUE.equals(redisTemplate.hasKey(dedupKey))) {
                log.info("Reminder {} already processed (dedup hit), marking SENT", reminder.getId());
                markSent(reminder, dedupKey);
                return;
            }

            // Resolve user email
            String userEmail = resolveUserEmail(reminder.getUserId());

            // Build notification payload
            NotificationPayload payload = NotificationPayload.builder()
                    .reminderId(reminder.getId())
                    .eventId(reminder.getEventId())
                    .userId(reminder.getUserId())
                    .userEmail(userEmail)
                    .eventTitle(resolveEventTitle(reminder.getEventId()))
                    .triggerTime(reminder.getTriggerTime())
                    .type(reminder.getType())
                    .build();

            // Dispatch through the correct sender
            NotificationSender sender = senderFactory.getSender(reminder.getType());
            sender.send(payload);

            // Mark as sent
            markSent(reminder, dedupKey);

            log.info("Reminder {} sent successfully via {} to user {}",
                    reminder.getId(), reminder.getType(), reminder.getUserId());

        } catch (Exception e) {
            handleFailure(reminder, e);
        }
    }

    private void markSent(Reminder reminder, String dedupKey) {
        reminder.setStatus(ReminderStatus.SENT);
        reminderRepository.save(reminder);

        // Set dedup key in Redis with TTL
        try {
            redisTemplate.opsForValue().set(dedupKey, "1", dedupTtlHours, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Failed to set Redis dedup key for reminder {}: {}", reminder.getId(), e.getMessage());
            // Non-critical: status is already SENT in DB
        }
    }

    private void handleFailure(Reminder reminder, Exception e) {
        int newRetryCount = reminder.getRetryCount() + 1;
        reminder.setRetryCount(newRetryCount);

        if (newRetryCount >= maxRetryAttempts) {
            reminder.setStatus(ReminderStatus.FAILED);
            log.error("Reminder {} FAILED after {} retries. Event: {}, Type: {}",
                    reminder.getId(), newRetryCount, reminder.getEventId(), reminder.getType(), e);
        } else {
            // Keep PENDING for retry on next scheduler tick
            log.warn("Reminder {} failed (attempt {}/{}). Will retry. Error: {}",
                    reminder.getId(), newRetryCount, maxRetryAttempts, e.getMessage());
        }

        reminderRepository.save(reminder);
    }

    /**
     * Resolve user email via REST call to user-management-service.
     */
    private String resolveUserEmail(String userId) {
        try {
            @SuppressWarnings("unchecked")
            var response = restTemplate.getForObject(
                    userServiceUrl + "/users/" + userId,
                    java.util.Map.class);

            if (response != null && response.containsKey("email")) {
                return (String) response.get("email");
            }
        } catch (Exception e) {
            log.warn("Failed to resolve email for user {}: {}", userId, e.getMessage());
        }
        return null;
    }

    /**
     * Resolve event title via REST call to calendar-service.
     * Falls back gracefully if the call fails.
     */
    private String resolveEventTitle(String eventId) {
        try {
            @SuppressWarnings("unchecked")
            var response = restTemplate.getForObject(
                    "http://calendars-service/events/" + eventId,
                    java.util.Map.class);

            if (response != null && response.containsKey("title")) {
                return (String) response.get("title");
            }
        } catch (Exception e) {
            log.warn("Failed to resolve event title for event {}: {}", eventId, e.getMessage());
        }
        return "Calendar Event";
    }
}
