# Backend Recent Changes (2026-04-08)

## 1) Jira Health and Refresh Endpoints

Added/verified supervisor and student Jira endpoints used by project detail Jira views.

- Supervisor:
  - `GET /api/supervisor/projects/{projectId}/jira/auth-url`
  - `POST /api/supervisor/jira/oauth/complete`
  - `POST /api/supervisor/projects/{projectId}/jira/disconnect`
  - `GET /api/supervisor/projects/{projectId}/jira/health`
  - `POST /api/supervisor/projects/{projectId}/jira/refresh`
- Student:
  - `GET /api/student/projects/{projectId}/jira/health`

Health and refresh endpoints return `JiraHealthDto`:

- `completionPercent`
- `openIssues`
- `overdueIssues`
- `highPriorityOpen`
- `statusBreakdown` (`toDo`, `inProgress`, `done`)
- `typeDistribution[]` (`type`, `count`)
- `bugRatio`
- `lastSyncedAt`

---

## 2) Jira Health Aggregation Refactor (SOLID)

Refactored Jira data-showing path to separate policy and mapping concerns.

- Added classifier abstraction:
  - `JiraHealthClassifier`
  - `DefaultJiraHealthClassifier`
- Added issue mapping abstraction:
  - `JiraIssueMapper`
  - `DefaultJiraIssueMapper`
- Updated services:
  - `JiraHealthServiceImpl` now delegates classification rules to classifier
  - `JiraIssueSyncServiceImpl` now delegates DTO -> entity mapping to mapper

Result:

- improved single-responsibility boundaries
- easier targeted unit testing for classification/mapping logic
- lower risk when adjusting Jira status/priority/type rules

---

## 3) Jira Backend Test Coverage Expansion

Added/updated tests for controller, service, and Jira-domain logic.

- Controller unit tests:
  - `SupervisorControllerTest`
  - `StudentControllerTest`
- Service unit tests:
  - `SupervisorServiceImplUnitTest`
  - `StudentServiceImplTest`
- Jira-domain unit tests:
  - `JiraHealthServiceImplTest`
  - `DefaultJiraIssueMapperTest`
  - `DefaultJiraHealthClassifierTest`
- Endpoint integration tests:
  - `SupervisorProjectControllerTest`
  - `StudentJiraControllerIntegrationTest`

Full backend verification run passed with no failures.

---

## 4) Database and Migration Notes

No schema changes were required for this slice.

- No new Flyway migration added.
- Existing Jira tables/migrations remain unchanged:
  - `project_jira_integrations`
  - `project_jira_oauth_states`
  - `project_jira_issues`
