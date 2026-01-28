package com.flux.calendar_service.member.dto;

import com.flux.calendar_service.event.Event;
import com.flux.calendar_service.event.dto.EventResponse;

import java.time.LocalDateTime;

public record MemberResponseDTO(
        String id,
        String email,
        String fullName,
        boolean isOwner,
        LocalDateTime createdAt
) {
}
