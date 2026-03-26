-- =============================================================
-- V10: GitHub Integration V2
-- Adds access-source abstraction, repository-link model, secure callback state,
-- and repo-level analytics storage for multi-repository projects.
-- =============================================================

CREATE TABLE github_access_sources (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id          UUID         NOT NULL,
    installation_id     BIGINT,
    owner_login         VARCHAR(255) NOT NULL,
    owner_type          VARCHAR(16)  NOT NULL,
    access_type         VARCHAR(32)  NOT NULL,
    created_by_user_id  UUID         NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL,
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_github_access_source_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_github_access_source_user
        FOREIGN KEY (created_by_user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_github_access_source_installation
        FOREIGN KEY (installation_id) REFERENCES github_app_installations (installation_id) ON DELETE SET NULL
);

CREATE INDEX idx_github_access_sources_project
    ON github_access_sources (project_id, is_active, created_at DESC);

CREATE INDEX idx_github_access_sources_installation
    ON github_access_sources (installation_id);

CREATE TABLE github_repositories (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    access_source_id  UUID         NOT NULL,
    github_repo_id    BIGINT       NOT NULL,
    full_name         VARCHAR(255) NOT NULL,
    name              VARCHAR(255) NOT NULL,
    default_branch    VARCHAR(255),
    html_url          TEXT,
    owner_login       VARCHAR(255),
    created_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_github_repository_source
        FOREIGN KEY (access_source_id) REFERENCES github_access_sources (id) ON DELETE CASCADE,
    CONSTRAINT uk_github_repository_source_repo
        UNIQUE (access_source_id, github_repo_id)
);

CREATE INDEX idx_github_repositories_source
    ON github_repositories (access_source_id);

CREATE TABLE project_repository_links (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id          UUID         NOT NULL,
    github_repository_id UUID        NOT NULL,
    github_repo_id      BIGINT       NOT NULL,
    custom_name         VARCHAR(255),
    is_primary          BOOLEAN      NOT NULL DEFAULT FALSE,
    linked_at           TIMESTAMPTZ  NOT NULL,
    last_synced_at      TIMESTAMPTZ,
    sync_status         VARCHAR(32),
    sync_error          TEXT,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ,
    CONSTRAINT fk_project_repository_link_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_project_repository_link_repository
        FOREIGN KEY (github_repository_id) REFERENCES github_repositories (id) ON DELETE CASCADE,
    CONSTRAINT uk_project_repository_link_project_repo
        UNIQUE (project_id, github_repository_id),
    CONSTRAINT uk_project_repository_link_project_github_repo
        UNIQUE (project_id, github_repo_id)
);

CREATE INDEX idx_project_repository_links_project
    ON project_repository_links (project_id, linked_at DESC);

CREATE INDEX idx_project_repository_links_primary
    ON project_repository_links (project_id, is_primary);

CREATE TABLE github_setup_states (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    state_jti_hash  VARCHAR(128) NOT NULL,
    project_id      UUID         NOT NULL,
    user_id         UUID         NOT NULL,
    flow_type       VARCHAR(32)  NOT NULL,
    request_id      UUID,
    expires_at      TIMESTAMPTZ  NOT NULL,
    used_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ,
    CONSTRAINT fk_github_setup_state_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_github_setup_state_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_github_setup_state_jti
        UNIQUE (state_jti_hash)
);

CREATE INDEX idx_github_setup_states_expiry
    ON github_setup_states (expires_at, used_at);

CREATE TABLE github_access_requests_v2 (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id            UUID         NOT NULL,
    requested_by_user_id  UUID         NOT NULL,
    token_hash            VARCHAR(128) NOT NULL,
    expires_at            TIMESTAMPTZ  NOT NULL,
    used_at               TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ,
    CONSTRAINT fk_github_access_request_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_github_access_request_user
        FOREIGN KEY (requested_by_user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_github_access_request_token
        UNIQUE (token_hash)
);

CREATE INDEX idx_github_access_requests_v2_expiry
    ON github_access_requests_v2 (expires_at, used_at);

CREATE TABLE project_repository_link_commits (
    id                         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    project_repository_link_id UUID         NOT NULL,
    sha                        VARCHAR(64)  NOT NULL,
    message                    TEXT         NOT NULL,
    author                     VARCHAR(255),
    committed_at               TIMESTAMPTZ,
    commit_type                VARCHAR(32),
    created_at                 TIMESTAMPTZ  NOT NULL,
    updated_at                 TIMESTAMPTZ,
    CONSTRAINT fk_project_repository_link_commit_link
        FOREIGN KEY (project_repository_link_id) REFERENCES project_repository_links (id) ON DELETE CASCADE,
    CONSTRAINT uk_project_repository_link_commit_sha
        UNIQUE (project_repository_link_id, sha)
);

CREATE INDEX idx_project_repository_link_commits_link
    ON project_repository_link_commits (project_repository_link_id);

CREATE INDEX idx_project_repository_link_commits_committed
    ON project_repository_link_commits (project_repository_link_id, committed_at DESC);

CREATE TABLE project_repository_link_contributors (
    id                         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    project_repository_link_id UUID         NOT NULL,
    contributor_name           VARCHAR(255) NOT NULL,
    commit_count               INTEGER      NOT NULL,
    last_contribution_at       TIMESTAMPTZ,
    updated_at                 TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_project_repository_link_contributor_link
        FOREIGN KEY (project_repository_link_id) REFERENCES project_repository_links (id) ON DELETE CASCADE,
    CONSTRAINT uk_project_repository_link_contributor
        UNIQUE (project_repository_link_id, contributor_name)
);

CREATE INDEX idx_project_repository_link_contributors_link
    ON project_repository_link_contributors (project_repository_link_id);

CREATE INDEX idx_project_repository_link_contributors_rank
    ON project_repository_link_contributors (project_repository_link_id, commit_count DESC, contributor_name ASC);
