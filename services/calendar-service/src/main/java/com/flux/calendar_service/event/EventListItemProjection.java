package com.flux.calendar_service.event;

import java.time.Instant;

public interface EventListItemProjection {
    String getId();
    String getCalendarId();
    String getTitle();
    String getDescription();
    String getColorHex();
    Instant getStartTime();
    Instant getEndTime();
    Boolean getAllDay();
    EventStatus getStatus();
    String getRecurringGroupId();
    Boolean getRecurring();
    Boolean getHasLocation();
    Boolean getHasConference();
    Boolean getHasSubtasks();
    Boolean getHasAttachments();
    Boolean getHasMembers();
    Boolean getHasNotes();
}
