# Auth API

## Endpoints

- [POST /api/auth/register](#post-apiauthregister)
- [POST /api/auth/login](#post-apiauthlogin)
- [POST /api/auth/refresh](#post-apiauthrefresh)
- [POST /api/auth/logout](#post-apiauthlogout)

---

## Cookie-Based Authentication

All session state is carried in two `HttpOnly` cookies set by the backend. Tokens are **never** included in response bodies and are invisible to JavaScript.

| Cookie | Path | Max-Age | Description |
|--------|------|---------|-------------|
| `ss_access_token` | `/api` | 900 s (15 min) | Short-lived JWT; sent automatically on every request to `/api/**` |
| `ss_refresh_token` | `/api/auth` | 604 800 s (7 days) | Long-lived opaque token; limited to `POST /api/auth/refresh` by its path scope |

All cookies are set with `HttpOnly; SameSite=Strict`. The `Secure` attribute is `true` in production and `false` in the `dev` Spring profile (to support plain-HTTP local development — see `application-dev.yaml`).

Clients must use `credentials: include` (fetch) or `withCredentials: true` (XMLHttpRequest). No `Authorization` header is needed or accepted.

### Role-Based Access (US2 / US3)

`JwtAuthFilter` intercepts every request to `/api/**` and extracts the authenticated user — including their `role` — from the `ss_access_token` cookie. The `SecurityContextHolder` is then populated, allowing Spring Security and `@PreAuthorize` annotations to enforce role-based access on protected endpoints:

- **STUDENT** — access to student-scoped project and membership endpoints.
- **SUPERVISOR** — access to supervisor-scoped project, membership, and assignment endpoints.

A request to a protected endpoint without a valid access token returns `401 UNAUTHORIZED`. A request with a valid token but insufficient role returns `403 FORBIDDEN`.

---

## POST /api/auth/register

Registers a new student account. No authentication required.

### Request

**Content-Type:** `application/json`

| Field              | Type   | Required | Rules                                                                 |
|--------------------|--------|----------|-----------------------------------------------------------------------|
| `firstName`        | string | yes      | Not blank. Max 100 characters.                                        |
| `lastName`         | string | yes      | Not blank. Max 100 characters.                                        |
| `email`            | string | yes      | Not blank. Must be a valid email format. Must be unique.              |
| `registrationNumber` | string | yes    | Not blank. Max 20 characters. Must be unique. Stored in uppercase (normalized server-side). |
| `password`         | string | yes      | Not blank. Min 8 characters. Must include uppercase, lowercase, digit, and special character. |

**Example:**

```json
{
  "firstName": "Amal",
  "lastName": "Perera",
  "email": "amal.perera@university.ac.lk",
  "registrationNumber": "IT24100400",
  "password": "Secure@123"
}
```

---

### Responses

#### 201 Created — registration successful

```json
{
  "success": true,
  "message": "Registration successful.",
  "data": {
    "id": "a3f1c2d4-1234-4abc-8def-000000000001",
    "email": "amal.perera@university.ac.lk",
    "firstName": "Amal",
    "lastName": "Perera",
    "registrationNumber": "IT24100400",
    "role": "STUDENT"
  },
  "error": null
}
```

#### 400 VALIDATION_ERROR — one or more fields are invalid

Returned when a required field is missing, blank, fails format checks, or the password does not meet strength requirements.

```json
{
  "timestamp": "2026-03-02T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_ERROR",
  "message": "Validation failed.",
  "path": "/api/auth/register",
  "traceId": null,
  "details": [
    { "field": "email", "issue": "Email must be a valid email address." },
    { "field": "password", "issue": "Password must contain: an uppercase letter, a digit." }
  ]
}
```

#### 409 CONFLICT — duplicate email

```json
{
  "timestamp": "2026-03-02T10:00:00Z",
  "status": 409,
  "error": "Conflict",
  "code": "CONFLICT",
  "message": "An account with this email already exists.",
  "path": "/api/auth/register",
  "traceId": null,
  "details": []
}
```

#### 409 CONFLICT — duplicate registration number

```json
{
  "timestamp": "2026-03-02T10:00:00Z",
  "status": 409,
  "error": "Conflict",
  "code": "CONFLICT",
  "message": "An account with this registration number already exists.",
  "path": "/api/auth/register",
  "traceId": null,
  "details": []
}
```

---

### Frontend Handling Guidance

- On `201`: navigate to `/login`. No session is created by registration — the user must sign in separately.
- On `400 VALIDATION_ERROR`: map each `details[].field` to the corresponding input and show `details[].issue` inline.
- On `409 CONFLICT`: show a targeted message on the specific field (`email` or `registrationNumber`).
- See [error-handling.md](error-handling.md) for the full error response contract and all error codes.

---

### Notes

- Assigned role is always `STUDENT`. Supervisor accounts are not self-registered.
- Password is never returned in any response. BCrypt hash is stored server-side.
- Email verification is out of scope — accounts are considered verified upon registration.

---

## POST /api/auth/login

Authenticates a user and issues a new session via httpOnly cookies. No authentication required.

### Request

**Content-Type:** `application/json`

| Field | Type | Required | Rules |
|-------|------|----------|-------|
| `email` | string | yes | Not blank. Valid email format. |
| `password` | string | yes | Not blank. |

**Example:**

```json
{
  "email": "amal.perera@university.ac.lk",
  "password": "Secure@123"
}
```

---

### Responses

#### 200 OK — login successful

Tokens are **not** in the body — they are delivered as `HttpOnly; Secure; SameSite=Strict` cookies (see [Cookie-Based Authentication](#cookie-based-authentication)).

```json
{
  "success": true,
  "message": "Login successful.",
  "data": {
    "user": {
      "id": "a3f1c2d4-1234-4abc-8def-000000000001",
      "email": "amal.perera@university.ac.lk",
      "firstName": "Amal",
      "lastName": "Perera",
      "role": "STUDENT"
    }
  },
  "error": null
}
```

**Set-Cookie headers on 200:**

```
Set-Cookie: ss_access_token=<jwt>; Path=/api; Max-Age=900; HttpOnly; Secure; SameSite=Strict
Set-Cookie: ss_refresh_token=<token>; Path=/api/auth; Max-Age=604800; HttpOnly; Secure; SameSite=Strict
```

#### 400 VALIDATION_ERROR — missing or blank fields

```json
{
  "timestamp": "2026-03-05T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_ERROR",
  "message": "Validation failed.",
  "path": "/api/auth/login",
  "traceId": null,
  "details": [
    { "field": "email", "issue": "Email must not be blank." }
  ]
}
```

#### 401 UNAUTHORIZED — unknown email or wrong password

The response deliberately does not distinguish between an unknown email and a wrong password to prevent user enumeration.

```json
{
  "timestamp": "2026-03-05T10:00:00Z",
  "status": 401,
  "error": "Unauthorized",
  "code": "UNAUTHORIZED",
  "message": "Invalid email or password.",
  "path": "/api/auth/login",
  "traceId": null,
  "details": []
}
```

---

### Frontend Handling Guidance

- On `200`: store the `data.user` profile locally; the browser holds the cookies automatically.
- On `400 VALIDATION_ERROR`: show field-level errors from `details[]`.
- On `401`: show a generic "Invalid email or password" banner — do not specify which field is wrong.

---

### Notes

- Each successful login issues a new refresh token persisted in the database. The old one (if any) is not automatically revoked — the user may hold parallel sessions.
- The `Secure` cookie attribute is `false` in the `dev` Spring profile so cookies work over plain HTTP on `localhost`.

---

## POST /api/auth/refresh

Issues a new access token and rotates the refresh token. No request body required — the browser sends the `ss_refresh_token` cookie automatically.

### Request

No body. Requires the `ss_refresh_token` cookie (sent automatically by the browser when `credentials: include` is used).

---

### Responses

#### 200 OK — token rotation successful

Both cookies are replaced. The old refresh token is immediately revoked (token rotation — it cannot be reused).

```json
{
  "success": true,
  "message": "Token refreshed.",
  "data": {
    "user": {
      "id": "a3f1c2d4-1234-4abc-8def-000000000001",
      "email": "amal.perera@university.ac.lk",
      "firstName": "Amal",
      "lastName": "Perera",
      "role": "STUDENT"
    }
  },
  "error": null
}
```

**Set-Cookie headers on 200:** same format as login (new values).

#### 401 UNAUTHORIZED — cookie missing, token unknown, revoked, or expired

```json
{
  "timestamp": "2026-03-05T10:00:00Z",
  "status": 401,
  "error": "Unauthorized",
  "code": "UNAUTHORIZED",
  "message": "Refresh token is missing.",
  "path": "/api/auth/refresh",
  "traceId": null,
  "details": []
}
```

---

### Frontend Handling Guidance

- This endpoint is called automatically by the `apiClient` 401 interceptor — it is not called directly from UI.
- On `200`: retry the original failed request transparently; update stored user profile.
- On `401`: clear local auth state and redirect to `/login`.

---

### Notes

- Token rotation is enforced: each `ss_refresh_token` value may only be used once. A second call with the same token returns `401`.
- The path scope (`/api/auth`) ensures the `ss_refresh_token` cookie is only sent to this endpoint — it is never attached to regular API requests.

---

## POST /api/auth/logout

Revokes the caller's refresh token and instructs the browser to delete both auth cookies. No authentication required — the endpoint is intentionally idempotent and always succeeds.

### Request

No body. The `ss_refresh_token` cookie, if present, is read and revoked.

---

### Responses

#### 204 No Content — logout successful

No response body. Both cookies are cleared via `Max-Age=0`.

**Set-Cookie headers on 204:**

```
Set-Cookie: ss_access_token=; Path=/api; Max-Age=0; HttpOnly; Secure; SameSite=Strict
Set-Cookie: ss_refresh_token=; Path=/api/auth; Max-Age=0; HttpOnly; Secure; SameSite=Strict
```

This response is returned regardless of whether a valid refresh token cookie was present.

---

### Frontend Handling Guidance

- Always call this endpoint before clearing local auth state — it performs the server-side token revocation.
- On `204`: clear `localStorage`, navigate to `/`.
- The endpoint always returns `204` — there is no error case to handle.

---

### Notes

- If the `ss_refresh_token` cookie is absent or its value is not found in the database, the call still returns `204` with cleared cookies. Clients can always trust that after this response the session is fully terminated.
- If the access token is still valid when logout is called, it will remain technically valid until it naturally expires (15 min). The frontend discards it immediately by dropping the cookie.
