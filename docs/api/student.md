# Student API

Student-only endpoints for loading assigned project lists.

All endpoints in this document:

- require authentication
- require the authenticated user to have role `STUDENT`
- return the standard `ApiResponse<T>` envelope on success
- return the shared `ApiError` shape on failure

## Endpoints

- [GET /api/student/projects](#get-apistudentprojects)

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
- Current student detail view (`/student/projects/{projectId}`) is still mock-backed and does not yet use a student detail API endpoint.
