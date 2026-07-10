# VinFast QC Copilot Frontend — API, Auth, Permission and Error Contract

**Document:** `docs/frontend/03-api-auth-permission-and-error-contract.md`  
**Status:** Reviewed and implementation-ready  
**Applies to:** `apps/web`  
**Last reviewed:** 2026-07-11

---

## 1. Purpose

This document defines the browser-facing contract for:

- API transport;
- authentication;
- access-token refresh;
- CSRF;
- CORS;
- system and workspace roles;
- feature permissions;
- error responses;
- request tracing;
- pagination;
- export download.

This document is based on verified backend implementation behavior.

---

## 2. API base

Public browser API namespace:

```text
/api/v1/**
```

Internal runner endpoints are forbidden from browser use:

```text
/internal/runner/**
```

Default local backend:

```text
http://localhost:8080
```

Default local frontend origin:

```text
http://localhost:3000
```

---

## 3. Authentication model

### 3.1 Access token

The backend returns an access token from:

```text
POST /api/v1/auth/login
POST /api/v1/auth/refresh
```

The frontend sends:

```http
Authorization: Bearer <access-token>
```

Default access-token lifetime:

```text
15 minutes
```

The frontend stores the access token only in memory.

### 3.2 Refresh token

The refresh token is stored in an HttpOnly cookie:

```text
Name:
  vfqc_refresh_token

Path:
  /api/v1/auth

HttpOnly:
  true

Default lifetime:
  7 days
```

Environment behavior:

```text
Development:
  Secure=false
  SameSite=Lax

Production:
  Secure=true
  SameSite=Strict
```

The frontend must not read this cookie.

### 3.3 Refresh rotation

Every successful refresh:

```text
revokes the existing refresh token
issues a new refresh token in the same family
sets a replacement cookie
returns a new access token
```

Reuse of a previously rotated token causes backend compromise handling and family revocation.

### 3.4 Logout

```text
POST /api/v1/auth/logout
```

Behavior:

```text
revoke current refresh token if present
clear refresh cookie
return 204
```

Logout is safe when no refresh cookie exists.

---

## 4. Public and authenticated endpoints

Public auth endpoints:

```text
GET  /api/v1/auth/csrf

POST /api/v1/auth/register
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
POST /api/v1/auth/verify-email
POST /api/v1/auth/password/forgot
POST /api/v1/auth/password/reset
```

Authenticated endpoint:

```text
GET /api/v1/auth/me
```

All other `/api/**` endpoints require authentication unless explicitly documented.

Admin namespace:

```text
/api/v1/admin/**
```

requires:

```text
PLATFORM_ADMIN
```

---

## 5. Registration mode

Backend modes:

```text
ADMIN_ONLY
SELF_VERIFY
```

Default:

```text
ADMIN_ONLY
```

Development may enable:

```text
SELF_VERIFY
```

Frontend behavior:

```text
SELF_VERIFY:
  show registration route and registration form

ADMIN_ONLY:
  hide self-registration entry point
  keep reset-password route for admin invitations
```

A disabled registration attempt returns a 403 business error.

---

## 6. CSRF contract

The backend uses Spring Security SPA CSRF behavior.

### 6.1 Token initialization

Call:

```text
GET /api/v1/auth/csrf
```

The backend creates or exposes:

```text
Cookie:
  XSRF-TOKEN

Header expected on mutation:
  X-XSRF-TOKEN
```

### 6.2 Browser request rule

For state-changing requests:

```text
POST
PUT
PATCH
DELETE
```

the frontend sends:

```http
X-XSRF-TOKEN: <value from XSRF-TOKEN cookie>
```

The frontend must use:

```ts
credentials: 'include'
```

### 6.3 Refresh and login

Public auth endpoints are public from Bearer authentication, but they still participate in Spring SPA CSRF handling.

The frontend should initialize CSRF before the first state-changing auth request.

### 6.4 CSRF failure

Treat CSRF failure as a transport/security error.

Do not automatically retry indefinitely.

A reasonable recovery is:

```text
refresh CSRF token once
retry the original safe-to-retry request once
otherwise surface the error
```

---

## 7. CORS contract

Allowed origin:

```text
vfqc.client.base-url
```

Allowed methods:

```text
GET
POST
PUT
PATCH
DELETE
OPTIONS
```

Allowed headers:

```text
Authorization
Content-Type
Accept
X-XSRF-TOKEN
X-Request-Id
```

Exposed headers:

```text
X-Request-Id
```

Credentials:

```text
allowed
```

Preflight cache:

```text
3600 seconds
```

Only the configured frontend origin is supported.

---

## 8. Access-token lifecycle

Recommended frontend session bootstrap:

```text
1. Initialize CSRF token.
2. Attempt POST /auth/refresh with credentials.
3. On success, store access token in memory.
4. Fetch GET /auth/me.
5. Enter authenticated application.
6. On refresh failure, remain unauthenticated.
```

Alternative bootstrap may call `/auth/me` first only when an in-memory access token already exists.

### 8.1 Single-flight refresh

Only one refresh request may run at a time.

Pseudo-flow:

```ts
if (!refreshPromise) {
  refreshPromise = refreshAccessToken().finally(() => {
    refreshPromise = null;
  });
}
return refreshPromise;
```

### 8.2 Request replay

An authenticated request may be replayed once after successful refresh.

Do not replay automatically when:

```text
request was aborted
request body is non-replayable
response is not 401
request is the refresh endpoint itself
request has already retried once
```

### 8.3 Refresh failure

On refresh failure:

```text
clear access token
clear authenticated user cache
cancel authenticated queries where practical
navigate to login
preserve intended route for post-login redirect
```

---

## 9. System roles

```text
PLATFORM_ADMIN
USER
```

### PLATFORM_ADMIN

Verified behavior:

```text
can access admin namespace
bypasses workspace permission checks
can see all non-deleted workspaces
can access personal resources owned by other users through current service rules
```

When a platform admin accesses a workspace without membership:

```text
currentUserRole may be null
```

Frontend must support this state.

### USER

Normal user behavior is determined by workspace membership.

---

## 10. Workspace roles and actions

Workspace roles:

```text
OWNER
ADMIN
MEMBER
VIEWER
```

Verified action matrix:

| Role | VIEW | EDIT | MANAGE |
|---|---:|---:|---:|
| OWNER | Yes | Yes | Yes |
| ADMIN | Yes | Yes | Yes |
| MEMBER | Yes | Yes | No |
| VIEWER | Yes | No | No |

Project permissions inherit from workspace permissions.

There is no separate project-role model in the current backend.

---

## 11. Capability mapping

### 11.1 Workspace lifecycle

| Action | Required capability |
|---|---|
| View workspace | VIEW |
| List workspace members | VIEW |
| Rename workspace | MANAGE |
| Delete workspace | MANAGE |
| Add member | MANAGE |
| Change member role | MANAGE |

Current backend does not expose a special distinction between OWNER and ADMIN for management actions.

### 11.2 Workspace secrets

| Action | Required capability |
|---|---|
| List/get workspace secrets | VIEW |
| Create workspace secret | EDIT |
| Rename secret | EDIT |
| Rotate secret | EDIT |
| Disable secret | EDIT |
| Delete secret | EDIT |

Personal secrets:

```text
owner or PLATFORM_ADMIN
```

### 11.3 Workspace providers

| Action | Required capability |
|---|---|
| List/get workspace providers | VIEW |
| Create provider | EDIT |
| Update provider | EDIT |
| Delete provider | EDIT |

Personal providers:

```text
owner or PLATFORM_ADMIN
```

### 11.4 Chatbot targets

| Action | Required capability |
|---|---|
| List/get target | VIEW |
| View examples/readiness | VIEW |
| Create target | EDIT |
| Update target | EDIT |
| Test target | EDIT |
| Update extraction | EDIT |
| Delete target | EDIT |

### 11.5 Datasets

| Action | Required capability |
|---|---|
| List/get dataset | VIEW |
| List versions | VIEW |
| Read schema/variables | VIEW |
| Create dataset | EDIT |
| Update dataset | EDIT |
| Delete dataset | EDIT |
| Create draft | EDIT |
| Publish draft | EDIT |
| Discard draft | EDIT |
| Add/update draft rows | EDIT |
| Create import preview | EDIT |
| Read import job | VIEW |
| Confirm import | EDIT |
| Export dataset | VIEW |

### 11.6 Evaluators

| Action | Required capability |
|---|---|
| List/get evaluator | VIEW |
| View versions | VIEW |
| Create evaluator | EDIT |
| Update metadata | EDIT |
| Delete evaluator | EDIT |
| Create new version | EDIT |
| Validate config while authoring | EDIT |

### 11.7 TestRuns

| Action | Required capability |
|---|---|
| List/get TestRun | VIEW |
| Read results | VIEW |
| Read comparison matrix | VIEW |
| Subscribe SSE | VIEW |
| Run preflight | VIEW |
| Create TestRun | EDIT |
| Cancel TestRun | EDIT |
| Create/update QC review | EDIT |
| Export TestRun results | VIEW |

Notable behavior:

```text
VIEWER can run preflight
VIEWER cannot create TestRun
VIEWER can export
VIEWER cannot review or override
```

---

## 12. Personal-resource rules

Personal secrets and providers are visible to:

```text
resource owner
PLATFORM_ADMIN
```

Other users receive:

```text
404 RESOURCE_NOT_FOUND
```

This prevents leaking resource existence.

Frontend must not infer personal-resource access from workspace roles.

---

## 13. Error response model

The backend has two verified error shapes.

### 13.1 Business/controller error

Spring `ProblemDetail`:

```ts
interface ApiProblemDetail {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  instance?: string;
  code?: string;
  messageKey?: string;
  traceId?: string;
  fieldErrors?: Array<{
    field: string;
    message: string;
  }>;
}
```

Example fields:

```text
code:
  WORKSPACE_NAME_ALREADY_EXISTS

messageKey:
  error.workspace.nameAlreadyExists

traceId:
  request reference

fieldErrors:
  validation details
```

Expected media type:

```text
application/problem+json
```

### 13.2 Spring Security error

Verified runtime behavior for missing or malformed Bearer token:

```text
HTTP 401
Content-Length: 0
no JSON body
WWW-Authenticate header present
```

Examples:

```text
missing token:
  WWW-Authenticate: Bearer ...

invalid token:
  WWW-Authenticate: Bearer error="invalid_token" ...
```

The frontend must not call `response.json()` unconditionally.

---

## 14. Error normalization

Recommended algorithm:

```text
1. Capture status.
2. Capture X-Request-Id header when present.
3. If body length is zero:
     return normalized empty security/transport error.
4. Inspect Content-Type.
5. If JSON/problem+json:
     parse safely.
6. If parsing fails:
     preserve status and fallback text.
7. Mark 401 as authenticationError.
```

Normalized type:

```ts
interface ApiError {
  status: number;
  code?: string;
  title?: string;
  detail?: string;
  messageKey?: string;
  traceId?: string;
  fieldErrors?: Array<{
    field: string;
    message: string;
  }>;
  authenticationError: boolean;
  permissionError: boolean;
  networkError: boolean;
}
```

---

## 15. Important error codes

Authentication:

```text
AUTHENTICATION_REQUIRED
INVALID_CREDENTIALS
REFRESH_TOKEN_MISSING
REFRESH_TOKEN_INVALID
REFRESH_TOKEN_EXPIRED
REGISTRATION_DISABLED
EMAIL_NOT_VERIFIED
EMAIL_TOKEN_MISSING
EMAIL_TOKEN_INVALID
EMAIL_TOKEN_EXPIRED
USER_DISABLED
```

Authorization:

```text
ACCESS_DENIED
```

Generic resource:

```text
RESOURCE_NOT_FOUND
DUPLICATE_RESOURCE
INVALID_RESOURCE_NAME
INVALID_STATE
```

Dataset:

```text
DATASET_NAME_ALREADY_EXISTS
DATASET_DRAFT_ALREADY_EXISTS
DATASET_VERSION_NOT_EDITABLE
DATASET_REQUIRED_COLUMN_MISSING
DATASET_UNKNOWN_COLUMN
DATASET_IMPORT_MAPPING_INVALID
DATASET_IMPORT_JOB_INVALID_STATE
```

Evaluator:

```text
EVALUATOR_NAME_ALREADY_EXISTS
EVALUATOR_RULE_INVALID
EVALUATOR_FIELD_SOURCE_INVALID
EVALUATOR_EXPECTED_VALUE_INVALID
EVALUATOR_LLM_JUDGE_CONFIG_INVALID
EVALUATOR_CUSTOM_JAVASCRIPT_FORBIDDEN
EVALUATOR_PROVIDER_INVALID
```

Secret:

```text
SECRET_SCOPE_MISMATCH
```

---

## 16. UI error behavior

### 16.1 400 validation

```text
map fieldErrors to form fields
show non-field detail in form alert
```

### 16.2 401

```text
attempt single-flight refresh once
on refresh failure:
  clear session
  redirect to login
```

### 16.3 403

```text
show permission message
do not retry automatically
do not convert to 404 in frontend
```

### 16.4 404

```text
show resource not found
return to nearest valid parent through explicit action
```

### 16.5 409

```text
show conflict detail
keep form data
refetch affected resource where needed
```

### 16.6 500

```text
show generic failure
show trace/reference ID when available
offer retry for safe read operations
```

---

## 17. Request tracing

Request header:

```text
X-Request-Id
```

Response header:

```text
X-Request-Id
```

ProblemDetail property:

```text
traceId
```

Frontend may generate a UUID request ID for important mutations and send it.

Display priority:

```text
ProblemDetail.traceId
X-Request-Id response header
none
```

Current security-generated 401 responses may not include `X-Request-Id`.

Treat the reference ID as optional.

---

## 18. Pagination contract

Spring Pageable query parameters:

```text
page
size
sort
```

Default size for TestRun/results/comparison:

```text
50
```

Examples:

```text
?page=0&size=50
?page=1&size=50&sort=id,asc
?page=0&size=50&sort=createdAt,desc
```

Response:

```ts
interface PageInfo {
  limit: number;
  nextCursor: string | null;
  hasMore: boolean;
}
```

Current behavior:

```text
nextCursor is null
hasMore means a next page exists
current page is not returned
total count is not returned
```

Frontend pagination:

```text
maintain page in URL
disable Previous on page 0
disable Next when hasMore=false
```

---

## 19. Export contract

Export creation:

```text
POST dataset-version export job
POST TestRun export job
```

Default format:

```text
XLSX
```

Default TestRun result mode:

```text
FINAL
```

Export permission:

```text
VIEW
```

Job terminal statuses:

```text
COMPLETED
FAILED
CANCELLED
EXPIRED
```

Download:

```text
GET /api/v1/export-jobs/{jobId}/download
```

Use Blob handling.

Do not parse the download response as JSON.

---

## 20. Dataset constraints relevant to frontend

Default project schema:

```text
question:
  TEXT
  required

expected:
  TEXT
  optional
```

Excel import:

```text
only EXCEL/XLSX in MVP
default preview rows: 10
maximum preview rows: 20
maximum imported rows: 500
```

Schema may be extended during import.

There is no standalone manual schema-management CRUD flow in the current API.

---

## 21. Known backend inconsistencies

### 21.1 OpenAPI public auth security

Global Bearer security may incorrectly imply that public auth endpoints require authentication.

Frontend implementation must follow verified security configuration, not the global OpenAPI security block.

### 21.2 OpenAPI error schemas

Several documented error responses use success DTO schemas.

Frontend must use the verified ProblemDetail/empty-body behavior instead.

### 21.3 CSRF OpenAPI parameter

The CSRF endpoint may be documented as requiring a `csrfToken` query parameter.

Actual frontend usage is:

```text
GET /api/v1/auth/csrf
```

with no query parameter.

### 21.4 Security error body

Spring Security 401 currently returns an empty body.

This differs from controller/service errors.

---

## 22. User-facing error and status language

Backend codes, enum values, and `ProblemDetail` fields remain unchanged in code.

User-facing messages are Vietnamese and follow document `08`.

Examples:

```text
401 after refresh failure:
  Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.

403:
  Bạn không có quyền thực hiện thao tác này.

404:
  Không tìm thấy tài nguyên.

500:
  Không thể hoàn tất yêu cầu. Vui lòng thử lại.
```

Established technical terms such as `Dataset`, `Chatbot Target`, `Evaluator`, `TestRun`, `API key`, `endpoint`, and `trace ID` remain in English.

Do not add duplicate Toast feedback when an error is already shown inline.

---

## 23. Frontend implementation requirements

The API client must have automated tests for:

```text
ProblemDetail parsing
empty 401 handling
single-flight refresh
one-time request replay
CSRF cookie/header behavior
Blob response
X-Request-Id extraction
field error mapping
```
