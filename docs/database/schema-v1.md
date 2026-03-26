# Schema Reference (Current Through V15)

This document reflects the effective schema after applying migrations `V1` to `V15`.

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

## Legacy GitHub V1 Artifacts (Decommissioned)

The following were removed by `V12__decommission_v1_github_integration.sql` and are not part of the current schema:

- `projects.repository_url`
- `project_repositories`
- `project_repository_commits`
- `project_repository_contributors`

## Notes

- For exact DDL, use migration files in `src/main/resources/db/migration`.
- For chronological change history, use `docs/database/migrations.md`.
