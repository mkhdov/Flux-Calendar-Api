package com.flux.calendar_service.task.dto;

import com.flux.calendar_service.task.TaskPriority;
import com.flux.calendar_service.task.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;
import java.time.LocalDate;

@Schema(description = "Request body for partial update of a task (all fields optional)")
@Builder
public record TaskUpdateRequest(
        @Schema(description = "New title") String title,
        @Schema(description = "New description") String description,
        @Schema(description = "New status") TaskStatus status,
        @Schema(description = "New priority") TaskPriority priority,
        @Schema(description = "New due date") LocalDate date,
        @Schema(description = "New deadline")
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = com.flux.calendar_service.config.FlexibleInstantDeserializer.class) Instant deadline) {
}
