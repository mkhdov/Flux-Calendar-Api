package com.flux.calendar_service.task.dto;

import com.flux.calendar_service.task.TaskPriority;
import com.flux.calendar_service.task.TaskStatus;
import com.flux.calendar_service.subtask.dto.SubtaskRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Request body for creating a new task")
@Builder
public record TaskRequest(
        @Schema(description = "ID of the calendar this task belongs to", example = "cal-123", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Calendar ID is required") String calendarId,
        @Schema(description = "Title of the task", example = "Review PR", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Title is required") String title,
        @Schema(description = "Optional detailed description of the task") String description,
        @Schema(description = "Current status of the task") TaskStatus status,
        @Schema(description = "Priority level of the task") TaskPriority priority,
        @Schema(description = "Optional due date (date only)") LocalDate date,
        @Schema(description = "Optional deadline (date and time)")
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = com.flux.calendar_service.config.FlexibleInstantDeserializer.class) Instant deadline,
        @Schema(description = "Optional list of subtasks") List<SubtaskRequest> subtasks) {
}
