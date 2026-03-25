package com.flux.notification_service.reminder.dto;

import java.time.Instant;

import com.flux.notification_service.reminder.ReminderType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReminderRequest(
        @NotBlank(message = "Event ID is required")
        String eventId,

        @NotBlank(message = "User ID is required")
        String userId,

        @NotNull(message = "Trigger time is required")
        Instant triggerTime,

        @NotNull(message = "Reminder type is required")
        ReminderType type
) {}
