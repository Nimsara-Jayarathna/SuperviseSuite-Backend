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
