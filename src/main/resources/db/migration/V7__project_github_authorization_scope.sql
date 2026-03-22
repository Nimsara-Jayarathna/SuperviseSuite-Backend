-- =============================================================
-- V7: Project-Scoped GitHub Authorization
-- Enforces per-project installation authorization and linkage audit.
-- =============================================================

ALTER TABLE project_repositories
    ADD COLUMN linked_by_supervisor_user_id UUID,
    ADD COLUMN linked_at TIMESTAMPTZ;

CREATE INDEX idx_project_repositories_linked_by_supervisor
    ON project_repositories (linked_by_supervisor_user_id);

CREATE TABLE project_github_installation_authorizations (
    id                               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id                       UUID        NOT NULL,
    installation_id                  BIGINT      NOT NULL,
    authorized_by_supervisor_user_id UUID        NOT NULL,
    authorized_at                    TIMESTAMPTZ NOT NULL,
    created_at                       TIMESTAMPTZ NOT NULL,
    updated_at                       TIMESTAMPTZ,
    CONSTRAINT fk_project_github_auth_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_project_github_auth_installation
        FOREIGN KEY (installation_id) REFERENCES github_app_installations (installation_id) ON DELETE CASCADE,
    CONSTRAINT uk_project_github_auth_project_installation
        UNIQUE (project_id, installation_id)
);

CREATE INDEX idx_project_github_auth_project_id
    ON project_github_installation_authorizations (project_id);

CREATE INDEX idx_project_github_auth_installation_id
    ON project_github_installation_authorizations (installation_id);
