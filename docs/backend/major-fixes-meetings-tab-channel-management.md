# Backend Major Fixes: Meetings Tab Channel Management

## Scope

This story documents backend implementation for project meeting channel management used by student and supervisor Meetings tabs.

## What was implemented

- Added project meeting channel domain with role-aware workflows:
  - student channel submission (`PENDING`)
  - supervisor channel creation (`APPROVED`)
  - supervisor update/delete
  - supervisor approval of pending submissions
- Added role-scoped controller endpoints under:
  - `/api/student/projects/{projectId}/meeting-channels`
  - `/api/supervisor/projects/{projectId}/meeting-channels`
- Added centralized service validation and authorization checks:
  - platform enum whitelist
  - channel name and link/identifier length/required checks
  - ownership/membership access control
  - pending-only approval rule
- Added DB persistence + constraints for consistency:
  - `project_meeting_channels` table
  - platform/status/role check constraints
  - approval consistency check (`PENDING` vs `APPROVED` fields)
  - project/status/time indexes for list ordering and filtering

## Main backend components

- `MeetingChannelService`
- `MeetingChannelServiceImpl`
- `ProjectMeetingChannel` entity
- `ProjectMeetingChannelRepository`
- `StudentController` meeting channel endpoints
- `SupervisorController` meeting channel endpoints
- Migration: `V29__project_meeting_channels.sql`

## API usage

- Student:
  - `GET /api/student/projects/{projectId}/meeting-channels`
  - `POST /api/student/projects/{projectId}/meeting-channels`
- Supervisor:
  - `GET /api/supervisor/projects/{projectId}/meeting-channels`
  - `POST /api/supervisor/projects/{projectId}/meeting-channels`
  - `PATCH /api/supervisor/projects/{projectId}/meeting-channels/{channelId}`
  - `DELETE /api/supervisor/projects/{projectId}/meeting-channels/{channelId}`
  - `POST /api/supervisor/projects/{projectId}/meeting-channels/{channelId}/approve`

## Notes

- Listing is sorted pending-first to prioritize approval workflow.
- Student write scope is intentionally limited to create only in current version.
- Records/minutes management is implemented separately; see `docs/backend/major-fixes-meetings-tab-records-management.md`.
