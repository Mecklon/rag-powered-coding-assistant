CREATE TABLE IF NOT EXISTS users (
    id           BIGSERIAL PRIMARY KEY,
    github_id    VARCHAR(64)  NOT NULL UNIQUE,
    login        VARCHAR(64)  NOT NULL,
    name         VARCHAR(128),
    email        VARCHAR(255),
    avatar_url   VARCHAR(512),
    access_token TEXT,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);