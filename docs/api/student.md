# Student API

Student-only endpoints for loading assigned project list and detail views.

All endpoints in this document:

- require authentication
- require the authenticated user to have role `STUDENT`
- return the standard `ApiResponse<T>` envelope on success
- return the shared `ApiError` shape on failure

## Endpoints

- [GET /api/student/projects](#get-apistudentprojects)
- [GET /api/student/projects/{projectId}](#get-apistudentprojectsprojectid)
- [GET /api/student/projects/{projectId}/github](#get-apistudentprojectsprojectidgithub)
- [GET /api/student/projects/{projectId}/github/activity?page=...&size=...](#get-apistudentprojectsprojectidgithubactivitypagesize)
- [GET /api/student/projects/{projectId}/github/contributors?page=...&size=...](#get-apistudentprojectsprojectidgithubcontributorspagesize)
- [GET /api/student/projects/{projectId}/jira/health](#get-apistudentprojectsprojectidjirahealth)
- [GET /api/student/projects/{projectId}/jira/sprint-progress](#get-apistudentprojectsprojectidjirasprint-progress)
- [GET /api/student/projects/{projectId}/jira/workload](#get-apistudentprojectsprojectidjiraworkload)
- [GET /api/student/projects/{projectId}/jira/hierarchy](#get-apistudentprojectsprojectidjirahierarchy)
- [GET /api/student/projects/{projectId}/meeting-channels](#get-apistudentprojectsprojectidmeeting-channels)
- [POST /api/student/projects/{projectId}/meeting-channels](#post-apistudentprojectsprojectidmeeting-channels)
- [GET /api/student/projects/{projectId}/meeting-records](#get-apistudentprojectsprojectidmeeting-records)
- [POST /api/student/projects/{projectId}/meeting-records](#post-apistudentprojectsprojectidmeeting-records)
- [GET /api/student/projects/{projectId}/files](#get-apistudentprojectsprojectidfiles)
- [POST /api/student/projects/{projectId}/files/upload-url](#post-apistudentprojectsprojectidfilesupload-url)
- [POST /api/student/projects/{projectId}/files/confirm](#post-apistudentprojectsprojectidfilesconfirm)
- [GET /api/student/projects/{projectId}/files/{fileId}/download-url](#get-apistudentprojectsprojectidfilesfileiddownload-url)

---

## GET /api/student/projects

Returns projects assigned to the authenticated student for the `/student/projects` list route.

### Response shape

Each item is a summary record used by the current student list UI:

- `id`
- `title`
- `summary`
- `status`
- `batch`
- `semester`
- `milestoneDate`
- `lastActivityAt`
- `progressPercent`
- `supervisorName`

### 200 OK

```json
{
  "success": true,
  "message": "Projects loaded.",
  "data": [
    {
      "id": "f14699be-6c09-4a86-83b8-a4c4fd7d457d",
      "title": "Smart Attendance Tracker",
      "summary": "AI-assisted attendance project",
      "status": "PLANNING",
      "batch": "2026",
      "semester": "Semester 1",
      "milestoneDate": "2026-03-21",
      "lastActivityAt": "2026-03-05T07:35:00Z",
      "progressPercent": 0,
      "supervisorName": "Dr. Fernando"
    }
  ],
  "error": null
}
```

### Notes

- Only projects where the student is assigned via `project_members` with `member_role = STUDENT` are returned.
- Soft-deleted projects (`deleted_at IS NOT NULL`) are excluded.
- Empty assignments return `200 OK` with `data: []`.

---

## GET /api/student/projects/{projectId}

Returns one project assigned to the authenticated student for the `/student/projects/:projectId` detail route.

### Path parameter

- `projectId` (UUID)

### Response shape

The detail payload currently includes backend-backed fields only:

- `id`
- `title`
- `summary`
- `status`
- `batch`
- `semester`
- `milestoneDate`
- `lastActivityAt`
- `progressPercent`
- `healthNote`
- `github`
- `jira`
- `members[]`
- `milestones[]`
- `files` (embedded file list/config seed for Files tab)

`members[]` item fields:

- `id`
- `firstName`
- `lastName`
- `email`
- `registrationNumber`
- `memberRole`

`milestones[]` item fields:

- `id`
- `title`
- `description`
- `dueDate`
- `status`
- `sequenceNo`

`github` preview fields:

- `repositoryLinked`
- `repositories[]` (`id`, `name`, `url`, `defaultBranch`, `lastSyncedAt`)
- `activitySummary` (`totalCommits`, `lastActivityAt`, `status`)
- `contributorsPreview[]` (top 4)
- `recentCommitsPreview[]` (preview list)

`jira` integration fields:

- `connected`
- `workspaceName`
- `workspaceUrl`

`files` fields:

- `items[]`
  - `id`
  - `fileName`
  - `fileType` (normalized extension, e.g. `pdf`, `docx`, `pptx`, `zip`)
  - `fileSize`
  - `uploadedBy`
  - `uploadedByName`
  - `uploadedByRole` (`SUPERVISOR` or `STUDENT`)
  - `createdAt`
  - `updatedAt`
- `config`
  - `maxFileSizeBytes`
  - `maxFileNameLength`
  - `allowedTypes[]`
  - `presignedUrlExpirySeconds`

### 200 OK

```json
{
  "success": true,
  "message": "Project loaded.",
  "data": {
    "id": "f14699be-6c09-4a86-83b8-a4c4fd7d457d",
    "title": "Smart Attendance Tracker",
    "summary": "AI-assisted attendance project",
    "status": "PLANNING",
    "batch": "2026",
    "semester": "Semester 1",
    "milestoneDate": "2026-03-21",
    "lastActivityAt": "2026-03-05T07:35:00Z",
    "progressPercent": 0,
    "healthNote": null,
    "members": [
      {
        "id": "48dc174f-5957-498f-a10a-e0b5c7cebc28",
        "firstName": "Nimsara",
        "lastName": "Jayarathna",
        "email": "nimsara@example.com",
        "registrationNumber": "IT24100400",
        "memberRole": "STUDENT"
      }
    ],
    "milestones": [
      {
        "id": "670e4f03-39cc-4280-a8ee-64f003ef4ee0",
        "title": "Proposal Submission",
        "description": "Initial proposal review",
        "dueDate": "2026-03-21",
        "status": "PLANNED",
        "sequenceNo": 1
      }
    ]
  },
  "error": null
}
```

### 404 Not Found

Returns `NOT_FOUND` when:

- `projectId` is not a valid UUID
- the project does not exist
- the project is soft-deleted
- the authenticated student is not assigned to the project as `member_role = STUDENT`

---

## GET /api/student/projects/{projectId}/github

Returns read-only GitHub dashboard data for the student project detail GitHub tab.

### Response fields

- `repositoryLinked`
- `repository` (`name`, `url`, `defaultBranch`)
- `activitySummary` (`totalCommits`, `lastActivityAt`, `status`)
- `contributors[]` (`name`, `commitCount`)
- `recentCommits[]` (`sha`, `message`, `author`, `committedAt`)

Notes:

- Served from backend DB cache.
- No student-side mutation actions are exposed.

---

## GET /api/student/projects/{projectId}/github/activity?page=...&size=...

Returns paginated activity rows for the activity modal.

### Query params

- `page` (1-based, default `1`)
- `size` (bounded by backend config defaults/max)

### Response fields

- `items[]` (`sha`, `message`, `author`, `committedAt`)
- `page`, `size`, `total`, `hasMore`

---

## GET /api/student/projects/{projectId}/github/contributors?page=...&size=...

Returns paginated contributors for the contributors modal.

### Query params

- `page` (1-based, default `1`)
- `size` (bounded by backend config defaults/max)

### Response fields

- `items[]` (`name`, `commitCount`)
- `page`, `size`, `total`, `hasMore`

---

## GET /api/student/projects/{projectId}/jira/health

Returns read-only Jira health overview for the student Jira tab.

### Response fields

- `completionPercent`
- `openIssues`
- `overdueIssues`
- `highPriorityOpen`
- `statusBreakdown`:
  - `toDo`, `inProgress`, `done`
- `typeDistribution[]`:
  - `type`, `count`
- `bugRatio`
- `lastSyncedAt` (nullable)

### Rules

- student must be assigned to project with `member_role = STUDENT`
- endpoint is read-only for student role
- response is computed from backend Jira cache data

---

## GET /api/student/projects/{projectId}/jira/sprint-progress

Returns read-only sprint progress metrics for the student Jira tab.

### Response fields

- `activeSprint` (`sprintId`, `sprintName`, `sprintState`, etc.)
- `recentSprints[]`
- `velocityWeeks[]` (`weekStart`, `created`, `resolved`, `averageCycleDays`)
- `backlogGrowing` (boolean)
- `sprintDataAvailable` (boolean)

---

## GET /api/student/projects/{projectId}/jira/workload

Returns read-only team workload distribution for the student Jira tab.

### Response fields

- `members[]` (`accountId`, `displayName`, `assigned`, `completed`, `inProgress`, `overdue`, `openIssues`, `storyPointsAssigned`, `storyPointsCompleted`, `completionRate`, `lastActiveDate`, `issueTypeCounts`)
- `unassignedCount`
- `dueDateAvailable` (boolean)
- `imbalanceDetected` (boolean)
- `imbalanceMessage` (nullable string)

---

## GET /api/student/projects/{projectId}/jira/hierarchy

Returns all cached Jira issues for the project structured as a hierarchy tree.

### Response fields

- `roots[]` - top-level issue nodes (Epics, or issues with no parent in cache)
- `orphans[]` - issues whose parentKey references an issue not in this project's cache

Each node in `roots[]`, `orphans[]`, and every nested `children[]` array has:

- `issueKey`
- `summary`
- `issueType` (`"Epic"`, `"Story"`, `"Task"`, `"Bug"`, `"Subtask"`, etc.)
- `status`
- `priority`
- `assigneeDisplayName` (nullable)
- `storyPoints` (nullable)
- `children[]` - recursively the same node shape

### Notes

- Endpoint is read-only for students.
- Data is served from DB-backed Jira issue cache; no live Jira API call is made.
- If Jira is not connected or no issues are cached, `roots` and `orphans` are both empty arrays.

---

## GET /api/student/projects/{projectId}/meeting-channels

Returns all meeting channels for a project where the authenticated student is a member.

### Response fields

Each item:

- `id`
- `projectId`
- `platform` (`GOOGLE_MEET|ZOOM|TEAMS|WHATSAPP|OTHER`)
- `channelName`
- `linkOrIdentifier`
- `addedBy`
- `addedByName`
- `addedByRole` (`SUPERVISOR|STUDENT`)
- `status` (`PENDING|APPROVED`)
- `approvedBy` (nullable)
- `approvedByName` (nullable)
- `approvedAt` (nullable)
- `createdAt`
- `updatedAt` (nullable)

### Notes

- Student must be assigned to project with `member_role = STUDENT`.
- Results are returned pending-first to prioritize items requiring approval.
- Data is role-safe and read-only for listing.

---

## POST /api/student/projects/{projectId}/meeting-channels

Submits a new meeting channel for the project.

### Request fields

- `platform` (required): `GOOGLE_MEET|ZOOM|TEAMS|WHATSAPP|OTHER`
- `channelName` (required, max `120`)
- `linkOrIdentifier` (required, max `1024`)

### Behavior

- Student must be assigned to project with `member_role = STUDENT`.
- Created channel is persisted with:
  - `addedByRole = STUDENT`
  - `status = PENDING`
  - `approvedBy = null`
  - `approvedAt = null`
- Returns created `MeetingChannelDto`.

### Common validation errors

- `platform`: `Platform is required.` / `Unsupported meeting platform.`
- `channelName`: required / max-length validation
- `linkOrIdentifier`: required / max-length validation

---

## GET /api/student/projects/{projectId}/meeting-records

Returns all meeting records for a project where the authenticated student is a member.

### Response fields

Each item:

- `id`
- `projectId`
- `meetingDate` (ISO date, `YYYY-MM-DD`)
- `durationMinutes` (positive integer)
- `discussionSummary` (max `1024`)
- `discussionDetails` (nullable, max `5000`)
- `channelId` (nullable, UUID of `project_meeting_channels`)
- `addedBy`
- `addedByName`
- `addedByRole` (`SUPERVISOR|STUDENT`)
- `status` (`PENDING|APPROVED`)
- `approvedBy` (nullable)
- `approvedByName` (nullable)
- `approvedAt` (nullable)
- `createdAt`
- `updatedAt` (nullable)

### Notes

- Student must be assigned to project with `member_role = STUDENT`.
- Results are returned pending-first to prioritize items requiring approval.
- Within the same status rank, records are ordered by `meetingDate DESC`, then `createdAt DESC`.

---

## POST /api/student/projects/{projectId}/meeting-records

Submits a new meeting record for the project.

### Request fields

- `meetingDate` (required): ISO date `YYYY-MM-DD`
- `durationMinutes` (required): must be `> 0`
- `discussionSummary` (required, max `1024`)
- `discussionDetails` (optional, max `5000`)
- `channelId` (optional, UUID): must belong to the same project if provided

### Behavior

- Student must be assigned to project with `member_role = STUDENT`.
- Created record is persisted with:
  - `addedByRole = STUDENT`
  - `status = PENDING`
  - `approvedBy = null`
  - `approvedAt = null`
- Returns created `MeetingRecordDto`.

### Common validation errors

- `meetingDate`: `Meeting date is required.`
- `durationMinutes`: `Duration is required.` / `Duration must be greater than 0 minutes.`
- `discussionSummary`: required / max-length validation
- `discussionDetails`: max-length validation
- `channelId`: `Invalid channel selected.`

---

## GET /api/student/projects/{projectId}/files

Returns non-deleted project files + upload constraints for student role.

### Response fields

- `files[]`:
  - `id`, `fileName`, `fileType`, `fileSize`
  - `uploadedBy`, `uploadedByName`, `uploadedByRole`
  - `createdAt`, `updatedAt`
- `config`:
  - `maxFileSizeBytes`
  - `maxFileNameLength`
  - `allowedTypes[]`
  - `presignedUrlExpirySeconds`

### Notes

- Excludes soft-deleted rows (`deleted_at IS NULL` only).
- Students can list only projects where they are members (`member_role = STUDENT`).

---

## POST /api/student/projects/{projectId}/files/upload-url

Generates a pre-signed S3 upload URL.

### Request fields

- `fileName` (required)
- `contentType` (required MIME)

### Behavior

- Validates file extension and MIME against configured `allowedTypes`.
- Validates `fileName` length against configured `maxFileNameLength`.
- Returns:
  - `presignedUrl`
  - `s3Key` (UUID-only opaque key)

---

## POST /api/student/projects/{projectId}/files/confirm

Persists uploaded file metadata after successful S3 PUT.

### Request fields

- `s3Key` (required, UUID format)
- `fileName` (required)
- `fileType` (required, MIME or extension)
- `fileSize` (required, positive)

### Validation rules

- `fileName` length must be `<= maxFileNameLength`.
- `fileType` must resolve to a configured allowed extension.
- `fileName` extension and `fileType` must match.
- `fileSize` must be `> 0` and `<= maxFileSizeBytes`.

### Response

- Returns normalized `ProjectFileDto` where `fileType` is stored as extension.

---

## GET /api/student/projects/{projectId}/files/{fileId}/download-url

Returns a pre-signed S3 download URL for one file.

### Notes

- Student access is restricted to assigned projects.
- Soft-deleted files return `404 NOT_FOUND`.
