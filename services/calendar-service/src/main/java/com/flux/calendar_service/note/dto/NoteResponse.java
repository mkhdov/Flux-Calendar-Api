package com.flux.calendar_service.note.dto;

import io.swagger.v3.oas.annotations.media.Schema;import java.time.Instant;
@Schema(description = "Note response with id, content and timestamps")
public record NoteResponse(
        @Schema(description = "Unique note ID") String id,
        @Schema(description = "Note text content") String content,
        @Schema(description = "Creation timestamp") Instant createdAt,
        @Schema(description = "Last update timestamp") Instant updatedAt) {
}
