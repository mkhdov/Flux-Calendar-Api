package com.flux.calendar_service.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class EventRangeValidatorTest {

    private EventRangeValidator validator;

    @BeforeEach
    void setUp() {
        validator = new EventRangeValidator();
    }

    // ── WEEK view ───────────────────────────────────────────────

    @Test
    void week_validRange_ShouldNotThrow() {
        Instant start = Instant.now();
        Instant end = start.plus(5, ChronoUnit.DAYS);

        assertDoesNotThrow(() -> validator.validate(ViewType.WEEK, start, end));
    }

    @Test
    void week_exactlySevenDays_ShouldNotThrow() {
        Instant start = Instant.now();
        Instant end = start.plus(7, ChronoUnit.DAYS);

        assertDoesNotThrow(() -> validator.validate(ViewType.WEEK, start, end));
    }

    @Test
    void week_exceedsSevenDays_ShouldThrowBadRequest() {
        Instant start = Instant.now();
        Instant end = start.plus(10, ChronoUnit.DAYS);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> validator.validate(ViewType.WEEK, start, end));

        assertEquals(400, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("Week view supports max 7 days"));
    }

    @Test
    void week_zeroDayRange_ShouldThrowBadRequest() {
        Instant start = Instant.now();
        Instant end = start.plusSeconds(30); // less than 1 day

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> validator.validate(ViewType.WEEK, start, end));

        assertEquals(400, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("Invalid date range"));
    }

    // ── MONTH view ──────────────────────────────────────────────

    @Test
    void month_validRange_ShouldNotThrow() {
        Instant start = Instant.now();
        Instant end = start.plus(28, ChronoUnit.DAYS);

        assertDoesNotThrow(() -> validator.validate(ViewType.MONTH, start, end));
    }

    @Test
    void month_exactly31Days_ShouldNotThrow() {
        Instant start = Instant.now();
        Instant end = start.plus(31, ChronoUnit.DAYS);

        assertDoesNotThrow(() -> validator.validate(ViewType.MONTH, start, end));
    }

    @Test
    void month_exceeds31Days_ShouldThrowBadRequest() {
        Instant start = Instant.now();
        Instant end = start.plus(45, ChronoUnit.DAYS);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> validator.validate(ViewType.MONTH, start, end));

        assertEquals(400, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("Month view supports max 31 days"));
    }

    @Test
    void month_zeroDayRange_ShouldThrowBadRequest() {
        Instant start = Instant.now();
        Instant end = start.plusSeconds(10);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> validator.validate(ViewType.MONTH, start, end));

        assertEquals(400, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("Invalid date range"));
    }
}
