package com.flux.calendar_service.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request body when joining an event as a member")
public record MemberRequest(
        @Schema(description = "Full name of the member") String fullName
) {
}
