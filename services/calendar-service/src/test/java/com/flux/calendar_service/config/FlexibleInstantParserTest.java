package com.flux.calendar_service.config;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlexibleInstantParserTest {

    @Test
    void parse_ShouldHandleZuluInstant() {
        assertEquals(Instant.parse("2026-03-09T22:30:00Z"), FlexibleInstantParser.parse("2026-03-09T22:30:00Z"));
    }

    @Test
    void parse_ShouldHandleOffsetDateTime() {
        assertEquals(Instant.parse("2026-03-09T22:30:00Z"), FlexibleInstantParser.parse("2026-03-10T03:30:00+05:00"));
    }

    @Test
    void parse_ShouldHandleTimezoneLessDateTimeAsUtc() {
        assertEquals(Instant.parse("2026-03-09T22:30:00Z"), FlexibleInstantParser.parse("2026-03-09T22:30:00"));
    }

    @Test
    void parse_ShouldHandleDateOnlyAsStartOfDayUtc() {
        assertEquals(Instant.parse("2026-03-09T00:00:00Z"), FlexibleInstantParser.parse("2026-03-09"));
    }

    @Test
    void parse_ShouldReturnNullForBlankInput() {
        assertNull(FlexibleInstantParser.parse("   "));
    }

    @Test
    void parse_ShouldThrowForInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> FlexibleInstantParser.parse("bad-input"));
    }
}
