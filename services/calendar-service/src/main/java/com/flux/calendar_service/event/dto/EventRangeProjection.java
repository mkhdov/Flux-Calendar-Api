package com.flux.calendar_service.event.dto;

import java.time.Instant;
import com.flux.calendar_service.event.EventStatus;

public interface EventRangeProjection {
    String getId();
    String getCalendarId();
    String getTitle();
    String getColorHex();
    Instant getStartTime();
    Instant getEndTime();
    boolean isAllDay();
    EventStatus getStatus();
    boolean getHasLocation();
    boolean getHasConference();
    boolean getHasAttachment();
    boolean getHasSubtasks();
    boolean getHasMember();
    boolean getHasNotes();
}
