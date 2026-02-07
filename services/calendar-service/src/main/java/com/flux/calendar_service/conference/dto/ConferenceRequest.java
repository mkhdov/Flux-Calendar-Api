package com.flux.calendar_service.conference.dto;

import com.flux.calendar_service.conference.Conference;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Conference/meeting link request (e.g. Google Meet, Zoom)")
public record ConferenceRequest(
        @Schema(description = "Conference type", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Conference.ConferenceType type,
        @Schema(description = "Google conference ID") String googleConferenceId,
        @Schema(description = "Meet link URL") String meetLink,
        @Schema(description = "Meeting code") String meetingCode,
        @Schema(description = "Phone number for dial-in") String phoneNumber,
        @Schema(description = "PIN") String pin,
        @Schema(description = "Conference join URL") String conferenceLink,
        @Schema(description = "Conference password") String conferencePassword,
        @Schema(description = "Platform name") String platformName,
        @Schema(description = "Sync status") Conference.SyncStatus syncStatus) {
}
