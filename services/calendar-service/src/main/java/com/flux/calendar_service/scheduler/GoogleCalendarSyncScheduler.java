package com.flux.calendar_service.scheduler;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

import com.flux.calendar_service.calendar.Calendar;
import com.flux.calendar_service.calendar.CalendarRepository;
import com.flux.calendar_service.event.EventService;
import com.flux.calendar_service.exception.GoogleTokenRevokedException;
import com.flux.calendar_service.google.GoogleAuthService;
import com.flux.calendar_service.google.GoogleCalendarApiService;
import com.flux.calendar_service.user.UserSyncRepository;
import com.google.api.client.auth.oauth2.Credential;

import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "google.calendar.enabled", havingValue = "true", matchIfMissing = false)
@EnableSchedulerLock(defaultLockAtMostFor = "PT2M")
public class GoogleCalendarSyncScheduler {

    private final CalendarRepository calendarRepository;
    private final EventService eventService;
    private final GoogleAuthService googleAuthService;
    private final GoogleCalendarApiService googleCalendarApiService;
    private final UserSyncRepository userSyncRepository;

    /**
     * Periodically syncs Google Calendar events for all users who have an active
     * Google connection.
     *
     * <p>
     * <strong>Crash-safety contract:</strong> any per-user exception (including
     * {@code invalid_grant}) is caught individually so that one user's failure
     * never aborts the sync batch for other users.
     *
     * <p>
     * Runs every minute (cron {@code "0 * * * * *"}).
     */
    @Scheduled(cron = "0 * * * * *")
    @SchedulerLock(name = "googleCalendarSync", lockAtMostFor = "PT2M", lockAtLeastFor = "PT50S")
    public void syncAllAuthorizedUsers() {
        log.info("Starting background Google Calendar synchronization...");

        // Only attempt sync for users that are flagged as Google-connected.
        // Users whose tokens were permanently revoked (invalid_grant) have
        // google_connected=false and are excluded here — no wasted API calls.
        Set<String> candidateUserIds = calendarRepository.findAllByIsDeletedFalse().stream()
                .map(Calendar::getUserId)
                .filter(this::isUserGoogleConnected)
                .collect(Collectors.toSet());

        log.debug("Found {} candidate users for Google Calendar sync.", candidateUserIds.size());

        int syncCount = 0;
        int skippedCount = 0;
        int errorCount = 0;

        for (String userId : candidateUserIds) {
            try {
                // ── Step 1: Load stored credential ──────────────────────────────────────
                Credential credential = googleAuthService.getValidCredential(userId);
                if (credential == null) {
                    // No token stored in DB — skip until re-auth
                    log.debug("No stored credential for user {}. Skipping.", userId);
                    skippedCount++;
                    continue;
                }

                // ── Step 3: Resolve the local calendar linked to Google ──────────────────
                // Optimized: Check if a calendar for this user already has a bound Google ID.
                java.util.Optional<Calendar> syncCalendar = calendarRepository.findCalendarByUserIdAndIsDeletedFalse(userId).stream()
                        .filter(c -> c.getGoogleCalendarId() != null && !c.getGoogleCalendarId().isBlank())
                        .findFirst();

                String googleCalendarId;
                if (syncCalendar.isPresent()) {
                    googleCalendarId = syncCalendar.get().getGoogleCalendarId();
                } else {
                    // Fallback to Google API only if no bound calendar exists locally
                    googleCalendarId = googleCalendarApiService.getPrimaryCalendarId(userId);
                    syncCalendar = calendarRepository
                        .findByUserIdAndGoogleCalendarIdAndIsDeletedFalse(userId, googleCalendarId)
                        .or(() -> calendarRepository.findByUserIdAndTitleAndIsDeletedFalse(userId, googleCalendarId))
                        .or(() -> calendarRepository.findByUserIdAndDescriptionAndIsDeletedFalse(
                                userId, "Google sync calendar for " + googleCalendarId));
                }

                if (syncCalendar.isEmpty()) {
                    log.warn("No local sync calendar found for user {} / Google calendar '{}'. Skipping.",
                            userId, googleCalendarId);
                    skippedCount++;
                    continue;
                }

                Calendar calendar = syncCalendar.get();
                if (calendar.getGoogleCalendarId() == null || calendar.getGoogleCalendarId().isBlank()) {
                    calendar.setGoogleCalendarId(googleCalendarId);
                    calendarRepository.save(calendar);
                }

                // ── Step 4: Perform incremental sync ────────────────────────────────────
                log.debug("Syncing Google calendar {} for user {}", calendar.getId(), userId);
                eventService.syncEventsFromGoogle(calendar.getId());

                // ── Step 5: Push pending / auth_error local events to Google ────────────
                eventService.pushUnsyncedLocalEventsToGoogle(calendar);

                syncCount++;

            } catch (GoogleTokenRevokedException e) {
                // ── TERMINAL — invalid_grant ─────────────────────────────────────────────
                // markUserAsDisconnected() was already called during token refresh.
                // We just log and continue to the next user. The user will see a
                // "Reconnect Google Calendar" prompt on their next login.
                log.warn("Google token permanently revoked for user {}. "
                        + "Sync suspended until user re-authorizes. Cause: {}", userId, e.getMessage());
                errorCount++;
                // DO NOT rethrow — the loop must continue for all other users.

            } catch (IOException e) {
                if (GoogleAuthService.isInvalidGrant(e)) {
                    // Sometimes invalid_grant arrives wrapped as a plain IOException
                    // before it is unwrapped into TokenResponseException.
                    log.error("invalid_grant detected (wrapped IOException) for user {}. "
                            + "Marking disconnected.", userId, e);
                    googleAuthService.markUserAsDisconnected(userId);
                } else {
                    // Transient failure: network timeout, 503, rate-limit, etc.
                    // Will be retried automatically on the next scheduler tick.
                    log.error("Transient Google Calendar sync failure for user: {}. "
                            + "Will retry next cycle.", userId, e);
                }
                errorCount++;

            } catch (Exception e) {
                // Final safety net — prevents one user's unexpected exception
                // from taking down the entire batch.
                log.error("Unexpected error syncing Google Calendar for user: {}. "
                        + "Scheduler continues for other users.", userId, e);
                errorCount++;
            }
        }

        log.info("Background sync complete. synced={}, skipped={}, errors={}",
                syncCount, skippedCount, errorCount);
    }

    /**
     * Returns {@code false} if this user's Google token has been marked as
     * revoked/expired (google_connected = false), preventing pointless API calls.
     *
     * <p>
     * If no {@code UserSync} record exists yet (new user), defaults to {@code true}
     * and lets the credential-load check handle the null case gracefully.
     */
    private boolean isUserGoogleConnected(String userId) {
        return userSyncRepository.findById(userId)
                .map(u -> u.isGoogleConnected())
                .orElse(true);
    }
}
