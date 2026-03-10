package com.flux.calendar_service.config;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public final class FlexibleInstantParser {

    private FlexibleInstantParser() {
    }

    public static Instant parse(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }

        String value = source.trim();

        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            // Try less strict ISO formats next.
        }

        try {
            return OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant();
        } catch (DateTimeParseException ignored) {
            // Try local date-time (assume UTC).
        }

        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            // Try date-only values next.
        }

        try {
            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay().toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Invalid datetime format: " + source, ex);
        }
    }
}
