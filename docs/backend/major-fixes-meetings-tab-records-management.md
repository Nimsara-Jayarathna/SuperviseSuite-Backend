# Backend Major Fixes: Meetings Tab Records Management

## Scope

This story documents backend implementation for project meeting **records** management used by student and supervisor Meetings tabs.

## What was implemented

- Added project meeting record domain with role-aware workflows:
  - student record submission (`PENDING`)
  - supervisor record creation (`APPROVED`)
  - supervisor update/delete
  - supervisor approval of pending submissions
- Added optional linking from meeting records to meeting channels:
  - `channel_id` is nullable
  - channel must belong to the same project when provided
  - FK uses `ON DELETE SET NULL` to keep records valid if a channel is removed
- Added role-scoped controller endpoints under:
  - `/api/student/projects/{projectId}/meeting-records`
  - `/api/supervisor/projects/{projectId}/meeting-records`
- Added centralized service validation and authorization checks:
  - required meeting date
  - duration > 0
  - discussion summary required and max length (`1024`)
  - discussion details optional and max length (`5000`)
  - channel validation (exists + same project)
  - ownership/membership access control
  - pending-only approval rule
- Added DB persistence + constraints for consistency:
  - `project_meeting_records` table
  - duration/status/role check constraints
  - approval consistency check (`PENDING` vs `APPROVED` fields)
  - project/status/date indexes for list ordering and filtering

## Main backend components

- `MeetingRecordService`
- `MeetingRecordServiceImpl`
- `ProjectMeetingRecord` entity
- `ProjectMeetingRecordRepository`
- `StudentController` meeting record endpoints
- `SupervisorController` meeting record endpoints
- Migration: `V30__project_meeting_records.sql`

## API usage

- Student:
  - `GET /api/student/projects/{projectId}/meeting-records`
  - `POST /api/student/projects/{projectId}/meeting-records`
- Supervisor:
  - `GET /api/supervisor/projects/{projectId}/meeting-records`
  - `POST /api/supervisor/projects/{projectId}/meeting-records`
  - `PATCH /api/supervisor/projects/{projectId}/meeting-records/{recordId}`
  - `DELETE /api/supervisor/projects/{projectId}/meeting-records/{recordId}`
  - `POST /api/supervisor/projects/{projectId}/meeting-records/{recordId}/approve`

## Notes

- Listing is sorted pending-first to prioritize approval workflow.
- Student write scope is intentionally limited to create only in current version.
- Channel linkage is optional and does not block quick record entry.

