# Backend Recent Changes (2026-02-22)

## 1) Database Configuration and Environment Loading

- Added datasource config in `src/main/resources/application.yaml`:
  - `spring.datasource.url: ${DB_URL}`
  - `spring.datasource.username: ${DB_USERNAME}`
  - `spring.datasource.password: ${DB_PASSWORD}`
  - `spring.datasource.driver-class-name: org.postgresql.Driver`
- Enabled automatic loading of backend `.env` file:
  - `spring.config.import: optional:file:.env[.properties]`
- Added environment template file:
  - `.env.example` with `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `APP_PORT`

## 2) Server Port Configuration

- Added configurable application port in `src/main/resources/application.yaml`:
  - `server.port: ${APP_PORT:8080}`
- Port can now be changed via `.env` without code changes.

## 3) Flyway Migration Setup

- Flyway is enabled in `src/main/resources/application.yaml`.
- Added initial migration:
  - `src/main/resources/db/migration/V1__init_schema.sql`
- Added dedicated DB docs directory:
  - `docs/database/README.md`
  - `docs/database/schema-v1.md`
  - `docs/database/migrations.md`
- `V1` creates:
  - `users`
  - `projects`
  - `project_members`
- Includes:
  - primary keys
  - unique constraint on `users.email`
  - foreign keys from `project_members` to `users` and `projects`
  - indexes on `project_members.user_id` and `project_members.project_id`

## 4) Startup Migration Behavior

- On every backend startup, Flyway checks `flyway_schema_history`.
- Only missing migration versions are applied.
- If schema is already at latest version, no migration runs.

## 5) Security Behavior Update

- Implemented explicit Spring Security filter chain in `src/main/java/com/supervisesuite/backend/config/SecurityConfig.java`.
- Disabled default form login and HTTP basic login.
- Configured stateless session policy.
- Added JWT filter into chain.
- Permitted:
  - `/api/auth/**`
  - `/actuator/health`
  - `OPTIONS /**`
- Other endpoints require authentication.
- Result: unauthenticated API calls return `401` instead of redirecting to default `/login` page.
