-- =============================================================
-- V9: Access Request Result Tokens
-- Adds post-callback public success token/ack tracking.
-- =============================================================

ALTER TABLE project_github_access_requests
    ADD COLUMN result_token_hash VARCHAR(128),
    ADD COLUMN result_expires_at TIMESTAMPTZ,
    ADD COLUMN result_acknowledged_at TIMESTAMPTZ;

CREATE UNIQUE INDEX uk_project_github_access_requests_result_token_hash
    ON project_github_access_requests (result_token_hash)
    WHERE result_token_hash IS NOT NULL;
