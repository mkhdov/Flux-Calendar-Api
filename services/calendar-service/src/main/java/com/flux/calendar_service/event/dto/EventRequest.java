package com.flux.calendar_service.event.dto;

import com.flux.calendar_service.event.EventStatus;
import com.flux.calendar_service.event.EventType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "Request object for creating a new event")
public record EventRequest(
        @Schema(description = "Title of the event", example = "Team Meeting") @NotNull @NotBlank String title,
        @Schema(description = "Detailed description of the event", example = "Weekly sync to discuss project progress") @NotNull @NotBlank String description,
        @Schema(description = "Hex color code for the event", example = "#4285F4") @NotNull @NotBlank String colorHex,
        @Schema(description = "Type of the event") EventType type,
        @Schema(description = "Date of the event") LocalDate date,
        @Schema(description = "Start time of the event") LocalDateTime startTime,
        @Schema(description = "End time of the event") LocalDateTime endTime,
        @Schema(description = "Whether the event is an all-day event") boolean allDay,
        @Schema(description = "Current status of the event") EventStatus status,
        @Schema(description = "List of tasks associated with the event") java.util.List<com.flux.calendar_service.task.dto.TaskRequest> tasks,
        @Schema(description = "List of attachments associated with the event") java.util.List<com.flux.calendar_service.attachment.dto.AttachmentRequest> attachments) {
}
