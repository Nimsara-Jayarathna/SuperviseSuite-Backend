-- =============================================================
-- V14: Add updated_at to access sources
-- Adds missing updated_at column to github_access_sources
-- to align with the domain model.
-- =============================================================

ALTER TABLE github_access_sources
    ADD COLUMN updated_at TIMESTAMPTZ;

-- Initialize updated_at with created_at for existing records
UPDATE github_access_sources
SET updated_at = created_at
WHERE updated_at IS NULL;
