CREATE TABLE project_jira_oauth_states (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    state_nonce_hash VARCHAR(255) NOT NULL,
    project_id UUID NOT NULL,
    user_id UUID NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    CONSTRAINT fk_project_jira_oauth_states_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_project_jira_oauth_states_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uq_project_jira_oauth_states_nonce_hash
    ON project_jira_oauth_states (state_nonce_hash);

CREATE INDEX idx_project_jira_oauth_states_expires_at
    ON project_jira_oauth_states (expires_at);
