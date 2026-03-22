# Migration Log

## 2026-03-02 — Full schema rewrite (no prior data; clean reset)

### V1__init_schema.sql

- Created core tables:
  - `users` — `id` (UUID, `gen_random_uuid()`), `created_at` (NOT NULL), `updated_at`, `email` (NOT NULL UNIQUE), `role` (NOT NULL)
  - `projects` — `id`, `created_at` (NOT NULL), `updated_at`, `name` (NOT NULL), `description`, `status` (NOT NULL), `supervisor_id` (FK → `users.id`), `deleted_at`
  - `project_members` — `id`, `created_at` (NOT NULL), `updated_at`, `user_id` (NOT NULL, FK → `users`), `project_id` (NOT NULL, FK → `projects`)
- Added constraints:
  - `chk_users_role`: `role IN ('SUPERVISOR', 'STUDENT')`
  - `fk_projects_supervisor`: supervisor_id → users.id
  - `fk_project_members_user`, `fk_project_members_project` with ON DELETE CASCADE
  - `uk_project_members_user_project`: unique (user_id, project_id)
- Added indexes:
  - `idx_project_members_user_id`
  - `idx_project_members_project_id`
  - `idx_projects_supervisor_id`

### V2__auth_schema.sql

- **`users`** extended:
  - `password_hash` varchar(255) nullable — populated on registration; pre-seeded rows have no password
  - `first_name`, `last_name` varchar(100) nullable
  - `registration_number` varchar(20) nullable, unique — populated on student registration; pre-seeded supervisor rows have no value
- **`refresh_tokens`** new table:
  - `token_hash` varchar(255) NOT NULL UNIQUE
  - `revoked_at` nullable — null means still active
  - `expires_at` NOT NULL
  - `created_at` NOT NULL
  - FK → `users.id` ON DELETE CASCADE

## 2026-03-04 — Project domain expansion

### V3__project_domain_expansion.sql

- **`projects`** updated:
  - Renamed columns:
    - `name` -> `title`
    - `description` -> `summary`
    - `status` -> `lifecycle_status`
  - Added columns:
    - `batch`, `semester`
    - `progress_percent`
    - `health_note`
    - `milestone_date`
    - `last_activity_at`
    - `communication_url`, `repository_url`
    - `jira_project_key`, `jira_board_url`
  - Added constraints:
    - `chk_projects_lifecycle_status`
    - `chk_projects_progress_percent`
  - Added indexes:
    - `idx_projects_lifecycle_status`
    - `idx_projects_milestone_date`

- **`project_members`** updated:
  - Added `member_role` with backfill based on whether the member matches `projects.supervisor_id`
  - Added `chk_project_members_member_role`
  - Added `idx_project_members_member_role`

- **`project_milestones`** new table:
  - `id`, `project_id`, `title`, `description`
  - `due_date`, `status`, `sequence_no`
  - `created_by`, `created_at`, `updated_at`
  - FK to `projects.id` with `ON DELETE CASCADE`
  - FK to `users.id` for `created_by`
  - Unique per project milestone order: `(project_id, sequence_no)`

## 2026-03-21 — Project leader assignment

### V4__project_leader_assignment.sql

- **`projects`** updated:
  - Added column:
    - `leader_user_id UUID NULL`
  - Added constraints:
    - `fk_projects_leader_user`: `leader_user_id` -> `users.id`
  - Added indexes:
    - `idx_projects_leader_user_id`

## 2026-03-21 — GitHub cache persistence

### V5__project_github_cache.sql

- Added **`project_repositories`** table:
  - `id` (UUID PK)
  - `project_id` (FK -> `projects.id`, ON DELETE CASCADE)
  - `provider` (`github`)
  - `repository_external_id` (nullable)
  - `repository_name`
  - `repository_url`
  - `owner_login` (nullable)
  - `default_branch` (nullable)
  - `installation_id` (nullable)
  - `is_primary` (default `true`)
  - `sync_status` / `last_sync_error` (nullable sync state)
  - `last_synced_at` (nullable)
  - `created_at`, `updated_at`
- Added constraints/indexes:
  - unique primary repository per project scope
  - indexes for `project_id`, `installation_id`

- Added **`project_repository_commits`** table:
  - `id` (UUID PK)
  - `repository_id` (FK -> `project_repositories.id`, ON DELETE CASCADE)
  - `sha`
  - `message`
  - `author`
  - `committed_at`
  - `commit_type` (nullable)
  - `created_at`
- Added constraints/indexes:
  - unique `(repository_id, sha)` to prevent duplicates
  - index for commit timeline queries by repository

- Added **`project_repository_contributors`** table:
  - `id` (UUID PK)
  - `repository_id` (FK -> `project_repositories.id`, ON DELETE CASCADE)
  - `contributor_name`
  - `commit_count`
  - `last_contribution_at` (nullable)
  - `updated_at`
- Added constraints/indexes:
  - unique `(repository_id, contributor_name)`
  - index for ranking queries (`commit_count DESC`)

## 2026-03-21 — GitHub App installation tracking

### V6__github_app_installations.sql

- Added **`github_app_installations`** table:
  - `id` (UUID PK)
  - `installation_id` (GitHub installation id, unique)
  - `account_id` (nullable)
  - `account_login` (nullable)
  - `account_type` (nullable)
  - `status` (ACTIVE/PENDING/SUSPENDED/DELETED style lifecycle)
  - `installed_at` (nullable)
  - `last_event_at` (nullable)
  - `created_at`, `updated_at`
- Added indexes:
  - unique index on `installation_id`
  - status/query support indexes for installation lookups

## Rules for Next Migrations

- Use versioned files: `V{number}__{description}.sql`
- Never edit a migration file that has been applied to shared environments.
- Add a new version for every schema/data change.
- Record each new migration in this file with date and summary.
- Keep versioned DDL deterministic — do not use `IF NOT EXISTS` in versioned Flyway migrations.
- `baseline-on-migrate`: disabled (`false`) in all profiles. Apply only to an empty database.
