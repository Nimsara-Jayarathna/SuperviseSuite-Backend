-- =============================================================
-- V5: Project GitHub Cache
-- Stores repository metadata, commit snapshots, and contributor snapshots.
-- Designed for future multi-repository support per project.
-- =============================================================

CREATE TABLE project_repositories (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id       UUID         NOT NULL,
    provider         VARCHAR(32)  NOT NULL,
    repository_url   TEXT         NOT NULL,
    repository_name  VARCHAR(255),
    default_branch   VARCHAR(255),
    is_primary       BOOLEAN      NOT NULL DEFAULT FALSE,
    last_synced_at   TIMESTAMPTZ,
    sync_status      VARCHAR(32),
    last_sync_error  TEXT,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ,
    CONSTRAINT fk_project_repositories_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT uk_project_repositories_provider_url
        UNIQUE (project_id, provider, repository_url)
);

CREATE INDEX idx_project_repositories_project_id ON project_repositories (project_id);
CREATE INDEX idx_project_repositories_is_primary ON project_repositories (project_id, is_primary);

CREATE TABLE project_repository_commits (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    repository_id  UUID         NOT NULL,
    sha            VARCHAR(64)  NOT NULL,
    message        TEXT         NOT NULL,
    author         VARCHAR(255),
    committed_at   TIMESTAMPTZ,
    commit_type    VARCHAR(32),
    created_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_project_repository_commits_repository
        FOREIGN KEY (repository_id) REFERENCES project_repositories (id) ON DELETE CASCADE,
    CONSTRAINT uk_project_repository_commits_sha
        UNIQUE (repository_id, sha)
);

CREATE INDEX idx_project_repository_commits_repository_id
    ON project_repository_commits (repository_id);
CREATE INDEX idx_project_repository_commits_committed_at
    ON project_repository_commits (repository_id, committed_at DESC);

CREATE TABLE project_repository_contributors (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    repository_id         UUID         NOT NULL,
    contributor_name      VARCHAR(255) NOT NULL,
    commit_count          INTEGER      NOT NULL,
    last_contribution_at  TIMESTAMPTZ,
    updated_at            TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_project_repository_contributors_repository
        FOREIGN KEY (repository_id) REFERENCES project_repositories (id) ON DELETE CASCADE,
    CONSTRAINT uk_project_repository_contributors_name
        UNIQUE (repository_id, contributor_name)
);

CREATE INDEX idx_project_repository_contributors_repository_id
    ON project_repository_contributors (repository_id);
CREATE INDEX idx_project_repository_contributors_rank
    ON project_repository_contributors (repository_id, commit_count DESC, contributor_name ASC);
