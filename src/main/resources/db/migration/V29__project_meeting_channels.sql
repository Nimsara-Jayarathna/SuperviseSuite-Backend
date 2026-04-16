CREATE TABLE project_meeting_channels (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL,
    platform VARCHAR(32) NOT NULL,
    channel_name VARCHAR(120) NOT NULL,
    link_or_identifier VARCHAR(1024) NOT NULL,
    added_by UUID NOT NULL,
    added_by_name VARCHAR(255) NOT NULL,
    added_by_role VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    approved_by UUID,
    approved_by_name VARCHAR(255),
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    CONSTRAINT fk_project_meeting_channels_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_project_meeting_channels_added_by
        FOREIGN KEY (added_by) REFERENCES users (id),
    CONSTRAINT fk_project_meeting_channels_approved_by
        FOREIGN KEY (approved_by) REFERENCES users (id),
    CONSTRAINT chk_project_meeting_channels_platform
        CHECK (platform IN ('GOOGLE_MEET', 'ZOOM', 'TEAMS', 'WHATSAPP', 'OTHER')),
    CONSTRAINT chk_project_meeting_channels_added_by_role
        CHECK (added_by_role IN ('SUPERVISOR', 'STUDENT')),
    CONSTRAINT chk_project_meeting_channels_status
        CHECK (status IN ('PENDING', 'APPROVED')),
    CONSTRAINT chk_project_meeting_channels_approval_consistency
        CHECK (
            (status = 'PENDING' AND approved_by IS NULL AND approved_at IS NULL)
            OR
            (status = 'APPROVED' AND approved_by IS NOT NULL AND approved_at IS NOT NULL)
        )
);

CREATE INDEX idx_project_meeting_channels_project_status_created
    ON project_meeting_channels (project_id, status, created_at DESC);

CREATE INDEX idx_project_meeting_channels_project_created
    ON project_meeting_channels (project_id, created_at DESC);

