package com.flux.calendar_service.event.dto;

import java.time.Instant;
import java.util.List;

import com.flux.calendar_service.conference.Conference.ConferenceType;
import com.flux.calendar_service.event.EventType;
import com.flux.calendar_service.subtask.dto.SubtaskResponse;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Lightweight event payload intended for reminder/notification rendering (excludes attachments, members, notes)")
public record EventNotificationResponse(
        @Schema(description = "Unique event ID") String id,
        @Schema(description = "Event title") String title,
        @Schema(description = "Event description") String description,
        @Schema(description = "Start time") Instant startTime,
        @Schema(description = "End time") Instant endTime,
        @Schema(description = "All-day event flag") boolean allDay,
        @Schema(description = "Event type") EventType type,
        @Schema(description = "Meeting type (if any)") ConferenceType meetingType,
        @Schema(description = "Meeting join URL (if any)") String joinUrl,
        @Schema(description = "Meeting code (Google Meet)") String meetingCode,
        @Schema(description = "Dial-in phone number (Google Meet)") String phoneNumber,
        @Schema(description = "Dial-in PIN (Google Meet)") String pin,
        @Schema(description = "Meeting password (Zoom)") String meetingPassword,
        @Schema(description = "Human-readable location (if any)") String locationText,
        @Schema(description = "List of subtasks") List<SubtaskResponse> subtasks
) {
}
