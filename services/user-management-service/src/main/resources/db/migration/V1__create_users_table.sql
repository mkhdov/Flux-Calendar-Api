CREATE TABLE users (
    id VARCHAR(36) PRIMARY KEY,

    firstname VARCHAR(255) NOT NULL,
    lastname VARCHAR(255) NOT NULL,

    email VARCHAR(255) NOT NULL UNIQUE,

    password VARCHAR(255),

    birthday DATE,

    provider VARCHAR(50),
    provider_id VARCHAR(255),

    verification_token VARCHAR(255),
    token_expiry_date TIMESTAMP,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
