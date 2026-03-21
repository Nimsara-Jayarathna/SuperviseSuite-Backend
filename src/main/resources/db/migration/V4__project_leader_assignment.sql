ALTER TABLE projects
    ADD COLUMN leader_user_id UUID NULL;

ALTER TABLE projects
    ADD CONSTRAINT fk_projects_leader_user
        FOREIGN KEY (leader_user_id) REFERENCES users(id);

CREATE INDEX idx_projects_leader_user_id ON projects (leader_user_id);
