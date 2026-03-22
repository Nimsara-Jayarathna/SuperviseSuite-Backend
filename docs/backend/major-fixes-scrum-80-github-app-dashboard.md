# Backend Major Fixes: SCRUM-80 GitHub App + DB-Backed Dashboard

Branch: `feat/SCRUM-80-display-github-activity`  
Compared against: `dev`  
Commit range: `43ff515` -> `5952bec`

## Fix 1: DB-Backed GitHub Domain for Project Repositories

### Why this fix was needed

- GitHub preview/activity data was previously request-time derived from repository URL only.
- The project detail page needed stable, cacheable data and pagination without direct frontend-to-GitHub coupling.
- The model had to be future-safe for multiple repositories while still supporting one repository in current UX.

### What was changed

- Added migration `V5__project_github_cache.sql` with repository/cache tables:
  - `project_repositories`
  - `project_repository_commits`
  - `project_repository_contributors`
- Added entities/repositories for the above domain.
- Added synchronization rules in `ProjectServiceImpl`:
  - refresh persists repository metadata, commits, contributor snapshot
  - refresh updates `last_synced_at`, `sync_status`, `last_sync_error`
  - duplicate-safe snapshot replacement for commits and contributors
- Added project-aware preview contract:
  - `ProjectGitHubPreviewDto` embedded in project detail payloads
  - top-4 contributor preview
  - small recent commit preview
  - status derivation (`active` within 48 hours, else `idle`)

## Fix 2: GitHub App Installation Integration

### Why this fix was needed

- PAT-only/public-link behavior was insufficient for private repositories and secure scoped access.
- Setup flow needed installation tracking and repository-installation linkage.

### What was changed

- Added migration `V6__github_app_installations.sql`.
- Added GitHub App installation entity/repository:
  - `GitHubAppInstallation`
  - `GitHubAppInstallationRepository`
- Added app-auth service abstraction:
  - `GitHubAppAuthService`
  - `GitHubAppAuthServiceImpl`
  - supports app JWT generation and installation-token flow
- Added setup/webhook integration service:
  - `GitHubAppIntegrationService`
  - setup callback links installation to project repository
  - webhook verifies signature and handles installation lifecycle events

## Fix 3: Setup Callback UX + Redirect Safety

### Why this fix was needed

- GitHub setup callback previously left the browser on API URL.
- Setup had to decode state context, complete linkage, and redirect users to frontend project view.

### What was changed

- `GET /api/github/setup` now:
  - requires `installation_id` + `state`
  - decodes base64/base64url `state` JSON (`projectId`, `repositoryUrl`)
  - processes setup via `GitHubAppIntegrationService`
  - redirects with HTTP `303 See Other` to frontend
- Redirect targets:
  - success: `/supervisor/projects/{projectId}?tab=github&githubSetup=success`
  - failure: `/supervisor/projects/{projectId}?tab=overview&githubSetup=failed`
- Uses `FRONTEND_BASE_URL` via `FrontendProperties`.

## Fix 4: Unified Role APIs for GitHub Dashboard + Pagination

### Why this fix was needed

- Student and supervisor needed the same read-only dashboard payload shape.
- Modal views required paginated DB-backed endpoints.

### What was changed

- Added/standardized role endpoints:
  - Supervisor:
    - `GET /api/supervisor/projects/{projectId}/github`
    - `GET /api/supervisor/projects/{projectId}/github/activity?page=&size=`
    - `GET /api/supervisor/projects/{projectId}/github/contributors?page=&size=`
    - `POST /api/supervisor/projects/{projectId}/github/refresh`
  - Student:
    - `GET /api/student/projects/{projectId}/github`
    - `GET /api/student/projects/{projectId}/github/activity?page=&size=`
    - `GET /api/student/projects/{projectId}/github/contributors?page=&size=`
- Main project detail responses now include `github` preview block for both roles:
  - `SupervisorProjectDetailDto.github`
  - `StudentProjectDetailDto.github`
- Pagination normalization is backend-configurable:
  - default: `GITHUB_DEFAULT_PAGE_SIZE`
  - cap: `GITHUB_MAX_PAGE_SIZE`

## Fix 5: Linking Integrity and Strict Linkage Rules

### Why this fix was needed

- Partial setup states were possible (installation row exists, project repo not fully linked).
- Manual URL and app-link modes needed deterministic behavior to avoid stale/mixed linkage.

### What was changed

- Setup flow now enforces repository URL resolution for project linkage:
  - state URL -> project URL -> existing repository cache URL
  - setup fails if URL cannot be resolved
- Installation linkage is asserted after setup before success continuation.
- Repository update/removal path and app linkage were aligned around a single active repository linkage per project.

## Post-Branch Updates (2026-03-22): Project-Scoped Authorization + Explicit Repo Selection

### Why this follow-up was needed

- Callback-time auto-linking based only on setup state was too early and brittle.
- One installation needed to be reusable across multiple projects with explicit per-project repository selection.
- Supervisors needed a secure "request more repository access" flow with single-use tokens.

### What was changed

- Setup callback behavior was refined:
  - callback stores installation + project authorization scope
  - callback no longer auto-links repository to project
  - repository linkage is now explicit via supervisor endpoint
- Added project authorization scope table:
  - `project_github_installation_authorizations` (`V7`)
- Added access-request token flow:
  - `project_github_access_requests` (`V8`) + result-token fields (`V9`)
  - hashed token/state persistence with expiry and single-use lifecycle
  - public callback helper endpoints:
    - `GET /api/github/access-requests/validate`
    - `POST /api/github/access-requests/continue`
    - `GET /api/github/access-updated/summary`
    - `POST /api/github/access-updated/acknowledge`
- Added explicit supervisor repository-link endpoint:
  - `POST /api/supervisor/projects/{projectId}/github/link`
- Added paginated installation repository endpoint:
  - `GET /api/supervisor/projects/{projectId}/github/installations/{installationId}/repositories?page=&size=`
  - backed by configurable page size defaults/caps
- Added scheduled maintenance jobs:
  - expired access-request cleanup (fixed delay)
  - linked repository refresh (cron + zone + batch size)

## Post-Branch Updates (2026-03-23): Supervisor Setup-Start Redirect Endpoint

### Why this follow-up was needed

- Frontend needed a stable "Connect GitHub App" entrypoint without building GitHub install URL/state in browser env.
- Setup URL/state generation should be owned by backend for consistency with callback and access-request flows.

### What was changed

- Added supervisor-authenticated setup-start endpoint:
  - `GET /api/supervisor/projects/{projectId}/github/setup/start`
  - validates supervisor ownership
  - builds project-aware `state` payload server-side
  - returns `303 See Other` to `GITHUB_APP_INSTALL_URL` + `state`
- Extended service layer contract:
  - `SupervisorService.buildGitHubSetupStartUrl(...)`
  - `GitHubAppIntegrationService.buildProjectSetupAuthorizeUrl(...)`
- Consolidated install URL validation in integration service with shared helper (`requireGitHubAppInstallUrl()`).

## Changed Files (`dev...HEAD`)

- `.env.example`
- `README.md`
- `src/main/java/com/supervisesuite/backend/config/FrontendProperties.java`
- `src/main/java/com/supervisesuite/backend/config/GitHubProperties.java`
- `src/main/java/com/supervisesuite/backend/config/SecurityConfig.java`
- `src/main/java/com/supervisesuite/backend/projects/controller/GitHubAppController.java`
- `src/main/java/com/supervisesuite/backend/projects/dto/GitHubWebhookResultDto.java`
- `src/main/java/com/supervisesuite/backend/projects/dto/ProjectGitHubDashboardDto.java`
- `src/main/java/com/supervisesuite/backend/projects/dto/ProjectGitHubPageDto.java`
- `src/main/java/com/supervisesuite/backend/projects/dto/ProjectGitHubPreviewDto.java`
- `src/main/java/com/supervisesuite/backend/projects/entity/GitHubAppInstallation.java`
- `src/main/java/com/supervisesuite/backend/projects/entity/ProjectRepository.java`
- `src/main/java/com/supervisesuite/backend/projects/entity/ProjectRepositoryCommit.java`
- `src/main/java/com/supervisesuite/backend/projects/entity/ProjectRepositoryContributor.java`
- `src/main/java/com/supervisesuite/backend/projects/integration/github/GitHubAppAuthService.java`
- `src/main/java/com/supervisesuite/backend/projects/integration/github/GitHubAppAuthServiceImpl.java`
- `src/main/java/com/supervisesuite/backend/projects/integration/github/GitHubCommitClient.java`
- `src/main/java/com/supervisesuite/backend/projects/integration/github/GitHubCommitClientImpl.java`
- `src/main/java/com/supervisesuite/backend/projects/repository/GitHubAppInstallationRepository.java`
- `src/main/java/com/supervisesuite/backend/projects/repository/ProjectRepositoryCacheRepository.java`
- `src/main/java/com/supervisesuite/backend/projects/repository/ProjectRepositoryCommitRepository.java`
- `src/main/java/com/supervisesuite/backend/projects/repository/ProjectRepositoryContributorRepository.java`
- `src/main/java/com/supervisesuite/backend/projects/service/GitHubAppIntegrationService.java`
- `src/main/java/com/supervisesuite/backend/projects/service/ProjectGitHubDashboardMapper.java`
- `src/main/java/com/supervisesuite/backend/projects/service/ProjectService.java`
- `src/main/java/com/supervisesuite/backend/projects/service/ProjectServiceImpl.java`
- `src/main/java/com/supervisesuite/backend/student/controller/StudentController.java`
- `src/main/java/com/supervisesuite/backend/student/dto/StudentProjectDetailDto.java`
- `src/main/java/com/supervisesuite/backend/student/service/StudentService.java`
- `src/main/java/com/supervisesuite/backend/student/service/StudentServiceImpl.java`
- `src/main/java/com/supervisesuite/backend/supervisor/controller/SupervisorController.java`
- `src/main/java/com/supervisesuite/backend/supervisor/dto/SupervisorProjectDetailDto.java`
- `src/main/java/com/supervisesuite/backend/supervisor/service/SupervisorService.java`
- `src/main/java/com/supervisesuite/backend/supervisor/service/SupervisorServiceImpl.java`
- `src/main/resources/application.yaml`
- `src/main/resources/db/migration/V5__project_github_cache.sql`
- `src/main/resources/db/migration/V6__github_app_installations.sql`
