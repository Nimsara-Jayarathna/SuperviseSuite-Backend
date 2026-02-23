-- =============================================================
-- V2: Auth Foundation
-- Applied on top of V1 (which is already in NeonDB).
-- Uses ALTER TABLE for existing tables; CREATE TABLE for new ones.
--
-- Notes on nullable columns:
--   password_hash  — nullable because existing user rows have no password.
--                    Application layer enforces NOT NULL on registration.
--   supervisor_id  — nullable because existing project rows predate this column.
--                    Application layer enforces required supervisor on project creation.
-- =============================================================

-- 1. Extend users table
ALTER TABLE users
    ADD COLUMN password_hash      VARCHAR(255),
    ADD COLUMN first_name         VARCHAR(100),
    ADD COLUMN last_name          VARCHAR(100),
    ADD COLUMN is_email_verified  BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE users
    ADD CONSTRAINT chk_users_role CHECK (role IN ('SUPERVISOR', 'STUDENT'));

-- 2. Extend projects table
ALTER TABLE projects
    ADD COLUMN description   TEXT,
    ADD COLUMN supervisor_id UUID,
    ADD COLUMN deleted_at    TIMESTAMPTZ;

ALTER TABLE projects
    ADD CONSTRAINT fk_projects_supervisor
        FOREIGN KEY (supervisor_id) REFERENCES users (id);

-- 3. Email Verification Tokens (new table)
CREATE TABLE verification_tokens (
    id          UUID         PRIMARY KEY,
    user_id     UUID         NOT NULL,
    token       VARCHAR(255) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ  NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_verification_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- 4. Refresh Tokens (new table)
CREATE TABLE refresh_tokens (
    id          UUID         PRIMARY KEY,
    user_id     UUID         NOT NULL,
    token_hash  VARCHAR(255) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- 5. New index for supervisor lookup on projects
CREATE INDEX idx_projects_supervisor_id ON projects (supervisor_id);
