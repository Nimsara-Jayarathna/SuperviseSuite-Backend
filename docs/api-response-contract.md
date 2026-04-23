# API Response Contract (Standardized)

## 1. Purpose
This document defines the single response contract used by backend application APIs after response standardization.

Scope:
- controller success responses
- validation and domain errors
- security/auth failures
- framework fallback errors for API requests

## 2. Standard Envelope
All standard JSON API responses use the same top-level keys:

- `success`
- `message`
- `data`
- `error`
- `meta`

### 2.1 Success shape
```json
{
  "success": true,
  "message": "Projects loaded.",
  "data": [],
  "error": null,
  "meta": {
    "timestamp": "2026-03-14T16:30:00Z",
    "path": "/api/student/projects",
    "traceId": null
  }
}
```

### 2.2 Error shape
```json
{
  "success": false,
  "message": "Validation failed.",
  "data": null,
  "error": {
    "code": "VALIDATION_ERROR",
    "status": 400,
    "details": [
      {
        "field": "email",
        "issue": "must be a well-formed email address"
      }
    ]
  },
  "meta": {
    "timestamp": "2026-03-14T16:30:00Z",
    "path": "/api/auth/login",
    "traceId": null
  }
}
```

## 3. Authoritative Builders
Response creation is centralized in one place:

- `src/main/java/com/supervisesuite/backend/common/api/ApiResponseFactory.java`

Model classes:
- `ApiResponse<T>` (top-level envelope)
- `ApiErrorBody` (`code`, `status`, `details`)
- `ApiMeta` (`timestamp`, `path`, `traceId`)

## 4. Success Response Path
Controllers use `ApiResponseFactory` (`ok`, `created`) for all standard JSON success responses.

Primary controller coverage:
- `AuthController` (`/api/auth/*` except logout)
- `StudentController` (`/api/student/*`)
- `SupervisorController` (`/api/supervisor/*`)

## 5. Error Response Path
### 5.1 MVC/Controller exceptions
`GlobalExceptionHandler` (`@RestControllerAdvice`) maps exceptions to the unified error envelope, including:

- validation errors
- malformed JSON/body errors
- domain/business exceptions
- authentication/authorization exceptions reaching MVC
- conflict/not-found/internal fallback
- method/media/no-handler framework exceptions

File:
- `src/main/java/com/supervisesuite/backend/common/error/GlobalExceptionHandler.java`

### 5.2 Security layer errors
Spring Security failures are outside MVC advice and are standardized via:

- `SecurityErrorResponseWriter`
- wired in `SecurityConfig` `authenticationEntryPoint` and `accessDeniedHandler`

This ensures 401/403 security responses use the same envelope as MVC errors.

### 5.3 Rate-limiting and temporary availability

For throttled requests and transient backend unavailability, the API contract uses:

- `TOO_MANY_REQUESTS` with HTTP `429`
- `SERVICE_UNAVAILABLE` with HTTP `503`

Rate-limited responses may include additional standard headers:

- `Retry-After`
- `X-RateLimit-Limit`
- `X-RateLimit-Remaining`
- `X-RateLimit-Reset`

## 6. Framework Fallback Handling
To prevent default Spring `/error` response leakage for API paths:

- `ApiErrorController` handles fallback error dispatches using the unified envelope.
- MVC settings enforce exception-based routing for unmapped handlers.

Files:
- `src/main/java/com/supervisesuite/backend/common/error/ApiErrorController.java`
- `src/main/resources/application.yaml`
  - `spring.mvc.throw-exception-if-no-handler-found: true`
  - `spring.web.resources.add-mappings: false`

## 7. Filter/Security Double-Write Protection
Security response writing includes committed-response checks and immediate return behavior to avoid duplicate response writes.

File:
- `src/main/java/com/supervisesuite/backend/config/SecurityErrorResponseWriter.java`

## 8. Intentional Exceptions
These are intentionally outside the JSON envelope:

1. `POST /api/auth/logout` returns `204 No Content` and clears auth cookies.
2. Actuator endpoints may use Spring Actuator-native response shapes.

All standard application JSON APIs should otherwise use the unified envelope.

## 9. Client Parsing Guidance
Clients should parse all standard API responses via the same keys:

- `success`
- `message`
- `data`
- `error.code`
- `error.status`
- `error.details`
- `meta.timestamp`
- `meta.path`
- `meta.traceId`

## 10. Related Docs
- `docs/api/auth.md`
- `docs/api/student.md`
- `docs/api/supervisor.md`
