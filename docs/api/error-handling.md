# API Error Handling Contract

## A) Standard Error Response Contract

All backend errors should follow this payload:

- `timestamp` (string, ISO-8601): time error was generated.
- `status` (number): HTTP status code.
- `error` (string): HTTP reason phrase (display label only).
- `code` (string): stable application error code for client logic.
- `message` (string): human-readable summary.
- `path` (string): request URI path.
- `traceId` (string | null): correlation id (currently nullable placeholder).
- `details` (array of `{ field, issue }`): optional validation-level details.

## B) Validation Errors

For validation failures, backend uses `code = VALIDATION_ERROR` and fills `details[]`.

Each detail item:

- `field`: input field or property path.
- `issue`: validation message to show or map in UI.

## C) Error Code Catalog

- `VALIDATION_ERROR` -> `400` : invalid input payload/params. FE action: show field/form errors.
- `BAD_REQUEST` -> `400` : malformed request body/shape. FE action: show generic input error and allow retry.
- `UNAUTHORIZED` -> `401` : missing/invalid auth context. FE action: redirect to login/refresh session.
- `FORBIDDEN` -> `403` : authenticated but not allowed. FE action: show permission message.
- `NOT_FOUND` -> `404` : requested resource missing. FE action: show empty/not-found state.
- `CONFLICT` -> `409` : duplicate/state conflict/integrity issue. FE action: show conflict message and refresh data.
- `SERVICE_UNAVAILABLE` -> `503` : backend/dependency unavailable. FE action: show retry option.
- `INTERNAL_ERROR` -> `500` : unexpected server failure. FE action: show generic error and retry/report.

## D) Concrete Examples

### 400 VALIDATION_ERROR

```json
{
  "timestamp": "2026-02-18T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_ERROR",
  "message": "Validation failed.",
  "path": "/api/projects",
  "traceId": null,
  "details": [
    { "field": "name", "issue": "must not be blank" },
    { "field": "status", "issue": "must be one of [DRAFT, ACTIVE]" }
  ]
}
```

### 401 UNAUTHORIZED

```json
{
  "timestamp": "2026-02-18T10:00:00Z",
  "status": 401,
  "error": "Unauthorized",
  "code": "UNAUTHORIZED",
  "message": "Authentication required.",
  "path": "/api/projects",
  "traceId": null,
  "details": []
}
```

### 403 FORBIDDEN

```json
{
  "timestamp": "2026-02-18T10:00:00Z",
  "status": 403,
  "error": "Forbidden",
  "code": "FORBIDDEN",
  "message": "Access denied.",
  "path": "/api/admin",
  "traceId": null,
  "details": []
}
```

### 404 NOT_FOUND

```json
{
  "timestamp": "2026-02-18T10:00:00Z",
  "status": 404,
  "error": "Not Found",
  "code": "NOT_FOUND",
  "message": "Resource not found.",
  "path": "/api/projects/123",
  "traceId": null,
  "details": []
}
```

### 409 CONFLICT

```json
{
  "timestamp": "2026-02-18T10:00:00Z",
  "status": 409,
  "error": "Conflict",
  "code": "CONFLICT",
  "message": "Data conflict detected.",
  "path": "/api/projects",
  "traceId": null,
  "details": []
}
```

### 500 INTERNAL_ERROR

```json
{
  "timestamp": "2026-02-18T10:00:00Z",
  "status": 500,
  "error": "Internal Server Error",
  "code": "INTERNAL_ERROR",
  "message": "An unexpected error occurred.",
  "path": "/api/projects",
  "traceId": null,
  "details": []
}
```

## E) Frontend Handling Guidance

- Use `code` + `status` for UI logic and behavior.
- Show `message` to users, with UX-safe fallback copy.
- Do not use `error` for logic decisions.
- Handle `traceId` as optional (`null` is valid for now).
