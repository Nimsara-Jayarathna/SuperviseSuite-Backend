ALTER TABLE project_repository_link_commits
    ADD COLUMN github_username VARCHAR(255),
    ADD COLUMN github_avatar_url VARCHAR(1024);

ALTER TABLE project_repository_link_contributors
    ADD COLUMN github_username VARCHAR(255),
    ADD COLUMN github_avatar_url VARCHAR(1024);
