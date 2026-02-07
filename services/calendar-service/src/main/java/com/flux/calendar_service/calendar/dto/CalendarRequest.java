package com.flux.calendar_service.calendar.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

@Schema(description = "Request body for creating a new calendar")
public record CalendarRequest(
        @Schema(description = "Owner user ID", requiredMode = Schema.RequiredMode.REQUIRED) @NotEmpty @NotBlank String userId,
        @Schema(description = "Calendar title", example = "Work", requiredMode = Schema.RequiredMode.REQUIRED) @NotEmpty @NotBlank String title,
        @Schema(description = "Optional description") String description,
        @Schema(description = "Hex color code", example = "#4285F4") String colorHex,
        @Schema(description = "Timezone", example = "Europe/London", requiredMode = Schema.RequiredMode.REQUIRED) @NotEmpty @NotBlank String timezone,
        @Schema(description = "Whether this is the user's primary calendar") boolean isPrimary
) {
}
