-- =============================================================
-- V2: Auth Layer
-- Extends users with auth fields and registration number.
-- Creates refresh_tokens table.
--
-- Notes on nullable columns:
--   password_hash       — nullable to support pre-seeded supervisor rows.
--                         Application layer enforces NOT NULL on registration.
--   registration_number — nullable to support pre-seeded supervisor rows.
--                         Application layer enforces NOT NULL on student registration.
-- No email verification flow — accounts are considered verified on registration.
-- =============================================================

-- 1. Extend users with auth and identity fields
ALTER TABLE users
    ADD COLUMN password_hash        VARCHAR(255),
    ADD COLUMN first_name           VARCHAR(100),
    ADD COLUMN last_name            VARCHAR(100),
    ADD COLUMN registration_number  VARCHAR(20) UNIQUE;

-- 2. Refresh tokens
CREATE TABLE refresh_tokens (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ  NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
