# Backend Recent Changes (2026-02-22)

## 1) Database Configuration and Environment Loading

- Added datasource config in `src/main/resources/application.yaml`:
  - `spring.datasource.url: ${DB_URL}`
  - `spring.datasource.username: ${DB_USERNAME}`
  - `spring.datasource.password: ${DB_PASSWORD}`
  - `spring.datasource.driver-class-name: org.postgresql.Driver`
- Enabled automatic loading of backend `.env` file:
  - `spring.config.import: optional:file:.env[.properties]`
- Added environment template file:
  - `.env.example` with `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `APP_PORT`

## 2) Server Port Configuration

- Added configurable application port in `src/main/resources/application.yaml`:
  - `server.port: ${APP_PORT:8080}`
- Port can now be changed via `.env` without code changes.

## 3) Flyway Migration Setup

- Flyway is enabled in `src/main/resources/application.yaml`.
- Added initial migration:
  - `src/main/resources/db/migration/V1__init_schema.sql`
- Added dedicated DB docs directory:
  - `docs/database/README.md`
  - `docs/database/schema-v1.md`
  - `docs/database/migrations.md`
- `V1` creates:
  - `users`
  - `projects`
  - `project_members`
- Includes:
  - primary keys
  - unique constraint on `users.email`
  - foreign keys from `project_members` to `users` and `projects`
  - indexes on `project_members.user_id` and `project_members.project_id`

## 4) Startup Migration Behavior

- On every backend startup, Flyway checks `flyway_schema_history`.
- Only missing migration versions are applied.
- If schema is already at latest version, no migration runs.
- Default profile keeps `baseline-on-migrate=false` to avoid silently skipping versioned migrations.
- `application-dev.yaml` has no Flyway overrides — apply only to an empty database.

## 5) Security Behavior Update

- Implemented explicit Spring Security filter chain in `src/main/java/com/supervisesuite/backend/config/SecurityConfig.java`.
- Disabled default form login and HTTP basic login.
- Configured stateless session policy.
- Added JWT filter into chain.
- Permitted:
  - `/api/auth/**`
  - `/actuator/health`
  - `OPTIONS /**`
- Other endpoints require authentication.
- Result: unauthenticated API calls return `401` instead of redirecting to default `/login` page.

## 6) Supervisor Project Read/Create Flow (2026-03-04)

- Added a dedicated supervisor feature slice:
  - `src/main/java/com/supervisesuite/backend/supervisor/controller/SupervisorController.java`
  - `src/main/java/com/supervisesuite/backend/supervisor/service/SupervisorService.java`
  - `src/main/java/com/supervisesuite/backend/supervisor/service/SupervisorServiceImpl.java`
  - DTOs under `src/main/java/com/supervisesuite/backend/supervisor/dto/`
- New supervisor-only endpoints under `/api/supervisor`:
  - `GET /api/supervisor/projects`
    - Requires `SUPERVISOR` role
    - Returns the authenticated supervisor's project list as summary records
    - Current payload is intentionally limited to list-card fields only
  - `GET /api/supervisor/projects/{projectId}`
    - Requires `SUPERVISOR` role
    - Returns one supervisor-owned project as a trimmed detail record
    - Current payload is intentionally limited to core project fields, members, and milestones
  - `GET /api/supervisor/students/search?q=...`
    - Requires `SUPERVISOR` role via method security
    - Searches registered `STUDENT` accounts by email
    - Returns `200 OK` with an empty array when no students match
  - `POST /api/supervisor/projects`
    - Requires `SUPERVISOR` role
    - Creates a project, project memberships, and the initial milestone in one transaction
- Backend defaults applied during project creation:
  - `lifecycle_status = 'PLANNING'`
  - `progress_percent = 0`
  - initial milestone `status = 'PLANNED'`
  - initial milestone `sequence_no = 1`
- Added validation rules in the supervisor service:
  - duplicate `studentIds` are rejected
  - all selected users must exist
  - all selected users must have role `STUDENT`
- Added persistence for milestones:
  - `src/main/java/com/supervisesuite/backend/projects/entity/ProjectMilestone.java`
  - `src/main/java/com/supervisesuite/backend/projects/repository/ProjectMilestoneRepository.java`
- Extended `UserRepository` with student email search support:
  - `findTop10ByRoleAndEmailContainingIgnoreCaseOrderByEmailAsc(...)`
- Extended project/member repositories for supervisor project summaries:
  - `ProjectRepository.findByIdAndSupervisor_IdAndDeletedAtIsNull(...)`
  - `ProjectRepository.findBySupervisorIdAndDeletedAtIsNullOrderByCreatedAtDesc(...)`
  - `ProjectMemberRepository.countByProjectId(...)`
  - `ProjectMemberRepository.findByProjectIdOrderByCreatedAtAsc(...)`
  - `ProjectMilestoneRepository.findByProjectIdOrderBySequenceNoAsc(...)`
- Added dedicated supervisor API reference:
  - `docs/api/supervisor.md`

## 7) Student Project List Read Flow (2026-03-05)

- Added a dedicated student feature slice:
  - `src/main/java/com/supervisesuite/backend/student/controller/StudentController.java`
  - `src/main/java/com/supervisesuite/backend/student/service/StudentService.java`
  - `src/main/java/com/supervisesuite/backend/student/service/StudentServiceImpl.java`
  - DTOs under `src/main/java/com/supervisesuite/backend/student/dto/`
- New student-only endpoint under `/api/student`:
  - `GET /api/student/projects`
    - Requires `STUDENT` role
    - Returns projects assigned to the authenticated student
    - Current payload is intentionally limited to student list-card summary fields
- Student visibility/ownership rules:
  - source membership is `project_members.user_id = authenticated student`
  - membership must have `member_role = STUDENT`
  - soft-deleted projects are excluded
- Extended repositories for student list reads:
  - `ProjectMemberRepository.findByUserIdAndMemberRoleOrderByCreatedAtDesc(...)`
  - `ProjectRepository.findByIdInAndDeletedAtIsNullOrderByCreatedAtDesc(...)`
- Added dedicated student API reference:
  - `docs/api/student.md`

## 8) Student Project Detail Read Flow (2026-03-05)

- Extended student API with detail endpoint under `/api/student`:
  - `GET /api/student/projects/{projectId}`
    - Requires `STUDENT` role
    - Returns one assigned project as a trimmed detail record
    - Payload includes core project fields, members, and milestones
- Added student detail DTO:
  - `src/main/java/com/supervisesuite/backend/student/dto/StudentProjectDetailDto.java`
- Extended student service contract and implementation:
  - `StudentService.getProjectById(...)`
  - `StudentServiceImpl.getProjectById(...)`
- Added student ownership/visibility checks for detail reads:
  - student must be assigned in `project_members` with `member_role = STUDENT`
  - soft-deleted projects are excluded
  - invalid UUID and unauthorized ownership both resolve as `404 NOT_FOUND`
- Extended repositories for detail reads:
  - `ProjectMemberRepository.existsByUserIdAndProjectIdAndMemberRole(...)`
  - `ProjectRepository.findByIdAndDeletedAtIsNull(...)`
- Updated student API reference:
  - `docs/api/student.md`
