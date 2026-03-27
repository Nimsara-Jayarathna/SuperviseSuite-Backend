# API Error Handling Contract

This file documents the **current standardized error behavior** used by backend JSON APIs.

Related:
- Global contract: `docs/api-response-contract.md`
- Auth endpoint examples: `docs/api/auth.md`

## 1) Unified Envelope

All standard API error responses use the same top-level shape:

```json
{
  "success": false,
  "message": "Validation failed.",
  "data": null,
  "error": {
    "code": "VALIDATION_ERROR",
    "status": 400,
    "details": [
      { "field": "email", "issue": "must be a well-formed email address" }
    ]
  },
  "meta": {
    "timestamp": "2026-03-14T16:30:00Z",
    "path": "/api/auth/register",
    "traceId": null
  }
}
```

Top-level keys are always:
- `success`
- `message`
- `data`
- `error`
- `meta`

## 2) Error Fields

- `message`: client-safe summary text to display.
- `error.code`: stable application code for frontend logic.
- `error.status`: HTTP status number.
- `error.details`: field-level validation details where applicable.
- `meta.timestamp`: ISO-8601 server timestamp.
- `meta.path`: request path.
- `meta.traceId`: correlation id if available, else `null`.

## 3) Error Code Catalog

- `VALIDATION_ERROR` -> `400`
- `BAD_REQUEST` -> `400`
- `UNAUTHORIZED` -> `401`
- `FORBIDDEN` -> `403`
- `NOT_FOUND` -> `404`
- `CONFLICT` -> `409`
- `INTERNAL_ERROR` -> `500`

`SERVICE_UNAVAILABLE` is produced by frontend network fallback handling, not normally emitted by backend controllers.

## 4) Security/Auth Failures

Security failures are standardized to the same envelope:
- unauthenticated protected request -> `401 UNAUTHORIZED`
- authenticated but forbidden -> `403 FORBIDDEN`

These are written by security handlers and match the same response contract as MVC/controller exceptions.

## 5) Framework Fallback Errors

Fallback framework errors for API requests are also wrapped in the same envelope (no raw Spring default `/error` shape for normal API paths).

## 6) Validation Details

For validation cases, `error.details[]` entries use:
- `field`
- `issue`

Example:

```json
{
  "success": false,
  "message": "Validation failed.",
  "data": null,
  "error": {
    "code": "VALIDATION_ERROR",
    "status": 400,
    "details": [
      { "field": "repositoryUrl", "issue": "Repository URL must be a valid GitHub repository URL (e.g., https://github.com/owner/repo)" }
    ]
  },
  "meta": {
    "timestamp": "2026-03-14T16:30:00Z",
    "path": "/api/supervisor/projects/abc/repository",
    "traceId": null
  }
}
```

## 7) Intentional Exceptions

The following intentionally do **not** return the JSON envelope:
- `POST /api/auth/logout` -> `204 No Content`
- Actuator endpoints (framework-native responses)
