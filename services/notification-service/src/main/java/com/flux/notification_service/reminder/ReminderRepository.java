package com.flux.notification_service.reminder;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReminderRepository extends JpaRepository<Reminder, String> {

    /**
     * Fetch the top N pending reminders whose trigger time has passed.
     * Used by the scheduler for batch processing.
     */
    List<Reminder> findTop100ByStatusAndTriggerTimeBeforeOrderByTriggerTimeAsc(
            ReminderStatus status, Instant cutoff);

    /**
     * Find all reminders for a specific event.
     */
    List<Reminder> findByEventIdOrderByTriggerTimeAsc(String eventId);

    /**
     * Delete all reminders for a specific event.
     */
    void deleteByEventId(String eventId);

    /**
     * Check if a reminder already exists (dedup at DB level).
     */
    boolean existsByEventIdAndTriggerTimeAndType(String eventId, Instant triggerTime, ReminderType type);
}
