# Recent Changes — 2026-04-12

## Scope

Post-baseline updates for:
- Auth registration verification flow (init/verify/complete)
- Email OTP/session persistence
- Backend rate limiting for auth and authenticated routes
- API envelope alignment for 429/503 error semantics

## API Checklist

### Auth registration flow
- `POST /api/auth/register/init` sends verification OTP.
- `POST /api/auth/register/verify` validates OTP and returns registration token.
- `POST /api/auth/register/complete` finalizes account creation.
- `GET /api/auth/register/config` returns domain/prefix restrictions.

### Error semantics
- 429 responses use code `TOO_MANY_REQUESTS`.
- 503 responses use code `SERVICE_UNAVAILABLE`.
- Security-layer error responses keep unified envelope contract.

### Rate-limit headers
For throttled/limited endpoints verify presence and value shape of:
- `X-RateLimit-Limit`
- `X-RateLimit-Remaining`
- `X-RateLimit-Reset`
- `Retry-After` (when blocked)

## Migration Checklist

### Added migrations
- `V24__registration_sessions.sql`
- `V25__email_otps.sql`

### Validation points
- Tables exist with expected primary keys and expiry columns.
- Required indexes/uniqueness constraints are present for lookup paths.
- Registration flow works after clean migrate against empty DB.
- No existing migration file was modified in-place.

## Configuration Checklist

Verify these runtime properties/env variables are configured:
- `app.rate-limiting.*`
- registration OTP/session expiry values
- email provider settings (Brevo sender/api key)

## Automated Test Coverage Added

- `RateLimitingFilterTest` for auth/authenticated routing, block behavior, headers, and bypasses.
- `ApiErrorControllerTest` extended with 429/503 code mapping assertions.

## Commands

```bash
./mvnw -q test
```
