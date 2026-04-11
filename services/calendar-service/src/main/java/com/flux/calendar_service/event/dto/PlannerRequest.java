package com.flux.calendar_service.event.dto;

public record PlannerRequest(
        boolean monday,
        boolean tuesday,
        boolean wednesday,
        boolean thursday,
        boolean friday,
        boolean saturday,
        boolean sunday,
        int numOfWeeks
) {
}
