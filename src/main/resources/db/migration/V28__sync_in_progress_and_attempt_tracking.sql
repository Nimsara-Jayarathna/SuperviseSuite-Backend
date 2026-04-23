-- V28: Add in-progress lifecycle fields and attempt tracking for GitHub/Jira sync.

ALTER TABLE project_repository_links
    ADD COLUMN last_sync_attempted_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE project_jira_integrations
    ADD COLUMN last_synced_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN last_sync_attempted_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN sync_status VARCHAR(64),
    ADD COLUMN sync_error TEXT;

CREATE INDEX idx_project_repository_links_sync_status_enabled
    ON project_repository_links (is_enabled, sync_status, last_synced_at ASC);

CREATE INDEX idx_project_jira_integrations_sync_status_active
    ON project_jira_integrations (revoked_at, sync_status, connected_at ASC);
