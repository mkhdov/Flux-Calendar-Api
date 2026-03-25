package com.flux.notification_service.notification;

import java.time.Instant;

import com.flux.notification_service.reminder.ReminderType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPayload {
    private String reminderId;
    private String eventId;
    private String userId;
    private String userEmail;
    private String eventTitle;
    private String eventDescription;
    private Instant triggerTime;
    private Instant eventStartTime;
    private ReminderType type;
}
