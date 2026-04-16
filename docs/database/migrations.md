# Migration Log

This log is aligned to migration files under `src/main/resources/db/migration`.

## 2026-03-02 — Initial schema and auth foundation

### `V1__init_schema.sql`

- Created core tables: `users`, `projects`, `project_members`.
- Added role/member constraints and baseline indexes.

### `V2__auth_schema.sql`

- Extended `users` with auth/profile fields (`password_hash`, `first_name`, `last_name`, `registration_number`).
- Added `refresh_tokens` table for refresh-token persistence/revocation.

## 2026-03-04 — Project domain expansion

### `V3__project_domain_expansion.sql`

- Expanded `projects` with lifecycle/progress/reporting fields.
- Added `project_milestones`.
- Added `member_role` to `project_members`.

## 2026-03-21 — Supervisor project leader support

### `V4__project_leader_assignment.sql`

- Added `projects.leader_user_id` (+ FK/index).

## 2026-03-21 — GitHub integration v1 foundation

### `V5__project_github_cache.sql`

- Added v1 repository cache tables:
  - `project_repositories`
  - `project_repository_commits`
  - `project_repository_contributors`

### `V6__github_app_installations.sql`

- Added `github_app_installations`.

## 2026-03-22 — Project-scoped authorization and access-request flow (v1 path)

### `V7__project_github_authorization_scope.sql`

- Added `project_github_installation_authorizations`.

### `V8__project_github_access_requests.sql`

- Added `project_github_access_requests` (request tokens + state hash/status).

### `V9__project_github_access_request_result_tokens.sql`

- Extended `project_github_access_requests` with result-token fields.

## 2026-03-22 onward — GitHub integration v2 (SCRUM-81)

### `V10__github_integration_v2.sql`

- Introduced v2 tables:
  - `github_access_sources`
  - `github_repositories`
  - `project_repository_links`
  - `github_setup_states`
  - `github_access_requests_v2`
  - `project_repository_link_commits`
  - `project_repository_link_contributors`

### `V11__github_repository_enablement_limits.sql`

- Added `is_enabled` to `project_repository_links`.
- Enforced one enabled primary link per project (partial unique index).

### `V12__decommission_v1_github_integration.sql`

- Dropped legacy `projects.repository_url`.
- Dropped v1 cache tables:
  - `project_repositories`
  - `project_repository_commits`
  - `project_repository_contributors`

### `V13__denormalized_repository_link_fields.sql`

- Added denormalized metadata columns to `project_repository_links`:
  - `github_installation_id`
  - `repository_url`
  - `repository_name`
  - `default_branch`
  - `linked_by_supervisor_user_id`
  - `access_type`

### `V14__add_updated_at_to_access_sources.sql`

- Added `updated_at` to `github_access_sources`.

### `V15__align_github_access_request_v2_with_result_tracking.sql`

- Added callback result-tracking columns to `github_access_requests_v2`:
  - `result_token_hash`
  - `result_expires_at`
  - `result_acknowledged_at`
  - `installation_id`

## 2026-04 Jira integration and cached analytics

### `V16__project_jira_integrations.sql`

- Added `project_jira_integrations` for per-project Jira workspace linkage and encrypted access token storage.
- Enforced one active Jira integration per project (`revoked_at IS NULL` partial unique index).

### `V17__project_jira_oauth_states.sql`

- Added `project_jira_oauth_states` for secure one-time OAuth state tracking (hashed nonce, expiry, used markers).

### `V18__project_jira_issue_cache.sql`

- Added `project_jira_issues` cache table for Jira issue snapshots and analytics source data.
- Includes hierarchy linkage key (`parent_key`) and issue metadata used by health/workload/sprint views.

### `V19__ensure_project_jira_issue_cache_exists.sql`

- Repair migration: idempotent `CREATE TABLE IF NOT EXISTS` for `project_jira_issues` and core indexes.
- Protects environments where `V18` was marked applied but table creation did not land.

### `V20__add_sprint_fields_to_jira_issues.sql`

- Added sprint metadata columns to `project_jira_issues`:
  - `sprint_id`
  - `sprint_name`
  - `sprint_state`
  - `sprint_start_date`
  - `sprint_end_date`
- Added sprint lookup index on `(project_id, sprint_id)`.

### `V21__add_parent_issue_type_to_jira_issues.sql`

- Added nullable `parent_issue_type` to speed parent/subtask workload calculations without self-joins.

### `V22__add_refresh_token_to_jira_integrations.sql`

- Added Jira OAuth refresh-token support fields on `project_jira_integrations`:
  - `refresh_token_encrypted`
  - `token_expires_at`

## 2026-04-12 — Registration verification flow (SCRUM-106)

### `V24__registration_sessions.sql`

- Added `registration_sessions` table for short-lived registration continuation tokens.
- Stores hashed token material and expiry metadata used after OTP verification.

### `V25__email_otps.sql`

- Added `email_otps` table for one-time password verification state.
- Stores hashed OTP values with expiry and attempt lifecycle columns.
- Supports cleanup and replay prevention for registration init/verify/complete flow.

## 2026-04-13 — Password reset token persistence

### `V26__password_reset_tokens.sql`

- Added `password_reset_tokens` table for forgot-password / reset-password continuation flow.
- Stores hashed reset token material, expiry, and usage/cleanup lifecycle fields.

## 2026-04-13 — Sync/concurrency hardening

### `V27__optimistic_locking_and_sync_indexes.sql`

- Added optimistic-locking/version support and supporting indexes for high-frequency sync/update paths.
- Focused on reducing stale-write collisions and improving query performance during background sync.

### `V28__sync_in_progress_and_attempt_tracking.sql`

- Added sync attempt/in-progress tracking fields used by background sync orchestration.
- Supports safer retry behavior and clearer sync state visibility.

## 2026-04-16 — Meetings channel schema

### `V29__project_meeting_channels.sql`

- Added `project_meeting_channels` table with:
  - channel metadata (`platform`, `channel_name`, `link_or_identifier`)
  - attribution (`added_by`, `added_by_name`, `added_by_role`)
  - approval lifecycle (`status`, `approved_by`, `approved_by_name`, `approved_at`)
  - audit fields (`created_at`, `updated_at`)
- Added constraints:
  - platform whitelist (`GOOGLE_MEET`, `ZOOM`, `TEAMS`, `WHATSAPP`, `OTHER`)
  - role/status check constraints
  - approval consistency check for pending vs approved rows
- Added indexes:
  - `(project_id, status, created_at DESC)` for pending-first listing
  - `(project_id, created_at DESC)` for recency listing
## 2026-04 Project file attachments

### `V23__project_files.sql`

- Added `project_files` table with:
  - linkage fields (`id`, `project_id`)
  - storage pointer and metadata (`s3_key`, `file_name`, `file_type`, `file_size`)
  - uploader and audit fields (`uploaded_by`, `uploaded_by_name`, `created_at`, `updated_at`, `deleted_at`)
- Added constraints:
  - FK to `projects` (`ON DELETE CASCADE`)
  - FK to `users` (`uploaded_by`)
  - check constraint `file_size > 0`
- Added indexes:
  - `(project_id, created_at DESC)` for ordered list queries
  - `(project_id, deleted_at)` for active-row filtering

## Rules for Next Migrations

- Use versioned files: `V{number}__{description}.sql`.
- Never modify an already-applied migration in shared environments.
- Add a new version for every schema/data change.
- Update this document whenever a new migration is added.
- Keep versioned DDL deterministic.
- `baseline-on-migrate` is disabled; apply migrations on an empty DB unless explicitly handling legacy bootstrap flows.