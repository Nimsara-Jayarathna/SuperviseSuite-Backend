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
  - `id`, `title`, `summary`, `lifecycleStatus`, `batch`, `semester`, `milestoneDate`, `progressPercent`, `healthNote`, `lastActivityAt`
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

Creates project + memberships + initial milestone in one transaction.

### Request fields

- `title` (required)
- `summary` (required)
- `batch` (required)
- `semester` (required)
- `studentIds[]` (required, non-empty, unique)
- `milestone`:
  - `title` (required)
  - `description` (optional)
  - `dueDate` (required)

### Backend defaults

- `lifecycleStatus = PLANNING`
- `progressPercent = 0`
- `healthNote = null`
- first milestone:
  - `status = PLANNED`
  - `sequenceNo = 1`
- project `milestoneDate = initial milestone dueDate`

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
- Updates project `updatedAt` and `lastActivityAt`.
- Returns refreshed project detail payload.

---

## Validation and Errors

Common supervisor mutation failures:

- malformed UUID path parameters -> `404 NOT_FOUND`
- project not owned by authenticated supervisor -> `404 NOT_FOUND`
- invalid enum values (`lifecycleStatus`, milestone `status`) -> `400 VALIDATION_ERROR`
- duplicate/invalid student assignment payloads -> `400 VALIDATION_ERROR`
