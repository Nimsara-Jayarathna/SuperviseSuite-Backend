# Backend Recent Changes (2026-04-10)

## Jira Analytics and Workload Extensibility

Expanding the Jira integration, backend endpoints and logic have been added to support Team Workload Analytics, Sprint Progress mapping, and aggregated Jira project health metrics. The focus is to fetch and map this data directly from cached `project_jira_issues`.

### 1) Jira Team Workload Analytics

Added supervisor and student endpoints to serve current team workload capacity derived from live Jira issue snapshots.

- `GET /api/supervisor/projects/{projectId}/jira/workload`
- `GET /api/student/projects/{projectId}/jira/workload`

**In-Memory Computation**:
Workload constraints, calculations, and imbalances are computed purely via `JiraWorkloadCalculator` and `WorkloadImbalanceDetector` using the `ProjectJiraIssueRepository`. 
- No direct external calls are made during these reads, avoiding rate-limiting on dashboard views.
- Members' capacity is scaled based on open, completed, overdue status, story points, and subtask-resolution constraints.
- Identifies critical anomalies or over/under-assignment imbalances on large teams.

**Response Payloads**:
`JiraWorkloadDto` captures:
- `unassignedCount`, `dueDateAvailable`
- `imbalanceDetected`, `imbalanceMessage`
- List of `MemberRow` items capturing specific `accountId` workload (`openIssues`, `inProgress`, `completionRate`, `storyPointsAssigned`, etc.)

### 2) Sprint Progress & Metrics

Added sprint velocity and active progress endpoints for charting metrics on the agile cycle.

- `GET /api/supervisor/projects/{projectId}/jira/sprint-progress`
- `GET /api/student/projects/{projectId}/jira/sprint-progress`

**Sprint Progress Mechanics**:
Using `JiraSprintProgressCalculator` and `JiraHealthMetricsAggregator` to resolve metrics:
- Extracts `SprintSummary` for the latest active iteration.
- Averages throughput of `recentSprints[]` to calculate a team's week-over-week velocity.
- Tracks `backlogGrowing` indicators to proactively surface issues missing sprint assignments or ballooning product backlog state.

### 3) API Documentation

API endpoints for all roles related to Jira functionality are standardized and documented in:

- `docs/api/supervisor.md`
- `docs/api/student.md`

All feature permutations handle standard 400/403/404 HTTP envelopes per `api-response-contract.md`.
