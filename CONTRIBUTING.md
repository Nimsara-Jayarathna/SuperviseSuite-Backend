# Contributing

## Local Standards

- Always use Maven Wrapper commands (`./mvnw` on macOS/Linux, `mvnw.cmd` on Windows).
- Before commit/PR, run `./mvnw test`.
- Do not commit `/target` build outputs.

## Database Schema Changes

- If DB schema changes are introduced, add a Flyway migration.
- Describe the migration and impact clearly in the PR description.
