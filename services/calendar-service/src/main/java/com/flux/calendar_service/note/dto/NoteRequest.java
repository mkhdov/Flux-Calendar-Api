package com.flux.calendar_service.note.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request body for creating or updating a note")
public record NoteRequest(
        @Schema(description = "Note text content", example = "Remember to send the report by Friday", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Note content is required") String content) {
}
