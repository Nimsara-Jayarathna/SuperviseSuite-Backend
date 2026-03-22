# SuperviseSuite-Backend

Core SuperviseSuite backend built with Spring Boot. Provides REST APIs for authentication/authorization, user and project management, and project membership/assignment workflows. Owns the main database model and supports future expansion for analytics, reporting, and external tool connectivity.

## API Documentation

Current API references:

- `docs/api/auth.md`
- `docs/api/supervisor.md`
- `docs/api/student.md`
- `docs/api/github-app.md`
- `docs/api-response-contract.md`

Backend fix documents:

- `docs/backend/major-fixes-scrum-97-supervisor-workflow.md`
- `docs/backend/major-fixes-scrum-80-github-app-dashboard.md`
- `docs/backend/recent-changes-2026-03-05.md`

## Recent API Contract Update (March 2026)

Backend API responses are standardized for all normal JSON endpoints:

- Top-level keys are always: `success`, `message`, `data`, `error`, `meta`.
- Security failures (`401`/`403`) now follow the same contract as controller exceptions.
- Framework fallback errors are normalized to avoid leaking raw Spring default error shapes.

Frontend integration note:

- Frontend must parse wrapped errors (`message` + nested `error.code/status/details` + `meta`), not legacy raw top-level error payloads.
- `POST /api/auth/logout` remains an intentional `204 No Content` exception.

## Local Run and Check Standards

Always use Maven Wrapper for local commands:

- macOS/Linux: `./mvnw`
- Windows: `mvnw.cmd`

## Common Commands

- Run dev: `./mvnw spring-boot:run`
- Run tests: `./mvnw test`
- Build jar: `./mvnw clean package`

## Environment Variables

The backend reads DB and auth config from environment variables:

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `APP_PORT`
- `CORS_ALLOWED_ORIGINS`
- `FRONTEND_BASE_URL` — frontend base URL used by backend redirects (for example GitHub setup callback)
- `COOKIE_SECURE` — set to `false` for local HTTP development (default: `true`)
- `JWT_SECRET` — base64-encoded secret used to sign and verify access token JWTs
- `GITHUB_TOKEN` — optional GitHub token (recommended for higher rate limits)
- `GITHUB_APP_ID` — GitHub App id used for app JWT generation
- `GITHUB_APP_CLIENT_ID` — GitHub App client id for setup/install flow
- `GITHUB_APP_CLIENT_SECRET` — GitHub App client secret (if used by your callback flow)
- `GITHUB_APP_INSTALL_URL` — GitHub App installation URL used by backend to continue project-scoped access requests
- `GITHUB_APP_PRIVATE_KEY` — GitHub App PEM private key (escaped newlines supported in `.env`)
- `GITHUB_APP_WEBHOOK_SECRET` — webhook signature secret for `/api/github/webhooks`
- `GITHUB_DEFAULT_BRANCH` — fallback default branch when metadata is missing (default: `main`)
- `GITHUB_ACTIVITY_ACTIVE_WINDOW_HOURS` — active/idle threshold window in hours (default: `48`)
- `GITHUB_PREVIEW_COMMITS_LIMIT` — preview commits count in project detail GitHub block (default: `6`)
- `GITHUB_PREVIEW_CONTRIBUTORS_LIMIT` — preview contributors count in project detail GitHub block (default: `4`)
- `GITHUB_DASHBOARD_CONTRIBUTORS_LIMIT` — contributors count for non-paginated `/github` dashboard endpoint (default: `5`)
- `GITHUB_COMMITS_PAGE_SIZE` — per-page size used while syncing commits from GitHub API (default: `100`)
- `GITHUB_DEFAULT_PAGE_SIZE` — default page size for GitHub paginated APIs (default: `10`)
- `GITHUB_MAX_PAGE_SIZE` — max allowed page size for GitHub paginated APIs (default: `100`)
- `GITHUB_INSTALLATION_REPOSITORIES_DEFAULT_PAGE_SIZE` — default page size for installation repositories listing (`GET /api/supervisor/projects/{projectId}/github/installations/{installationId}/repositories`) when client does not provide `size` (default: `100`)
- `GITHUB_INSTALLATION_REPOSITORIES_MAX_PAGE_SIZE` — max allowed `size` for installation repositories listing; request values above this are capped (default: `100`)
- `GITHUB_ACCESS_REQUEST_EXPIRES_IN_MINUTES` — expiry for project-scoped GitHub access-request tokens used by `/api/supervisor/projects/{projectId}/github/access-requests*` flow (default: `15`)

Setup for local development:

1. Create your local env file: `cp .env.example .env`
2. Use these local defaults (or confirm your `.env` matches them):

```
APP_PORT=8081
CORS_ALLOWED_ORIGINS=http://localhost:5173
COOKIE_SECURE=false
GITHUB_DEFAULT_BRANCH=main
GITHUB_ACTIVITY_ACTIVE_WINDOW_HOURS=48
GITHUB_PREVIEW_COMMITS_LIMIT=6
GITHUB_PREVIEW_CONTRIBUTORS_LIMIT=4
GITHUB_DASHBOARD_CONTRIBUTORS_LIMIT=5
GITHUB_COMMITS_PAGE_SIZE=100
GITHUB_DEFAULT_PAGE_SIZE=10
GITHUB_MAX_PAGE_SIZE=100
GITHUB_APP_INSTALL_URL=https://github.com/apps/<your-app-slug>/installations/new
GITHUB_INSTALLATION_REPOSITORIES_DEFAULT_PAGE_SIZE=100
GITHUB_INSTALLATION_REPOSITORIES_MAX_PAGE_SIZE=100
GITHUB_ACCESS_REQUEST_EXPIRES_IN_MINUTES=15
```

3. Keep hostnames consistent across FE/BE in local dev:
- Frontend: `http://localhost:5173`
- Backend API: `http://localhost:8081`
- Avoid mixing `localhost` and `127.0.0.1` across these values.

4. Run backend:
   `./mvnw spring-boot:run`

`.env` is auto-loaded by Spring via `spring.config.import`.

## Flyway Migrations

- Flyway is enabled in `src/main/resources/application.yaml`.
- Migration scripts are in `src/main/resources/db/migration`.
- On each backend start, Flyway checks the schema history table and applies only pending versions.
- `V1__init_schema.sql` creates the base tables for `users`, `projects`, and `project_members`.
- `V2__add_refresh_tokens.sql` adds the `refresh_tokens` table for httpOnly cookie session management.
- Default safety: `baseline-on-migrate` is disabled.
- Dev-only fallback exists in `application-dev.yaml` if you need one-time baseline for a legacy/local DB.
  - Run with dev profile only when required:
    `SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run`

## Verify Standard (Local)

Before commit/PR, run:

- `./mvnw -q test`

If the project has no tests yet, `./mvnw test` is still acceptable and should compile and execute the test phase.

## Contributing / Workflow

For branching rules, PR expectations, and local verification steps, see `CONTRIBUTING.md`.

## Future: CI

CI is intentionally not configured yet. In a later phase, automated pipelines will enforce the same local checks (build/test/verification) currently documented for developers.
