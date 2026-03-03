-- =============================================================
-- V1: Core Schema
-- Tables: users, projects, project_members
-- gen_random_uuid() is built-in from PostgreSQL 13+.
-- =============================================================

CREATE TABLE users (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ,
    email      VARCHAR(255) NOT NULL UNIQUE,
    role       VARCHAR(64)  NOT NULL,
    CONSTRAINT chk_users_role CHECK (role IN ('SUPERVISOR', 'STUDENT'))
);

CREATE TABLE projects (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ,
    name          VARCHAR(255) NOT NULL,
    description   TEXT,
    status        VARCHAR(64)  NOT NULL,
    supervisor_id UUID,
    deleted_at    TIMESTAMPTZ,
    CONSTRAINT fk_projects_supervisor
        FOREIGN KEY (supervisor_id) REFERENCES users (id)
);

CREATE TABLE project_members (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    user_id    UUID        NOT NULL,
    project_id UUID        NOT NULL,
    CONSTRAINT fk_project_members_user
        FOREIGN KEY (user_id)    REFERENCES users (id)    ON DELETE CASCADE,
    CONSTRAINT fk_project_members_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT uk_project_members_user_project UNIQUE (user_id, project_id)
);

CREATE INDEX idx_project_members_user_id    ON project_members (user_id);
CREATE INDEX idx_project_members_project_id ON project_members (project_id);
CREATE INDEX idx_projects_supervisor_id     ON projects (supervisor_id);
