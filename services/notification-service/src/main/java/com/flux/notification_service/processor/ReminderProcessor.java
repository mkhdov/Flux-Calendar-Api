package com.flux.notification_service.processor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flux.notification_service.notification.NotificationPayload;
import com.flux.notification_service.notification.NotificationSubtask;
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
    private final ObjectMapper objectMapper;

    @Value("${notification.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${notification.dedup.ttl-hours:24}")
    private long dedupTtlHours;

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

            String eventTitle      = reminder.getEventTitle() != null ? reminder.getEventTitle() : "Calendar Event";
            String eventDescription= reminder.getEventDescription();
            Instant startTime      = reminder.getEventStartTime();
            Instant endTime        = reminder.getEventEndTime();
            Boolean allDay         = reminder.getAllDay() != null ? reminder.getAllDay() : false;
            
            String meetingUrl      = reminder.getJoinUrl();
            String meetingType     = reminder.getMeetingType();
            String meetingCode     = reminder.getMeetingCode();
            String dialInPhone     = reminder.getDialInPhone();
            String dialInPin       = reminder.getDialInPin();
            String meetingPassword = reminder.getMeetingPassword();
            String locationText    = reminder.getLocationText();

            List<NotificationSubtask> subtasks = reminder.getSubtasksJson() != null && !reminder.getSubtasksJson().isBlank()
                    ? parseSubtasksJson(reminder.getSubtasksJson())
                    : List.of();

            // Build notification payload strictly from Kafka-stored database snapshot
            NotificationPayload payload = NotificationPayload.builder()
                    .reminderId(reminder.getId())
                    .eventId(reminder.getEventId())
                    .userId(reminder.getUserId())
                    .userEmail(null) // Email omitted unless synchronously resolved or replicated via User service
                    .eventTitle(eventTitle)
                    .eventDescription(eventDescription)
                    .eventStartTime(startTime)
                    .eventEndTime(endTime)
                    .allDay(allDay)
                    .eventLocation(locationText)
                    .meetingType(meetingType)
                    .meetingUrl(meetingUrl)
                    .meetingCode(meetingCode)
                    .dialInPhone(dialInPhone)
                    .dialInPin(dialInPin)
                    .meetingPassword(meetingPassword)
                    .subtasks(subtasks)
                    .triggerTime(reminder.getTriggerTime())
                    .type(reminder.getType())
                    .build();

            // Dispatch through the correct sender
            NotificationSender sender = senderFactory.getSender(reminder.getType());
            sender.send(payload);

            // Mark successful
            markSent(reminder, dedupKey);
            log.info("Notification sent successfully for reminder {}", reminder.getId());

        } catch (Exception e) {
            handleFailure(reminder, e);
        }
    }

    private List<NotificationSubtask> parseSubtasks(Object val) {
        if (!(val instanceof List<?> list) || list.isEmpty()) return List.of();

        List<NotificationSubtask> subtasks = new java.util.ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                String id = map.get("id") != null ? map.get("id").toString() : null;
                String subtask = map.get("subtask") != null ? map.get("subtask").toString() : null;
                boolean isDone = false;
                Object doneObj = map.get("isDone");
                if (doneObj instanceof Boolean b) isDone = b;
                if (doneObj instanceof String s) isDone = Boolean.parseBoolean(s);

                if (subtask != null && !subtask.isBlank()) {
                    subtasks.add(new NotificationSubtask(id, subtask, isDone));
                }
            }
        }
        return subtasks;
    }

    private List<NotificationSubtask> parseSubtasksJson(String json) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> list = objectMapper.readValue(json, java.util.List.class);
            return parseSubtasks(list);
        } catch (Exception e) {
            log.warn("Failed to parse subtasksJson: {}", e.getMessage());
            return List.of();
        }
    }

    private void markSent(Reminder reminder, String dedupKey) {
        reminder.setStatus(ReminderStatus.SENT);
        reminder.setSentAt(Instant.now());
        reminderRepository.save(reminder);

        redisTemplate.opsForValue().set(dedupKey, "SENT", dedupTtlHours, TimeUnit.HOURS);
    }

    private void handleFailure(Reminder reminder, Exception e) {
        int newRetryCount = reminder.getRetryCount() + 1;
        reminder.setRetryCount(newRetryCount);

        if (newRetryCount >= maxRetryAttempts) {
            reminder.setStatus(ReminderStatus.FAILED);
            log.error("Reminder {} FAILED after {} retries. Event: {}, Type: {}",
                    reminder.getId(), newRetryCount, reminder.getEventId(), reminder.getType(), e);
        } else {
            log.warn("Reminder {} failed (attempt {}/{}). Will retry. Error: {}",
                    reminder.getId(), newRetryCount, maxRetryAttempts, e.getMessage());
        }

        reminderRepository.save(reminder);
    }
}
