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
import org.springframework.web.client.RestTemplate;

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
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${notification.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${notification.dedup.ttl-hours:24}")
    private long dedupTtlHours;

    @Value("${user-management-service.url:http://user-management-service:8081}")
    private String userServiceUrl;

    @Value("${calendar-service.url:http://calendar-service:8082}")
    private String calendarServiceUrl;

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

            // Resolve event details (fallback only; preferred source is snapshot stored in Reminder)
            Map<String, Object> eventDetails = java.util.Collections.emptyMap();
            if (needsEventLookup(reminder)) {
                eventDetails = resolveEventDetails(reminder.getEventId());
            }

            String eventTitle = firstNonBlank(reminder.getEventTitle(),
                    (String) eventDetails.getOrDefault("title", "Calendar Event"));
            String eventDescription = firstNonBlank(reminder.getEventDescription(),
                    (String) eventDetails.get("description"));

            Instant startTime = reminder.getEventStartTime() != null
                    ? reminder.getEventStartTime()
                    : parseInstant(eventDetails.get("startTime"));
            Instant endTime = reminder.getEventEndTime() != null
                    ? reminder.getEventEndTime()
                    : parseInstant(eventDetails.get("endTime"));

            Boolean allDay = reminder.getAllDay();
            if (allDay == null) {
                Object allDayObj = eventDetails.get("allDay");
                if (allDayObj instanceof Boolean b) allDay = b;
                if (allDayObj instanceof String s) allDay = Boolean.parseBoolean(s);
            }

            String meetingUrl = firstNonBlank(reminder.getJoinUrl(), asString(eventDetails.get("joinUrl")));
            String meetingType = firstNonBlank(reminder.getMeetingType(), asString(eventDetails.get("meetingType")));
            String meetingCode = firstNonBlank(reminder.getMeetingCode(), asString(eventDetails.get("meetingCode")));
            String dialInPhone = firstNonBlank(reminder.getDialInPhone(), asString(eventDetails.get("phoneNumber")));
            String dialInPin = firstNonBlank(reminder.getDialInPin(), asString(eventDetails.get("pin")));
            String meetingPassword = firstNonBlank(reminder.getMeetingPassword(), asString(eventDetails.get("meetingPassword")));
            String locationText = firstNonBlank(reminder.getLocationText(), (String) eventDetails.get("locationText"));

            List<NotificationSubtask> subtasks = reminder.getSubtasksJson() != null && !reminder.getSubtasksJson().isBlank()
                    ? parseSubtasksJson(reminder.getSubtasksJson())
                    : parseSubtasks(eventDetails.get("subtasks"));

            // Build notification payload
            NotificationPayload payload = NotificationPayload.builder()
                    .reminderId(reminder.getId())
                    .eventId(reminder.getEventId())
                    .userId(reminder.getUserId())
                    .userEmail(userEmail)
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

    private Instant parseInstant(Object val) {
        if (val == null) return null;
        try {
            if (val instanceof String s) return Instant.parse(s);
            if (val instanceof Long l) return Instant.ofEpochMilli(l);
        } catch (Exception e) {
            log.warn("Failed to parse instant from {}: {}", val, e.getMessage());
        }
        return null;
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

    private boolean needsEventLookup(Reminder reminder) {
        if (reminder == null) return true;
        return isBlank(reminder.getEventTitle())
                || reminder.getEventStartTime() == null;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String asString(Object o) {
        return o == null ? null : o.toString();
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }

    /**
     * Mark reminder as SENT and record in Redis for dedup.
     */
    private void markSent(Reminder reminder, String dedupKey) {
        reminder.setStatus(ReminderStatus.SENT);
        reminder.setSentAt(Instant.now());
        reminderRepository.save(reminder);

        redisTemplate.opsForValue().set(dedupKey, "SENT", dedupTtlHours, TimeUnit.HOURS);
    }

    /**
     * Handle processing failure: increment retry count or mark FAILED.
     */
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
        String path = "/users/" + userId + "/email";
        return tryGetMap(userServiceUrl + path)
                .or(() -> tryGetMap("http://localhost:8081" + path))
                .map(m -> m.get("email"))
                .map(Object::toString)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .orElse(null);
    }

    /**
     * Resolve event details via REST call to calendar-service.
     * Falls back gracefully if the call fails.
     */
    private Map<String, Object> resolveEventDetails(String eventId) {
        String path = "/events/" + eventId + "/notification";

        return tryGetMap(calendarServiceUrl + path)
                .or(() -> tryGetMap("http://localhost:8082" + path))
                .or(() -> tryGetMap("http://localhost:8083" + path))
                .orElseGet(java.util.Collections::emptyMap);
    }

    private Optional<Map<String, Object>> tryGetMap(String url) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, java.util.Map.class);
            return Optional.ofNullable(response);
        } catch (Exception e) {
            log.warn("Failed to GET {}: {}", url, e.getMessage());
            return Optional.empty();
        }
    }
}
