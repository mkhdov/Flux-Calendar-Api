ALTER TABLE calendars
    ADD COLUMN google_calendar_id VARCHAR(255);

CREATE INDEX idx_calendars_user_google_calendar_id
    ON calendars (user_id, google_calendar_id);
