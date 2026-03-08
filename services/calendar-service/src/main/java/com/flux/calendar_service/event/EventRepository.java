package com.flux.calendar_service.event;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, String> {
    @Query("""
            SELECT DISTINCT e FROM Event e
            JOIN FETCH e.calendar
            LEFT JOIN FETCH e.location
            LEFT JOIN FETCH e.conference
            LEFT JOIN FETCH e.subtasks
            LEFT JOIN FETCH e.attachments
            LEFT JOIN FETCH e.members
            LEFT JOIN FETCH e.notes
            WHERE e.calendar.id = :calendarId
            AND e.startTime BETWEEN :start AND :end
            """)
    List<Event> findEventsWithRelations(
            @Param("calendarId") String calendarId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    List<Event> findByCalendarId(String calendarId);

    List<Event> findByCalendar_UserId(String userId);

    Optional<Event> findByIdAndCalendar_UserId(String id, String userId);

    Optional<Event> findEventByLocationId(String locationId);

    List<Event> findByStartTimeBetween(LocalDateTime start, LocalDateTime end);

    List<Event> findByCalendarIdAndStartTimeBetween(String calendarId, LocalDateTime start, LocalDateTime end);

    List<Event> findByCalendarIdAndSyncStatusIn(String calendarId, List<SyncStatus> syncStatuses);

    Optional<Event> findByGoogleCalendarId(String googleCalendarId);
}
