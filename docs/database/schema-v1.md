# Schema Reference

The schema is built by three Flyway migrations applied in order:

- `V1__init_schema.sql` — core tables
- `V2__auth_schema.sql` — auth fields and refresh tokens
- `V3__project_domain_expansion.sql` — project domain expansion and milestones

---

## V1 Tables

### `users`

- `id` UUID primary key, `DEFAULT gen_random_uuid()`
- `created_at` timestamptz, **not null**
- `updated_at` timestamptz
- `email` varchar(255), not null, unique
- `role` varchar(64), not null — CHECK: `IN ('SUPERVISOR', 'STUDENT')`

### `projects`

- `id` UUID primary key, `DEFAULT gen_random_uuid()`
- `created_at` timestamptz, **not null**
- `updated_at` timestamptz
- `name` varchar(255), **not null**
- `description` text
- `status` varchar(64), **not null**
- `supervisor_id` UUID, FK → `users.id`
- `deleted_at` timestamptz (soft delete — null means active)

### `project_members`

- `id` UUID primary key, `DEFAULT gen_random_uuid()`
- `created_at` timestamptz, **not null**
- `updated_at` timestamptz
- `user_id` UUID, not null, FK → `users.id` (delete cascade)
- `project_id` UUID, not null, FK → `projects.id` (delete cascade)
- unique constraint: `(user_id, project_id)`

## V1 Indexes

- `idx_project_members_user_id` on `project_members(user_id)`
- `idx_project_members_project_id` on `project_members(project_id)`
- `idx_projects_supervisor_id` on `projects(supervisor_id)`

---

## V2 Additions

### `users` (extended)

- `password_hash` varchar(255), nullable — populated on registration
- `first_name` varchar(100), nullable
- `last_name` varchar(100), nullable
- `registration_number` varchar(20), nullable, unique — normalized to uppercase on registration

### `refresh_tokens` (new table)

- `id` UUID primary key, `DEFAULT gen_random_uuid()`
- `user_id` UUID, not null, FK → `users.id` (delete cascade)
- `token_hash` varchar(255), not null, unique
- `expires_at` timestamptz, not null
- `revoked_at` timestamptz, nullable — null means still active
- `created_at` timestamptz, not null

---

## V3 Changes

### `projects` (expanded)

- existing columns retained:
  - `id`, `created_at`, `updated_at`, `supervisor_id`, `deleted_at`
- renamed columns:
  - `name` -> `title`
  - `description` -> `summary`
  - `status` -> `lifecycle_status`
- new columns:
  - `batch` varchar(32)
  - `semester` varchar(64)
  - `progress_percent` integer
  - `health_note` text
  - `milestone_date` date
  - `last_activity_at` timestamptz
  - `communication_url` text
  - `repository_url` text
  - `jira_project_key` varchar(32)
  - `jira_board_url` text
- constraints:
  - `lifecycle_status` CHECK: `IN ('PLANNING', 'ACTIVE', 'AT_RISK', 'BEHIND', 'COMPLETED')`
  - `progress_percent` CHECK: `0..100` when present

### `project_members` (expanded)

- existing columns retained:
  - `id`, `created_at`, `updated_at`, `user_id`, `project_id`
- new column:
  - `member_role` varchar(64), not null — CHECK: `IN ('SUPERVISOR', 'STUDENT')`

### `project_milestones` (new table)

- `id` UUID primary key, `DEFAULT gen_random_uuid()`
- `project_id` UUID, not null, FK → `projects.id` (delete cascade)
- `title` varchar(255), not null
- `description` text
- `due_date` date, not null
- `status` varchar(64), not null
- `sequence_no` integer, not null
- `created_by` UUID, FK → `users.id`
- `created_at` timestamptz, not null
- `updated_at` timestamptz
- constraints:
  - `status` CHECK: `IN ('PLANNED', 'IN_PROGRESS', 'COMPLETED', 'MISSED', 'CANCELLED')`
  - `sequence_no > 0`
  - unique per project order: `(project_id, sequence_no)`

---

## Current Backend Coverage

The database schema is ahead of the currently implemented backend read/write APIs.

### Implemented in the API today

- Auth:
  - register student
  - login
- Supervisor:
  - project list summaries
  - project detail read model
  - student search by email
  - project creation with first milestone

### Present in schema but not yet exposed as full workflow APIs

- meeting management
- action items
- file handling
- integration management (GitHub, Jira, communication links as first-class workflows)

This means some tables/columns already exist for future work, but the frontend should only rely on currently implemented API contracts.
