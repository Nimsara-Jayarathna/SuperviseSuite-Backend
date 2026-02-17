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

## Verify Standard (Local)

Before commit/PR, run:

- `./mvnw -q test`

If the project has no tests yet, `./mvnw test` is still acceptable and should compile and execute the test phase.
