# SuperviseSuite-Backend

Core SuperviseSuite backend built with Spring Boot. Provides REST APIs for authentication/authorization, user and project management, and project membership/assignment workflows. Owns the main database model and supports future expansion for analytics, reporting, and external tool connectivity.

## Local Run and Check Standards

Always use Maven Wrapper for local commands:

- macOS/Linux: `./mvnw`
- Windows: `mvnw.cmd`

## Common Commands

- Run dev: `./mvnw spring-boot:run`
- Run tests: `./mvnw test`
- Build jar: `./mvnw clean package`

## Environment Variables

The backend reads DB config from environment variables:

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`

Setup for local development:

1. Create your local env file: `cp .env.example .env`
2. Run backend:
   `./mvnw spring-boot:run`

`.env` is auto-loaded by Spring via `spring.config.import`.

## Flyway Migrations

- Flyway is enabled in `src/main/resources/application.yaml`.
- Migration scripts are in `src/main/resources/db/migration`.
- On each backend start, Flyway checks the schema history table and applies only pending versions.
- `V1__init_schema.sql` creates the base tables for `users`, `projects`, and `project_members`.

## Verify Standard (Local)

Before commit/PR, run:

- `./mvnw -q test`

If the project has no tests yet, `./mvnw test` is still acceptable and should compile and execute the test phase.

## Contributing / Workflow

For branching rules, PR expectations, and local verification steps, see `CONTRIBUTING.md`.

## Future: CI

CI is intentionally not configured yet. In a later phase, automated pipelines will enforce the same local checks (build/test/verification) currently documented for developers.
