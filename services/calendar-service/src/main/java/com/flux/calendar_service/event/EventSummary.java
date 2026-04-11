package com.flux.calendar_service.event;

import java.time.Instant;

public interface EventSummary {
    String getId();
    String getTitle();
    String getDescription();
    String getColorHex();
    EventType getType();
    Instant getStartTime();
    Instant getEndTime();
    boolean isAllDay();
    SyncStatus getSyncStatus();
    EventStatus getStatus();
    Instant getCreatedAt();
    Instant getUpdatedAt();

    // Calendar
    String getCalendarId();
    String getCalendarName();

    // Location (nullable)
    String getLocationId();
    String getLocationName();
    String getLocationAddress();

    // Conference (nullable)
    String getConferenceId();
    String getConferenceType();
    String getConferenceLink();
}
