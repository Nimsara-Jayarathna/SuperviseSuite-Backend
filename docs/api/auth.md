# Auth API

## Endpoints

- [POST /api/auth/register](#post-apiauthregister)

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

- On `201`: store user info, navigate to login or dashboard depending on flow.
- On `400 VALIDATION_ERROR`: map each `details[].field` to the corresponding input and show `details[].issue` inline.
- On `409 CONFLICT`: show a targeted message on the specific field (`email` or `registrationNumber`).
- See [error-handling.md](error-handling.md) for the full error response contract and all error codes.

---

### Notes

- Assigned role is always `STUDENT`. Supervisor accounts are not self-registered.
- Password is never returned in any response. BCrypt hash is stored server-side.
- Email verification is out of scope — accounts are considered verified upon registration.
