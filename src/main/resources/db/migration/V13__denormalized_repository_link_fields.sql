-- =============================================================
-- V13: Denormalized Repository Link Fields
-- Adds denormalized metadata to ProjectRepositoryLink to optimize
-- project detail responses and background maintenance.
-- =============================================================

ALTER TABLE project_repository_links
    ADD COLUMN github_installation_id BIGINT,
    ADD COLUMN repository_url          TEXT,
    ADD COLUMN repository_name         VARCHAR(255),
    ADD COLUMN default_branch          VARCHAR(255),
    ADD COLUMN linked_by_supervisor_user_id UUID,
    ADD COLUMN access_type             VARCHAR(32);

-- Update existing records if any (unlikely to have many in a new V2 system)
-- Mapping from github_repositories and github_access_sources
UPDATE project_repository_links prl
SET repository_url = gr.html_url,
    repository_name = gr.full_name,
    default_branch = gr.default_branch,
    github_installation_id = gas.installation_id,
    access_type = gas.access_type
FROM github_repositories gr
JOIN github_access_sources gas ON gr.access_source_id = gas.id
WHERE prl.github_repository_id = gr.id;

-- Set constraints after data migration
ALTER TABLE project_repository_links
    ALTER COLUMN repository_url SET NOT NULL,
    ALTER COLUMN repository_name SET NOT NULL,
    ALTER COLUMN access_type SET NOT NULL;
