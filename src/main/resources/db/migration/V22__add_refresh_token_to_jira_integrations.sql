ALTER TABLE project_jira_integrations
ADD COLUMN refresh_token_encrypted TEXT,
ADD COLUMN token_expires_at TIMESTAMP;
