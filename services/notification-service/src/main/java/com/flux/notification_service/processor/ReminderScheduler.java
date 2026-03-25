package com.flux.notification_service.processor;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * Scheduler that polls for pending reminders every 30 seconds.
 * Uses ShedLock to ensure only one instance processes at a time
 * in a horizontally-scaled deployment.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReminderScheduler {

    private final ReminderProcessor reminderProcessor;

    @Scheduled(fixedRateString = "${notification.scheduler.poll-interval:30000}")
    @SchedulerLock(
            name = "reminder-poll",
            lockAtMostFor = "25s",
            lockAtLeastFor = "10s"
    )
    public void pollAndProcessReminders() {
        log.debug("Scheduler tick: polling for pending reminders...");
        try {
            reminderProcessor.processReminders();
        } catch (Exception e) {
            log.error("Error during reminder processing", e);
        }
    }
}
