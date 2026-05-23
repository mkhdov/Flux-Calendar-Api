ALTER TABLE events
    ADD COLUMN recurring_group_id VARCHAR(255);

CREATE INDEX idx_events_recurring_group_id ON events(recurring_group_id);
