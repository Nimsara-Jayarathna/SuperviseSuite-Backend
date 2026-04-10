CREATE TABLE project_jira_integrations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL,
    cloud_id VARCHAR(128) NOT NULL,
    workspace_name VARCHAR(255) NOT NULL,
    workspace_url TEXT,
    access_token_encrypted TEXT NOT NULL,
    scope TEXT,
    connected_by UUID,
    connected_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT fk_project_jira_integrations_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_project_jira_integrations_connected_by
        FOREIGN KEY (connected_by) REFERENCES users (id)
);

CREATE UNIQUE INDEX uq_project_jira_integrations_project_active
    ON project_jira_integrations (project_id)
    WHERE revoked_at IS NULL;

CREATE INDEX idx_project_jira_integrations_connected_at
    ON project_jira_integrations (connected_at DESC);

