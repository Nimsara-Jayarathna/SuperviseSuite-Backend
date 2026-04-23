# Recent Changes — 2026-04-13

## Scope

This change set covers:

- Password-change API contract simplification (remove backend `confirmPassword` field)
- Authentication principal alignment in password-change flow (JWT subject/userId)
- Datasource/Hikari runtime tuning via environment variables

## API Contract Update

### Password change endpoints

- `PATCH /api/supervisor/me/password`
- `PATCH /api/student/me/password`

### Request payload change

Previous payload:

```json
{
  "currentPassword": "...",
  "newPassword": "...",
  "confirmPassword": "..."
}
```

Current payload:

```json
{
  "currentPassword": "...",
  "newPassword": "..."
}
```

### Validation behavior

- Confirm-password matching is now a frontend UX validation concern.
- Backend still enforces:
  - current password verification
  - strong password constraints (`@StrongPassword`)
  - new password must differ from current password

## Authentication Flow Fix

Password change now resolves the authenticated user by JWT subject (`userId`) instead of treating the principal as email.

Result:

- Prevents principal mismatch paths that could surface `401 Unauthorized` when token subject is UUID user id.
- Keeps role-gated controller behavior unchanged.

## Configuration Update (Environment + Application)

Added datasource pool settings in `application.yaml` under `spring.datasource.hikari` with env overrides:

- `DB_MAX_POOL_SIZE` (default `10`)
- `DB_MIN_IDLE` (default `5`)
- `DB_CONNECTION_TIMEOUT_MS` (default `30000`)
- `DB_IDLE_TIMEOUT_MS` (default `600000`)
- `DB_MAX_LIFETIME_MS` (default `1800000`)
- `DB_KEEPALIVE_TIME_MS` (default `300000`)
- `DB_VALIDATION_TIMEOUT_MS` (default `5000`)

Purpose:

- Reduce stale/closed pooled-connection warnings in long-lived backend sessions.
- Make pool behavior tunable by environment without code changes.

## Test and Verification Notes

Validation completed after change:

```bash
./mvnw -q -Dtest=SupervisorControllerTest,StudentControllerTest test
./mvnw -q -DskipTests compile
```

Frontend compatibility/build validation:

```bash
npm run -s build
```

## Migration Notes

- No Flyway migration added.
- No schema change required for this slice.
