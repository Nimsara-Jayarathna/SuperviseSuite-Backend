CREATE TABLE project_files (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id       UUID NOT NULL,
    s3_key           VARCHAR(1024) NOT NULL,
    file_name        VARCHAR(255) NOT NULL,
    file_type        VARCHAR(255) NOT NULL,
    file_size        BIGINT NOT NULL,
    uploaded_by      UUID NOT NULL,
    uploaded_by_name VARCHAR(255) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ,
    deleted_at       TIMESTAMPTZ,
    CONSTRAINT fk_project_files_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_project_files_uploaded_by
        FOREIGN KEY (uploaded_by) REFERENCES users (id),
    CONSTRAINT chk_project_files_file_size
        CHECK (file_size > 0)
);

CREATE INDEX idx_project_files_project_id_created_at
    ON project_files (project_id, created_at DESC);

CREATE INDEX idx_project_files_project_id_deleted_at
    ON project_files (project_id, deleted_at);
