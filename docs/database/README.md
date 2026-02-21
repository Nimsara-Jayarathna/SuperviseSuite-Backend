# Database Documentation

This directory tracks database structure and migration history for the backend.

## Structure

- `schema-v1.md`: base schema created by Flyway `V1`.
- `migrations.md`: migration log and rules for future schema changes.

## Source of Truth

- Runtime migrations: `src/main/resources/db/migration`
- Flyway history table: `flyway_schema_history`
- Profile-specific Flyway behavior:
  - default: `src/main/resources/application.yaml` (`baseline-on-migrate: false`)
  - dev only: `src/main/resources/application-dev.yaml` (`baseline-on-migrate: true`)

## Change Workflow

1. Add a new Flyway file in `src/main/resources/db/migration` (example: `V2__add_project_owner.sql`).
2. Update `docs/database/migrations.md` with a short entry.
3. If table/column model changes significantly, update `docs/database/schema-v1.md` (or create next schema doc when needed).
