-- Reminders table for notification scheduling
CREATE TABLE IF NOT EXISTS reminders (
    id              TEXT PRIMARY KEY DEFAULT gen_random_uuid()::TEXT,
    event_id        TEXT NOT NULL,
    user_id         TEXT NOT NULL,
    trigger_time    TIMESTAMPTZ NOT NULL,
    type            VARCHAR(20) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count     INT NOT NULL DEFAULT 0,
    sent_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_reminder UNIQUE (event_id, trigger_time, type)
);

-- Composite index for the scheduler polling query: status + trigger_time
CREATE INDEX idx_reminders_status_trigger ON reminders (status, trigger_time);

-- Index for lookups by event
CREATE INDEX idx_reminders_event_id ON reminders (event_id);

-- Index for lookups by user
CREATE INDEX idx_reminders_user_id ON reminders (user_id);
