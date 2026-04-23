CREATE TABLE project_meeting_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL,
    meeting_date DATE NOT NULL,
    duration_minutes INT NOT NULL,
    discussion_summary VARCHAR(1024) NOT NULL,
    discussion_details TEXT,
    channel_id UUID,
    added_by UUID NOT NULL,
    added_by_name VARCHAR(255) NOT NULL,
    added_by_role VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    approved_by UUID,
    approved_by_name VARCHAR(255),
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    CONSTRAINT fk_project_meeting_records_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_project_meeting_records_channel
        FOREIGN KEY (channel_id) REFERENCES project_meeting_channels (id) ON DELETE SET NULL,
    CONSTRAINT fk_project_meeting_records_added_by
        FOREIGN KEY (added_by) REFERENCES users (id),
    CONSTRAINT fk_project_meeting_records_approved_by
        FOREIGN KEY (approved_by) REFERENCES users (id),
    CONSTRAINT chk_project_meeting_records_duration_minutes
        CHECK (duration_minutes > 0),
    CONSTRAINT chk_project_meeting_records_added_by_role
        CHECK (added_by_role IN ('SUPERVISOR', 'STUDENT')),
    CONSTRAINT chk_project_meeting_records_status
        CHECK (status IN ('PENDING', 'APPROVED')),
    CONSTRAINT chk_project_meeting_records_approval_consistency
        CHECK (
            (status = 'PENDING' AND approved_by IS NULL AND approved_at IS NULL)
            OR
            (status = 'APPROVED' AND approved_by IS NOT NULL AND approved_at IS NOT NULL)
        )
);

CREATE INDEX idx_project_meeting_records_project_status_date_created
    ON project_meeting_records (project_id, status, meeting_date DESC, created_at DESC);

CREATE INDEX idx_project_meeting_records_project_date_created
    ON project_meeting_records (project_id, meeting_date DESC, created_at DESC);

