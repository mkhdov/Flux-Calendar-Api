package com.flux.calendar_service.event.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventUpdateRequestJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void shouldDeserializeTimezoneLessInstantFields() throws Exception {
        String json = """
                {
                  "startTime": "2026-03-09T11:00:00",
                  "endTime": "2026-03-09T12:00:00"
                }
                """;

        EventUpdateRequest request = mapper.readValue(json, EventUpdateRequest.class);

        assertEquals(Instant.parse("2026-03-09T11:00:00Z"), request.startTime());
        assertEquals(Instant.parse("2026-03-09T12:00:00Z"), request.endTime());
    }
}
