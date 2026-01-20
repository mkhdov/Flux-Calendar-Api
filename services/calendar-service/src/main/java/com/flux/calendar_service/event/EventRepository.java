package com.flux.calendar_service.event;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, String> {
    List<Event> findByCalendarId(String calendarId);

    List<Event> findByCalendar_UserId(String userId);

    Optional<Event> findByIdAndCalendar_UserId(String id, String userId);

    Optional<Event> findEventByLocationId(String locationId);

    List<Event> findByStartTimeBetween(LocalDateTime start, LocalDateTime end);
}
