package com.flux.calendar_service.event.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.flux.calendar_service.config.FlexibleInstantDeserializer;
import com.flux.calendar_service.conference.dto.ConferenceRequest;
import com.flux.calendar_service.event.EventStatus;
import com.flux.calendar_service.event.EventType;
import com.flux.calendar_service.location.dto.LocationRequest;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Request body for updating an event (all fields optional)")
public record EventUpdateRequest(
                @Schema(description = "New title") String title,
                @Schema(description = "New description") String description,
                @Schema(description = "New hex color") String colorHex,
                @Schema(description = "New location") LocationRequest location,
                @Schema(description = "New conference") ConferenceRequest conference,
                @Schema(description = "New event type") EventType type,
                @Schema(description = "New start time (ISO-8601; timezone-less values are treated as UTC)", example = "2026-03-09T22:30:00Z")
                @JsonDeserialize(using = FlexibleInstantDeserializer.class) Instant startTime,
                @Schema(description = "New end time (ISO-8601; timezone-less values are treated as UTC)", example = "2026-03-09T23:30:00Z")
                @JsonDeserialize(using = FlexibleInstantDeserializer.class) Instant endTime,
                @Schema(description = "All-day flag") Boolean allDay,
                @Schema(description = "New status") EventStatus status,
                @Schema(description = "Updated attachments list") java.util.List<com.flux.calendar_service.attachment.dto.AttachmentRequest> attachments) {
}
