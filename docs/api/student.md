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
- `members[]`
- `milestones[]`

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
