# Migration Log

This log is aligned to migration files under `src/main/resources/db/migration`.

## 2026-03-02 — Initial schema and auth token persistence

### `V1__init_schema.sql`

- Created core tables: `users`, `projects`, `project_members`.
- Added role/member constraints and baseline indexes.

### `V2__add_refresh_tokens.sql`

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

## Rules for Next Migrations

- Use versioned files: `V{number}__{description}.sql`.
- Never modify an already-applied migration in shared environments.
- Add a new version for every schema/data change.
- Update this document whenever a new migration is added.
- Keep versioned DDL deterministic.
- `baseline-on-migrate` is disabled; apply migrations on an empty DB unless explicitly handling legacy bootstrap flows.
