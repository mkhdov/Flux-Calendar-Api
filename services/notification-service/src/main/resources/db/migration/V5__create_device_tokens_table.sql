-- V5: Create device_tokens table for FCM push notification support
CREATE TABLE IF NOT EXISTS device_tokens (
    id          VARCHAR(36)   NOT NULL PRIMARY KEY,
    user_id     VARCHAR(255)  NOT NULL,
    device_id   VARCHAR(256)  NOT NULL,
    fcm_token   VARCHAR(4096) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_device UNIQUE (user_id, device_id)
);

CREATE INDEX IF NOT EXISTS idx_device_tokens_user_id ON device_tokens (user_id);
