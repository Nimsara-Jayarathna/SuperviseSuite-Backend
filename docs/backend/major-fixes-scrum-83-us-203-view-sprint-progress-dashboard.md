# Backend Major Fixes: SCRUM-83 US-203 - View Sprint Progress Dashboard

## Scope

This story covers backend data shaping used by the Jira tab sprint progress dashboard.

## What was implemented

- Jira sprint progress endpoint support and payload shaping for dashboard widgets:
  - active sprint summary
  - recent sprint summaries
  - weekly velocity timeline
  - backlog growth indicator
- Data is served from cached Jira issue records (no direct frontend-to-Jira calls).

## Main backend components

- `JiraSprintProgressService`
- `JiraSprintProgressServiceImpl`
- `JiraHealthClassifier` (status-category classification used by sprint progress computations)

## API usage

- Supervisor: `GET /api/supervisor/projects/{projectId}/jira/sprint-progress`
- Student: `GET /api/student/projects/{projectId}/jira/sprint-progress`

## Notes

- This story is Jira tab data scope only.
- Jira OAuth connect/disconnect/callback flow is out of scope and unchanged.
