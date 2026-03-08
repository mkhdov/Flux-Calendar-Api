package com.flux.calendar_service.event;

public enum SyncStatus {
    /** Successfully synced with Google Calendar. */
    SYNCED,
    /** Created locally; waiting to be synced with Google Calendar. */
    PENDING,
    /**
     * Sync attempt failed due to a transient error (network, quota, etc). Will be
     * retried.
     */
    FAILED,
    /**
     * Sync skipped because the user's Google OAuth token has been permanently
     * revoked.
     * The user must re-authorize before sync can resume.
     */
    AUTH_ERROR
}
