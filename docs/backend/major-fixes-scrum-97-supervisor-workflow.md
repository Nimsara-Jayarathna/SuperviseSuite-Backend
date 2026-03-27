# Backend Major Fixes: SCRUM-97 Supervisor Workflow

Branch: `feature/scrum-97-ui-workflow-improvements`  
Compared against: `dev`  
Commit range: `9aa6a2d` -> `4b53949`

## Fix 1: Project Leader Assignment Integrity

### Why this fix was needed

- Supervisor workflow needed an explicit project leader model.
- Frontend could submit leader updates, but backend contract/storage needed formal support and validation.
- Leader selection had to be restricted to valid student members to prevent invalid state.

### What was changed

- Added DB migration `V4__project_leader_assignment.sql`:
  - `projects.leader_user_id UUID NULL`
  - FK `fk_projects_leader_user` -> `users(id)`
  - index `idx_projects_leader_user_id`
- Added `leaderUserId` to `Project` entity.
- Extended DTO contracts:
  - `UpdateSupervisorProjectRequest.leaderStudentId` (optional)
  - `CreateSupervisorProjectResponse.leader` (nullable)
  - `SupervisorProjectDetailDto.leader` with student identity fields.
- Added validation in service layer:
  - create: leader must be included in submitted `studentIds`
  - update: leader must already be assigned as a project student member.

## Fix 2: Multi-Milestone Create Contract

### Why this fix was needed

- Single-initial-milestone creation was a workflow bottleneck.
- Product flow now requires creating full milestone plans at project creation time.

### What was changed

- `CreateSupervisorProjectRequest`:
  - replaced `milestone` with required `milestones[]`
  - added optional `leaderStudentId`
- `CreateSupervisorProjectResponse`:
  - replaced single `milestone` with `milestones[]`
  - includes `leader`
- `SupervisorServiceImpl.createProject(...)` now:
  - creates all milestones in one transaction
  - assigns `sequenceNo` from `1` in request order
  - sets `milestoneDate` using earliest due date across provided milestones.

## Fix 3: Correct Progress Calculation on Milestone Changes

### Why this fix was needed

- Stored `progressPercent` could become stale after milestone updates.
- Progress had to reflect milestone status changes immediately.

### What was changed

- Added `refreshProjectProgressPercent(...)` + `calculateProgressPercent(...)` in `SupervisorServiceImpl`.
- Recalculation now runs after:
  - `createProject(...)`
  - `addProjectMilestone(...)`
  - `updateProjectMilestone(...)`
- Formula now used consistently:
  - ignore `CANCELLED` milestones
  - if active milestone count is `0`, progress is `0`
  - else `round(completedActive * 100 / activeCount)` with `COMPLETED` as done status.

## Fix 4: Startup Backfill for Legacy Progress Values

### Why this fix was needed

- Existing projects may have outdated `progressPercent` values from older logic.
- Needed one automatic correction path without manual data repair.

### What was changed

- Added `ProjectProgressBackfillRunner` (`ApplicationRunner`):
  - scans non-deleted projects at startup
  - recalculates progress from milestone statuses
  - bulk-updates only changed projects
  - logs scanned vs updated counts.
- Added repository method:
  - `ProjectRepository.findByDeletedAtIsNullOrderByCreatedAtDesc()`

## Changed Files (`dev..HEAD`)

- `src/main/resources/db/migration/V4__project_leader_assignment.sql`
- `src/main/java/com/supervisesuite/backend/projects/entity/Project.java`
- `src/main/java/com/supervisesuite/backend/projects/repository/ProjectRepository.java`
- `src/main/java/com/supervisesuite/backend/supervisor/dto/CreateSupervisorProjectRequest.java`
- `src/main/java/com/supervisesuite/backend/supervisor/dto/CreateSupervisorProjectResponse.java`
- `src/main/java/com/supervisesuite/backend/supervisor/dto/SupervisorProjectDetailDto.java`
- `src/main/java/com/supervisesuite/backend/supervisor/dto/UpdateSupervisorProjectRequest.java`
- `src/main/java/com/supervisesuite/backend/supervisor/service/SupervisorServiceImpl.java`
- `src/main/java/com/supervisesuite/backend/supervisor/service/ProjectProgressBackfillRunner.java`
