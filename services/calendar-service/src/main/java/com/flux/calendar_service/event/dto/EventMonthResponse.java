package com.flux.calendar_service.event.dto;

import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Simplified event response for month view")
public record EventMonthResponse(
        @Schema(description = "Unique event ID") String id,
        @Schema(description = "Calendar ID") String calendarId,
        @Schema(description = "Event title") String title,
        @Schema(description = "Hex color code") String colorHex,
        @Schema(description = "Start time") Instant startTime,
        @Schema(description = "End time") Instant endTime,
        @Schema(description = "All-day flag") boolean allDay,
        @Schema(description = "Shared group ID for locally-created recurring series (null for non-recurring)") String recurringGroupId,
        @Schema(description = "True if this event belongs to a recurring series") boolean isRecurring
) {
}
