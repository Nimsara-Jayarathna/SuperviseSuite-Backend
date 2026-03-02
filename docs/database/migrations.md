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

## Rules for Next Migrations

- Use versioned files: `V{number}__{description}.sql`
- Never edit a migration file that has been applied to shared environments.
- Add a new version for every schema/data change.
- Record each new migration in this file with date and summary.
- Keep versioned DDL deterministic — do not use `IF NOT EXISTS` in versioned Flyway migrations.
- `baseline-on-migrate`: disabled (`false`) in all profiles. Apply only to an empty database.
