package com.flux.notification_service.kafka;

import java.time.Instant;
import java.util.List;

import com.flux.notification_service.reminder.ReminderType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Kafka event DTO for reminder operations.
 * Published by calendar-service when events are created/updated/deleted.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReminderEvent {

    private String eventId;
    private String userId;
    private String eventTitle;
    private Action action;
    private List<ReminderDetail> reminders;

    public enum Action {
        CREATE,
        UPDATE,
        DELETE
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReminderDetail {
        private Instant triggerTime;
        private ReminderType type;
    }
}
