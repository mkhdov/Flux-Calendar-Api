package com.flux.calendar_service.event;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, String> {
    @Query("""
        SELECT e FROM Event e
        JOIN FETCH e.calendar
        LEFT JOIN FETCH e.location
        LEFT JOIN FETCH e.conference
        WHERE e.calendar.id = :calendarId
        AND e.startTime BETWEEN :start AND :end
        """)
    List<Event> findRangeEventsWithCoreRelations(
            @Param("calendarId") String calendarId,
            @Param("start") Instant start,
            @Param("end") Instant end);

    @Query("""
        SELECT e.id AS id,
               e.calendar.id AS calendarId,
               e.title AS title,
               e.colorHex AS colorHex,
               e.startTime AS startTime,
               e.endTime AS endTime,
               e.allDay AS allDay,
               e.status AS status,
               (CASE WHEN e.location IS NOT NULL THEN true ELSE false END) AS hasLocation,
               (CASE WHEN e.conference IS NOT NULL THEN true ELSE false END) AS hasConference,
               (CASE WHEN EXISTS (SELECT 1 FROM Attachment a WHERE a.event.id = e.id) THEN true ELSE false END) AS hasAttachment,
               (CASE WHEN EXISTS (SELECT 1 FROM Subtask s WHERE s.event.id = e.id) THEN true ELSE false END) AS hasSubtasks,
               (CASE WHEN EXISTS (SELECT 1 FROM Member m WHERE m.event.id = e.id) THEN true ELSE false END) AS hasMember,
               (CASE WHEN EXISTS (SELECT 1 FROM Note n WHERE n.event.id = e.id) THEN true ELSE false END) AS hasNotes
        FROM Event e
        WHERE e.calendar.id = :calendarId
        AND e.startTime BETWEEN :start AND :end
        """)
    List<com.flux.calendar_service.event.dto.EventRangeProjection> findEventsByRangeFiltered(
            @Param("calendarId") String calendarId,
            @Param("start") Instant start,
            @Param("end") Instant end);

    List<Event> findByCalendarId(String calendarId);

    List<Event> findByCalendar_UserId(String userId);

    Optional<Event> findByIdAndCalendar_UserId(String id, String userId);

    Optional<Event> findEventByLocationId(String locationId);

    List<Event> findByStartTimeBetween(Instant start, Instant end);

    List<Event> findByCalendarIdAndStartTimeBetween(String calendarId, Instant start, Instant end);

    List<Event> findByCalendarIdAndSyncStatusIn(String calendarId, List<SyncStatus> syncStatuses);

    Optional<Event> findByGoogleCalendarId(String googleCalendarId);
}
