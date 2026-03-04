# Supervisor API

Supervisor-only endpoints for project listing, student lookup, and project creation.

All endpoints in this document:

- require authentication
- require the authenticated user to have role `SUPERVISOR`
- return the standard `ApiResponse<T>` envelope on success
- return the shared `ApiError` shape on failure

## Endpoints

- [GET /api/supervisor/projects](#get-apisupervisorprojects)
- [GET /api/supervisor/students/search](#get-apisupervisorstudentssearch)
- [POST /api/supervisor/projects](#post-apisupervisorprojects)

---

## GET /api/supervisor/projects

Returns the authenticated supervisor's project list as summary records for the `/supervisor/projects` route.

### Response shape

Each item includes only summary fields needed by the current list UI:

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
      "lifecycleStatus": "PLANNING",
      "batch": "2026",
      "semester": "Semester 1",
      "milestoneDate": "2026-03-21",
      "progressPercent": 0,
      "healthNote": null,
      "memberCount": 3
    }
  ],
  "error": null
}
```

### Notes

- Only projects owned by `projects.supervisor_id = authenticated supervisor` are returned.
- Soft-deleted projects (`deleted_at IS NOT NULL`) are excluded.
- Empty result sets return `200 OK` with `data: []`.

---

## GET /api/supervisor/students/search

Searches registered student users for project assignment.

### Query parameters

| Name | Required | Notes |
|------|----------|-------|
| `q` | yes | Email search term. Values shorter than 3 characters return an empty array. |

### 200 OK

```json
{
  "success": true,
  "message": "Students loaded.",
  "data": [
    {
      "id": "d09ec85d-a704-44bf-b0fb-38a8d1e1e417",
      "firstName": "Nimsara",
      "lastName": "Jayarathna",
      "email": "nimsara@example.com",
      "registrationNumber": "IT24100400"
    }
  ],
  "error": null
}
```

### Notes

- Search currently targets student email only.
- No-match results return `200 OK` with `data: []`.
- This endpoint is intended for type-ahead lookup in the create-project form.

---

## POST /api/supervisor/projects

Creates a new project, assigns the supervisor and selected students, and creates the first milestone in one transaction.

### Request

**Content-Type:** `application/json`

| Field | Type | Required | Rules |
|------|------|----------|-------|
| `title` | string | yes | Not blank. |
| `summary` | string | yes | Not blank. |
| `batch` | string | yes | Not blank. |
| `semester` | string | yes | Not blank. |
| `studentIds` | `uuid[]` | yes | At least one student. Must be unique. |
| `milestone` | object | yes | First milestone payload. |
| `milestone.title` | string | yes | Not blank. |
| `milestone.description` | string | no | Optional. Blank is normalized to `null`. |
| `milestone.dueDate` | date | yes | Required ISO date. |

### Example request

```json
{
  "title": "Smart Attendance Tracker",
  "summary": "AI-assisted attendance project",
  "batch": "2026",
  "semester": "Semester 1",
  "studentIds": [
    "d09ec85d-a704-44bf-b0fb-38a8d1e1e417",
    "f0c2f1a7-d955-463f-9b18-3daec2c54cf4"
  ],
  "milestone": {
    "title": "Proposal Submission",
    "description": "Initial proposal review",
    "dueDate": "2026-03-21"
  }
}
```

### Backend defaults

These values are applied by the backend and are not required from the client:

- project `lifecycleStatus = PLANNING`
- project `progressPercent = 0`
- project `healthNote = null`
- milestone `status = PLANNED`
- milestone `sequenceNo = 1`
- project `milestoneDate = milestone.dueDate`

### 201 Created

```json
{
  "success": true,
  "message": "Project created successfully.",
  "data": {
    "id": "f14699be-6c09-4a86-83b8-a4c4fd7d457d",
    "title": "Smart Attendance Tracker",
    "summary": "AI-assisted attendance project",
    "batch": "2026",
    "semester": "Semester 1",
    "lifecycleStatus": "PLANNING",
    "progressPercent": 0,
    "milestoneDate": "2026-03-21",
    "students": [
      {
        "id": "d09ec85d-a704-44bf-b0fb-38a8d1e1e417",
        "firstName": "Nimsara",
        "lastName": "Jayarathna",
        "email": "nimsara@example.com",
        "registrationNumber": "IT24100400"
      }
    ],
    "milestone": {
      "id": "b54c4486-4d45-455a-93bc-4097148bf8d2",
      "title": "Proposal Submission",
      "description": "Initial proposal review",
      "dueDate": "2026-03-21",
      "status": "PLANNED",
      "sequenceNo": 1
    }
  },
  "error": null
}
```

### Validation behavior

- duplicate `studentIds` -> `400 VALIDATION_ERROR`
- non-existent student IDs -> `400 VALIDATION_ERROR`
- non-student user IDs in `studentIds` -> `400 VALIDATION_ERROR`
- malformed request body -> `400 BAD_REQUEST`

### Notes

- The supervisor is taken from the JWT principal, not from the request body.
- The backend creates:
  - one `projects` row
  - one supervisor membership row
  - one membership row per selected student
  - one `project_milestones` row
