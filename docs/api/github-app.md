# GitHub App Integration API

Backend-managed GitHub integration endpoints used for installation setup, webhook processing, and project-scoped GitHub dashboard data.

## Public Callback/Webhook Endpoints

These endpoints are intentionally unauthenticated in `SecurityConfig` to support GitHub callback delivery.

### GET /api/github/setup

Processes GitHub App setup callback and redirects browser back to frontend.

Required query params:

- `installation_id` (number)
- `state` (base64/base64url JSON)

Optional query params:

- `setup_action`

Expected `state` JSON shape:

```json
{
  "projectId": "044e20b9-b96d-4764-bd0c-3f1f9c20c002",
  "repositoryUrl": "https://github.com/owner/repo"
}
```

Behavior:

- decodes `state`
- validates `projectId`
- stores/updates installation (`github_app_installations`)
- links installation to project repository (`project_repositories.installation_id`)
- triggers GitHub refresh for linked repository
- redirects with `303 See Other`

Redirect outcomes:

- success: `{FRONTEND_BASE_URL}/supervisor/projects/{projectId}?tab=github&githubSetup=success`
- failure: `{FRONTEND_BASE_URL}/supervisor/projects/{projectId}?tab=overview&githubSetup=failed`

### POST /api/github/webhooks

Processes GitHub webhooks with HMAC verification.

Headers:

- `X-GitHub-Event` (required)
- `X-Hub-Signature-256` (required)

Body:

- raw GitHub webhook JSON payload

Behavior:

- validates signature using `GITHUB_APP_WEBHOOK_SECRET`
- handles installation lifecycle events (`installation`, `installation_repositories`)
- updates installation status and unlinks project repository installations on delete events

## Project-Scoped GitHub Dashboard Endpoints

All project endpoints are authenticated and role-protected (`SUPERVISOR`/`STUDENT`).

### Shared Read Endpoints (Supervisor + Student)

- `GET /api/{role}/projects/{projectId}/github`
- `GET /api/{role}/projects/{projectId}/github/activity?page=&size=`
- `GET /api/{role}/projects/{projectId}/github/contributors?page=&size=`

Where `{role}` is either:

- `supervisor`
- `student`

Response source:

- DB-backed cache (`project_repositories`, `project_repository_commits`, `project_repository_contributors`)
- frontend never calls GitHub directly

### Supervisor Refresh Endpoint

- `POST /api/supervisor/projects/{projectId}/github/refresh`

Behavior:

- resolves linked repository
- fetches metadata + commits from GitHub using app installation where available
- updates DB cache and sync fields
- returns success/failure via standard API envelope

## Main Project Detail GitHub Preview

Both project-detail endpoints now include `github` preview block:

- `GET /api/supervisor/projects/{projectId}`
- `GET /api/student/projects/{projectId}`

Preview block shape:

```json
{
  "repositoryLinked": true,
  "repositories": [
    {
      "id": "repo-id",
      "name": "repo-name",
      "url": "https://github.com/owner/repo",
      "defaultBranch": "main",
      "lastSyncedAt": "2026-03-22T05:24:15.622132Z"
    }
  ],
  "activitySummary": {
    "totalCommits": 80,
    "lastActivityAt": "2026-03-12T06:41:23Z",
    "status": "idle"
  },
  "contributorsPreview": [
    { "name": "User 1", "commitCount": 80 }
  ],
  "recentCommitsPreview": [
    {
      "sha": "e21e845dcdd5bc6629250c2e210619c7d81faa39",
      "message": "Merge pull request #12 ...",
      "author": "User 1",
      "committedAt": "2026-03-12T06:41:23Z",
      "type": "merge"
    }
  ]
}
```

Notes:

- `contributorsPreview` is capped to top 4 contributors.
- `repositories` remains list-shaped for future multi-repo support.
- current UI uses a single active repository.
