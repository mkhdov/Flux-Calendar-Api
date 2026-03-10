package com.flux.calendar_service.task.dto;

import com.flux.calendar_service.task.TaskPriority;
import com.flux.calendar_service.task.TaskStatus;
import com.flux.calendar_service.subtask.dto.SubtaskResponse;
import com.flux.calendar_service.note.dto.NoteResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Task response with full details including subtasks and notes")
@Builder
public record TaskResponse(
        @Schema(description = "Unique task ID") String id,
        @Schema(description = "ID of the calendar") String calendarId,
        @Schema(description = "Task title") String title,
        @Schema(description = "Task description") String description,
        @Schema(description = "Current status") TaskStatus status,
        @Schema(description = "Priority level") TaskPriority priority,
        @Schema(description = "Due date") LocalDate date,
        @Schema(description = "Deadline date and time") Instant deadline,
        @Schema(description = "List of subtasks") List<SubtaskResponse> subtasks,
        @Schema(description = "List of notes attached to the task") List<NoteResponse> notes,
        @Schema(description = "Creation timestamp") Instant createdAt,
        @Schema(description = "Last update timestamp") Instant updatedAt) {
}
