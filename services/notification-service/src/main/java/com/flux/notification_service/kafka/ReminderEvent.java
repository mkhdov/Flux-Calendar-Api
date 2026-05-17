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
    private String eventDescription;
    private Instant eventStartTime;
    private Instant eventEndTime;
    private Boolean allDay;
    private String locationText;
    private String meetingType; // ZOOM | GOOGLE_MEET
    private String joinUrl;
    private String meetingCode;
    private String dialInPhone;
    private String dialInPin;
    private String meetingPassword;
    private List<Subtask> subtasks;
    private Boolean recurringMaster;
    private String recurrenceRule;
    private Action action;
    private List<ReminderDetail> reminders;

    public enum Action {
        CREATE,
        UPDATE,
        DELETE,
        SNAPSHOT_UPDATE
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReminderDetail {
        private Instant triggerTime;
        private ReminderType type;
        private Instant eventStartTime;
        private Instant eventEndTime;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Subtask {
        private String id;
        private String subtask;
        private boolean isDone;
    }
}
