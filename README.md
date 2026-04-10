# SuperviseSuite-Backend

Core SuperviseSuite backend built with Spring Boot. Provides REST APIs for authentication/authorization, user and project management, and project membership/assignment workflows. Owns the main database model and supports future expansion for analytics, reporting, and external tool connectivity.

Current scope includes GitHub integration v2 (SCRUM-81) and Jira integration: project-scoped authorization, explicit repository linking, request-access continuation flow, callback-result acknowledgement, repository-link based sync/preview APIs, and Jira OAuth + cached project analytics endpoints (health, sprint progress, team workload, hierarchy view, refresh).

## API Documentation

Current API references:

- `docs/api/auth.md`
- `docs/api/supervisor.md`
- `docs/api/student.md`
- `docs/api/github-app.md`
- `docs/api-response-contract.md`

Backend fix documents:

- `docs/major-fixes-scrum-97-supervisor-workflow.md`
- `docs/major-fixes-scrum-80-github-app-dashboard.md`
- `docs/major-fixes-scrum-81-multiple-github-repositories.md`
- `docs/major-fixes-scrum-83-us-203-view-sprint-progress-dashboard.md`
- `docs/major-fixes-scrum-84-us-204-sprint-progress-velocity.md`
- `docs/recent-changes-2026-03-05.md`
- `docs/recent-changes-2026-04-08.md`
- `docs/recent-changes-2026-04-09.md`

Database documentation:

- `docs/database/README.md`
- `docs/database/migrations.md`
- `docs/database/schema-v1.md`

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
- `GITHUB_APP_INSTALL_URL` — GitHub App installation URL used by backend for setup-start redirects (`GET /api/supervisor/projects/{projectId}/github/setup/start`) and access-request continuation flows
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
- `GITHUB_ACCESS_REQUEST_CLEANUP_ENABLED` — enables repeating cleanup job for expired access-request tokens (default: `true`)
- `GITHUB_ACCESS_REQUEST_CLEANUP_INITIAL_DELAY_MS` — delay before first cleanup run in milliseconds (default: `120000`)
- `GITHUB_ACCESS_REQUEST_CLEANUP_FIXED_DELAY_MS` — fixed delay between cleanup runs in milliseconds (default: `900000`)
- `GITHUB_REPOSITORY_REFRESH_JOB_ENABLED` — enables cron-based scheduled refresh for linked GitHub repositories (default: `true`)
- `GITHUB_REPOSITORY_REFRESH_CRON` — cron expression for scheduled refresh time (default: `0 0 0 * * *`; set `0 0 12 * * *` for daily 12:00)
- `GITHUB_REPOSITORY_REFRESH_ZONE` — timezone for refresh cron evaluation (default: `UTC`)
- `GITHUB_REPOSITORY_REFRESH_BATCH_SIZE` — max linked repositories refreshed per cron run (default: `50`)
- `GITHUB_SYNC_MAX_COMMIT_PAGES` — cap for GitHub commit pagination during sync (default: `5`)
- `ATLASSIAN_CLIENT_ID` — Atlassian OAuth client id
- `ATLASSIAN_CLIENT_SECRET` — Atlassian OAuth client secret
- `ATLASSIAN_REDIRECT_URI` — OAuth redirect URI registered in Atlassian developer console (used by `/api/supervisor/jira/oauth/complete`)
- `ATLASSIAN_SCOPE` — Jira scopes requested during OAuth (default: `read:jira-user read:jira-work`)
- `ATLASSIAN_AUDIENCE` — OAuth audience for Atlassian API token exchange (default: `api.atlassian.com`)
- `ATLASSIAN_AUTH_TARGET_URL` — Atlassian authorize endpoint (default: `https://auth.atlassian.com/authorize`)
- `ATLASSIAN_TOKEN_TARGET_URL` — Atlassian token endpoint (default: `https://auth.atlassian.com/oauth/token`)
- `ATLASSIAN_OAUTH_STATE` — state key prefix for Jira OAuth flow (default: `supervisesuite_jira_state`)
- `ATLASSIAN_OAUTH_STATE_TTL_SECONDS` — OAuth state expiry in seconds (default: `900`)
- `ATLASSIAN_TOKEN_ENCRYPTION_SECRET` — secret used to encrypt stored Jira tokens (defaults to `JWT_SECRET` when not set)
- `ATLASSIAN_ANALYTICS_RECENT_SPRINTS_LIMIT` — number of recent sprints used in sprint analytics/trend calculations (default: `3`)
- `ATLASSIAN_ANALYTICS_BACKLOG_GROWING_CONSECUTIVE_WEEKS` — consecutive negative-net weeks required to flag backlog growth (default: `2`)
- `ATLASSIAN_ANALYTICS_HIGH_PRIORITY_NAMES` — comma-separated Jira priority names treated as high priority (default: `High,Highest`)
- `ATLASSIAN_ANALYTICS_BUG_TYPE_NAMES` — comma-separated Jira issue type names treated as bugs/defects (default: `Bug`)

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
GITHUB_ACCESS_REQUEST_CLEANUP_ENABLED=true
GITHUB_ACCESS_REQUEST_CLEANUP_INITIAL_DELAY_MS=120000
GITHUB_ACCESS_REQUEST_CLEANUP_FIXED_DELAY_MS=900000
GITHUB_REPOSITORY_REFRESH_JOB_ENABLED=true
GITHUB_REPOSITORY_REFRESH_CRON=0 0 0 * * *
GITHUB_REPOSITORY_REFRESH_ZONE=UTC
GITHUB_REPOSITORY_REFRESH_BATCH_SIZE=50
GITHUB_SYNC_MAX_COMMIT_PAGES=5
ATLASSIAN_CLIENT_ID=<your-atlassian-client-id>
ATLASSIAN_CLIENT_SECRET=<your-atlassian-client-secret>
ATLASSIAN_REDIRECT_URI=http://localhost:5173/jira/callback
ATLASSIAN_SCOPE=read:jira-user read:jira-work
ATLASSIAN_AUDIENCE=api.atlassian.com
ATLASSIAN_AUTH_TARGET_URL=https://auth.atlassian.com/authorize
ATLASSIAN_TOKEN_TARGET_URL=https://auth.atlassian.com/oauth/token
ATLASSIAN_OAUTH_STATE=supervisesuite_jira_state
ATLASSIAN_OAUTH_STATE_TTL_SECONDS=900
ATLASSIAN_TOKEN_ENCRYPTION_SECRET=<optional; defaults to JWT_SECRET>
ATLASSIAN_ANALYTICS_RECENT_SPRINTS_LIMIT=3
ATLASSIAN_ANALYTICS_BACKLOG_GROWING_CONSECUTIVE_WEEKS=2
ATLASSIAN_ANALYTICS_HIGH_PRIORITY_NAMES=High,Highest
ATLASSIAN_ANALYTICS_BUG_TYPE_NAMES=Bug
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
- `V2__auth_schema.sql` adds auth/profile fields on `users` and the `refresh_tokens` table for httpOnly cookie session management.
- SCRUM-81 GitHub integration v2 migrations:
  - `V10__github_integration_v2.sql`
    - introduces v2 tables for access sources, repositories, project links, setup states, access requests, and link-level commits/contributors
  - `V11__github_repository_enablement_limits.sql`
    - adds repository enable/disable state and enforces one enabled primary repository per project
  - `V12__decommission_v1_github_integration.sql`
    - removes legacy `projects.repository_url` and drops deprecated v1 repository cache tables
  - `V13__denormalized_repository_link_fields.sql`
    - adds denormalized repository metadata to `project_repository_links` for faster project-detail/dashboard reads
  - `V14__add_updated_at_to_access_sources.sql`
    - adds `updated_at` to `github_access_sources` and backfills existing rows
  - `V15__align_github_access_request_v2_with_result_tracking.sql`
    - adds result-token tracking fields used by access-request callback confirmation flow
- Database documentation for migration-by-migration context and effective schema:
  - `docs/database/migrations.md`
  - `docs/database/schema-v1.md`
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
