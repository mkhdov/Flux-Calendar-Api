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
        AND e.recurringMaster = false
        AND e.startTime < :end
        AND (e.endTime IS NULL OR e.endTime > :start)
        """)
    List<Event> findRangeEventsWithCoreRelations(
            @Param("calendarId") String calendarId,
            @Param("start") Instant start,
            @Param("end") Instant end);
    @Query("""
        SELECT
            e.id AS id,
            c.id AS calendarId,
            e.title AS title,
            e.description AS description,
            e.colorHex AS colorHex,
            e.startTime AS startTime,
            e.endTime AS endTime,
            e.allDay AS allDay,
            e.status AS status,
            e.recurringGroupId AS recurringGroupId,
            CASE
                WHEN (e.recurringGroupId IS NOT NULL OR e.googleRecurringEventId IS NOT NULL OR e.recurringMaster = true)
                THEN true
                ELSE false
            END AS recurring,
            CASE WHEN e.location IS NOT NULL THEN true ELSE false END AS hasLocation,
            CASE WHEN e.conference IS NOT NULL THEN true ELSE false END AS hasConference,
            CASE WHEN EXISTS (SELECT 1 FROM Subtask st WHERE st.event = e) THEN true ELSE false END AS hasSubtasks,
            CASE WHEN EXISTS (SELECT 1 FROM Attachment at WHERE at.event = e) THEN true ELSE false END AS hasAttachments,
            CASE WHEN EXISTS (SELECT 1 FROM Member mb WHERE mb.event = e) THEN true ELSE false END AS hasMembers,
            CASE WHEN EXISTS (SELECT 1 FROM Note nt WHERE nt.event = e) THEN true ELSE false END AS hasNotes
        FROM Event e
        JOIN e.calendar c
        WHERE c.id = :calendarId
        AND e.recurringMaster = false
        AND e.startTime < :end
        AND (e.endTime IS NULL OR e.endTime > :start)
        ORDER BY e.startTime ASC
        """)
    List<EventListItemProjection> findRangeEventListItems(
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
    AND e.recurringMaster = false
    AND e.startTime < :end
    AND (e.endTime IS NULL OR e.endTime > :start)
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

    // ── Recurring series queries ──────────────────────────────────────────

    /** All events sharing the same local recurring group (planner batch). */
    List<Event> findByRecurringGroupId(String recurringGroupId);

    /** Events in the same group whose startTime is >= the given cutoff (for THIS_AND_FOLLOWING). */
    @Query("SELECT e FROM Event e WHERE e.recurringGroupId = :groupId AND e.startTime >= :from ORDER BY e.startTime ASC")
    List<Event> findByRecurringGroupIdAndStartTimeGreaterThanEqual(
            @Param("groupId") String groupId,
            @Param("from") Instant from);

    /** All events sharing the same Google recurring event (synced series). */
    List<Event> findByGoogleRecurringEventId(String googleRecurringEventId);

    /** Google-synced recurring events whose startTime >= cutoff. */
    @Query("SELECT e FROM Event e WHERE e.googleRecurringEventId = :recId AND e.startTime >= :from ORDER BY e.startTime ASC")
    List<Event> findByGoogleRecurringEventIdAndStartTimeGreaterThanEqual(
            @Param("recId") String recId,
            @Param("from") Instant from);

    /** All events in a local recurring group, ordered by start time. */
    @Query("SELECT e FROM Event e WHERE e.recurringGroupId = :groupId ORDER BY e.startTime ASC NULLS LAST")
    List<Event> findByRecurringGroupIdOrderByStartTime(@Param("groupId") String groupId);

    @Query("""
        SELECT
            e.id AS id,
            c.id AS calendarId,
            e.title AS title,
            e.description AS description,
            e.colorHex AS colorHex,
            e.startTime AS startTime,
            e.endTime AS endTime,
            e.allDay AS allDay,
            e.status AS status,
            e.recurringGroupId AS recurringGroupId,
            CASE
                WHEN (e.recurringGroupId IS NOT NULL OR e.googleRecurringEventId IS NOT NULL OR e.recurringMaster = true)
                THEN true
                ELSE false
            END AS recurring,
            CASE WHEN e.location IS NOT NULL THEN true ELSE false END AS hasLocation,
            CASE WHEN e.conference IS NOT NULL THEN true ELSE false END AS hasConference,
            CASE WHEN EXISTS (SELECT 1 FROM Subtask st WHERE st.event = e) THEN true ELSE false END AS hasSubtasks,
            CASE WHEN EXISTS (SELECT 1 FROM Attachment at WHERE at.event = e) THEN true ELSE false END AS hasAttachments,
            CASE WHEN EXISTS (SELECT 1 FROM Member mb WHERE mb.event = e) THEN true ELSE false END AS hasMembers,
            CASE WHEN EXISTS (SELECT 1 FROM Note nt WHERE nt.event = e) THEN true ELSE false END AS hasNotes
        FROM Event e
        JOIN e.calendar c
        WHERE e.recurringGroupId = :groupId
        AND e.recurringMaster = false
        ORDER BY e.startTime ASC
        """)
    List<EventListItemProjection> findListItemsByRecurringGroupIdOrderByStartTime(@Param("groupId") String groupId);
}

