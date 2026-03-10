package com.flux.calendar_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlexibleInstantDeserializerTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule().addDeserializer(Instant.class, new FlexibleInstantDeserializer()));

    @Test
    void deserialize_ShouldParseTimezoneLessInstant() throws Exception {
        Payload payload = mapper.readValue("{\"at\":\"2026-03-09T22:30:00\"}", Payload.class);
        assertEquals(Instant.parse("2026-03-09T22:30:00Z"), payload.at());
    }

    @Test
    void deserialize_ShouldParseInstantWithZone() throws Exception {
        Payload payload = mapper.readValue("{\"at\":\"2026-03-09T22:30:00Z\"}", Payload.class);
        assertEquals(Instant.parse("2026-03-09T22:30:00Z"), payload.at());
    }

    @Test
    void deserialize_ShouldFailForInvalidValue() {
        assertThrows(InvalidFormatException.class,
                () -> mapper.readValue("{\"at\":\"invalid\"}", Payload.class));
    }

    private record Payload(Instant at) {
    }
}
