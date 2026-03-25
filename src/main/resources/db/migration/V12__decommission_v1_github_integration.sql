-- =============================================================
-- V12: Decommission V1 GitHub Integration
-- Removes legacy repository metadata, commits, and contributors
-- that were replaced by the ProjectRepositoryLink V2 system.
-- =============================================================

-- 1. Drop the legacy repository_url field from projects
ALTER TABLE projects
    DROP COLUMN repository_url;

-- 2. Drop the decommissioned V1 cache tables
-- Note: Order matters due to foreign key constraints
DROP TABLE IF EXISTS project_repository_contributors;
DROP TABLE IF EXISTS project_repository_commits;
DROP TABLE IF EXISTS project_repositories;
