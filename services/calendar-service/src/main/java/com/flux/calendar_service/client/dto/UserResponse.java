package com.flux.calendar_service.client.dto;

import java.time.LocalDate;

public record UserResponse(
        String id,
        String firstname,
        String lastname,
        String email,
        LocalDate birthday) {
}
