-- =============================================================
-- V3: Project Domain Expansion
-- Expands project metadata, upgrades project membership roles,
-- and introduces project milestones.
-- =============================================================

-- 1. Align projects with the richer project domain.
ALTER TABLE projects
    RENAME COLUMN name TO title;

ALTER TABLE projects
    RENAME COLUMN description TO summary;

ALTER TABLE projects
    RENAME COLUMN status TO lifecycle_status;

ALTER TABLE projects
    ADD COLUMN batch VARCHAR(32),
    ADD COLUMN semester VARCHAR(64),
    ADD COLUMN progress_percent INTEGER,
    ADD COLUMN health_note TEXT,
    ADD COLUMN milestone_date DATE,
    ADD COLUMN last_activity_at TIMESTAMPTZ,
    ADD COLUMN communication_url TEXT,
    ADD COLUMN repository_url TEXT,
    ADD COLUMN jira_project_key VARCHAR(32),
    ADD COLUMN jira_board_url TEXT;

ALTER TABLE projects
    ADD CONSTRAINT chk_projects_lifecycle_status
        CHECK (lifecycle_status IN ('PLANNING', 'ACTIVE', 'AT_RISK', 'BEHIND', 'COMPLETED'));

ALTER TABLE projects
    ADD CONSTRAINT chk_projects_progress_percent
        CHECK (progress_percent IS NULL OR (progress_percent >= 0 AND progress_percent <= 100));

CREATE INDEX idx_projects_lifecycle_status ON projects (lifecycle_status);
CREATE INDEX idx_projects_milestone_date ON projects (milestone_date);

-- 2. Upgrade project membership to track the member's role.
ALTER TABLE project_members
    ADD COLUMN member_role VARCHAR(64);

UPDATE project_members pm
SET member_role = CASE
    WHEN p.supervisor_id = pm.user_id THEN 'SUPERVISOR'
    ELSE 'STUDENT'
END
FROM projects p
WHERE p.id = pm.project_id;

ALTER TABLE project_members
    ALTER COLUMN member_role SET NOT NULL;

ALTER TABLE project_members
    ADD CONSTRAINT chk_project_members_member_role
        CHECK (member_role IN ('SUPERVISOR', 'STUDENT'));

CREATE INDEX idx_project_members_member_role ON project_members (member_role);

-- 3. Milestones become first-class project records.
CREATE TABLE project_milestones (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id  UUID         NOT NULL,
    title       VARCHAR(255) NOT NULL,
    description TEXT,
    due_date    DATE         NOT NULL,
    status      VARCHAR(64)  NOT NULL,
    sequence_no INTEGER      NOT NULL,
    created_by  UUID,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ,
    CONSTRAINT fk_project_milestones_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_project_milestones_created_by
        FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT chk_project_milestones_status
        CHECK (status IN ('PLANNED', 'IN_PROGRESS', 'COMPLETED', 'MISSED', 'CANCELLED')),
    CONSTRAINT chk_project_milestones_sequence_no
        CHECK (sequence_no > 0),
    CONSTRAINT uk_project_milestones_project_sequence
        UNIQUE (project_id, sequence_no)
);

CREATE INDEX idx_project_milestones_project_id ON project_milestones (project_id);
CREATE INDEX idx_project_milestones_due_date ON project_milestones (due_date);
