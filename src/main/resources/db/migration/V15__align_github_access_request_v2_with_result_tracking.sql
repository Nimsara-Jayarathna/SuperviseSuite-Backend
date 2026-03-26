-- V15: Align GitHub Access Request V2 with result tracking logic for confirmation flow
ALTER TABLE github_access_requests_v2
    ADD COLUMN result_token_hash VARCHAR(255),
    ADD COLUMN result_expires_at TIMESTAMP,
    ADD COLUMN result_acknowledged_at TIMESTAMP,
    ADD COLUMN installation_id BIGINT;

CREATE INDEX idx_github_access_requests_v2_result_token_hash ON github_access_requests_v2(result_token_hash);
