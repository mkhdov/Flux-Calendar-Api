package com.flux.calendar_service.subtask.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request body for creating or updating a subtask")
public record SubtaskRequest(
                @Schema(description = "Subtask title/description", example = "Book the room", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank(message = "Subtask description is required") String subtask,
                @Schema(description = "Whether the subtask is completed") boolean isDone) {
}
