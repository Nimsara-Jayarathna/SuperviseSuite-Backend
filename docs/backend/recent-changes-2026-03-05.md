# Backend Recent Changes (2026-03-05)

## 1) httpOnly Cookie Migration

Auth tokens are no longer returned in response bodies. They are delivered as `HttpOnly; Secure; SameSite=Strict` cookies by the backend and are invisible to JavaScript.

**New cookies:**

| Cookie | Path | Max-Age |
|--------|------|---------|
| `ss_access_token` | `/api` | 900 s (15 min) |
| `ss_refresh_token` | `/api/auth` | 604 800 s (7 days) |

**New endpoints:**

- `POST /api/auth/login` — authenticates and issues both cookies; JSON body contains user profile only.
- `POST /api/auth/refresh` — rotates the token pair (old refresh token revoked immediately); no request body needed.
- `POST /api/auth/logout` — revokes refresh token server-side; clears both cookies with `Max-Age=0`; always returns `204` (idempotent).

**New classes / services:**

- `ResponseCookieService` — builds all `ResponseCookie` instances; `app.cookie.secure` flag controls the `Secure` attribute (see below).
- `CookieService` — cookie name constants.
- `RefreshTokenService` — issues and revokes refresh tokens; persists hashed values in `refresh_tokens` table.
- `RefreshTokenValidator` — validates, checks expiry, and checks revocation.
- `RefreshTokenRepository` — Spring Data JPA repository for `RefreshToken` entity.
- `LoginRequest` / `LoginResponse` / `LoginUserResponse` — DTOs for the login/refresh flow.

**Modified classes:**

- `AuthController` — added `login`, `refresh`, `logout` handler methods.
- `AuthService` — added `login(LoginRequest)` method.
- `SecurityConfig` — `WebConfig.allowCredentials` changed to `true` (required for cookie-based CORS).

---

## 2) Dev-Profile Cookie Secure Flag

The `Secure` attribute on both cookies is `false` when running with the `dev` Spring profile so cookies work over plain HTTP on `localhost` (required for Firefox and Safari).

- `application.yaml`: `app.cookie.secure: ${COOKIE_SECURE:true}` (production default: `true`)
- `application-dev.yaml`: `app.cookie.secure: false`
- `ResponseCookieService` reads the flag via `@Value("${app.cookie.secure:true}")`.

---

## 3) Role-Based Access Enforcement (US2 / US3)

`JwtAuthFilter` now reads the `ss_access_token` **cookie** (previously expected an `Authorization: Bearer` header) and populates `SecurityContextHolder` with the authenticated user including their role.

- **US2 (Student access):** STUDENT-scoped endpoints will use `@PreAuthorize("hasRole('STUDENT')")` — enforced by the populated security context.
- **US3 (Supervisor access):** SUPERVISOR-scoped endpoints will use `@PreAuthorize("hasRole('SUPERVISOR')")`.
- Requests to protected endpoints without a valid access token return `401 UNAUTHORIZED`.
- Requests with a valid token but insufficient role return `403 FORBIDDEN`.

`SecurityConfig` permits all routes under `/api/auth/**` without authentication. All other `/api/**` routes require an authenticated principal.

---

## 4) Refresh Token Table

Added `V2__add_refresh_tokens.sql` Flyway migration:

- New table: `refresh_tokens`
  - `id` (UUID PK)
  - `user_id` (FK → `users.id`, ON DELETE CASCADE)
  - `token_hash` (VARCHAR, unique) — SHA-256 hash of the raw token value
  - `expires_at` (TIMESTAMPTZ)
  - `revoked_at` (TIMESTAMPTZ, nullable) — set on use or explicit logout
  - `created_at` (TIMESTAMPTZ)
- Index on `token_hash` for fast lookup.
- Index on `user_id` for cascade queries.

---

## 5) Security Hardening

- `allowCredentials(true)` in `WebConfig` (was `false` — cookies require this).
- `emailVerified` field removed from `LoginResponse.UserInfo` — was hardcoded `true`, served no purpose, and leaked an unimplemented concept to clients.
- `JwtAuthFilter` reads tokens from cookie, not `Authorization` header — tokens are never exposed to JavaScript.
