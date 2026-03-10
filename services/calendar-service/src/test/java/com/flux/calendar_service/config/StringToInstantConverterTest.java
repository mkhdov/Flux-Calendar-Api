package com.flux.calendar_service.config;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StringToInstantConverterTest {

    private final StringToInstantConverter converter = new StringToInstantConverter();

    @Test
    void convert_ShouldParseInstantWithZulu() {
        Instant result = converter.convert("2026-02-06T00:00:00Z");
        assertEquals(Instant.parse("2026-02-06T00:00:00Z"), result);
    }

    @Test
    void convert_ShouldParseOffsetDateTime() {
        Instant result = converter.convert("2026-02-06T05:00:00+05:00");
        assertEquals(Instant.parse("2026-02-06T00:00:00Z"), result);
    }

    @Test
    void convert_ShouldTreatLocalDateTimeAsUtc() {
        Instant result = converter.convert("2026-02-06T00:00:00");
        assertEquals(Instant.parse("2026-02-06T00:00:00Z"), result);
    }

    @Test
    void convert_ShouldThrowForInvalidFormat() {
        assertThrows(IllegalArgumentException.class, () -> converter.convert("not-a-date"));
    }
}
