package com.flux.calendar_service.calendar.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request body for updating a calendar (all fields optional)")
public record CalendarUpdateRequest(
        @Schema(description = "New title") String title,
        @Schema(description = "New description") String description,
        @Schema(description = "New hex color") String colorHex,
        @Schema(description = "New timezone") String timezone
) {
}
