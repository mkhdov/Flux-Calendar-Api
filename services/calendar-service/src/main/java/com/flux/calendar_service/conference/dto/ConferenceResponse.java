package com.flux.calendar_service.conference.dto;

import com.flux.calendar_service.conference.Conference;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Conference/meeting response with join link and metadata")
public record ConferenceResponse(
        @Schema(description = "Unique conference ID") String id,
        @Schema(description = "Conference type") Conference.ConferenceType type,
        @Schema(description = "Google conference ID") String googleConferenceId,
        @Schema(description = "Meet link") String meetLink,
        @Schema(description = "Meeting code") String meetingCode,
        @Schema(description = "Dial-in phone number") String phoneNumber,
        @Schema(description = "PIN") String pin,
        @Schema(description = "Conference join URL") String conferenceLink,
        @Schema(description = "Password") String conferencePassword,
        @Schema(description = "Platform name") String platformName,
        @Schema(description = "Sync status") Conference.SyncStatus syncStatus,
        @Schema(description = "Last sync time") Instant lastSynced,
        @Schema(description = "Creation timestamp") Instant createdAt,
        @Schema(description = "Last update timestamp") Instant updatedAt) {
}
