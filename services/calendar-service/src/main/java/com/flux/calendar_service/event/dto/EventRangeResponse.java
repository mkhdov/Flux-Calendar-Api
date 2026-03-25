package com.flux.calendar_service.event.dto;

import java.time.Instant;
import com.flux.calendar_service.event.EventStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Lightweight event representation for range queries")
public record EventRangeResponse(
    @Schema(description = "Unique identifier of the event")
    String id,
    @Schema(description = "ID of the calendar this event belongs to")
    String calendarId,
    @Schema(description = "Title of the event")
    String title,
    @Schema(description = "Hex color code for the event")
    String colorHex,
    @Schema(description = "Start time of the event")
    Instant startTime,
    @Schema(description = "End time of the event")
    Instant endTime,
    @Schema(description = "Whether it is an all-day event")
    boolean allDay,
    @Schema(description = "Current status of the event")
    EventStatus status,
    @Schema(description = "Flag indicating if the event has a location attached")
    boolean hasLocation,
    @Schema(description = "Flag indicating if the event has a conference (e.g. Meet/Zoom) attached")
    boolean hasConference,
    @Schema(description = "Flag indicating if the event has attachments")
    boolean hasAttachment,
    @Schema(description = "Flag indicating if the event has subtasks")
    boolean hasSubtasks,
    @Schema(description = "Flag indicating if the event has members/attendees")
    boolean hasMember,
    @Schema(description = "Flag indicating if the event has notes")
    boolean hasNotes
) {}
