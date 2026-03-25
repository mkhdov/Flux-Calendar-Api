ALTER TABLE events
    ADD CONSTRAINT uq_events_google_calendar_id
        UNIQUE (google_calendar_id);