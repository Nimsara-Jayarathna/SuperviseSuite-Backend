# Schema Reference

The schema is built by two Flyway migrations applied in order:

- `V1__init_schema.sql` — core tables
- `V2__auth_schema.sql` — auth fields and refresh tokens

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
