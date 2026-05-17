package com.flux.notification_service.reminder.dto;

import java.time.Instant;

import com.flux.notification_service.reminder.ReminderStatus;
import com.flux.notification_service.reminder.ReminderType;

public record ReminderResponse(
        String id,
        String eventId,
        String userId,
        Instant triggerTime,
        ReminderType type,
        ReminderStatus status,
        int retryCount,
        Instant eventStartTime,
        Instant eventEndTime,
        Instant createdAt,
        Instant updatedAt
) {}
