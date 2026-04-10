-- Add sprint metadata fields for Jira sprint progress and velocity analytics.
-- Columns are nullable to preserve existing cached rows.
ALTER TABLE project_jira_issues
    ADD COLUMN IF NOT EXISTS sprint_id BIGINT,
    ADD COLUMN IF NOT EXISTS sprint_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS sprint_state VARCHAR(32),
    ADD COLUMN IF NOT EXISTS sprint_start_date TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS sprint_end_date TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_jira_issues_sprint
    ON project_jira_issues (project_id, sprint_id);
