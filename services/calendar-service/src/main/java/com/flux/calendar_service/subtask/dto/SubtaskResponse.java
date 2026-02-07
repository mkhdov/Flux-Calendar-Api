package com.flux.calendar_service.subtask.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Subtask response with id, title and completion status")
public record SubtaskResponse(
                @Schema(description = "Unique subtask ID") String id,
                @Schema(description = "Subtask title/description") String subtask,
                @Schema(description = "Whether the subtask is done") boolean isDone) {
}
