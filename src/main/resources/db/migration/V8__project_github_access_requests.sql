-- =============================================================
-- V8: Project-Scoped GitHub Access Requests
-- Adds short-lived single-use request/state tracking for secure
-- in-app "request more repository access" flow.
-- =============================================================

CREATE TABLE project_github_access_requests (
    id                                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id                        UUID         NOT NULL,
    requested_by_supervisor_user_id   UUID         NOT NULL,
    token_hash                        VARCHAR(128) NOT NULL,
    github_state_hash                 VARCHAR(128),
    status                            VARCHAR(32)  NOT NULL,
    expires_at                        TIMESTAMPTZ  NOT NULL,
    used_at                           TIMESTAMPTZ,
    installation_id                   BIGINT,
    created_at                        TIMESTAMPTZ  NOT NULL,
    updated_at                        TIMESTAMPTZ,
    CONSTRAINT fk_project_github_access_request_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_project_github_access_request_supervisor
        FOREIGN KEY (requested_by_supervisor_user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_project_github_access_request_installation
        FOREIGN KEY (installation_id) REFERENCES github_app_installations (installation_id) ON DELETE SET NULL,
    CONSTRAINT uk_project_github_access_request_token_hash
        UNIQUE (token_hash),
    CONSTRAINT uk_project_github_access_request_state_hash
        UNIQUE (github_state_hash)
);

CREATE INDEX idx_project_github_access_request_project_id
    ON project_github_access_requests (project_id);

CREATE INDEX idx_project_github_access_request_supervisor_id
    ON project_github_access_requests (requested_by_supervisor_user_id);

CREATE INDEX idx_project_github_access_request_status_expires
    ON project_github_access_requests (status, expires_at);
