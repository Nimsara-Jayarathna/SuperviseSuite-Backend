# Schema Reference (Current Through V29)

This document reflects the effective schema after applying migrations `V1` to `V29`.

## Core Tables

### `users`

- Identity and role records for supervisors/students.
- Includes auth/profile fields used by registration/login and role-based access.

### `projects`

- Supervisor-owned project record.
- Includes lifecycle/progress/reporting fields (title, summary, batch/semester, lifecycle status, progress, health/milestone metadata).
- Includes `leader_user_id` FK for project leader assignment.

### `project_members`

- Project membership rows (`user_id`, `project_id`, `member_role`).
- Unique per `(user_id, project_id)`.

### `project_milestones`

- Ordered milestone list per project (`sequence_no`, status, due date, audit timestamps).

### `refresh_tokens`

- Hashed refresh-token persistence for cookie-based auth sessions.

### `registration_sessions`

- Registration continuation token state used in OTP-verified registration flow.
- Stores hashed token material, expiry, and usage timestamps.

### `email_otps`

- One-time password lifecycle table for email verification.
- Stores hashed OTP values, expiry timestamps, and attempt counters.

### `password_reset_tokens`

- Password reset token state and expiry tracking.
- Used by forgot-password / reset-password validation flows.

### `project_files`

- Project-scoped file metadata table for supervisor/student attachments.
- Key columns:
  - identity/linkage: `id`, `project_id`
  - object pointer + metadata: `s3_key`, `file_name`, `file_type`, `file_size`
  - uploader: `uploaded_by`, `uploaded_by_name`
  - audit/soft-delete: `created_at`, `updated_at`, `deleted_at`
- Soft-delete model:
  - active rows use `deleted_at IS NULL`
  - file listing and access operations exclude soft-deleted rows
- Notable constraints:
  - FK `project_id -> projects.id` with `ON DELETE CASCADE`
  - FK `uploaded_by -> users.id`
  - check: `file_size > 0`

### `project_meeting_channels`

- Project-scoped meeting channel registry used by Meetings tab channel management.
- Key columns:
  - identity/linkage: `id`, `project_id`
  - channel metadata: `platform`, `channel_name`, `link_or_identifier`
  - creator attribution: `added_by`, `added_by_name`, `added_by_role`
  - approval lifecycle: `status`, `approved_by`, `approved_by_name`, `approved_at`
  - audit: `created_at`, `updated_at`
- Supported platforms: `GOOGLE_MEET`, `ZOOM`, `TEAMS`, `WHATSAPP`, `OTHER`.
- Status model: `PENDING`, `APPROVED`.
- Notable constraints:
  - FK `project_id -> projects.id` with `ON DELETE CASCADE`
  - FK `added_by -> users.id`
  - FK `approved_by -> users.id`
  - approval consistency check:
    - `PENDING` requires `approved_by`/`approved_at` null
    - `APPROVED` requires `approved_by`/`approved_at` non-null
- Key indexes:
  - `(project_id, status, created_at DESC)` for pending-first listing
  - `(project_id, created_at DESC)` for default recency listing

## GitHub Integration (Current V2 Model)

### `github_app_installations`

- Tracks GitHub App installations and account metadata.

### `github_access_sources`

- Project-scoped source of GitHub access (installation/public/manual lineage).

### `github_repositories`

- Repositories discoverable under an access source.

### `project_repository_links`

- Explicit project-to-repository linkage table.
- Contains linkage/sync status plus denormalized metadata:
  - `repository_url`
  - `repository_name`
  - `default_branch`
  - `github_installation_id`
  - `access_type`
  - `is_enabled` / `is_primary`

### `project_repository_link_commits`

- Commit snapshot cache per project repository link.

### `project_repository_link_contributors`

- Contributor summary snapshot per project repository link.

### `github_setup_states`

- Secure one-time setup state tracking (hashed JTI, expiry, used markers).

### `github_access_requests_v2`

- Project-scoped request-access tokens and callback-result tracking:
  - request token hash + expiry/usage
  - result token hash + expiry + acknowledgement timestamp
  - linked installation id (when resolved)

## Jira Integration and Cached Analytics

### `project_jira_integrations`

- Per-project Jira workspace connection details.
- Stores encrypted OAuth tokens and workspace metadata.
- Includes refresh token support (`refresh_token_encrypted`, `token_expires_at`).
- One active integration per project is enforced via partial unique index on `(project_id)` where `revoked_at IS NULL`.

### `project_jira_oauth_states`

- One-time Jira OAuth state records per project/user.
- Stores hashed nonce (`state_nonce_hash`), expiry, used marker, and audit timestamps.

### `project_jira_issues`

- DB-backed Jira issue cache used by project-level Jira dashboards (health, sprint progress, workload, hierarchy).
- Key columns include:
  - identity/linkage: `project_id`, `issue_key`, `parent_key`, `parent_issue_type`
  - issue metadata: `summary`, `issue_type`, `status_name`, `priority_name`, `assignee_display_name`, `story_points`, `due_date`
  - sprint metadata: `sprint_id`, `sprint_name`, `sprint_state`, `sprint_start_date`, `sprint_end_date`
  - sync/audit fields: `jira_created_at`, `jira_updated_at`, `synced_at`
- Unique per `(project_id, issue_key)`.

## Legacy GitHub V1 Artifacts (Decommissioned)

The following were removed by `V12__decommission_v1_github_integration.sql` and are not part of the current schema:

- `projects.repository_url`
- `project_repositories`
- `project_repository_commits`
- `project_repository_contributors`

## Notes

- For exact DDL, use migration files in `src/main/resources/db/migration`.
- For chronological change history, use `docs/database/migrations.md`.