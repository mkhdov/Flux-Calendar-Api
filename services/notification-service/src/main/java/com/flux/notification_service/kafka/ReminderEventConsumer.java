package com.flux.notification_service.kafka;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.flux.notification_service.reminder.ReminderService;
import com.flux.notification_service.reminder.dto.ReminderRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Consumes reminder events from Kafka.
 * When calendar-service creates/updates/deletes events, it can publish
 * to the "reminder-events" topic and this consumer will handle the reminders.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReminderEventConsumer {

    private final ReminderService reminderService;

    @KafkaListener(
            topics = "reminder-events",
            groupId = "notification-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleReminderEvent(ReminderEvent event) {
        log.info("Received reminder event: eventId={}, action={}", event.getEventId(), event.getAction());

        try {
            switch (event.getAction()) {
                case CREATE -> handleCreate(event);
                case UPDATE -> handleUpdate(event);
                case DELETE -> handleDelete(event);
            }
        } catch (Exception e) {
            log.error("Failed to process reminder event: eventId={}, action={}",
                    event.getEventId(), event.getAction(), e);
        }
    }

    private void handleCreate(ReminderEvent event) {
        if (event.getReminders() == null || event.getReminders().isEmpty()) {
            log.warn("CREATE event has no reminders: eventId={}", event.getEventId());
            return;
        }

        var requests = event.getReminders().stream()
                .map(detail -> new ReminderRequest(
                        event.getEventId(),
                        event.getUserId(),
                        detail.getTriggerTime(),
                        detail.getType()
                ))
                .toList();

        reminderService.createReminders(requests);
        log.info("Created {} reminder(s) for event {}", requests.size(), event.getEventId());
    }

    private void handleUpdate(ReminderEvent event) {
        // On update: cancel old reminders and create new ones
        reminderService.cancelByEventId(event.getEventId());
        handleCreate(event);
        log.info("Updated reminders for event {}", event.getEventId());
    }

    private void handleDelete(ReminderEvent event) {
        reminderService.cancelByEventId(event.getEventId());
        log.info("Cancelled reminders for deleted event {}", event.getEventId());
    }
}
