-- V27: Optimistic Locking and Sync Indexing
-- Adds versioning and optimized indexes for background synchronization.

-- 1. Add version column for Optimistic Locking
ALTER TABLE project_repository_links 
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE project_jira_integrations 
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- 2. Add optimized index for background sync queue selection
-- This optimizes the findByIsEnabledTrueOrderByLastSyncedAtAsc query
CREATE INDEX idx_project_repository_links_sync_queue 
    ON project_repository_links (is_enabled, last_synced_at ASC);

-- 3. Add index for Jira sync selection
CREATE INDEX idx_project_jira_integrations_sync_queue
    ON project_jira_integrations (revoked_at, connected_at ASC);
