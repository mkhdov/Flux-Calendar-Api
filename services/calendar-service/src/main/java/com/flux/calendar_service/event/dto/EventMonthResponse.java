package com.flux.calendar_service.event.dto;

import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Simplified event response for month view")
public record EventMonthResponse(
        @Schema(description = "Unique event ID") String id,
        @Schema(description = "Event title") String title,
        @Schema(description = "Hex color code") String colorHex,
        @Schema(description = "Start time") Instant start,
        @Schema(description = "End time") Instant end
) {
}
