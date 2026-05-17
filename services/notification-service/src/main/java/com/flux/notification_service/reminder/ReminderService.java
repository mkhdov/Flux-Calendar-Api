package com.flux.notification_service.reminder;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flux.notification_service.kafka.ReminderEvent;
import com.flux.notification_service.exception.DuplicateReminderException;
import com.flux.notification_service.exception.ReminderNotFoundException;
import com.flux.notification_service.reminder.dto.ReminderRequest;
import com.flux.notification_service.reminder.dto.ReminderResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReminderService {

    private final ReminderRepository reminderRepository;
    private final ReminderMapper reminderMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public ReminderResponse createReminder(ReminderRequest request) {
        // Check for duplicates at the application level
        if (reminderRepository.existsByEventIdAndTriggerTimeAndType(
                request.eventId(), request.triggerTime(), request.type())) {
            throw new DuplicateReminderException(
                    "Reminder already exists for event %s at %s with type %s"
                            .formatted(request.eventId(), request.triggerTime(), request.type()));
        }

        Reminder reminder = reminderMapper.toEntity(request);
        Reminder saved = reminderRepository.save(reminder);

        log.info("Created reminder {} for event {} at {} (type={})",
                saved.getId(), saved.getEventId(), saved.getTriggerTime(), saved.getType());

        return reminderMapper.toResponse(saved);
    }

    @Transactional
    public List<ReminderResponse> createReminders(List<ReminderRequest> requests) {
        return requests.stream()
                .map(this::createReminder)
                .collect(Collectors.toList());
    }

    /**
     * Kafka path: create reminders while persisting an event snapshot.
     * Designed to be idempotent (skips duplicates instead of throwing).
     *
     * @return number of reminders created
     */
    @Transactional
    public int createRemindersFromEvent(ReminderEvent event) {
        if (event == null || event.getReminders() == null || event.getReminders().isEmpty()) {
            return 0;
        }

        int created = 0;

        String subtasksJson = null;
        try {
            if (event.getSubtasks() != null && !event.getSubtasks().isEmpty()) {
                subtasksJson = objectMapper.writeValueAsString(event.getSubtasks());
            }
        } catch (Exception e) {
            log.warn("Failed to serialize subtasks for event {}: {}", event.getEventId(), e.getMessage());
        }

        for (var detail : event.getReminders()) {
            if (detail == null) continue;

            boolean exists = reminderRepository.existsByEventIdAndTriggerTimeAndType(
                    event.getEventId(), detail.getTriggerTime(), detail.getType());
            if (exists) {
                log.info("Skipping duplicate reminder for event {} at {} (type={})",
                        event.getEventId(), detail.getTriggerTime(), detail.getType());
                continue;
            }

            Reminder reminder = Reminder.builder()
                    .eventId(event.getEventId())
                    .userId(event.getUserId())
                    .triggerTime(detail.getTriggerTime())
                    .type(detail.getType())
                    .status(ReminderStatus.PENDING)
                    .retryCount(0)
                    .build();

            reminder.setEventTitle(event.getEventTitle());
            reminder.setEventDescription(event.getEventDescription());
            reminder.setEventStartTime(detail.getEventStartTime() != null
                    ? detail.getEventStartTime()
                    : event.getEventStartTime());
            reminder.setEventEndTime(detail.getEventEndTime() != null
                    ? detail.getEventEndTime()
                    : event.getEventEndTime());
            reminder.setAllDay(event.getAllDay());
            reminder.setLocationText(event.getLocationText());
            reminder.setMeetingType(event.getMeetingType());
            reminder.setJoinUrl(event.getJoinUrl());
            reminder.setMeetingCode(event.getMeetingCode());
            reminder.setDialInPhone(event.getDialInPhone());
            reminder.setDialInPin(event.getDialInPin());
            reminder.setMeetingPassword(event.getMeetingPassword());
            reminder.setSubtasksJson(subtasksJson);

            reminderRepository.save(reminder);
            created++;
        }

        return created;
    }

    /**
     * Kafka path: update the stored snapshot for existing reminders of an event
     * without touching trigger times or statuses.
     *
     * @return number of reminders updated
     */
    @Transactional
    public int updateSnapshotForEvent(ReminderEvent event) {
        if (event == null || event.getEventId() == null || event.getEventId().isBlank()) {
            return 0;
        }

        String subtasksJson = null;
        try {
            if (event.getSubtasks() != null && !event.getSubtasks().isEmpty()) {
                subtasksJson = objectMapper.writeValueAsString(event.getSubtasks());
            }
        } catch (Exception e) {
            log.warn("Failed to serialize subtasks for event {}: {}", event.getEventId(), e.getMessage());
        }

        List<Reminder> reminders = reminderRepository.findByEventIdOrderByTriggerTimeAsc(event.getEventId());
        int updated = 0;
        for (Reminder reminder : reminders) {
            if (reminder.getStatus() != ReminderStatus.PENDING) continue;

            reminder.setEventTitle(event.getEventTitle());
            reminder.setEventDescription(event.getEventDescription());
            if (!Boolean.TRUE.equals(event.getRecurringMaster())) {
                reminder.setEventStartTime(event.getEventStartTime());
                reminder.setEventEndTime(event.getEventEndTime());
            }
            reminder.setAllDay(event.getAllDay());
            reminder.setLocationText(event.getLocationText());
            reminder.setMeetingType(event.getMeetingType());
            reminder.setJoinUrl(event.getJoinUrl());
            reminder.setMeetingCode(event.getMeetingCode());
            reminder.setDialInPhone(event.getDialInPhone());
            reminder.setDialInPin(event.getDialInPin());
            reminder.setMeetingPassword(event.getMeetingPassword());
            reminder.setSubtasksJson(subtasksJson);

            reminderRepository.save(reminder);
            updated++;
        }

        return updated;
    }

    public List<ReminderResponse> getByEventId(String eventId) {
        return reminderRepository.findByEventIdOrderByTriggerTimeAsc(eventId)
                .stream()
                .map(reminderMapper::toResponse)
                .collect(Collectors.toList());
    }

    public ReminderResponse getById(String id) {
        return reminderRepository.findById(id)
                .map(reminderMapper::toResponse)
                .orElseThrow(() -> new ReminderNotFoundException("Reminder not found: " + id));
    }

    @Transactional
    public void deleteReminder(String id) {
        if (!reminderRepository.existsById(id)) {
            throw new ReminderNotFoundException("Reminder not found: " + id);
        }
        reminderRepository.deleteById(id);
        log.info("Deleted reminder {}", id);
    }

    @Transactional
    public void deleteByEventId(String eventId) {
        reminderRepository.deleteByEventId(eventId);
        log.info("Deleted all reminders for event {}", eventId);
    }

    @Transactional
    public void cancelByEventId(String eventId) {
        List<Reminder> reminders = reminderRepository.findByEventIdOrderByTriggerTimeAsc(eventId);
        reminders.stream()
                .filter(r -> r.getStatus() == ReminderStatus.PENDING)
                .forEach(r -> {
                    r.setStatus(ReminderStatus.CANCELLED);
                    reminderRepository.save(r);
                });
        log.info("Cancelled all pending reminders for event {}", eventId);
    }
}
