# Database Documentation

This directory tracks database structure and migration history for the backend.

## Structure

- `schema-v1.md`: schema reference updated through meetings records schema migration (`V30`).
- `migrations.md`: chronological migration log and rules for future schema changes.

## Source of Truth

- Runtime migrations: `src/main/resources/db/migration`
- Flyway history table: `flyway_schema_history`
- Profile-specific Flyway behavior:
  - default: `src/main/resources/application.yaml` (`baseline-on-migrate: false`)
  - dev: `src/main/resources/application-dev.yaml` — no Flyway overrides; apply only to an empty database

## Current Baseline

- Core domain initialized by `V1__init_schema.sql`.
- Auth/user schema updates and refresh-token support added by `V2__auth_schema.sql`.
- Project domain expanded by `V3__project_domain_expansion.sql` and `V4__project_leader_assignment.sql`.
- GitHub integration v1 introduced by `V5__project_github_cache.sql` and `V6__github_app_installations.sql`.
- GitHub integration v2 finalized by:
  - `V10__github_integration_v2.sql`
  - `V11__github_repository_enablement_limits.sql`
  - `V12__decommission_v1_github_integration.sql`
  - `V13__denormalized_repository_link_fields.sql`
  - `V14__add_updated_at_to_access_sources.sql`
  - `V15__align_github_access_request_v2_with_result_tracking.sql`
- Jira integration and cached analytics migrations:
  - `V16__project_jira_integrations.sql`
  - `V17__project_jira_oauth_states.sql`
  - `V18__project_jira_issue_cache.sql`
  - `V19__ensure_project_jira_issue_cache_exists.sql`
  - `V20__add_sprint_fields_to_jira_issues.sql`
  - `V21__add_parent_issue_type_to_jira_issues.sql`
  - `V22__add_refresh_token_to_jira_integrations.sql`
- Project files and registration/credential lifecycle migrations:
  - `V23__project_files.sql`
  - `V24__registration_sessions.sql`
  - `V25__email_otps.sql`
  - `V26__password_reset_tokens.sql`
  - `V27__optimistic_locking_and_sync_indexes.sql`
  - `V28__sync_in_progress_and_attempt_tracking.sql`
- Meetings channel migrations:
  - `V29__project_meeting_channels.sql`
- Meetings record migrations:
  - `V30__project_meeting_records.sql`

## Change Workflow

1. Add a new Flyway file in `src/main/resources/db/migration` (example: `V2__add_project_owner.sql`).
2. Update `docs/database/migrations.md` with a short entry.
3. If table/column model changes significantly, update `docs/database/schema-v1.md` (or create next schema doc when needed).
