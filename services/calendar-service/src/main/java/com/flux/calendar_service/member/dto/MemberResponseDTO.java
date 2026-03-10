package com.flux.calendar_service.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Event member response")
public record MemberResponseDTO(
                @Schema(description = "Member ID") String id,
                @Schema(description = "Member email") String email,
                @Schema(description = "Full name") String fullName,
                @Schema(description = "Whether this member is the event owner") boolean isOwner,
                @Schema(description = "When the member was added") Instant createdAt) {
}
