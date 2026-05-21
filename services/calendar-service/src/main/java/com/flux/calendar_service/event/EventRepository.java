package com.flux.calendar_service.event;

import java.time.Instant;
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
        WHERE e.calendar.id = :calendarId
        AND e.startTime < :end
        AND e.endTime > :start
        """)
    List<Event> findRangeEventsWithCoreRelations(
            @Param("calendarId") String calendarId,
            @Param("start") Instant start,
            @Param("end") Instant end);


    @Query("""
    SELECT
        e.id                AS id,
        e.title             AS title,
        e.description       AS description,
        e.colorHex          AS colorHex,
        e.type              AS type,
        e.startTime         AS startTime,
        e.endTime           AS endTime,
        e.allDay            AS allDay,
        e.syncStatus        AS syncStatus,
        e.status            AS status,
        e.createdAt         AS createdAt,
        e.updatedAt         AS updatedAt,

        c.id                AS calendarId,
        c.title              AS calendarName,

        l.id                AS locationId,
        l.placeName         AS locationName,
        l.streetAddress     AS locationAddress,

        cf.id               AS conferenceId,
        cf.type             AS conferenceType,
        cf.conferenceLink   AS conferenceLink

    FROM Event e
    JOIN e.calendar c
    LEFT JOIN e.location l
    LEFT JOIN e.conference cf
    WHERE c.id = :calendarId
    AND e.startTime < :end
    AND e.endTime > :start
    """)
    List<EventSummary> findRangeEventsProjected(
            @Param("calendarId") String calendarId,
            @Param("start") Instant start,
            @Param("end") Instant end);

    List<Event> findByCalendarId(String calendarId);

    List<Event> findByCalendar_UserId(String userId);

    Optional<Event> findByIdAndCalendar_UserId(String id, String userId);

    List<Event> findEventByLocationId(String locationId);

    List<Event> findByStartTimeBetween(Instant start, Instant end);

    List<Event> findByCalendarIdAndStartTimeBetween(String calendarId, Instant start, Instant end);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"location"})
    List<Event> findByCalendarIdAndSyncStatusIn(String calendarId, List<SyncStatus> syncStatuses);

    Optional<Event> findByGoogleCalendarId(String googleCalendarId);
}
