package com.flux.calendar_service.event.dto;

import com.flux.calendar_service.event.EventStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Compact event response for list views")
public record EventListItemResponse(
        @Schema(description = "Unique event ID") String id,
        @Schema(description = "Calendar ID this event belongs to") String calendarId,
        @Schema(description = "Event title") String title,
        @Schema(description = "Event description") String description,
        @Schema(description = "Hex color code") String colorHex,
        @Schema(description = "Start time") Instant startTime,
        @Schema(description = "End time") Instant endTime,
        @Schema(description = "All-day flag") boolean allDay,
        @Schema(description = "Event status") EventStatus status,
        @Schema(description = "Shared group ID for locally-created recurring series (null for non-recurring)") String recurringGroupId,
        @Schema(description = "True if this event belongs to a recurring series") boolean isRecurring,
        @Schema(description = "True if location exists") boolean hasLocation,
        @Schema(description = "True if conference exists") boolean hasConference,
        @Schema(description = "True if subtasks list is non-empty") boolean hasSubtasks,
        @Schema(description = "True if attachments list is non-empty") boolean hasAttachments,
        @Schema(description = "True if members list is non-empty") boolean hasMembers,
        @Schema(description = "True if notes list is non-empty") boolean hasNotes
) {
}
