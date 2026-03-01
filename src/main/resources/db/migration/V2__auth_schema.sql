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
-- No email verification flow — accounts are considered verified on registration.
-- =============================================================

-- 1. Extend users table
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS password_hash      VARCHAR(255),
    ADD COLUMN IF NOT EXISTS first_name         VARCHAR(100),
    ADD COLUMN IF NOT EXISTS last_name          VARCHAR(100);

ALTER TABLE users
    DROP CONSTRAINT IF EXISTS chk_users_role;
ALTER TABLE users
    ADD CONSTRAINT chk_users_role CHECK (role IN ('SUPERVISOR', 'STUDENT'));

-- 2. Extend projects table
ALTER TABLE projects
    ADD COLUMN IF NOT EXISTS description   TEXT,
    ADD COLUMN IF NOT EXISTS supervisor_id UUID,
    ADD COLUMN IF NOT EXISTS deleted_at    TIMESTAMPTZ;

ALTER TABLE projects
    DROP CONSTRAINT IF EXISTS fk_projects_supervisor;
ALTER TABLE projects
    ADD CONSTRAINT fk_projects_supervisor
        FOREIGN KEY (supervisor_id) REFERENCES users (id);

-- 3. Refresh Tokens (new table)
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id          UUID         PRIMARY KEY,
    user_id     UUID         NOT NULL,
    token_hash  VARCHAR(255) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- 4. New index for supervisor lookup on projects
CREATE INDEX IF NOT EXISTS idx_projects_supervisor_id ON projects (supervisor_id);
