-- V12: Add Google OAuth connection state tracking to user_sync table
-- Prevents the scheduler from endlessly retrying revoked tokens (invalid_grant).
-- google_connected = FALSE means the user must re-authorize before sync resumes.

ALTER TABLE user_sync
    ADD COLUMN IF NOT EXISTS google_connected         BOOLEAN          NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS google_disconnected_at   TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS google_email             VARCHAR(255);
