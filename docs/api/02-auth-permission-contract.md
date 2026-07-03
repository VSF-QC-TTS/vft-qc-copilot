# VinFast QC Copilot - Auth & Permission API Contract

Version: `v0.2`  
Status: API Contract Draft  
Base path: `/api/v1`  
Applies to: Authentication, current user, token refresh and authorization rules  
Related docs: `docs/api/00-api-overview.md`, `docs/api/01-api-conventions.md`, PRD v0.7, ERD v0.5

---

## 1. Purpose

This document defines authentication and authorization rules for VinFast QC Copilot APIs.

The permission model has two layers:

```text
1. System role: users.system_role
2. Workspace role: workspace_members.role
```

The core permission rule is:

```text
1. PLATFORM_ADMIN can manage almost all platform resources.
2. Normal USER permissions depend on workspace_members.role.
3. Nobody can read plaintext secrets after creation, including PLATFORM_ADMIN.
4. Users outside a workspace cannot access that workspace's resources.
```

---

## 2. Authentication Model

MVP uses an access token + refresh token model.

```text
Access token:
- Short-lived JWT or opaque token.
- Sent by frontend in Authorization header.
- Used for normal API authorization.

Refresh token:
- Longer-lived token.
- Stored in HttpOnly Secure cookie set by backend.
- Used only to obtain a new access token.
```

Normal authenticated API requests must include:

```http
Authorization: Bearer <accessToken>
```

Refresh token must be set by backend through `Set-Cookie`:

```http
Set-Cookie: vfqc_refresh_token=<refreshToken>; HttpOnly; Secure; SameSite=Lax; Path=/api/v1/auth; Max-Age=604800
```

Rules:

```text
1. Refresh token MUST NOT be returned in JSON response body.
2. Refresh token MUST NOT be stored in localStorage or sessionStorage.
3. Refresh token MUST be HttpOnly so frontend JavaScript cannot read it.
4. Refresh token MUST be Secure in non-local environments.
5. SameSite=Lax is the MVP default when frontend/backend are same-site.
6. SameSite=None; Secure is allowed only when cross-site deployment requires it.
7. If frontend and backend origins differ, frontend must call auth refresh/logout with credentials included.
8. Backend CORS must allow credentials only for explicit trusted origins, never wildcard origin.
```

Every authenticated request must resolve to a current user:

```text
CurrentUser
  id
  email
  name
  systemRole
  status
```

---

## 2.1. Refresh Token Persistence Note

The API contract assumes refresh tokens can be revoked or rotated. If the backend implements refresh token rotation, the data model must include a persistence table such as `auth_refresh_tokens` or `user_sessions`.

Recommended fields:

```text
id UUID PK
user_id UUID FK users(id)
token_hash TEXT NOT NULL
family_id UUID NOT NULL
status ACTIVE | REVOKED | EXPIRED
issued_at TIMESTAMPTZ
expires_at TIMESTAMPTZ
rotated_at TIMESTAMPTZ
revoked_at TIMESTAMPTZ
created_ip TEXT
user_agent TEXT
```

Rules:

```text
1. Store only a hash of the refresh token, never the plaintext token.
2. Plaintext refresh token exists only in the HttpOnly cookie and request processing memory.
3. Logout should revoke the current refresh token/session if persistence exists.
4. Reuse detection requires token family tracking.
5. If the MVP chooses stateless refresh JWT instead, remove rotation/reuse-detection requirements from implementation scope.
```

---

## 3. Auth API Endpoints

### 3.1. Login

```http
POST /api/v1/auth/login
```

#### Purpose

Authenticate user and issue access token + refresh token cookie.

#### Request body

MVP email/password example:

```json
{
  "email": "qc.member@example.com",
  "password": "user-password"
}
```

If company SSO is added later, this endpoint may be replaced or extended. The token/cookie response contract should remain the same for frontend.

#### Response - 200 OK

Headers:

```http
Set-Cookie: vfqc_refresh_token=<refreshToken>; HttpOnly; Secure; SameSite=Lax; Path=/api/v1/auth; Max-Age=604800
```

Body:

```json
{
  "accessToken": "eyJhbGciOi...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": "a38f0878-1ebe-49db-abf2-f6a372b6c246",
    "email": "qc.member@example.com",
    "name": "QC Member",
    "systemRole": "USER",
    "status": "ACTIVE"
  }
}
```

#### Error codes

```text
VALIDATION_ERROR
AUTHENTICATION_REQUIRED
INVALID_CREDENTIALS
USER_DISABLED
INTERNAL_ERROR
```

Security rules:

```text
1. Do not reveal whether email or password is wrong.
2. Do not return refresh token in body.
3. Do not include password in logs or ProblemDetail responses.
```

---

### 3.2. Refresh Access Token

```http
POST /api/v1/auth/refresh
```

#### Purpose

Issue a new access token using refresh token cookie.

#### Request

No JSON body is required.

Browser sends the cookie automatically if the request includes credentials and cookie policy allows it:

```http
Cookie: vfqc_refresh_token=<refreshToken>
```

#### Response - 200 OK

Recommended: rotate refresh token on every successful refresh.

Headers:

```http
Set-Cookie: vfqc_refresh_token=<newRefreshToken>; HttpOnly; Secure; SameSite=Lax; Path=/api/v1/auth; Max-Age=604800
```

Body:

```json
{
  "accessToken": "eyJhbGciOi...",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

#### Error codes

```text
REFRESH_TOKEN_MISSING
REFRESH_TOKEN_INVALID
REFRESH_TOKEN_EXPIRED
USER_DISABLED
```

On invalid/expired refresh token, backend should clear the cookie:

```http
Set-Cookie: vfqc_refresh_token=; HttpOnly; Secure; SameSite=Lax; Path=/api/v1/auth; Max-Age=0
```

---

### 3.3. Logout

```http
POST /api/v1/auth/logout
```

#### Purpose

Revoke or invalidate the current refresh token and clear refresh cookie.

#### Request

No JSON body is required.

#### Response - 204 No Content

Headers:

```http
Set-Cookie: vfqc_refresh_token=; HttpOnly; Secure; SameSite=Lax; Path=/api/v1/auth; Max-Age=0
```

Rules:

```text
1. Logout should be safe to call even when refresh cookie is already missing.
2. If refresh token rotation storage exists, backend should revoke the token family/session.
3. Frontend should clear in-memory access token after successful logout.
```

---

### 3.4. Get Current User

```http
GET /api/v1/auth/me
```

#### Purpose

Return current authenticated user and visible workspace summaries.

#### Permission

Authenticated ACTIVE user.

#### Response - 200 OK

```json
{
  "id": "a38f0878-1ebe-49db-abf2-f6a372b6c246",
  "email": "qc.member@example.com",
  "name": "QC Member",
  "avatarUrl": null,
  "systemRole": "USER",
  "status": "ACTIVE",
  "workspaces": [
    {
      "id": "4bcfb80f-b2e1-4449-ad72-5c3b4ecb42a1",
      "name": "VinFast QC Team",
      "type": "TEAM",
      "role": "MEMBER"
    }
  ]
}
```

---

## 4. Frontend Token Handling

Frontend rules:

```text
1. Store access token in memory/state, not localStorage if avoidable.
2. Send access token using Authorization Bearer header.
3. Do not read refresh token; it is HttpOnly and inaccessible to JavaScript.
4. When API returns 401 ACCESS_TOKEN_EXPIRED, call POST /api/v1/auth/refresh.
5. For refresh/logout requests, use credentials: include when frontend/backend origins differ.
6. If refresh fails, clear access token and redirect user to login.
```

Example fetch refresh:

```ts
await fetch("/api/v1/auth/refresh", {
  method: "POST",
  credentials: "include"
});
```

Example axios config:

```ts
axios.create({
  baseURL: "/api/v1",
  withCredentials: true
});
```

---

## 5. CSRF and Cookie Safety

Because refresh token is stored in a cookie, auth endpoints need cookie-specific protection.

MVP rules:

```text
1. Refresh/logout endpoints must use POST, never GET.
2. Refresh/logout endpoints should validate Origin/Referer for browser requests.
3. SameSite=Lax is the default when frontend/backend are same-site.
4. If SameSite=None is required, CORS origin allowlist and credentials handling are mandatory.
5. Normal business APIs should use Authorization Bearer access token, not refresh cookie.
```

Optional hardening for later:

```text
1. CSRF token header for refresh/logout.
2. Refresh token rotation with reuse detection.
3. Device/session management page.
4. IP/user-agent anomaly monitoring.
```

---

## 6. Authentication Error Responses

Unauthenticated requests must return:

```http
401 Unauthorized
Content-Type: application/problem+json
```

Example:

```json
{
  "type": "https://vfqc.vinfast.vn/problems/authentication-required",
  "title": "Authentication required",
  "status": 401,
  "detail": "Authentication is required to access this resource.",
  "instance": "/api/v1/workspaces",
  "code": "AUTHENTICATION_REQUIRED",
  "traceId": "01J0Z7Y4Y4F9E1S2Y9W8Q7P6A5"
}
```

Expired access token example:

```json
{
  "type": "https://vfqc.vinfast.vn/problems/access-token-expired",
  "title": "Access token expired",
  "status": 401,
  "detail": "The access token has expired. Refresh the access token and retry the request.",
  "instance": "/api/v1/projects",
  "code": "ACCESS_TOKEN_EXPIRED",
  "traceId": "01J0Z7Y4Y4F9E1S2Y9W8Q7P6A5"
}
```

---

## 7. User Status Rule

Only active users can access the API.

```text
users.status:
- ACTIVE
- DISABLED
- DELETED
```

Rules:

```text
1. ACTIVE users may access APIs according to permission rules.
2. DISABLED users must receive 403 Forbidden.
3. DELETED users must be treated as unauthenticated or forbidden depending on auth implementation.
```

Forbidden example:

```json
{
  "type": "https://vfqc.vinfast.vn/problems/user-disabled",
  "title": "User is disabled",
  "status": 403,
  "detail": "This user account is disabled.",
  "instance": "/api/v1/workspaces",
  "code": "USER_DISABLED",
  "traceId": "01J0Z7Y4Y4F9E1S2Y9W8Q7P6A5"
}
```

---

## 8. System Roles

System roles are stored in `users.system_role`.

```text
PLATFORM_ADMIN
USER
```

### PLATFORM_ADMIN

Platform admin can operate across the platform.

Allowed:

```text
- View workspaces/projects/configs/runs.
- Manage users/workspaces/projects according to admin APIs.
- Inspect operational metadata.
- Access debug/admin endpoints when implemented.
```

Denied:

```text
- Read plaintext secret values.
```

### USER

Normal user. Permissions depend on workspace membership.

---

## 9. Workspace Roles

Workspace roles are stored in `workspace_members.role`.

```text
OWNER
ADMIN
MEMBER
VIEWER
```

### OWNER

Can manage workspace and members.

Typical permissions:

```text
- View workspace.
- Update workspace.
- Invite members.
- Change member roles.
- Archive/delete workspace if policy allows.
- Create/update/archive projects.
- Create workspace secrets/providers.
- Import datasets.
- Configure chatbot targets.
- Configure evaluators.
- Start/cancel test runs.
- View dashboards/results/artifacts.
```

### ADMIN

Can manage most workspace configuration.

Typical permissions:

```text
- View workspace.
- Update workspace.
- Invite members.
- Change member roles, except owner-protected operations.
- Create/update/archive projects.
- Create workspace secrets/providers.
- Import datasets.
- Configure chatbot targets.
- Configure evaluators.
- Start/cancel test runs.
- View dashboards/results/artifacts.
```

Restrictions:

```text
- Cannot delete/archive workspace unless policy later allows it.
- Cannot remove/demote the last OWNER.
```

### MEMBER

Primary QC role.

Typical permissions:

```text
- View workspace.
- Create/update projects.
- Import datasets.
- Reuse datasets.
- Configure chatbot targets.
- Configure evaluators.
- Create personal secrets/providers.
- Start/cancel test runs.
- View dashboards/results/artifacts.
```

Restrictions:

```text
- Cannot update workspace settings.
- Cannot invite members.
- Cannot change member roles.
- Cannot create workspace secrets/providers.
```

### VIEWER

Read-only role.

Allowed:

```text
- View workspace.
- View projects.
- View dataset metadata.
- View dashboards/results.
```

Denied:

```text
- Create/update/delete resources.
- Import datasets.
- Configure targets/evaluators.
- Start/cancel test runs.
- Create secrets/providers.
```

---

## 10. Workspace Membership Rule

For normal USER accounts, workspace membership is required to access workspace-scoped resources.

Workspace-scoped resources include:

```text
workspaces
projects
datasets with scope PROJECT or WORKSPACE
dataset schemas with scope PROJECT or WORKSPACE
chatbot targets
evaluators
test runs
runner events visible through test run APIs
artifacts visible through test run APIs
workspace secrets/providers
```

Rule:

```text
If users.system_role = USER and no active workspace_members row exists for the workspace, deny access.
```

Use `404 Not Found` instead of `403 Forbidden` when revealing resource existence is sensitive.

---

## 11. Permission Resolver

Pseudo-code:

```java
boolean canPerform(User user, UUID workspaceId, Action action) {
    if (action == Action.READ_PLAINTEXT_SECRET) {
        return false;
    }

    if (user.status() != UserStatus.ACTIVE) {
        return false;
    }

    if (user.systemRole() == SystemRole.PLATFORM_ADMIN) {
        return true;
    }

    WorkspaceMember member = findActiveWorkspaceMember(workspaceId, user.id());

    if (member == null) {
        return false;
    }

    return workspacePermissionMatrix.allows(member.role(), action);
}
```

Implementation notes:

```text
1. Permission check should be centralized in PermissionService.
2. Controllers should not implement role logic manually.
3. Services should re-check permission for state-changing operations.
4. Internal runner APIs use service authentication, not workspace role authentication.
```

---

## 12. Permission Matrix MVP

| Action | Owner | Admin | Member | Viewer |
| ------ | :---: | :---: | :----: | :----: |
| View workspace | Yes | Yes | Yes | Yes |
| Update workspace | Yes | Yes | No | No |
| Delete/archive workspace | Yes | No | No | No |
| Invite member | Yes | Yes | No | No |
| Change member role | Yes | Yes | No | No |
| Remove/disable member | Yes | Yes | No | No |
| Create project | Yes | Yes | Yes | No |
| Update project | Yes | Yes | Yes | No |
| Delete/archive project | Yes | Yes | Yes | No |
| View project | Yes | Yes | Yes | Yes |
| Import dataset | Yes | Yes | Yes | No |
| Reuse dataset | Yes | Yes | Yes | No |
| Configure chatbot target | Yes | Yes | Yes | No |
| Configure evaluator | Yes | Yes | Yes | No |
| Start test run | Yes | Yes | Yes | No |
| Cancel test run | Yes | Yes | Yes | No |
| View dashboard/result | Yes | Yes | Yes | Yes |
| Create workspace secret | Yes | Yes | No | No |
| Create workspace provider | Yes | Yes | No | No |
| Create personal secret | Yes | Yes | Yes | No |
| Create personal provider | Yes | Yes | Yes | No |
| Read plaintext secret | No | No | No | No |

Platform admin can perform all actions above except `Read plaintext secret`.

---

## 13. Endpoint Permission Summary

### Workspace APIs

| Endpoint | Required permission |
| -------- | ------------------- |
| `GET /api/v1/workspaces` | Authenticated ACTIVE user |
| `POST /api/v1/workspaces` | Authenticated ACTIVE user |
| `GET /api/v1/workspaces/{workspaceId}` | Workspace VIEW |
| `PATCH /api/v1/workspaces/{workspaceId}` | Workspace UPDATE |
| `DELETE /api/v1/workspaces/{workspaceId}` | Workspace DELETE/ARCHIVE |
| `GET /api/v1/workspaces/{workspaceId}/members` | Workspace VIEW_MEMBERS |
| `POST /api/v1/workspaces/{workspaceId}/members` | Workspace INVITE_MEMBER |
| `PATCH /api/v1/workspaces/{workspaceId}/members/{memberId}` | Workspace CHANGE_MEMBER_ROLE |
| `DELETE /api/v1/workspaces/{workspaceId}/members/{memberId}` | Workspace REMOVE_MEMBER |

### Project APIs

| Endpoint | Required permission |
| -------- | ------------------- |
| `GET /api/v1/workspaces/{workspaceId}/projects` | Workspace VIEW |
| `POST /api/v1/workspaces/{workspaceId}/projects` | Project CREATE |
| `GET /api/v1/projects/{projectId}` | Project VIEW |
| `PATCH /api/v1/projects/{projectId}` | Project UPDATE |
| `DELETE /api/v1/projects/{projectId}` | Project DELETE/ARCHIVE |

### Secret APIs

| Endpoint | Required permission |
| -------- | ------------------- |
| `GET /api/v1/secrets?scope=PERSONAL` | Own personal secret metadata |
| `GET /api/v1/secrets?scope=WORKSPACE&workspaceId={id}` | Workspace secret management: OWNER / ADMIN |
| `POST /api/v1/secrets` with `scope=PERSONAL` | Create own personal secret: OWNER / ADMIN / MEMBER |
| `POST /api/v1/secrets` with `scope=WORKSPACE` | Create workspace secret: OWNER / ADMIN |
| `GET /api/v1/secrets/{secretId}` | Secret metadata access only |
| `PATCH /api/v1/secrets/{secretId}` | Secret metadata update |
| `POST /api/v1/secrets/{secretId}/rotate` | Secret value rotation |
| `DELETE /api/v1/secrets/{secretId}` | Secret delete/archive |

### Provider APIs

| Endpoint | Required permission |
| -------- | ------------------- |
| `GET /api/v1/llm-providers?scope=PERSONAL_PROVIDER` | Own personal provider metadata |
| `GET /api/v1/llm-providers?scope=WORKSPACE_PROVIDER&workspaceId={id}` | Workspace provider visibility |
| `POST /api/v1/llm-providers` with `scope=PERSONAL_PROVIDER` | Create own personal provider: OWNER / ADMIN / MEMBER |
| `POST /api/v1/llm-providers` with `scope=WORKSPACE_PROVIDER` | Create workspace provider: OWNER / ADMIN |
| `GET /api/v1/llm-providers/{providerId}` | Provider metadata access |
| `PATCH /api/v1/llm-providers/{providerId}` | Provider management access |
| `DELETE /api/v1/llm-providers/{providerId}` | Provider delete/archive access |
| `POST /api/v1/llm-providers/{providerId}/test-connection` | Provider use/manage access; not VIEWER |
| `GET /api/v1/llm-providers/{providerId}/models` | Provider metadata access |

### Dataset APIs

| Endpoint | Required permission |
| -------- | ------------------- |
| `GET /api/v1/projects/{projectId}/dataset-schemas/current` | Project VIEW |
| `GET /api/v1/dataset-schemas/{schemaId}` | Schema VIEW through project/workspace/personal scope |
| `POST /api/v1/projects/{projectId}/dataset-schemas/versions` | Project UPDATE: OWNER / ADMIN / MEMBER |
| `GET /api/v1/projects/{projectId}/datasets` | Project VIEW |
| `GET /api/v1/datasets/{datasetId}` | Dataset VIEW |
| `PATCH /api/v1/datasets/{datasetId}` | Dataset UPDATE: OWNER / ADMIN / MEMBER |
| `DELETE /api/v1/datasets/{datasetId}` | Dataset DELETE/ARCHIVE: OWNER / ADMIN / MEMBER |
| `GET /api/v1/datasets/{datasetId}/versions` | Dataset VIEW |
| `GET /api/v1/dataset-versions/{datasetVersionId}/rows` | Dataset VIEW |
| `GET /api/v1/dataset-versions/{datasetVersionId}/variables` | Dataset VIEW |
| `POST /api/v1/dataset-versions/{datasetVersionId}/draft` | Dataset UPDATE: OWNER / ADMIN / MEMBER |
| `POST /api/v1/dataset-versions/{draftVersionId}/rows` | Dataset UPDATE on DRAFT version: OWNER / ADMIN / MEMBER |
| `PATCH /api/v1/dataset-versions/{draftVersionId}/rows/{rowId}/cells` | Dataset UPDATE on DRAFT version: OWNER / ADMIN / MEMBER |
| `POST /api/v1/dataset-versions/{draftVersionId}/rows/bulk-upsert` | Dataset UPDATE on DRAFT version: OWNER / ADMIN / MEMBER |
| `DELETE /api/v1/dataset-versions/{draftVersionId}/rows/{rowId}` | Dataset UPDATE on DRAFT version: OWNER / ADMIN / MEMBER |
| `POST /api/v1/dataset-versions/{draftVersionId}/publish` | Dataset UPDATE/PUBLISH: OWNER / ADMIN / MEMBER |
| `POST /api/v1/dataset-versions/{draftVersionId}/discard` | Dataset UPDATE on DRAFT version: OWNER / ADMIN / MEMBER |
| `GET /api/v1/projects/{projectId}/dataset-import-sources/*` | Target project VIEW and source dataset/project VIEW |
| `POST /api/v1/projects/{projectId}/dataset-import-jobs` | Target project IMPORT_DATASET: OWNER / ADMIN / MEMBER |
| `GET /api/v1/dataset-import-jobs/{importJobId}` | Target project VIEW |
| `POST /api/v1/dataset-import-jobs/{importJobId}/confirm` | Target project IMPORT_DATASET: OWNER / ADMIN / MEMBER |
| `POST /api/v1/dataset-import-jobs/{importJobId}/cancel` | Target project IMPORT_DATASET: OWNER / ADMIN / MEMBER |
| `POST /api/v1/dataset-versions/{datasetVersionId}/exports` | Project EXPORT_DATASET: OWNER / ADMIN / MEMBER |

Dataset edit rules:

```text
1. VIEWER can view dataset rows but cannot edit cells, add rows, delete rows or publish versions.
2. OWNER / ADMIN / MEMBER can create DRAFT versions and edit dataset rows.
3. ACTIVE DatasetVersion must not be patched directly.
4. Publishing a DRAFT version creates the new current ACTIVE version and preserves old versions for audit.
5. Dataset export requires EXPORT_DATASET because it produces a report/handoff file.
```

### Chatbot Target APIs

| Endpoint | Required permission |
| -------- | ------------------- |
| `GET /api/v1/projects/{projectId}/chatbot-targets` | Project VIEW |
| `POST /api/v1/projects/{projectId}/chatbot-targets` | Project UPDATE / Configure chatbot target: OWNER / ADMIN / MEMBER |
| `GET /api/v1/chatbot-targets/{chatbotTargetId}` | Project VIEW |
| `PATCH /api/v1/chatbot-targets/{chatbotTargetId}` | Project UPDATE / Configure chatbot target: OWNER / ADMIN / MEMBER |
| `DELETE /api/v1/chatbot-targets/{chatbotTargetId}` | Project UPDATE / Configure chatbot target: OWNER / ADMIN / MEMBER |
| `POST /api/v1/chatbot-targets/inspect-curl` | Authenticated ACTIVE user; no persistence allowed |
| `POST /api/v1/chatbot-targets/{chatbotTargetId}/test-connection` | Project UPDATE / Configure chatbot target: OWNER / ADMIN / MEMBER |
| `GET /api/v1/chatbot-targets/{chatbotTargetId}/examples` | Project VIEW |
| `GET /api/v1/chatbot-target-examples/{exampleId}` | Project VIEW through target ownership |
| `PATCH /api/v1/chatbot-targets/{chatbotTargetId}/response-extraction` | Project UPDATE / Configure chatbot target: OWNER / ADMIN / MEMBER |

### Evaluator APIs

| Endpoint | Required permission |
| -------- | ------------------- |
| `GET /api/v1/projects/{projectId}/evaluators` | Project VIEW |
| `POST /api/v1/projects/{projectId}/evaluators` | Project UPDATE / Configure evaluator: OWNER / ADMIN / MEMBER |
| `GET /api/v1/evaluators/{evaluatorId}` | Project VIEW through evaluator ownership |
| `PATCH /api/v1/evaluators/{evaluatorId}` | Project UPDATE / Configure evaluator: OWNER / ADMIN / MEMBER |
| `DELETE /api/v1/evaluators/{evaluatorId}` | Project UPDATE / Configure evaluator: OWNER / ADMIN / MEMBER |
| `GET /api/v1/evaluators/{evaluatorId}/versions` | Project VIEW through evaluator ownership |
| `POST /api/v1/evaluators/{evaluatorId}/versions` | Project UPDATE / Configure evaluator: OWNER / ADMIN / MEMBER |
| `GET /api/v1/evaluator-versions/{evaluatorVersionId}` | Project VIEW through evaluator ownership |
| `POST /api/v1/projects/{projectId}/evaluators/validate` | Project UPDATE / Configure evaluator: OWNER / ADMIN / MEMBER |
| `GET /api/v1/projects/{projectId}/evaluator-field-sources` | Project VIEW |
| `GET /api/v1/evaluator-operators` | Authenticated ACTIVE user |

### Test Run APIs

| Endpoint | Required permission |
| -------- | ------------------- |
| `POST /api/v1/projects/{projectId}/test-runs/preflight` | Project RUN_TEST: OWNER / ADMIN / MEMBER |
| `POST /api/v1/projects/{projectId}/test-runs` | Project RUN_TEST: OWNER / ADMIN / MEMBER |
| `GET /api/v1/projects/{projectId}/test-runs` | Project VIEW |
| `GET /api/v1/test-runs/{testRunId}` | Project VIEW through run ownership |
| `GET /api/v1/test-runs/{testRunId}/results` | Project VIEW through run ownership |
| `GET /api/v1/test-runs/{testRunId}/results/{testCaseResultId}` | Project VIEW through run ownership |
| `GET /api/v1/test-runs/{testRunId}/artifacts` | Project VIEW through run ownership; artifacts must be sanitized |
| `GET /api/v1/test-runs/{testRunId}/artifacts/{artifactType}/content` | Project VIEW through run ownership; artifacts must be sanitized |
| `PUT /api/v1/test-case-results/{testCaseResultId}/review` | Project REVIEW_TEST_RESULT: OWNER / ADMIN / MEMBER |
| `DELETE /api/v1/test-case-results/{testCaseResultId}/review` | Project REVIEW_TEST_RESULT: OWNER / ADMIN / MEMBER |
| `PATCH /api/v1/test-runs/{testRunId}/results/reviews/bulk` | Project REVIEW_TEST_RESULT: OWNER / ADMIN / MEMBER |
| `POST /api/v1/test-runs/{testRunId}/exports` | Project EXPORT_TEST_RESULT: OWNER / ADMIN / MEMBER |
| `POST /api/v1/test-runs/{testRunId}/cancel` | Project CANCEL_RUN: OWNER / ADMIN / MEMBER |
| `GET /api/v1/test-runs/{testRunId}/events` | Project VIEW through run ownership |

Review/export rules:

```text
1. VIEWER can view machine and reviewed results but cannot change QC final verdict.
2. OWNER / ADMIN / MEMBER can review testcase results and add notes.
3. Export result/dataset actions are write-like actions because they create files/jobs.
4. Exported files must follow the same visibility rules as the source project/run/dataset.
5. PLATFORM_ADMIN may review/export for operational support, except plaintext secret exposure is still denied.
```

### Runner Internal APIs

Runner internal APIs use service authentication and do not use workspace role permission directly.

| Endpoint | Required authentication / scope |
| -------- | ------------------------------- |
| `POST /internal/runner/instances/register` | Runner service token with `runner:register` |
| `POST /internal/runner/instances/heartbeat` | Runner service token with `runner:heartbeat` |
| `GET /internal/runner/test-runs/{testRunId}/config` | Runner service token with `runner:read-config` |
| `POST /internal/runner/test-runs/{testRunId}/runtime-secrets` | Runner service token with `runner:read-runtime-secrets` |
| `GET /internal/runner/test-runs/{testRunId}/control-state` | Runner service token with `runner:read-control-state` |
| `POST /internal/runner/test-runs/{testRunId}/artifacts` | Runner service token with `runner:write-artifacts` |
| `POST /internal/runner/test-runs/{testRunId}/events` | Runner service token with `runner:write-events`; fallback/debug only |

Rules:

```text
1. /internal/runner/** must not be callable from apps/web.
2. /internal/runner/** must not accept user access tokens as sufficient authentication.
3. Runtime secret resolution is allowed only for secrets referenced by the locked TestRun config.
4. Internal APIs must still avoid logging service token, plaintext secret, Authorization and Cookie values.
```

---

## 14. Secret Access Rule

No API may return plaintext secret after creation.

Forbidden response:

```json
{
  "secretValue": "sk-real-api-key"
}
```

Allowed response:

```json
{
  "id": "3e8d1de2-2a26-4d9a-a0dd-9c6ce056d9ea",
  "name": "OpenAI Workspace Key",
  "scope": "WORKSPACE",
  "hasValue": true,
  "maskedPreview": "sk-****abcd",
  "status": "ACTIVE"
}
```

Secret runtime resolution is allowed only inside backend/runner trusted execution flow.

Rules:

```text
1. UI cannot request plaintext secret.
2. PLATFORM_ADMIN cannot request plaintext secret.
3. Secret update means replacing the encrypted value with a newly submitted value.
4. Secret test endpoints must mask errors and request/response metadata.
```

---

## 15. Resource Visibility Rule

When a user does not have permission to access a resource:

```text
1. Return 404 if exposing existence is sensitive.
2. Return 403 if the user can know the resource exists but cannot perform the action.
```

Examples:

```text
GET /api/v1/projects/{projectId}
- If project is outside user's workspace: 404.

PATCH /api/v1/workspaces/{workspaceId}
- If user is VIEWER in that workspace: 403.
```

---

## 16. Auditing Expectations

MVP should at least log security-relevant events:

```text
1. Login success/failure.
2. Refresh token failure.
3. Logout.
4. Workspace member role change.
5. Secret create/update/delete.
6. Provider create/update/test.
7. Test run start/cancel.
8. Permission denied for sensitive actions.
```

Logs must not contain:

```text
- plaintext password
- plaintext refresh token
- plaintext API key
- Authorization header value
- Cookie header value
```
