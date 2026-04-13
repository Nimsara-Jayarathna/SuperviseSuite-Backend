# QA Test Cases - Project File Management (FE + BE)
SuperviseSuite | Sprint 4 | Project Files / Attachments

## 1. Introduction
This document defines end-to-end QA coverage for project file management across frontend and backend.  
Scope includes supervisor and student role behaviors, API validation/security, S3 pre-signed URL flows, soft delete behavior, and schema-level expectations.

## 2. In Scope
- Files tab list, upload, download, and delete (supervisor only).
- Upload URL generation and confirm-upload metadata persistence.
- FE validation driven by backend config (`maxFileSizeBytes`, `maxFileNameLength`, `allowedTypes`).
- Embedded files payload in project detail responses.
- Soft-delete behavior (`deleted_at`) and list filtering.
- Storage integration contract (`StorageService` / S3 presigned URLs).

## 3. Out of Scope
- Virus scanning/content inspection.
- Multipart/chunked upload resume.
- File preview/inline rendering.
- Version history per file.

## 4. Test Data and Preconditions
- Roles:
  - `SUPERVISOR` user with ownership of project `P1`.
  - `STUDENT` user assigned to `P1`.
  - Unrelated student/supervisor users without access to `P1`.
- Project:
  - Existing active project `P1` with members and at least one milestone.
- Environment variables configured:
  - `PROJECT_FILES_AWS_ACCESS_KEY_ID`
  - `PROJECT_FILES_AWS_SECRET_ACCESS_KEY`
  - `PROJECT_FILES_AWS_REGION`
  - `PROJECT_FILES_BUCKET_NAME`
  - `PROJECT_FILES_PRESIGNED_URL_EXPIRY_SECONDS`
  - `PROJECT_FILES_MAX_FILE_SIZE_BYTES`
  - `PROJECT_FILES_MAX_FILE_NAME_LENGTH`
  - `PROJECT_FILES_ALLOWED_TYPES`
- Default config reference:
  - allowed: `pdf,docx,pptx,zip`
  - max size: `10485760` (10 MB)
  - max name length: `50`
  - presigned expiry: `180` or environment override

## 5. Test Execution Summary
| Category | Total | Passed | Failed |
|---|---:|---:|---:|
| FE Functional + UX | 24 |  |  |
| BE API + Validation | 24 |  |  |
| Security + Access Control | 10 |  |  |
| Data/Schema + Storage | 8 |  |  |
| Total | 66 |  |  |

## 6. Frontend Test Cases
| Test Case ID | Scenario | Steps | Expected Result | Priority |
|---|---|---|---|---|
| PF-FE-01 | Supervisor Files tab loads from seeded project detail | Login as supervisor -> open project detail with embedded `files` | Files table renders without immediate extra list call; shows data from `project.files` | High |
| PF-FE-02 | Student Files tab loads from seeded project detail | Login as student -> open project detail with embedded `files` | Files table renders from seeded data | High |
| PF-FE-03 | Files refresh button reloads list (supervisor) | Click refresh in Files tab | Loading state shown; list refreshed from `/files` endpoint | High |
| PF-FE-04 | Files refresh button reloads list (student) | Click refresh in student Files tab | Loading state shown; list refreshed | High |
| PF-FE-05 | Upload modal open/close | Open Upload modal -> close with `X` | Modal opens/closes cleanly, no state leak | Medium |
| PF-FE-06 | Upload button disabled with no file selected | Open upload modal; do not select file | Upload action disabled/blocked; no API call | High |
| PF-FE-07 | Upload button disabled with empty file name | Select file then clear filename input | Upload action blocked with inline validation | High |
| PF-FE-08 | Drag-and-drop select file | Drop valid file into drop zone | File selected; selected-file area updates | Medium |
| PF-FE-09 | File picker accept filter | Click choose file | Browser picker is filtered to allowed extensions | Medium |
| PF-FE-10 | FE type validation | Select unsupported extension (e.g. `.png`) | Inline friendly error shown; upload blocked | High |
| PF-FE-11 | FE size validation | Select file > configured max size | Inline friendly error shown; upload blocked before API | High |
| PF-FE-12 | FE filename length validation | Enter name > configured max length | Input capped; counter reflects limit; submit blocked if invalid | High |
| PF-FE-13 | Character counter placement/value | Type in filename field | Counter updates correctly and matches limit config | Low |
| PF-FE-14 | Selected file long-name truncation | Select long filename | Selected text truncates with ellipsis; full name available via tooltip/title | Medium |
| PF-FE-15 | Upload request lifecycle modal - loading | Upload valid file | Shared request-state modal shows loading while upload/confirm runs | High |
| PF-FE-16 | Upload success behavior | Complete upload successfully | Success state shown; request modal auto closes; upload modal closes; row added to list | High |
| PF-FE-17 | Upload failure behavior | Force confirm/upload-url API failure | Error state shown in request modal + inline modal context; upload modal stays open | High |
| PF-FE-18 | Retry after upload failure | Trigger failure then correct input and retry | Retry succeeds without re-opening modal | High |
| PF-FE-19 | Download action | Click download icon/button on row | Pre-signed download URL fetched and opened | High |
| PF-FE-20 | Supervisor delete success | Click delete -> confirm | Request-state modal success; delete modal closes; row removed locally | High |
| PF-FE-21 | Supervisor delete failure | Force delete API error | Request-state modal error; delete modal remains for retry | High |
| PF-FE-22 | Student cannot see delete action | Open student Files tab | No delete controls rendered anywhere | High |
| PF-FE-23 | File type badge rendering | List has `pdf/docx/pptx/zip` rows | Type is shown as normalized badge label, not raw MIME | Medium |
| PF-FE-24 | Uploaded by rendering | List row has uploader + role | Uploader name and role badge displayed inline without layout break | Medium |

## 7. Backend API Test Cases
| Test Case ID | Endpoint | Scenario | Request/Steps | Expected Result |
|---|---|---|---|---|
| PF-BE-01 | `GET /api/supervisor/projects/{projectId}/files` | Supervisor list success | Auth as owner supervisor | `200`, `data.files[]`, `data.config` returned |
| PF-BE-02 | `GET /api/student/projects/{projectId}/files` | Student list success | Auth as assigned student | `200`, same response shape |
| PF-BE-03 | `GET .../files` | Soft-deleted rows excluded | Seed active + deleted rows | Deleted rows not returned |
| PF-BE-04 | `POST .../files/upload-url` | Valid supervisor upload-url | Valid `fileName/contentType` | `200`, returns `presignedUrl` + UUID `s3Key` |
| PF-BE-05 | `POST .../files/upload-url` | Valid student upload-url | Valid request | `200`, returns URL + UUID key |
| PF-BE-06 | `POST .../files/upload-url` | Reject unsupported file type | `fileName=.png`, `contentType=image/png` | `400 VALIDATION_ERROR` |
| PF-BE-07 | `POST .../files/upload-url` | Reject mismatched MIME and extension | `fileName=.pdf`, `contentType=pptx mime` | `400 VALIDATION_ERROR` |
| PF-BE-08 | `POST .../files/upload-url` | Reject file name > limit | Name length > config | `400 VALIDATION_ERROR` |
| PF-BE-09 | `POST .../files/confirm` | Supervisor confirm success | Valid UUID `s3Key`, valid type/size/name | `200`, normalized `fileType` extension in dto |
| PF-BE-10 | `POST .../files/confirm` | Student confirm success | Valid payload | `200`, row persisted |
| PF-BE-11 | `POST .../files/confirm` | Reject non-UUID `s3Key` | `s3Key=projects/...` | `400 VALIDATION_ERROR` |
| PF-BE-12 | `POST .../files/confirm` | Reject `fileSize <= 0` | zero/negative size | `400 VALIDATION_ERROR` |
| PF-BE-13 | `POST .../files/confirm` | Reject file size over max | `fileSize=max+1` | `400 VALIDATION_ERROR` |
| PF-BE-14 | `POST .../files/confirm` | Reject disallowed type | extension/type not in allowed list | `400 VALIDATION_ERROR` |
| PF-BE-15 | `POST .../files/confirm` | Reject type-extension mismatch | `.pdf` + pptx MIME | `400 VALIDATION_ERROR` |
| PF-BE-16 | `GET .../files/{fileId}/download-url` | Supervisor download-url success | Valid file ID in project | `200`, pre-signed URL |
| PF-BE-17 | `GET .../files/{fileId}/download-url` | Student download-url success | Assigned student | `200`, pre-signed URL |
| PF-BE-18 | `GET .../download-url` | Deleted file cannot be downloaded | Soft-delete then call | `404 NOT_FOUND` |
| PF-BE-19 | `DELETE /api/supervisor/projects/{projectId}/files/{fileId}` | Supervisor delete success | Auth owner supervisor | `200`, row soft-deleted and storage delete invoked |
| PF-BE-20 | `DELETE ...` | Delete non-existing file | random UUID fileId | `404 NOT_FOUND` |
| PF-BE-21 | Project detail API | Supervisor detail includes embedded files | `GET /api/supervisor/projects/{projectId}` | `data.files.items[]` + `data.files.config` present |
| PF-BE-22 | Project detail API | Student detail includes embedded files | `GET /api/student/projects/{projectId}` | `data.files.items[]` + `data.files.config` present |
| PF-BE-23 | Config propagation | Config values reflect env override | set env overrides -> call list/detail | `config` matches overridden values |
| PF-BE-24 | Timestamp population | Confirm upload sets timestamps | create file and inspect response/db | `createdAt` non-null, `updatedAt` non-null |

## 8. Security and Access Control Test Cases
| Test Case ID | Scenario | Steps | Expected Result |
|---|---|---|---|
| PF-SEC-01 | Unauthenticated list request blocked | Call list endpoint without token | `401 UNAUTHORIZED` |
| PF-SEC-02 | Student calling supervisor files endpoint | Auth student -> call `/api/supervisor/.../files` | `403 FORBIDDEN` |
| PF-SEC-03 | Supervisor calling student files endpoint | Auth supervisor -> call `/api/student/.../files` | `403 FORBIDDEN` |
| PF-SEC-04 | Student accessing unassigned project files | Auth student not in project | `404 NOT_FOUND` |
| PF-SEC-05 | Supervisor accessing non-owned project files | Auth supervisor not owner | `404 NOT_FOUND` |
| PF-SEC-06 | Student delete attempt via API | Call supervisor delete endpoint as student | `403 FORBIDDEN` |
| PF-SEC-07 | Malformed projectId/fileId | Use invalid UUID path value | `404 NOT_FOUND` |
| PF-SEC-08 | Confirm upload tampering - oversized | FE bypass with manual payload > max size | `400 VALIDATION_ERROR` |
| PF-SEC-09 | Confirm upload tampering - disallowed type | FE bypass with custom type | `400 VALIDATION_ERROR` |
| PF-SEC-10 | Presigned URL expiry honored | Use URL after expiry window | S3 rejects expired signature |

## 9. Data, Schema, and Storage Contract Test Cases
| Test Case ID | Scenario | Steps | Expected Result |
|---|---|---|---|
| PF-DB-01 | Migration applies cleanly | Run Flyway up to `V23` | `project_files` exists with expected columns/constraints/indexes |
| PF-DB-02 | Column naming alignment | Execute list query path | Uses `s3_key` mapping; no `s3key` SQL error |
| PF-DB-03 | Check constraint on file size | Insert invalid `file_size <= 0` at DB level | DB rejects row |
| PF-DB-04 | Soft delete persistence | Delete a file | `deleted_at` populated, row still present |
| PF-DB-05 | Project cascade delete | Delete project owning files | Child `project_files` rows removed (`ON DELETE CASCADE`) |
| PF-DB-06 | Uploaded by FK integrity | Insert unknown uploader ID | DB rejects with FK violation |
| PF-DB-07 | Presigned URL TTL config | Change `PROJECT_FILES_PRESIGNED_URL_EXPIRY_SECONDS` | URL `X-Amz-Expires` reflects configured value (min 60 in service) |
| PF-DB-08 | UUID-only key generation | Request upload URL repeatedly | `s3Key` is UUID only; no projectId/timestamp/filename leakage |

## 10. Regression Checklist
- Existing project detail (overview/team/milestones/github/jira) still loads with embedded files block.
- Student/supervisor route guards unchanged.
- No regression in request-state modal behavior in unrelated flows.
- Files tab empty/loading/error states remain consistent with design system.
- Download and delete icons/tooltips remain keyboard accessible.

## 11. Suggested Automation Coverage
- Backend integration tests:
  - `ProjectFileServiceImpl` validation matrix.
  - Controller tests for role/access boundaries.
  - `project_files` repository filtering on `deleted_at`.
- Frontend unit/component tests:
  - `useSupervisorProjectFiles` and `useStudentProjectFiles` (seed/lazy/load/update/remove).
  - `UploadFileModal` validation and request-state transitions.
  - Supervisor delete flow modal state transitions.

## 12. Sign-off
- QA Engineer:
- Date:
- Build/Commit:
- Environment:
- Notes:
