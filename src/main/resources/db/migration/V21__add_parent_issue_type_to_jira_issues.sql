-- Add parent_issue_type to project_jira_issues.
-- Allows the workload service to identify parent issues that have subtasks
-- in a single O(n) pass without a self-join.
-- Nullable: existing rows default to NULL and will be populated on next Jira sync.
-- IF NOT EXISTS makes this idempotent — safe on envs where the column was added manually.

ALTER TABLE project_jira_issues
    ADD COLUMN IF NOT EXISTS parent_issue_type VARCHAR(64);
