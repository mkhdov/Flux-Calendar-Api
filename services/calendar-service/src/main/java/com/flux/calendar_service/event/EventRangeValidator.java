package com.flux.calendar_service.event;

import java.time.Duration;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class EventRangeValidator {

    public void validate(ViewType view, Instant start, Instant end) {
        long days = Duration.between(start, end).toDays();

        if (days <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid date range: must span at least 1 day"
            );
        }

        switch (view) {
            case WEEK -> {
                if (days > 7) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Week view supports max 7 days"
                    );
                }
            }
            case MONTH -> {
                if (days > 31) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Month view supports max 31 days"
                    );
                }
            }
        }
    }
}
