package com.flux.calendar_service.event.dto;

import com.flux.calendar_service.calendar.dto.CalendarResponse;
import com.flux.calendar_service.conference.dto.ConferenceResponse;
import com.flux.calendar_service.event.EventStatus;
import com.flux.calendar_service.event.EventType;
import com.flux.calendar_service.event.SyncStatus;
import com.flux.calendar_service.location.dto.LocationResponse;
import com.flux.calendar_service.member.dto.MemberResponseDTO;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Event response with full details including calendar, location, conference, subtasks, attachments, members and notes")
public record EventResponse(
        @Schema(description = "Unique event ID") String id,
        @Schema(description = "Calendar this event belongs to") CalendarResponse calendar,
        @Schema(description = "Event title") String title,
        @Schema(description = "Event description") String description,
        @Schema(description = "Hex color code") String colorHex,
        @Schema(description = "Event location") LocationResponse location,
        @Schema(description = "Conference/meeting link") ConferenceResponse conference,
        @Schema(description = "Event type") EventType type,
        @Schema(description = "Start time") Instant startTime,
        @Schema(description = "End time") Instant endTime,
        @Schema(description = "All-day event flag") boolean allDay,
        @Schema(description = "Sync status with Google") SyncStatus syncStatus,
        @Schema(description = "Event status") EventStatus status,
        @Schema(description = "List of subtasks") List<com.flux.calendar_service.subtask.dto.SubtaskResponse> subtasks,
        @Schema(description = "List of attachments") List<com.flux.calendar_service.attachment.dto.AttachmentResponse> attachments,
        @Schema(description = "List of members") List<MemberResponseDTO> members,
        @Schema(description = "List of notes") List<com.flux.calendar_service.note.dto.NoteResponse> notes,
        @Schema(description = "Creation timestamp") Instant createdAt,
        @Schema(description = "Last update timestamp") Instant updatedAt) {
}
