# VinFast QC Copilot - API Conventions

Version: `v0.3`  
Status: API Contract Draft  
Base path: `/api/v1`  
Applies to: Frontend-facing REST APIs and shared API conventions  
Related docs: `docs/api/00-api-overview.md`, PRD v0.7, ERD v0.5

---

## 1. Purpose

This document defines the REST API conventions for VinFast QC Copilot.

The goals are:

```text
1. Keep API endpoints predictable, stable and easy for frontend to consume.
2. Keep backend implementation aligned with the ERD v0.5 domain model.
3. Follow Microsoft-style REST API design where practical.
4. Use RFC 9457 Problem Details for standardized error responses.
5. Avoid leaking secrets, raw credentials, raw cURL, request tokens or unsanitized artifacts.
6. Make async execution flows explicit: preflight, start run, SSE progress, cancel and result retrieval.
```

This document does not list every endpoint. Endpoint-level contracts are defined in separate files:

```text
docs/api/02-auth-permission-contract.md
docs/api/03-workspace-project-api.md
docs/api/04-secret-provider-api.md
docs/api/05-dataset-api.md
docs/api/06-chatbot-target-api.md
docs/api/07-evaluator-api.md
docs/api/08-test-run-api.md
docs/api/09-sse-events-contract.md
docs/api/10-runner-internal-api.md
```

---

## 2. Reference Style

The API should follow these principles:

```text
1. Resources are nouns, not verbs.
2. Collections use plural resource names.
3. HTTP methods describe the operation.
4. Paths are stable and predictable.
5. Request and response bodies use JSON unless the endpoint explicitly accepts file upload.
6. Errors use RFC 9457 Problem Details with VFQC extension fields.
7. Sensitive data is never returned in plaintext.
8. API versioning is explicit in the URL path.
```

Reference materials:

```text
- Microsoft REST API Guidelines
  https://github.com/microsoft/api-guidelines

- Microsoft Graph REST API Guidelines
  https://github.com/microsoft/api-guidelines/blob/vNext/graph/GuidelinesGraph.md

- RFC 9457: Problem Details for HTTP APIs
  https://www.rfc-editor.org/rfc/rfc9457.html

- Spring Framework Error Responses / ProblemDetail
  https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html
```

---

## 3. API Base Path

All frontend-facing APIs must use:

```text
/api/v1
```

Examples:

```http
GET  /api/v1/workspaces
POST /api/v1/workspaces
GET  /api/v1/projects/{projectId}
POST /api/v1/projects/{projectId}/test-runs
```

Do not expose versionless frontend APIs:

```http
GET /workspaces
GET /projects/{projectId}
```

Internal runner APIs are mounted behind nginx under `/internal/runner`. They are not frontend-facing APIs and intentionally do not use `/api/v1` in this deployment.

Example:

```http
GET /internal/runner/test-runs/{testRunId}/config
```

---

## 4. Resource Naming

### 4.1. Path casing

Use lowercase kebab-case for URL path segments.

Correct:

```http
/api/v1/workspaces/{workspaceId}/projects
/api/v1/chatbot-targets/{targetId}/test-connection
/api/v1/test-runs/{testRunId}/results
```

Avoid:

```http
/api/v1/workspaceProjects
/api/v1/chatbotTargets
/api/v1/test_runs
```

---

### 4.2. Collection names

Use plural nouns for collections.

Correct:

```http
/workspaces
/projects
/datasets
/dataset-versions
/chatbot-targets
/evaluators
/test-runs
```

Avoid singular collection names:

```http
/workspace
/project
/dataset
```

---

### 4.3. Resource IDs

Use explicit ID names in documentation and DTOs.

Examples:

```text
workspaceId
projectId
datasetId
datasetVersionId
chatbotTargetId
evaluatorId
evaluatorVersionId
testRunId
testCaseResultId
```

Use UUID IDs for main resources exposed to frontend.

Use BIGINT IDs only for detail/high-volume resources already defined that way in ERD v0.5, for example:

```text
workspaceMemberId
datasetRowId
chatbotTargetExampleId
testCaseResultId
```


### 4.4. Resource display name policy

Workspace, project, dataset, chatbot target, evaluator, secret and provider names must follow one shared validation policy unless an endpoint explicitly tightens it.

Default name normalization:

```text
1. Trim leading and trailing whitespace.
2. Collapse repeated internal whitespace to a single space for uniqueness comparison.
3. Preserve the user's display casing in the stored `name` field.
4. Compare uniqueness case-insensitively using normalized name.
5. Reject empty names after trimming.
6. Reject control characters and path separators that can confuse file/export paths.
```

Default length limits:

| Resource | Max length | Uniqueness scope | Duplicate error code |
| -------- | ----------: | ---------------- | -------------------- |
| Workspace name | 120 | Visible workspaces for current user or workspace policy | `WORKSPACE_NAME_ALREADY_EXISTS` |
| Project name | 120 | Same workspace, excluding deleted projects | `PROJECT_NAME_ALREADY_EXISTS` |
| Dataset name | 160 | Same project/workspace/personal scope, excluding deleted datasets | `DATASET_NAME_ALREADY_EXISTS` |
| Chatbot target name | 160 | Same project, excluding deleted targets | `CHATBOT_TARGET_NAME_ALREADY_EXISTS` |
| Evaluator name | 160 | Same project, excluding deleted evaluators | `EVALUATOR_NAME_ALREADY_EXISTS` |
| Secret name | 160 | Same owner/scope or workspace/scope, excluding deleted secrets | `SECRET_NAME_ALREADY_EXISTS` |
| LLM provider name | 160 | Same owner/scope or workspace/scope, excluding deleted providers | `PROVIDER_NAME_ALREADY_EXISTS` |

Description fields:

```text
1. Description is optional unless a module states otherwise.
2. Default max length is 1000 characters.
3. Backend may trim descriptions but should preserve line breaks.
4. Description is not part of uniqueness checks.
```

Rename behavior:

```text
1. PATCH rename uses the same validation as create.
2. Rename must be rejected with the module-specific duplicate error code if normalized name conflicts.
3. Rename must not mutate historical TestRun snapshots, artifacts, previous DatasetVersions or previous EvaluatorVersions.
4. Historical run screens may show the locked display name stored in run snapshots, not the current resource name.
```

Implementation rule:

```text
Frontend validation should mirror these rules for fast feedback, but backend validation remains authoritative.
```

---

## 5. HTTP Methods

Use HTTP methods consistently.

| Method | Meaning | Example |
| ------ | ------- | ------- |
| `GET` | Read a resource or collection | `GET /api/v1/projects/{projectId}` |
| `POST` | Create a resource or execute a non-idempotent action | `POST /api/v1/workspaces` |
| `PATCH` | Partially update a resource | `PATCH /api/v1/projects/{projectId}` |
| `DELETE` | Soft delete/archive a resource when supported | `DELETE /api/v1/projects/{projectId}` |

Avoid verbs in paths when resource-oriented paths are enough.

Good:

```http
POST  /api/v1/workspaces
PATCH /api/v1/projects/{projectId}
```

Avoid:

```http
POST /api/v1/createWorkspace
POST /api/v1/updateProject
```

---

## 6. Action Endpoints

Some operations are actions rather than pure CRUD. Action endpoints are allowed only when the operation cannot be cleanly represented as resource CRUD.

Allowed MVP action endpoints:

```http
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
POST /api/v1/chatbot-targets/{targetId}/test-connection
POST /api/v1/projects/{projectId}/dataset-import-jobs
POST /api/v1/dataset-import-jobs/{importJobId}/confirm
POST /api/v1/dataset-import-jobs/{importJobId}/cancel
POST /api/v1/projects/{projectId}/test-runs/preflight
POST /api/v1/test-runs/{testRunId}/cancel
```

Read-only helper endpoints are allowed when they aggregate existing resources for a UI workflow. They should use `GET` and remain side-effect free.

Allowed MVP helper endpoints:

```http
GET /api/v1/projects/{projectId}/setup-status
GET /api/v1/test-runs/{testRunId}/comparison-matrix
```

Rules:

```text
1. Action names use kebab-case.
2. Action endpoints must use POST unless the action is clearly read-only.
3. Action endpoints must define idempotency behavior explicitly.
4. Action endpoints must return a normal resource response or a specific action response DTO.
```

---

## 7. Authentication Header and Cookie Convention

MVP uses JWT-style access tokens plus refresh tokens stored in HttpOnly cookies.

Normal API requests use:

```http
Authorization: Bearer <accessToken>
```

Refresh tokens are not sent in the JSON response body. Backend sets refresh tokens through the `Set-Cookie` response header:

```http
Set-Cookie: vfqc_refresh_token=<token>; HttpOnly; Secure; SameSite=Lax; Path=/api/v1/auth; Max-Age=604800
```

Rules:

```text
1. Access token is short-lived and sent through Authorization Bearer header.
2. Refresh token is longer-lived and stored only in an HttpOnly Secure cookie.
3. Refresh token must not be stored in localStorage or sessionStorage.
4. Refresh token must not be returned in response JSON.
5. Browser clients must call refresh/logout endpoints with credentials included when frontend/backend origins differ.
6. Backend CORS must allow credentials only for explicit trusted origins, never wildcard origin.
```

Details are defined in `docs/api/02-auth-permission-contract.md`.

---

## 8. Request Body Convention

Request bodies use JSON by default.

Required header:

```http
Content-Type: application/json
```

Request field names use `camelCase`.

Example:

```json
{
  "name": "VF Sales Chatbot",
  "description": "Evaluation project for sales chatbot"
}
```

Do not use snake_case in API DTOs unless the field is a raw external payload that must preserve original shape.

Correct:

```json
{
  "workspaceId": "4bcfb80f-b2e1-4449-ad72-5c3b4ecb42a1",
  "createdAt": "2026-07-02T10:00:00+07:00"
}
```

Avoid:

```json
{
  "workspace_id": "4bcfb80f-b2e1-4449-ad72-5c3b4ecb42a1",
  "created_at": "2026-07-02T10:00:00+07:00"
}
```

Database columns may remain snake_case. API DTOs should be camelCase.

---

## 9. File Upload Convention

Endpoints that upload Excel or artifacts may use `multipart/form-data`.

Example:

```http
POST /api/v1/projects/{projectId}/dataset-import-jobs
Content-Type: multipart/form-data
```

Rules:

```text
1. File upload endpoints must document accepted MIME types and size limits.
2. Excel import job creation may parse metadata and sample rows, but must not create ACTIVE dataset rows before confirm.
3. Import job responses should return parsed metadata and preview sample rows only.
4. Confirm endpoints should persist dataset/version/rows.
```

---

## 10. Response Body Convention

### 10.1. Single resource response

Return the resource directly. Do not wrap every response in a generic `data` envelope.

Example:

```json
{
  "id": "0ddf0c99-c632-4ef8-9044-8e37c940d1b9",
  "workspaceId": "4bcfb80f-b2e1-4449-ad72-5c3b4ecb42a1",
  "name": "VF Sales Chatbot",
  "description": "Evaluation project for sales chatbot",
  "createdBy": "3f8f62cf-2504-48e1-a9b4-805e87b6e8d2",
  "createdAt": "2026-07-02T10:00:00+07:00",
  "updatedAt": "2026-07-02T10:00:00+07:00"
}
```

---

### 10.2. Collection response

Collections must use a list envelope so pagination metadata can be added consistently.

Example:

```json
{
  "items": [
    {
      "id": "0ddf0c99-c632-4ef8-9044-8e37c940d1b9",
      "name": "VF Sales Chatbot"
    }
  ],
  "page": {
    "limit": 20,
    "nextCursor": "eyJpZCI6Ij...",
    "hasMore": true
  }
}
```

Rules:

```text
1. Collection response root fields are `items` and `page`.
2. `items` is always an array.
3. `page` is always present for pageable endpoints.
4. If an endpoint intentionally returns all items, document it explicitly.
```

---

### 10.3. Create response

For successful creation, return `201 Created` with the created resource.

Recommended header:

```http
Location: /api/v1/projects/{projectId}
```

Example:

```http
HTTP/1.1 201 Created
Location: /api/v1/projects/0ddf0c99-c632-4ef8-9044-8e37c940d1b9
Content-Type: application/json
```

---

## 11. Date and Time Convention

Use ISO 8601 / RFC 3339 timestamp strings with timezone offset.

Example:

```json
{
  "createdAt": "2026-07-02T10:00:00+07:00",
  "updatedAt": "2026-07-02T10:05:30+07:00"
}
```

Backend should persist timestamps as `TIMESTAMPTZ` in PostgreSQL.

API responses should include timezone offset. UTC `Z` is also acceptable for machine-generated internal timestamps.

---

## 12. Enum Convention

API enum values use uppercase snake case, matching database enum values where practical.

Examples:

```json
{
  "systemRole": "USER",
  "workspaceRole": "ADMIN",
  "status": "ACTIVE",
  "runType": "COMPARISON"
}
```

Do not localize enum values in API responses. Frontend may translate enum labels for display.

---

## 13. Pagination Convention

Default pagination uses cursor-based pagination for normal collection endpoints. High-volume tabular endpoints may use explicit page/size pagination when random access is useful for UI tables.

Request query parameters:

```http
GET /api/v1/workspaces?limit=20&cursor=eyJpZCI6Ij...
```

Response:

```json
{
  "items": [],
  "page": {
    "limit": 20,
    "nextCursor": null,
    "hasMore": false
  }
}
```

Rules:

```text
1. `limit` default is 20.
2. `limit` maximum is 100 unless the endpoint says otherwise.
3. `cursor` is opaque to frontend.
4. Frontend must not parse cursor contents.
5. Cursor pagination is preferred for resources sorted by createdAt/id.
6. Every collection endpoint must document whether it uses cursor pagination or page/size pagination.
7. Endpoints returning potentially large rows/results must enforce a maximum page size.
```

For table-like resources where users need deterministic page numbers, page/size pagination is allowed and must use the same `items` + `page` envelope. Examples include dataset rows and testcase results.

### 13.1. Table-heavy QC Workspace APIs

QC-facing table APIs must be designed for Excel-like workflows. These APIs power dataset editing, testcase review and report preparation.

```text
1. Must support page/size pagination.
2. Must support stable sorting for table columns that are displayed in UI.
3. Must support filtering by status/reviewStatus/finalStatus where applicable.
4. Must support row-level and cell-level editing where the resource is editable.
5. Must not return huge raw request/response JSON in list endpoints.
6. Must return preview fields suitable for table/card/library views.
7. Must enforce a maximum page size. Default max page size is 100.
8. Bulk review/export endpoints must apply the same filter semantics as the table view where possible.
9. Bulk edit endpoints must have explicit row ids and should reject unbounded filter-only mutation in MVP.
```

Dataset table APIs should preserve Excel-like ergonomics:

```text
1. Inline cell edit should patch only changed cells.
2. Bulk paste from Excel should be represented as a bounded batch of row/cell changes.
3. Published ACTIVE dataset versions must not be edited directly.
4. Editing starts from a DRAFT dataset version and publishes a new ACTIVE version.
5. Table view and library/card view should use the same row source and pagination/filter contract.
```

---

## 14. Sorting Convention

Use the `sort` query parameter.

Examples:

```http
GET /api/v1/projects/{projectId}/test-runs?sort=-createdAt
GET /api/v1/projects/{projectId}/test-runs?sort=status,-createdAt
```

Rules:

```text
1. Ascending sort uses the field name: `createdAt`.
2. Descending sort uses minus prefix: `-createdAt`.
3. Multiple sort fields are comma-separated.
4. Unsupported sort fields must return 400 validation error.
```

---

## 15. Filtering and Search Convention

Use explicit query parameters for common filters.

Examples:

```http
GET /api/v1/projects/{projectId}/test-runs?status=RUNNING
GET /api/v1/projects/{projectId}/datasets?scope=PROJECT
GET /api/v1/workspaces/{workspaceId}/projects?q=chatbot
```

Rules:

```text
1. Use `q` for free-text search.
2. Use field names for exact filters: `status`, `scope`, `role`.
3. Repeated params are preferred for multiple enum values.
4. Invalid filters must return 400 validation error.
```

Recommended style for multiple enum values:

```http
GET /api/v1/projects/{projectId}/test-runs?status=RUNNING&status=PENDING
```

---

## 16. HTTP Status Codes

Use status codes consistently.

| Status | Meaning |
| ------ | ------- |
| `200 OK` | Successful read/update/action with response body |
| `201 Created` | Resource created successfully |
| `202 Accepted` | Async operation accepted but not completed yet |
| `204 No Content` | Successful delete/update/action with no response body |
| `400 Bad Request` | Malformed request or basic validation error |
| `401 Unauthorized` | Missing/invalid authentication |
| `403 Forbidden` | Authenticated but not allowed |
| `404 Not Found` | Resource not found or hidden by permission policy |
| `409 Conflict` | Duplicate resource, invalid state transition or version conflict |
| `413 Payload Too Large` | Upload or request too large |
| `415 Unsupported Media Type` | Unsupported content type |
| `422 Unprocessable Content` | Semantically valid JSON but domain validation failed |
| `429 Too Many Requests` | Rate limit exceeded |
| `500 Internal Server Error` | Unexpected backend error |
| `502 Bad Gateway` | External provider/target gateway error |
| `503 Service Unavailable` | Dependency unavailable |
| `504 Gateway Timeout` | External provider/target timeout |

Guidance:

```text
1. Use 400 for malformed request or basic DTO validation failure.
2. Use 401 for missing, expired or invalid access token.
3. Use 403 for permission denied after authentication succeeds.
4. Use 404 when resource does not exist or user must not know it exists.
5. Use 409 for invalid state transition or duplicate resource conflict.
6. Use 422 for valid JSON that violates domain rules.
```

Example 409 cases:

```text
- test_run is already terminal and cannot be cancelled.
- evaluator version is already locked by a run and cannot be updated.
- project name already exists in a workspace.
```

Example 422 cases:

```text
- dataset row count exceeds maxRowsPerRun.
- chatbot target response extraction path does not exist in example response.
- comparison run has fewer than 2 targets.
```

---

## 17. Error Response Convention - RFC 9457

All error responses must use RFC 9457 Problem Details.

Content type:

```http
Content-Type: application/problem+json
```

Minimum fields:

```json
{
  "type": "https://vfqc.vinfast.vn/problems/validation-error",
  "title": "Validation failed",
  "status": 400,
  "detail": "Request body contains invalid fields.",
  "instance": "/api/v1/projects"
}
```

VFQC extension fields:

```json
{
  "type": "https://vfqc.vinfast.vn/problems/validation-error",
  "title": "Validation failed",
  "status": 400,
  "detail": "Request body contains invalid fields.",
  "instance": "/api/v1/projects",
  "code": "VALIDATION_ERROR",
  "traceId": "01J0Z7Y4Y4F9E1S2Y9W8Q7P6A5",
  "errors": [
    {
      "field": "name",
      "code": "REQUIRED",
      "message": "Project name is required."
    }
  ]
}
```

Rules:

```text
1. `type`, `title`, `status`, `detail`, `instance` follow RFC 9457.
2. `code` is a stable application error code for frontend logic.
3. `traceId` is required for debugging and log correlation.
4. `errors` is optional and used for field-level validation errors.
5. Do not include stack traces in API responses.
6. Do not include plaintext secret values in API responses.
7. Do not include raw Authorization, Cookie or API key values in errors.
```

Spring implementation note:

```text
Spring Framework provides ProblemDetail and ErrorResponse abstractions for RFC 9457 problem details.
VFQC should still define application-level extension fields such as `code`, `traceId`, and `errors` so frontend behavior stays stable and not tied to framework defaults.
```

---

## 18. Stable Error Codes

Backend must expose stable error codes.

Initial common codes:

```text
VALIDATION_ERROR
AUTHENTICATION_REQUIRED
ACCESS_TOKEN_EXPIRED
REFRESH_TOKEN_INVALID
REFRESH_TOKEN_MISSING
REFRESH_TOKEN_EXPIRED
INVALID_CREDENTIALS
USER_DISABLED
PERMISSION_DENIED
RESOURCE_NOT_FOUND
CONFLICT
INVALID_STATE_TRANSITION
PAYLOAD_TOO_LARGE
UNSUPPORTED_MEDIA_TYPE
RATE_LIMITED
INTERNAL_ERROR
DEPENDENCY_UNAVAILABLE
```

Domain-specific codes:

```text
WORKSPACE_NOT_FOUND
WORKSPACE_NAME_ALREADY_EXISTS
WORKSPACE_MEMBER_NOT_FOUND
WORKSPACE_MEMBER_ALREADY_EXISTS
WORKSPACE_OWNER_REQUIRED
USER_NOT_FOUND
PROJECT_NOT_FOUND
PROJECT_NAME_ALREADY_EXISTS
PROJECT_HAS_ACTIVE_RUNS
SECRET_NOT_FOUND
SECRET_PLAINTEXT_READ_DENIED
SECRET_IN_USE_BY_PROVIDER
PROVIDER_NOT_FOUND
PROVIDER_TEST_FAILED
PROVIDER_AUTH_FAILED
PROVIDER_SECRET_SCOPE_MISMATCH
PROVIDER_CONFIG_CONTAINS_SECRET
SECRET_FIELD_NOT_ALLOWED
DATASET_NOT_FOUND
DATASET_NAME_ALREADY_EXISTS
DATASET_IN_USE_BY_TEST_RUN
DATASET_VERSION_NOT_FOUND
DATASET_VERSION_NOT_ACTIVE
DATASET_SCHEMA_NOT_FOUND
DATASET_SCHEMA_INCOMPATIBLE
DATASET_SCHEMA_COLUMN_INVALID
DATASET_SCHEMA_VERSION_CONFLICT
DATASET_IMPORT_JOB_NOT_FOUND
DATASET_IMPORT_JOB_INVALID_STATE
DATASET_IMPORT_FILE_INVALID
DATASET_IMPORT_SHEET_NOT_FOUND
DATASET_IMPORT_MAPPING_INVALID
DATASET_IMPORT_FAILED
CHATBOT_TARGET_NOT_FOUND
CHATBOT_TARGET_NAME_ALREADY_EXISTS
CHATBOT_TARGET_INVALID
CHATBOT_TARGET_DISABLED
CHATBOT_TARGET_IN_USE_BY_ACTIVE_RUN
CHATBOT_TARGET_CONTAINS_PLAINTEXT_SECRET
CHATBOT_TARGET_SECRET_INVALID
CHATBOT_TARGET_SECRET_SCOPE_MISMATCH
CHATBOT_TARGET_DATASET_MAPPING_INVALID
CHATBOT_TARGET_REQUEST_CONFIG_INVALID
CHATBOT_TARGET_TEST_FAILED
CHATBOT_TARGET_TIMEOUT
CHATBOT_TARGET_EXAMPLE_NOT_FOUND
CURL_PARSE_FAILED
CURL_UNSUPPORTED_OPTION
CURL_CONTAINS_UNSUPPORTED_BODY_TYPE
RESPONSE_EXTRACTION_PATH_INVALID
RESPONSE_EXTRACTION_CONFIG_INVALID
EVALUATOR_NOT_FOUND
EVALUATOR_NAME_ALREADY_EXISTS
EVALUATOR_TYPE_IMMUTABLE
EVALUATOR_IN_USE_BY_ACTIVE_RUN
EVALUATOR_VERSION_NOT_FOUND
EVALUATOR_VERSION_IMMUTABLE
EVALUATOR_VERSION_CONFLICT
EVALUATOR_RULE_INVALID
EVALUATOR_OPERATOR_UNSUPPORTED
EVALUATOR_FIELD_SOURCE_INVALID
EVALUATOR_EXPECTED_VALUE_INVALID
EVALUATOR_LLM_JUDGE_CONFIG_INVALID
EVALUATOR_RUBRIC_INVALID
EVALUATOR_CUSTOM_JAVASCRIPT_FORBIDDEN
EVALUATOR_PROVIDER_INVALID
EVALUATOR_VALIDATE_FAILED
TEST_RUN_NOT_FOUND
TEST_RUN_INVALID_STATE
TEST_RUN_CANCEL_NOT_ALLOWED
TEST_RUN_ALREADY_TERMINAL
TEST_RUN_ROW_LIMIT_EXCEEDED
TEST_RUN_PRECHECK_FAILED
TEST_RUN_TARGET_INVALID
TEST_RUN_DATASET_INVALID
TEST_RUN_EVALUATOR_INVALID
TEST_RUN_PROVIDER_INVALID
TEST_RUN_JUDGE_PROVIDER_REQUIRED
TEST_RUN_COMPARISON_TARGET_REQUIRED
TEST_RUN_ACTIVE_LIMIT_REACHED
TEST_RUN_START_FAILED
TEST_RUN_RESULT_NOT_FOUND
TEST_RUN_ARTIFACT_NOT_FOUND
TEST_RUN_ARTIFACT_UNSANITIZED
TEST_CASE_RESULT_REVIEW_NOT_FOUND
TEST_CASE_RESULT_REVIEW_INVALID
TEST_CASE_RESULT_REVIEW_CONFLICT
TEST_CASE_RESULT_REVIEW_REASON_REQUIRED
TEST_CASE_RESULT_REVIEW_NOT_ALLOWED
TEST_CASE_RESULT_REVIEW_BULK_LIMIT_EXCEEDED
TEST_RUN_EXPORT_NOT_ALLOWED
TEST_RUN_EXPORT_FAILED
DATASET_DRAFT_NOT_FOUND
DATASET_DRAFT_INVALID_STATE
DATASET_DRAFT_ALREADY_EXISTS
DATASET_ROW_NOT_FOUND
DATASET_ROW_EDIT_NOT_ALLOWED
DATASET_CELL_VALUE_INVALID
DATASET_BULK_EDIT_LIMIT_EXCEEDED
DATASET_PUBLISH_FAILED
DATASET_EXPORT_NOT_ALLOWED
DATASET_EXPORT_FAILED
EXPORT_JOB_NOT_FOUND
EXPORT_JOB_INVALID_STATE
EXPORT_JOB_EXPIRED
EXPORT_JOB_DOWNLOAD_UNAVAILABLE
RUNNER_COMMAND_PUBLISH_FAILED
RUNNER_UNAUTHORIZED
RUNNER_FORBIDDEN
RUNNER_INSTANCE_NOT_FOUND
RUNNER_INSTANCE_CONFLICT
RUNNER_HEARTBEAT_INVALID
RUNNER_CONFIG_NOT_FOUND
RUNNER_CONFIG_SNAPSHOT_INVALID
RUNNER_CONFIG_HASH_MISMATCH
RUNNER_SECRET_RESOLUTION_DENIED
RUNNER_SECRET_NOT_REFERENCED_BY_RUN
RUNNER_CONTROL_STATE_UNAVAILABLE
RUNNER_ARTIFACT_METADATA_INVALID
RUNNER_ARTIFACT_PATH_INVALID
RUNNER_ARTIFACT_UNSANITIZED
RUNNER_ARTIFACT_HASH_INVALID
RUNNER_ARTIFACT_HASH_CONFLICT
RUNNER_EVENT_INVALID
RUNNER_EVENT_DUPLICATE
RUNNER_EVENT_SEQUENCE_CONFLICT
RUNNER_EVENT_PAYLOAD_CONTAINS_SECRET
SSE_REPLAY_UNAVAILABLE
SSE_CONNECTION_LIMIT_EXCEEDED
SSE_EVENT_STREAM_UNAVAILABLE
IDEMPOTENCY_KEY_REUSED
```

---

## 19. Validation Error Format

For field-level validation, use the `errors` extension field.

Example:

```json
{
  "type": "https://vfqc.vinfast.vn/problems/validation-error",
  "title": "Validation failed",
  "status": 400,
  "detail": "Request body contains invalid fields.",
  "instance": "/api/v1/workspaces",
  "code": "VALIDATION_ERROR",
  "traceId": "01J0Z7Y4Y4F9E1S2Y9W8Q7P6A5",
  "errors": [
    {
      "field": "name",
      "code": "REQUIRED",
      "message": "Workspace name is required."
    },
    {
      "field": "type",
      "code": "INVALID_ENUM_VALUE",
      "message": "Workspace type must be PERSONAL or TEAM."
    }
  ]
}
```

Field path examples:

```text
name
members[0].email
rules[2].operator
datasetMapping.body.question
```

---

## 20. Idempotency Convention

For normal CRUD endpoints, idempotency follows HTTP semantics.

For async/action endpoints, define idempotency explicitly.

Recommended header for client-generated idempotency:

```http
Idempotency-Key: 5b7d5f43-0a12-4fd8-b4e1-2f73f84af5c8
```

MVP usage:

```text
1. POST /api/v1/projects/{projectId}/test-runs may accept Idempotency-Key.
2. Backend still creates `runner_commands.command_id` as the execution idempotency key.
3. Duplicate START_RUN must not create duplicate execution.
4. Duplicate event must not create duplicate testcase result.
```

If an endpoint supports `Idempotency-Key`, its endpoint contract must specify:

```text
- key scope
- retention period
- conflict behavior when the same key is reused with a different body
```

---

## 21. Export Job Convention

Excel export is a first-class workflow for QC handoff and reporting. Dataset export and TestRun result export must use the same async job contract so frontend can reuse one export-job component.

Recommended endpoints:

```http
POST /api/v1/test-runs/{testRunId}/exports
POST /api/v1/dataset-versions/{datasetVersionId}/exports
GET  /api/v1/export-jobs/{exportJobId}
GET  /api/v1/export-jobs/{exportJobId}/download
```

Common export job types:

```text
DATASET_VERSION
TEST_RUN_RESULTS
```

Common export formats:

```text
XLSX
```

CSV may be added later, but XLSX is the MVP format.

Common export job statuses:

```text
PENDING
RUNNING
COMPLETED
FAILED
CANCELLED
EXPIRED
```

### 21.1. ExportJobResponse

All export create/status endpoints should return this common DTO shape or a documented strict subset of it.

```json
{
  "id": "50b6276d-768c-4ef3-b7be-35eb308f24c9",
  "exportJobId": "50b6276d-768c-4ef3-b7be-35eb308f24c9",
  "jobType": "TEST_RUN_RESULTS",
  "status": "PENDING",
  "format": "XLSX",
  "source": {
    "testRunId": "4a2bb4d2-fb85-4fb5-9496-1e2e09179813",
    "datasetVersionId": null
  },
  "options": {
    "resultMode": "FINAL",
    "includeMachineResult": true,
    "includeReviewResult": true,
    "includeRequestResponse": false
  },
  "file": {
    "fileName": null,
    "fileSizeBytes": null,
    "downloadUrl": null,
    "expiresAt": null
  },
  "error": {
    "code": null,
    "message": null
  },
  "createdBy": "a38f0878-1ebe-49db-abf2-f6a372b6c246",
  "createdAt": "2026-07-03T10:35:00+07:00",
  "startedAt": null,
  "finishedAt": null
}
```

Field rules:

```text
1. `id` and `exportJobId` are both allowed in MVP for transition compatibility; new clients should use `id`.
2. `source.testRunId` is required when jobType = TEST_RUN_RESULTS.
3. `source.datasetVersionId` is required when jobType = DATASET_VERSION.
4. `file.downloadUrl` must be null until status = COMPLETED and the file is still available.
5. `file.downloadUrl` must be an API URL, not a server-local path.
6. `error.code` and `error.message` are populated only for FAILED/CANCELLED/EXPIRED states when useful.
7. `options` contains sanitized export options only. It must not contain raw request/response payloads or secrets.
```

### 21.2. Create export response

Create endpoints should return `202 Accepted` with `ExportJobResponse`.

Rules:

```text
1. Export creation must be side-effect free for the underlying dataset/run/result data.
2. Export creation may be idempotent if the client sends Idempotency-Key.
3. Dataset export must lock the requested datasetVersionId.
4. Result export must lock the requested testRunId and result filters/options at job creation time.
5. Result export should default to QC final reviewed result, not raw machine-only result.
```

### 21.3. Status and download

`GET /api/v1/export-jobs/{exportJobId}` returns the same `ExportJobResponse`.

`GET /api/v1/export-jobs/{exportJobId}/download` returns the file bytes only when:

```text
1. status = COMPLETED
2. file is not expired
3. caller still has permission to the underlying dataset version or test run
4. generated file has been sanitized
```

Common rules:

```text
1. Export jobs must check the same permission as the underlying data.
2. Export may include both machine result and QC reviewed result when requested.
3. Exported request/response fields must be sanitized before being written to file.
4. Export files should expire according to retention policy.
5. Download endpoints must not expose server-local filesystem paths.
6. Failed exports should keep enough sanitized error metadata for frontend troubleshooting.
```
---

## 22. Security and Sensitive Data Convention

General rules:

```text
1. No API returns plaintext secret after creation.
2. Secret responses may include `hasValue` and `maskedPreview` only.
3. Refresh token must be stored in HttpOnly Secure cookie, not in JSON response body.
4. raw cURL containing Authorization/API key/token/cookie must be sanitized before persistence.
5. Request/response examples must be masked before returning to frontend.
6. Artifact download/view endpoints must check `sanitized = true` before exposing artifacts.
7. ProblemDetail responses must never include secret values.
```

Forbidden response example:

```json
{
  "apiKey": "sk-real-secret-key"
}
```

Allowed response example:

```json
{
  "secretId": "3e8d1de2-2a26-4d9a-a0dd-9c6ce056d9ea",
  "hasValue": true,
  "maskedPreview": "sk-****abcd"
}
```

---

## 23. Soft Delete Convention

For resources with `deleted_at` in ERD, `DELETE` means soft delete unless the endpoint explicitly states otherwise.

Soft-delete resources include:

```text
users
workspaces
projects
secrets
llm_providers
datasets
chatbot_targets
evaluators
```

API behavior:

```text
1. DELETE returns 204 No Content by default.
2. Deleted resources do not appear in normal list endpoints.
3. Deleted resources return 404 unless endpoint explicitly supports includeDeleted=true.
4. Execution data such as test_runs and test_case_results must not be hard-deleted by normal user APIs.
```

---

## 24. API Versioning Rule

MVP uses URL path versioning:

```text
/api/v1
```

Breaking API changes require a new major version:

```text
/api/v2
```

Non-breaking changes allowed in v1:

```text
1. Add optional request fields.
2. Add response fields.
3. Add new endpoints.
4. Add new enum values only if frontend is designed to tolerate unknown values.
```

Breaking changes:

```text
1. Rename or remove response fields.
2. Change field type.
3. Change required request fields.
4. Change endpoint meaning.
5. Change enum value names.
6. Change error code semantics.
```

---

## 25. OpenAPI Rule

After API contracts stabilize, backend should expose OpenAPI documentation.

Recommended path:

```http
GET /api/v1/openapi.json
```

OpenAPI must match these markdown contracts. If OpenAPI and markdown conflict, the markdown contract is the product/design source until implementation catches up.

---

## 26. Checklist for Every Endpoint Contract

Every endpoint document should specify:

```text
1. Method and path.
2. Purpose.
3. Permission.
4. Request params.
5. Request body.
6. Response body.
7. Status codes.
8. Error codes.
9. State changes.
10. Entities touched.
11. Sensitive data behavior.
12. Idempotency behavior if applicable.
```
