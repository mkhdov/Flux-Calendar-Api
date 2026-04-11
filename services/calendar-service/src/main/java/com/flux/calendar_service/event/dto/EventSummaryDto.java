package com.flux.calendar_service.event.dto;

import com.flux.calendar_service.event.EventStatus;
import com.flux.calendar_service.event.EventType;
import com.flux.calendar_service.event.SyncStatus;
import com.flux.calendar_service.event.EventSummary;

import java.time.Instant;

public record EventSummaryDto(
    String id,
    String title,
    String description,
    String colorHex,
    EventType type,
    Instant startTime,
    Instant endTime,
    boolean allDay,
    SyncStatus syncStatus,
    EventStatus status,
    Instant createdAt,
    Instant updatedAt,
    String calendarId,
    String calendarName,
    String locationId,
    String locationName,
    String locationAddress,
    String conferenceId,
    String conferenceType,
    String conferenceLink
) implements EventSummary {
    @Override public String getId() { return id; }
    @Override public String getTitle() { return title; }
    @Override public String getDescription() { return description; }
    @Override public String getColorHex() { return colorHex; }
    @Override public EventType getType() { return type; }
    @Override public Instant getStartTime() { return startTime; }
    @Override public Instant getEndTime() { return endTime; }
    @Override public boolean isAllDay() { return allDay; }
    @Override public SyncStatus getSyncStatus() { return syncStatus; }
    @Override public EventStatus getStatus() { return status; }
    @Override public Instant getCreatedAt() { return createdAt; }
    @Override public Instant getUpdatedAt() { return updatedAt; }

    @Override public String getCalendarId() { return calendarId; }
    @Override public String getCalendarName() { return calendarName; }

    @Override public String getLocationId() { return locationId; }
    @Override public String getLocationName() { return locationName; }
    @Override public String getLocationAddress() { return locationAddress; }

    @Override public String getConferenceId() { return conferenceId; }
    @Override public String getConferenceType() { return conferenceType; }
    @Override public String getConferenceLink() { return conferenceLink; }

    public static EventSummaryDto fromProjection(EventSummary proj) {
        if (proj == null) return null;
        return new EventSummaryDto(
            proj.getId(), proj.getTitle(), proj.getDescription(), proj.getColorHex(),
            proj.getType(), proj.getStartTime(), proj.getEndTime(), proj.isAllDay(),
            proj.getSyncStatus(), proj.getStatus(), proj.getCreatedAt(), proj.getUpdatedAt(),
            proj.getCalendarId(), proj.getCalendarName(),
            proj.getLocationId(), proj.getLocationName(), proj.getLocationAddress(),
            proj.getConferenceId(), proj.getConferenceType(), proj.getConferenceLink()
        );
    }
}
