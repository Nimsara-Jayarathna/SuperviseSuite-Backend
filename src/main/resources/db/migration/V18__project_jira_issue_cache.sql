CREATE TABLE project_jira_issues (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id              UUID NOT NULL,
    issue_key               VARCHAR(32) NOT NULL,
    summary                 VARCHAR(512),
    issue_type              VARCHAR(64),
    status_name             VARCHAR(128),
    status_category_key     VARCHAR(32),
    assignee_account_id     VARCHAR(128),
    assignee_display_name   VARCHAR(255),
    priority_name           VARCHAR(64),
    story_points            NUMERIC(6,1),
    due_date                DATE,
    resolution_date         TIMESTAMPTZ,
    parent_key              VARCHAR(32),
    jira_created_at         TIMESTAMPTZ,
    jira_updated_at         TIMESTAMPTZ,
    synced_at               TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_jira_issues_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT uq_jira_issue_per_project
        UNIQUE (project_id, issue_key)
);

CREATE INDEX idx_jira_issues_project_id
    ON project_jira_issues (project_id);

CREATE INDEX idx_jira_issues_status
    ON project_jira_issues (project_id, status_category_key);
