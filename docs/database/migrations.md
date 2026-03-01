# Migration Log

## 2026-02-22

### V1__init_schema.sql

- Created initial schema for:
  - `users`
  - `projects`
  - `project_members`
- Added constraints:
  - unique `users.email`
  - foreign keys on `project_members.user_id` and `project_members.project_id`
  - unique `(user_id, project_id)` in `project_members`
- Added indexes:
  - `idx_project_members_user_id`
  - `idx_project_members_project_id`

## 2026-02-23

### V2__auth_schema.sql

- **`users`** extended:
  - `password_hash` varchar(255) nullable — populated on registration; existing rows have no password
  - `first_name`, `last_name` varchar(100) nullable
  - `is_email_verified` boolean not null default false
  - Added CHECK constraint on `role`: must be `'SUPERVISOR'` or `'STUDENT'`
- **`verification_tokens`** new table:
  - UUID token, unique, short-lived
  - `used_at` nullable — null means not yet consumed
  - `expires_at` enforces time-bound validity
  - FK → `users.id` on delete cascade
- **`refresh_tokens`** new table:
  - `token_hash` stores SHA-256 hash of raw token (raw never persisted)
  - `revoked_at` nullable — null means still active
  - `expires_at` enforces time-bound validity
  - FK → `users.id` on delete cascade
- **`projects`** extended:
  - `description` text nullable
  - `supervisor_id` UUID nullable — FK → `users.id` (supervisor ownership; nullable for existing rows)
  - `deleted_at` timestamptz nullable (soft delete marker)
- Added index: `idx_projects_supervisor_id`

## 2026-03-02

### V3__add_registration_number.sql

- **`users`** extended:
  - `registration_number` varchar(100) nullable, unique — populated on student registration; existing rows have no value

## Rules for Next Migrations

- Use versioned files: `V{number}__{description}.sql`
- Never edit a migration file that has been applied to shared environments.
- Add a new version for every schema/data change.
- Record each new migration in this file with date and summary.
- Keep versioned DDL deterministic:
  - do not use `IF NOT EXISTS` in versioned Flyway migrations.
  - allow startup to fail when schema state does not match expected history.
- `baseline-on-migrate` policy:
  - default profile: disabled (`false`)
  - `dev` profile only: enabled (`true`) for one-time onboarding of legacy/local schemas.
