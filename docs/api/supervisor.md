# Supervisor API

Supervisor-only endpoints for dashboard, project list/detail reads, student lookup, and project management actions.

All endpoints in this document:

- require authentication
- require the authenticated user to have role `SUPERVISOR`
- return `ApiResponse<T>` on success
- return `ApiError` on failure

## Endpoints

- `GET /api/supervisor/dashboard`
- `GET /api/supervisor/projects`
- `GET /api/supervisor/projects/{projectId}`
- `GET /api/supervisor/students/search?q=...`
- `POST /api/supervisor/projects`
- `PATCH /api/supervisor/projects/{projectId}`
- `PATCH /api/supervisor/projects/{projectId}/status`
- `PATCH /api/supervisor/projects/{projectId}/repository`
- `GET /api/supervisor/projects/{projectId}/github`
- `GET /api/supervisor/projects/{projectId}/github/activity?page=...&size=...`
- `GET /api/supervisor/projects/{projectId}/github/contributors?page=...&size=...`
- `GET /api/supervisor/projects/{projectId}/github/installations/{installationId}/repositories?page=...&size=...`
- `POST /api/supervisor/projects/{projectId}/github/access-requests`
- `GET /api/supervisor/projects/{projectId}/github/access-requests/validate?token=...`
- `POST /api/supervisor/projects/{projectId}/github/access-requests/continue?token=...`
- `POST /api/supervisor/projects/{projectId}/github/link`
- `POST /api/supervisor/projects/{projectId}/github/access/remove`
- `POST /api/supervisor/projects/{projectId}/github/refresh`
- `POST /api/supervisor/projects/{projectId}/members`
- `POST /api/supervisor/projects/{projectId}/milestones`
- `PATCH /api/supervisor/projects/{projectId}/milestones/{milestoneId}`

---

## GET /api/supervisor/dashboard

Returns dashboard aggregates and lightweight project records for `/supervisor/dashboard`.

### Response fields

- aggregate counts:
  - `totalProjects`
  - `planningProjects`
  - `activeProjects`
  - `atRiskProjects`
  - `behindProjects`
  - `completedProjects`
  - `upcomingMilestonesCount` (within next 14 days, inclusive)
- project collections:
  - `projects[]` (all supervisor-owned projects in summary form)
  - `recentProjects[]` (top 5 by `lastActivityAt`, fallback `createdAt`)

### Notes

- Includes only supervisor-owned, non-deleted projects.
- `projects[]` item fields:
  - `id`, `title`, `summary`, `lifecycleStatus`, `milestoneDate`, `lastActivityAt`, `progressPercent`, `healthNote`

---

## GET /api/supervisor/projects

Returns supervisor-owned project list records for `/supervisor/projects`.

### Response fields per item

- `id`
- `title`
- `summary`
- `lifecycleStatus`
- `batch`
- `semester`
- `milestoneDate`
- `progressPercent`
- `healthNote`
- `memberCount`

### Notes

- Excludes soft-deleted projects.
- Empty set returns `200` with `data: []`.

---

## GET /api/supervisor/projects/{projectId}

Returns one supervisor-owned project detail record.

### Response fields

- core fields:
  - `id`, `title`, `summary`, `lifecycleStatus`, `batch`, `semester`, `milestoneDate`, `progressPercent`, `healthNote`, `repositoryUrl`, `lastActivityAt`
- GitHub preview block:
  - `github.repositoryLinked`
  - `github.authorizedInstallationId`
  - `github.accessibleRepositoryCount`
  - `github.accessScope`
  - `github.repositories[]` (`id`, `name`, `url`, `defaultBranch`, `lastSyncedAt`)
  - `github.activitySummary` (`totalCommits`, `lastActivityAt`, `status`)
  - `github.contributorsPreview[]` (top 4)
  - `github.recentCommitsPreview[]` (small preview list)
- `leader` (nullable):
  - `id`, `firstName`, `lastName`, `email`, `registrationNumber`
- `members[]`:
  - `id`, `firstName`, `lastName`, `email`, `registrationNumber`, `memberRole`
- `milestones[]`:
  - `id`, `title`, `description`, `dueDate`, `status`, `sequenceNo`

### 404 cases

- invalid UUID
- project not found
- project is soft-deleted
- project not owned by authenticated supervisor

---

## GET /api/supervisor/students/search?q=...

Searches registered users with role `STUDENT`.

### Rules

- Query length below 3 characters returns empty array.
- Current matching scope: email contains query (case-insensitive).
- Returns at most 10 records, ordered by email.

### Response item

- `id`
- `firstName`
- `lastName`
- `email`
- `registrationNumber`

---

## POST /api/supervisor/projects

Creates project + memberships + initial milestones in one transaction.

### Request fields

- `title` (required)
- `summary` (required)
- `batch` (required)
- `semester` (required)
- `studentIds[]` (required, non-empty, unique)
- `leaderStudentId` (optional, nullable)
  - when present, must be one of `studentIds[]`
- `milestones[]` (required, non-empty)
  - each milestone:
  - `title` (required)
  - `description` (optional)
  - `dueDate` (required)

### Backend defaults

- `lifecycleStatus = PLANNING`
- `progressPercent = 0`
- `healthNote = null`
- each initial milestone:
  - `status = PLANNED`
  - `sequenceNo` starts at `1` and increments in request order
- project `milestoneDate = earliest dueDate among milestones[]`

### Response highlights

- Returns assigned students in `students[]`.
- Returns selected leader in `leader` (nullable).
- Returns all created milestones in `milestones[]`.

---

## PATCH /api/supervisor/projects/{projectId}

Updates core project fields used by overview edit.

### Request fields

- `title` (required)
- `summary` (required)
- `batch` (required)
- `semester` (required)
- `lifecycleStatus` (required, one of `PLANNING|ACTIVE|AT_RISK|BEHIND|COMPLETED`)
- `healthNote` (optional nullable string)
- `leaderStudentId` (optional nullable UUID)
  - when present, must refer to an already assigned project student member

### Behavior

- Updates `updatedAt` and `lastActivityAt`.
- Returns refreshed project detail payload.

---

## PATCH /api/supervisor/projects/{projectId}/status

Focused status update endpoint for quick header-level status changes.

### Request fields

- `lifecycleStatus` (required, one of `PLANNING|ACTIVE|AT_RISK|BEHIND|COMPLETED`)

### Behavior

- Updates only project lifecycle status (+ `updatedAt`, `lastActivityAt`).
- Returns refreshed project detail payload.

---

## PATCH /api/supervisor/projects/{projectId}/repository

Adds, updates, or clears a project's GitHub repository URL.

### Request fields

- `repositoryUrl` (nullable)
  - `null` clears the linked repository URL
  - otherwise must match: `^https://github\.com/[a-zA-Z0-9_-]+/[a-zA-Z0-9_-]+$`
  - max length: `500`

### Validation messages

- `Repository URL must be a valid GitHub repository URL (e.g., https://github.com/owner/repo)`
- `Repository URL must not exceed 500 characters`

### Example request

```http
PATCH /api/supervisor/projects/{projectId}/repository
Content-Type: application/json

{
  "repositoryUrl": "https://github.com/team/project"
}
```

To remove:

```http
PATCH /api/supervisor/projects/{projectId}/repository
Content-Type: application/json

{
  "repositoryUrl": null
}
```

### Behavior

- Only supervisor-owned, non-deleted projects can be updated.
- Not found, not owned, deleted, and malformed UUID all return `404 NOT_FOUND`.
- Updates `updatedAt` and `lastActivityAt`.
- Returns refreshed project detail payload, including `repositoryUrl`.
- In SCRUM-80 flow, this endpoint is also the manual-link control path used by the Overview "Link repository" modal.

---

## GET /api/supervisor/projects/{projectId}/github/installations/{installationId}/repositories?page=...&size=...

Returns paginated repositories currently accessible under a GitHub App installation authorized for this project.

### Query params

- `page` (1-based, default `1`, must be > 0)
- `size` (optional; default and cap are resolved from backend config)

### Response fields

- `items[]`:
  - `repositoryId`, `name`, `fullName`, `url`, `ownerLogin`, `defaultBranch`
- `page`, `size`, `returnedCount`, `totalCount`
- `hasNext`, `hasPrevious`, `nextPage`

### Rules

- project must belong to authenticated supervisor
- installation must be active/usable
- installation must be explicitly authorized for this project

---

## POST /api/supervisor/projects/{projectId}/github/access-requests

Creates a short-lived project-scoped access-request link/token used before redirecting to GitHub.

### Response fields

- `projectId`
- `requestToken`
- `requestUrl` (relative FE route such as `/github/request-access?token=...`)
- `expiresAt`

---

## GET /api/supervisor/projects/{projectId}/github/access-requests/validate?token=...

Supervisor-authenticated validation endpoint for project-scoped access-request token.

### Response fields

- `projectId`
- `projectTitle`
- `status` (`PENDING`)
- `expiresAt`

---

## POST /api/supervisor/projects/{projectId}/github/access-requests/continue?token=...

Supervisor-authenticated continuation endpoint that returns GitHub authorization/install URL.

### Response fields

- `projectId`
- `githubAuthorizeUrl`

---

## POST /api/supervisor/projects/{projectId}/github/link

Explicitly links one selected installation repository to this project.

### Request fields

- `installationId` (required, > 0)
- `repositoryId` (required, > 0)

### Behavior

- validates supervisor ownership and installation authorization
- validates repository is accessible under selected installation
- writes repository linkage into primary `project_repositories` row
- attempts refresh sync after linking
- returns linked repository details

---

## POST /api/supervisor/projects/{projectId}/github/access/remove

Removes project-scoped GitHub installation authorization and linked repository/cache data.

### Behavior

- clears `project_github_installation_authorizations` rows for project
- clears linked `project_repositories` + commit/contributor snapshots for project
- clears project `repositoryUrl`
- returns refreshed supervisor project detail payload

---

## GET /api/supervisor/projects/{projectId}/github

Returns read-only GitHub dashboard data for the project.

### Response fields

- `repositoryLinked`
- `repository`:
  - `name`, `url`, `defaultBranch`
- `activitySummary`:
  - `totalCommits`, `lastActivityAt`, `status` (`active|idle`)
- `contributors[]`:
  - `name`, `commitCount`
- `recentCommits[]`:
  - `sha`, `message`, `author`, `committedAt`

Notes:

- Data is served from DB-backed cache, not direct passthrough GitHub payload.
- If no repository is linked, `repositoryLinked = false` and lists are empty.

---

## GET /api/supervisor/projects/{projectId}/github/activity?page=...&size=...

Returns paginated activity rows for the "View full activity" modal.

### Query params

- `page` (1-based, default `1`)
- `size` (bounded by backend config defaults/max)

### Response fields

- `items[]`:
  - `sha`, `message`, `author`, `committedAt`
- `page`, `size`, `total`, `hasMore`

---

## GET /api/supervisor/projects/{projectId}/github/contributors?page=...&size=...

Returns paginated contributors for the "View all contributors" modal.

### Query params

- `page` (1-based, default `1`)
- `size` (bounded by backend config defaults/max)

### Response fields

- `items[]`:
  - `name`, `commitCount`
- `page`, `size`, `total`, `hasMore`

---

## POST /api/supervisor/projects/{projectId}/github/refresh

Triggers backend GitHub sync and updates DB cache for the linked repository.

### Behavior

- Validates project ownership and repository linkage.
- Fetches metadata/commits via backend GitHub integration (installation-aware when linked).
- Rebuilds commit + contributor snapshots.
- Updates repository sync fields (`lastSyncedAt`, `syncStatus`, `lastSyncError`).
- If GitHub installation authorization is removed/invalid, backend clears project linkage/cache and returns validation error.
- Returns `200 OK` with success message on completion.

### Status codes

- `200 OK` - GitHub repository data refreshed
- `400 BAD_REQUEST` - validation failure
- `401 UNAUTHORIZED` - not authenticated
- `403 FORBIDDEN` - authenticated but not supervisor role
- `404 NOT_FOUND` - project missing or not owned by authenticated supervisor

---

## POST /api/supervisor/projects/{projectId}/members

Adds one or more students to an existing project (add-only in current scope).

### Request fields

- `studentIds[]` (required, non-empty, unique)

### Rules

- All users must exist and have role `STUDENT`.
- Already assigned users are rejected (validation error).
- Supervisor membership remains unchanged.

### Behavior

- Creates new `project_members` rows with `memberRole = STUDENT`.
- Updates `updatedAt` and `lastActivityAt`.
- Returns refreshed project detail payload.

---

## POST /api/supervisor/projects/{projectId}/milestones

Adds a new project milestone.

### Request fields

- `title` (required)
- `description` (optional nullable)
- `dueDate` (required)

### Behavior

- `status` defaults to `PLANNED`.
- `sequenceNo` is auto-assigned as `(max existing sequenceNo + 1)`; starts at `1`.
- Updates project `milestoneDate` to the added milestone’s due date.
- Recalculates and persists project `progressPercent` using milestone statuses.
- Updates `updatedAt` and `lastActivityAt`.
- Returns refreshed project detail payload.

---

## PATCH /api/supervisor/projects/{projectId}/milestones/{milestoneId}

Updates one milestone.

### Request fields

- `title` (required)
- `description` (optional nullable)
- `dueDate` (required)
- `status` (required: `PLANNED|IN_PROGRESS|COMPLETED|MISSED|CANCELLED`)

### Behavior

- Updates milestone `updatedAt`.
- Recalculates and persists project `progressPercent` using milestone statuses.
- Updates project `updatedAt` and `lastActivityAt`.
- Returns refreshed project detail payload.

---

## Validation and Errors

Common supervisor mutation failures:

- malformed UUID path parameters -> `404 NOT_FOUND`
- project not owned by authenticated supervisor -> `404 NOT_FOUND`
- invalid enum values (`lifecycleStatus`, milestone `status`) -> `400 VALIDATION_ERROR`
- duplicate/invalid student assignment payloads -> `400 VALIDATION_ERROR`
