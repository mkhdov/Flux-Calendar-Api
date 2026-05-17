ALTER TABLE events
    ADD COLUMN recurrence_rule TEXT,
    ADD COLUMN is_recurring_master BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN google_recurring_event_id VARCHAR(255);

CREATE INDEX idx_events_recurring_master ON events(is_recurring_master);
CREATE INDEX idx_events_google_recurring_event_id ON events(google_recurring_event_id);
