package com.flux.calendar_service.event.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.flux.calendar_service.config.FlexibleInstantDeserializer;
import com.flux.calendar_service.event.EventStatus;
import com.flux.calendar_service.event.EventType;
import com.flux.calendar_service.location.dto.LocationRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;

@Schema(description = "Request object for creating a new event")
public record EventRequest(
        @Schema(description = "Title of the event", example = "Team Meeting") @NotNull @NotBlank String title,
        @Schema(description = "Detailed description of the event", example = "Weekly sync to discuss project progress") String description,
        @Schema(description = "Hex color code for the event", example = "#4285F4") @NotNull @NotBlank String colorHex,
        @Schema(description = "Type of the event") EventType type,
        @Schema(description = "Date of the event") LocalDate date,
        @Schema(description = "Start time of the event (ISO-8601; timezone-less values are treated as UTC)", example = "2026-03-09T22:30:00Z")
        @JsonDeserialize(using = FlexibleInstantDeserializer.class) Instant startTime,
        @Schema(description = "End time of the event (ISO-8601; timezone-less values are treated as UTC)", example = "2026-03-09T23:30:00Z")
        @JsonDeserialize(using = FlexibleInstantDeserializer.class) Instant endTime,
        @Schema(description = "Whether the event is an all-day event") boolean allDay,
        @Schema(description = "Current status of the event") EventStatus status,
        @Schema(description = "List of subtasks associated with the event") java.util.List<com.flux.calendar_service.subtask.dto.SubtaskRequest> subtasks,
        @Schema(description = "List of attachments associated with the event") java.util.List<com.flux.calendar_service.attachment.dto.AttachmentRequest> attachments,
        @Schema(description = "Location associated with event") LocationRequest location,
        @Schema(description = "Planner mode") PlannerRequest planner) {
}
