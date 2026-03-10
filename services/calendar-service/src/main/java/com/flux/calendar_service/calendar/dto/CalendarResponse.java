package com.flux.calendar_service.calendar.dto;

import com.flux.calendar_service.calendar.AccessLevel;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Calendar response with full details")
public record CalendarResponse(
        @Schema(description = "Unique calendar ID") String id,
        @Schema(description = "Owner user ID") String userId,
        @Schema(description = "Calendar title") String title,
        @Schema(description = "Calendar description") String description,
        @Schema(description = "Hex color code") String colorHex,
        @Schema(description = "Timezone") String timezone,
        @Schema(description = "Whether this is the primary calendar") boolean isPrimary,
        @Schema(description = "Soft-delete flag") boolean isDeleted,
        @Schema(description = "Creation timestamp") Instant createdAt,
        @Schema(description = "Last update timestamp") Instant updatedAt
) {
}
