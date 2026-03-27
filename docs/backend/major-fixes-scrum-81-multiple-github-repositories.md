# Backend Major Fixes: SCRUM-81 Multiple GitHub Repositories

Branch: `feat/SCRUM-81-multiple-github-repositories`  
Compared against: `dev`  
Commit range: `57bb73e` -> `a5d59ef`  
Merge reference: `b27152a` (PR #22 into `dev`)

## Fix 1: GitHub Integration V2 Domain Model and Services

### Why this was needed

- V1 linkage model was too rigid for repeated authorization and controlled project-level linking.
- Project workflows required explicit and auditable authorization/link boundaries.

### What changed

- Added/updated v2 entities and repositories:
  - `GitHubAccessSource`
  - `GitHubAccessRequestV2`
  - `ProjectRepositoryLink`
  - `ProjectRepositoryLinkCommit`
  - `ProjectRepositoryLinkContributor`
  - related repositories for access source, access request, repository cache/linkage
- Added v2 service layer under `projects/service/githubv2`:
  - `AccessSourceService`
  - `AccessRequestService`
  - `RepositoryLinkService`
  - `SetupCallbackService`
  - `GitHubSyncService`
  - `WebhookService`
  - `GitHubIntegrationGuardService`

## Fix 2: Supervisor APIs for Setup-Start, Installation Repositories, and Explicit Link

### Why this was needed

- Frontend should not generate GitHub setup/install URL state.
- Supervisor flow needs explicit repository selection after installation authorization.

### What changed

- Added/updated supervisor/project endpoints:
  - `GET /api/supervisor/projects/{projectId}/github/setup/start`
  - `GET /api/supervisor/projects/{projectId}/github/installations/{installationId}/repositories`
  - `POST /api/supervisor/projects/{projectId}/github/link`
  - `POST /api/supervisor/projects/{projectId}/github/access/remove`
  - `POST /api/supervisor/projects/{projectId}/github/access-requests`
  - `GET /api/supervisor/projects/{projectId}/github/access-requests/validate`
  - `POST /api/supervisor/projects/{projectId}/github/access-requests/continue`
- Added/updated supporting DTOs for repository options, link payloads, access metadata, and callback summaries.

## Fix 3: Public Callback Helpers for Request-Access Continuation

### Why this was needed

- GitHub app callback updates must be completed through secure tokenized continuation paths.

### What changed

- Added public endpoints:
  - `GET /api/github/access-requests/validate`
  - `POST /api/github/access-requests/continue`
  - `GET /api/github/access-updated/summary`
  - `POST /api/github/access-updated/acknowledge`
- Added token lifecycle and scheduled cleanup support for request-access tokens.

## Fix 4: Database Migrations for V2 Enablement and V1 Decommissioning

### What changed

- Added migrations:
  - `V10__github_integration_v2.sql`
  - `V11__github_repository_enablement_limits.sql`
  - `V12__decommission_v1_github_integration.sql`
  - `V13__denormalized_repository_link_fields.sql`
  - `V14__add_updated_at_to_access_sources.sql`
  - `V15__align_github_access_request_v2_with_result_tracking.sql`
- Updated runtime/config properties for installation repository paging and access-request maintenance jobs.

### Migration details

- `V10__github_integration_v2.sql`
  - Introduces new v2 tables:
    - `github_access_sources`
    - `github_repositories`
    - `project_repository_links`
    - `github_setup_states`
    - `github_access_requests_v2`
    - `project_repository_link_commits`
    - `project_repository_link_contributors`
  - Added unique constraints and indexes for token safety, link uniqueness, and project/repository query performance.
- `V11__github_repository_enablement_limits.sql`
  - Added `is_enabled` to `project_repository_links`.
  - Backfilled existing rows to enabled, enforced NOT NULL/default.
  - Added index and partial unique index to guarantee one enabled primary link per project.
- `V12__decommission_v1_github_integration.sql`
  - Removed legacy `projects.repository_url`.
  - Dropped v1 cache tables:
    - `project_repository_contributors`
    - `project_repository_commits`
    - `project_repositories`
- `V13__denormalized_repository_link_fields.sql`
  - Added denormalized fields to `project_repository_links`:
    - `github_installation_id`, `repository_url`, `repository_name`, `default_branch`, `linked_by_supervisor_user_id`, `access_type`
  - Backfilled from `github_repositories` and `github_access_sources`.
  - Enforced NOT NULL for `repository_url`, `repository_name`, `access_type`.
- `V14__add_updated_at_to_access_sources.sql`
  - Added `updated_at` to `github_access_sources`.
  - Backfilled `updated_at = created_at` for existing rows.
- `V15__align_github_access_request_v2_with_result_tracking.sql`
  - Added result-tracking fields to `github_access_requests_v2`:
    - `result_token_hash`, `result_expires_at`, `result_acknowledged_at`, `installation_id`
  - Added index on `result_token_hash`.

## Fix 5: Student/Supervisor Detail Payload Alignment and Sync Behavior

### What changed

- Updated student/supervisor detail service/controller flows to expose repository-link-aware GitHub preview data.
- Updated GitHub client adapters and sync pipeline for repository-link scoped refresh and read behavior.
- Added tests around controller/service/scheduler paths impacted by v2 flow.

## Changed Files (`dev..HEAD`)

The branch touches configuration, controllers, DTOs, entities, repositories, services, migrations, and tests across GitHub integration, supervisor/student APIs, and project detail flows.

Key areas:

- Config and security:
  - `.env.example`
  - `config/GitHubProperties.java`
  - `config/SecurityConfig.java`
- Controllers:
  - `projects/controller/GitHubAppController.java`
  - `projects/controller/GitHubAccessSourceController.java`
  - `projects/controller/ProjectController.java`
  - `supervisor/controller/SupervisorController.java`
  - `student/controller/StudentController.java`
- Services:
  - `projects/service/ProjectServiceImpl.java`
  - `projects/service/GitHubAppIntegrationService.java`
  - `projects/service/githubv2/*.java`
  - `supervisor/service/SupervisorServiceImpl.java`
  - `student/service/StudentServiceImpl.java`
- DB migrations:
  - `src/main/resources/db/migration/V10__github_integration_v2.sql`
  - `src/main/resources/db/migration/V11__github_repository_enablement_limits.sql`
  - `src/main/resources/db/migration/V12__decommission_v1_github_integration.sql`
  - `src/main/resources/db/migration/V13__denormalized_repository_link_fields.sql`
  - `src/main/resources/db/migration/V14__add_updated_at_to_access_sources.sql`
  - `src/main/resources/db/migration/V15__align_github_access_request_v2_with_result_tracking.sql`
