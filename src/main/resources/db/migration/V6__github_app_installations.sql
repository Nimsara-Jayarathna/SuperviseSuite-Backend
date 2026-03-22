-- =============================================================
-- V6: GitHub App Installation Linkage
-- Adds installation-level persistence and repository linkage fields.
-- =============================================================

CREATE TABLE github_app_installations (
    installation_id  BIGINT       PRIMARY KEY,
    account_id       BIGINT,
    account_login    VARCHAR(255),
    account_type     VARCHAR(64),
    status           VARCHAR(32)  NOT NULL,
    installed_at     TIMESTAMPTZ,
    last_event_at    TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ
);

CREATE INDEX idx_github_app_installations_account_login
    ON github_app_installations (account_login);

ALTER TABLE project_repositories
    ADD COLUMN installation_id BIGINT,
    ADD COLUMN repository_external_id BIGINT,
    ADD COLUMN owner_login VARCHAR(255);

ALTER TABLE project_repositories
    ADD CONSTRAINT fk_project_repositories_installation
        FOREIGN KEY (installation_id) REFERENCES github_app_installations (installation_id)
            ON DELETE SET NULL;

CREATE INDEX idx_project_repositories_installation_id
    ON project_repositories (installation_id);

CREATE INDEX idx_project_repositories_owner_login
    ON project_repositories (owner_login);

