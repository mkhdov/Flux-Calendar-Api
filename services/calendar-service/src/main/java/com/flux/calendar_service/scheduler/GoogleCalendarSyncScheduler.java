package com.flux.calendar_service.scheduler;

import com.flux.calendar_service.calendar.Calendar;
import com.flux.calendar_service.calendar.CalendarRepository;
import com.flux.calendar_service.event.EventService;
import com.flux.calendar_service.google.GoogleAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "google.calendar.enabled", havingValue = "true", matchIfMissing = false)
public class GoogleCalendarSyncScheduler {

    private final CalendarRepository calendarRepository;
    private final EventService eventService;
    private final GoogleAuthService googleAuthService;

    /**
     * Periodically sync events from Google Calendar for all users who have
     * connected their accounts.
     * Runs every 15 minutes.
     */
    @Scheduled(cron = "0 * * * * *")
    public void syncAllAuthorizedUsers() {
        log.info("Starting background Google Calendar synchronization...");

        List<Calendar> primaryCalendars = calendarRepository.findAll().stream()
                .filter(Calendar::isPrimary)
                .toList();

        int syncCount = 0;
        for (Calendar calendar : primaryCalendars) {
            try {
                if (googleAuthService.getLoadedCredential(calendar.getUserId()) != null) {
                    log.debug("Syncing Google Calendar for user: {}", calendar.getUserId());
                    eventService.syncEventsFromGoogle(calendar.getId());
                    syncCount++;
                }
            } catch (Exception e) {
                log.error("Failed to sync Google Calendar for user: {}", calendar.getUserId(), e);
            }
        }

        log.info("Completed background synchronization. Synced {} calendars.", syncCount);
    }
}
