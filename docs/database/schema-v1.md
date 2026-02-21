# Schema V1

Applies through Flyway migration:

- `src/main/resources/db/migration/V1__init_schema.sql`

## Tables

## `users`

- `id` UUID primary key
- `created_at` timestamptz
- `updated_at` timestamptz
- `email` varchar(255), not null, unique
- `role` varchar(64), not null

## `projects`

- `id` UUID primary key
- `created_at` timestamptz
- `updated_at` timestamptz
- `name` varchar(255)
- `status` varchar(64)

## `project_members`

- `id` UUID primary key
- `created_at` timestamptz
- `updated_at` timestamptz
- `user_id` UUID, not null, FK -> `users.id` (delete cascade)
- `project_id` UUID, not null, FK -> `projects.id` (delete cascade)
- unique constraint: `(user_id, project_id)`

## Indexes

- `idx_project_members_user_id` on `project_members(user_id)`
- `idx_project_members_project_id` on `project_members(project_id)`
