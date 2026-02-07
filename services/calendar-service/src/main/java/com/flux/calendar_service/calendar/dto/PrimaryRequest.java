package com.flux.calendar_service.calendar.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request body for setting primary calendar (user ID)")
public record PrimaryRequest(
        @Schema(description = "User ID who owns the calendar", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String userId
) {
}
