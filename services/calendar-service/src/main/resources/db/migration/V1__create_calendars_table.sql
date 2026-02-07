CREATE TABLE calendars (
                           id VARCHAR(255) PRIMARY KEY,
                           user_id VARCHAR(255) NOT NULL,
                           title VARCHAR(255) NOT NULL,
                           description TEXT,
                           color_hex VARCHAR(255) NOT NULL,
                           timezone VARCHAR(255) NOT NULL,
                           is_primary BOOLEAN DEFAULT FALSE,
                           is_deleted BOOLEAN DEFAULT FALSE,
                           last_google_sync TIMESTAMP,
                           google_sync_token VARCHAR(255),
                           created_at TIMESTAMP,
                           updated_at TIMESTAMP
);