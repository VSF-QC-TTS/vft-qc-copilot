# VinFast QC Copilot - API Overview

Version: `v0.2`  
Status: API Contract Draft  
Base path: `/api/v1`  
Applies to: Frontend-facing REST APIs, SSE APIs and internal Runner APIs  
Related docs: PRD v0.7, ERD v0.5

---

## 1. Purpose

This document is the entry point for the VinFast QC Copilot API contract.

It explains:

```text
1. How the API documentation set is organized.
2. Which API areas exist in the MVP.
3. Which services consume each API area.
4. Which conventions all endpoint contracts must follow.
5. How frontend, backend, runner, Redis Streams and Promptfoo fit together.
6. Which files should be read before implementing a specific module.
```

This file does not define all request/response DTOs. Detailed endpoint contracts are defined in separate files under `docs/api`.

---

## 1.1. QC Review Principle

The product must reduce QC dependency on raw Excel while staying Excel-compatible for input/output.

```text
1. Machine evaluation is advisory.
2. QC reviewed result is authoritative for reporting/export.
3. Machine result must remain immutable for audit.
4. QC review/manual override must be stored as a separate review layer.
5. Dataset must behave like an Excel-compatible QC workspace, not a static uploaded file.
6. Published DatasetVersion remains immutable; edits happen in DRAFT versions and publish as new ACTIVE versions.
7. Excel import/export remains a first-class workflow because QC teams are already familiar with Excel.
```

---

## 2. API Documentation Map

The API contract is split by responsibility.

```text
docs/api/
  00-api-overview.md
  01-api-conventions.md
  02-auth-permission-contract.md
  03-workspace-project-api.md
  04-secret-provider-api.md
  05-dataset-api.md
  06-chatbot-target-api.md
  07-evaluator-api.md
  08-test-run-api.md
  09-sse-events-contract.md
  10-runner-internal-api.md
```

Recommended reading order:

```text
1. 00-api-overview.md
2. 01-api-conventions.md
3. 02-auth-permission-contract.md
4. Module-specific API file
5. 09-sse-events-contract.md if the feature displays realtime run progress
6. 10-runner-internal-api.md if the feature is used by the Node Runner
```

---

## 3. API Files and Ownership

| File | Responsibility | Primary consumers |
| ---- | -------------- | ----------------- |
| `00-api-overview.md` | API documentation map and high-level system API flow | All developers |
| `01-api-conventions.md` | Naming, HTTP methods, pagination, errors, auth header, versioning | Frontend, Backend, Runner |
| `02-auth-permission-contract.md` | Login, refresh, logout, current user, permission model | Frontend, Backend |
| `03-workspace-project-api.md` | Workspace, workspace members, projects and project setup readiness | Frontend, Backend |
| `04-secret-provider-api.md` | Secret metadata, secret rotation, LLM providers, provider testing and provider test history | Frontend, Backend |
| `05-dataset-api.md` | Dataset schemas, dataset versions, Excel import, project clone import | Frontend, Backend |
| `06-chatbot-target-api.md` | cURL target config, request mapping, test connection, response extraction | Frontend, Backend |
| `07-evaluator-api.md` | Rule-based evaluator, LLM Judge evaluator and evaluator versioning | Frontend, Backend |
| `08-test-run-api.md` | Preflight, start run, cancel run, run results, comparison matrix, exports and artifacts | Frontend, Backend |
| `09-sse-events-contract.md` | Realtime run progress via Server-Sent Events | Frontend, Backend |
| `10-runner-internal-api.md` | Runner config fetch, heartbeat, control state, artifact metadata | Backend, Runner |

---

## 4. System Boundary

VinFast QC Copilot has three main application services:

```text
apps/web
  Frontend web console used by QC/QA members.

apps/api
  Java Spring Boot backend API.
  Source of truth for domain config, permissions, state machine and persistence.

apps/runner
  Node.js runner service.
  Consumes Redis Stream commands, builds Promptfoo config and executes Promptfoo Node API.
```

Recommended repository layout:

```text
apps/
  web/
  api/
  runner/

docs/
  api/
  backend/
  runner/
  architecture/
  prd/
  erd/

infra/
  docker/
  nginx/
  postgres/
  redis/

storage/
  artifacts/
```

---

## 5. API Base Path

All frontend-facing REST APIs must be exposed under:

```text
/api/v1
```

Examples:

```http
GET  /api/v1/workspaces
POST /api/v1/workspaces/{workspaceId}/projects
GET  /api/v1/projects/{projectId}
GET  /api/v1/projects/{projectId}/setup-status
POST /api/v1/projects/{projectId}/test-runs/preflight
```

Internal Runner APIs are mounted on the internal route behind nginx:

```text
/internal/runner
```

Examples:

```http
GET  /internal/runner/test-runs/{testRunId}/config
GET  /internal/runner/test-runs/{testRunId}/control-state
POST /internal/runner/instances/heartbeat
```

Rule:

```text
1. Do not expose versionless frontend-facing APIs.
2. Internal Runner APIs are intentionally outside `/api/v1` because nginx can route `/internal/**` only inside the deployment network.
3. Do not expose internal runner APIs to the public frontend origin.
4. Internal APIs still require authentication using runner/service credentials.
```

---

## 6. API Design Style

The API should follow Microsoft-style REST principles where practical:

```text
1. Resources are nouns.
2. Collections use plural resource names.
3. URL path segments use lowercase kebab-case.
4. JSON fields use camelCase.
5. HTTP methods define the operation.
6. Actions are allowed only when resource-oriented modeling is awkward or misleading.
7. Errors use RFC 9457 Problem Details with VFQC extension fields.
```

Examples of accepted action endpoints:

```http
POST /api/v1/auth/refresh
POST /api/v1/chatbot-targets/{targetId}/test-connection
POST /api/v1/projects/{projectId}/test-runs/preflight
POST /api/v1/test-runs/{testRunId}/cancel
```

These are allowed because they represent domain commands, not simple CRUD updates.

---

## 6.1. Project Setup Readiness Summary

Frontend setup wizard should use the project setup status endpoint before run preflight.

```http
GET /api/v1/projects/{projectId}/setup-status
```

Rules:

```text
1. setup-status is a lightweight readiness snapshot for UI guidance.
2. It must not replace TestRun preflight because preflight validates a concrete run request.
3. It aggregates dataset, target, evaluator and provider readiness into reusable wizard steps.
4. It must not expose secrets, raw cURL, raw request/response payloads or artifacts.
```

---

## 7. Authentication Summary

MVP uses access token + refresh token authentication.

```text
Access token:
- Short-lived.
- Sent by frontend using Authorization: Bearer <accessToken>.
- Used for normal API authorization.

Refresh token:
- Longer-lived.
- Stored in HttpOnly Secure cookie.
- Set by backend through Set-Cookie response header.
- Used only by /auth/refresh and /auth/logout.
```

Normal API request:

```http
Authorization: Bearer <accessToken>
```

Refresh token cookie example:

```http
Set-Cookie: vfqc_refresh_token=<refreshToken>; HttpOnly; Secure; SameSite=Lax; Path=/api/v1/auth; Max-Age=604800
```

Rules:

```text
1. Refresh token must not be returned in JSON response body.
2. Refresh token must not be stored in localStorage/sessionStorage.
3. Frontend JavaScript must not be able to read refresh token.
4. If frontend/backend are cross-origin, frontend must use credentials: include for refresh/logout calls.
5. Backend CORS must allow credentials only for explicit trusted origins, never wildcard origin.
```

Detailed auth contract is defined in:

```text
docs/api/02-auth-permission-contract.md
```

---

## 8. Permission Summary

Permission has two layers:

```text
System role:
  users.system_role = PLATFORM_ADMIN | USER

Workspace role:
  workspace_members.role = OWNER | ADMIN | MEMBER | VIEWER
```

General permission rule:

```text
1. PLATFORM_ADMIN can manage almost all platform resources.
2. Normal USER permissions depend on workspace membership and workspace role.
3. Users outside a workspace cannot access that workspace's resources.
4. Nobody can read plaintext secrets after creation, including PLATFORM_ADMIN.
5. VIEWER can read workspace/project/dashboard resources but cannot mutate them.
```

Detailed permission matrix is defined in:

```text
docs/api/02-auth-permission-contract.md
```

---

## 9. Error Format Summary

All API errors should use RFC 9457 Problem Details.

Content type:

```http
Content-Type: application/problem+json
```

Standard error shape:

```json
{
  "type": "https://vfqc.vinfast.vn/problems/validation-error",
  "title": "Validation failed",
  "status": 400,
  "detail": "Request body is invalid.",
  "instance": "/api/v1/projects",
  "code": "VALIDATION_ERROR",
  "traceId": "7f3f0e6a8c2a4f8f9d1a1b2c3d4e5f6a",
  "errors": [
    {
      "field": "name",
      "message": "Project name is required."
    }
  ]
}
```

VFQC extension fields:

```text
code:
  Stable machine-readable error code.

traceId:
  Request trace id for debugging.

errors:
  Optional field-level validation errors.
```

Rules:

```text
1. Frontend should use `code` for stable branching.
2. `title` and `detail` are user/developer readable but should not be used as stable programmatic keys.
3. ProblemDetail must not contain plaintext secret, Authorization header, API token, cookie or unsanitized cURL.
```

Detailed error conventions are defined in:

```text
docs/api/01-api-conventions.md
```

---

## 10. High-Level Product API Flow

The main QC flow is:

```text
1. User logs in.
2. User selects workspace.
3. User creates or opens project.
4. User configures one or more chatbot targets by cURL.
5. User tests connection and selects response extraction path.
6. User imports or reuses dataset.
7. User configures evaluator.
8. User runs preflight.
9. User starts a SINGLE run or a multi-target COMPARISON run.
10. Frontend listens to SSE progress.
11. User views dashboard, testcase detail and comparison matrix.
12. User cancels run if needed.
```

API areas involved:

```text
Login/current user:
  02-auth-permission-contract.md

Workspace/project:
  03-workspace-project-api.md

Secret/provider:
  04-secret-provider-api.md

Dataset:
  05-dataset-api.md

Chatbot target:
  06-chatbot-target-api.md

Evaluator:
  07-evaluator-api.md

Test run:
  08-test-run-api.md

Realtime progress:
  09-sse-events-contract.md

Runner internals:
  10-runner-internal-api.md
```

---

## 11. Execution API Flow

Test execution spans Backend API, Redis Streams, Runner and Promptfoo.

```text
1. Frontend calls POST /api/v1/projects/{projectId}/test-runs/preflight.
2. Backend validates dataset, selected comparison targets, evaluator, provider and row limit.
3. Frontend confirms start run.
4. Frontend calls POST /api/v1/projects/{projectId}/test-runs.
5. Backend locks dataset/schema/evaluator/comparison-target/provider snapshots.
6. Backend creates test_runs status PENDING.
7. Backend publishes START_RUN command to Redis Stream.
8. Runner consumes START_RUN.
9. Runner calls internal API to fetch locked run config.
10. Runner builds sanitized Promptfoo config.
11. Runner executes Promptfoo Node API.
12. Runner publishes normalized events to Redis Stream.
13. Backend consumes events and persists test_case_results.
14. Backend pushes realtime progress to frontend via SSE.
15. Runner writes sanitized artifacts and publishes final event.
16. Backend updates test_runs final status and summary metrics.
```

Important boundary:

```text
Redis command/event payloads contain references and compact normalized data only.
They must not contain plaintext secret, full dataset, full Promptfoo config or large artifacts.
```

---

## 12. Frontend-Facing APIs vs Internal Runner APIs

### 12.1. Frontend-facing APIs

Frontend-facing APIs are used by `apps/web`.

Examples:

```http
GET  /api/v1/workspaces
POST /api/v1/workspaces/{workspaceId}/projects
POST /api/v1/projects/{projectId}/dataset-import-jobs
POST /api/v1/chatbot-targets/{targetId}/test-connection
POST /api/v1/projects/{projectId}/test-runs
GET  /api/v1/test-runs/{testRunId}/events
```

Authentication:

```text
Authorization: Bearer <accessToken>
```

Authorization:

```text
PermissionService checks current user system role and workspace role.
```

---

### 12.2. Internal Runner APIs

Internal Runner APIs are used by `apps/runner`.

Examples:

```http
GET  /internal/runner/test-runs/{testRunId}/config
GET  /internal/runner/test-runs/{testRunId}/control-state
POST /internal/runner/instances/heartbeat
POST /internal/runner/test-runs/{testRunId}/artifacts
```

Authentication:

```text
Runner/service token.
```

Rules:

```text
1. Internal API responses may include runtime-only secret resolution metadata.
2. They must not return plaintext secret unless the endpoint is explicitly designed for secure runner runtime resolution.
3. If plaintext secret is ever returned to runner runtime, it must never be logged, stored in Redis events or written to artifact.
4. Internal APIs should be inaccessible from public browser clients.
```

Detailed contract is defined in:

```text
docs/api/10-runner-internal-api.md
```

---

## 13. Data and Snapshot Ownership

Backend database is the source of truth for domain config.

```text
Source of truth:
- workspace
- project
- secret metadata
- provider config
- dataset schema/version/rows
- chatbot target config
- evaluator version
- test run snapshot
- normalized results
- artifact metadata
```

Generated runtime artifacts are not source of truth for editable domain config.

```text
Generated artifacts:
- promptfooconfig.json
- results.json
- results.jsonl
- report.html
- runner.log
```

Rules:

```text
1. Promptfoo config is generated from locked backend domain config.
2. TestRun must lock all config needed to reproduce/debug the run.
3. promptfoo_config_snapshot_json in DB is sanitized compact snapshot only.
4. Full generated config belongs in artifact storage and must be sanitized.
5. Hashes are used for audit/integrity, not for secret recovery.
```

---

## 14. Secret Handling Boundary

Secret-related API rules:

```text
1. Plaintext secret may be accepted only on create/rotate flows.
2. Plaintext secret must never be returned by API after creation.
3. `secrets.encrypted_value` is persistence-internal and must never be exposed.
4. Secret metadata APIs may return maskedPreview only.
5. Provider APIs reference `secretId`; they must not duplicate API keys in provider config.
6. Chatbot target cURL/config must be sanitized before persistence.
7. Redis messages and SSE events must not contain plaintext secret.
8. Artifacts must pass sensitive data masking before they are exposed.
```

Detailed secret/provider contract is defined in:

```text
docs/api/04-secret-provider-api.md
```

Backend encryption and masking implementation should be defined in:

```text
docs/backend/security-secret-masking-spec.md
```

---

## 15. Realtime Boundary

MVP uses Server-Sent Events for frontend realtime progress.

```http
GET /api/v1/test-runs/{testRunId}/events
```

SSE is one-way:

```text
Backend -> Frontend
```

Commands from frontend still use HTTP APIs:

```http
POST /api/v1/test-runs/{testRunId}/cancel
```

SSE events should use `runner_events.sequence_no` as event id where possible.

Detailed SSE contract is defined in:

```text
docs/api/09-sse-events-contract.md
```

---

## 16. MVP API Areas

### 16.1. In scope

```text
- Authentication and token refresh.
- Current user profile.
- Workspace listing and management.
- Workspace member management.
- Project listing and management.
- Secret metadata and secret rotation.
- LLM provider management and provider testing.
- Dataset schema/version management.
- Excel dataset import preview/confirm.
- Excel-like dataset table editing through DRAFT dataset versions.
- Dataset import from another project/workspace/personal dataset.
- Chatbot target cURL config and test connection.
- Response extraction configuration.
- Rule-based evaluator configuration.
- LLM Judge evaluator configuration.
- Test run preflight/start/cancel/results/QC review/export/artifacts, including chatbot-vs-chatbot and chatbot-vs-baseline comparison.
- SSE progress events.
- Runner internal config/control/heartbeat/artifact APIs.
```

---

### 16.2. Out of scope

```text
- Public external API for third-party customers.
- OAuth2 app marketplace integration.
- WebSocket command/control channel.
- CI/CD integration APIs.
- Scheduled regression APIs.
- Pause/resume run APIs.
- Partial rerun failed testcase APIs.
- Enterprise audit log APIs.
- Full secret manager/KMS admin APIs.
```

---

## 17. API Contract Change Rules

API contract changes should follow these rules:

```text
1. Additive changes are preferred.
2. Removing or renaming response fields requires explicit migration notes.
3. New enum values require frontend fallback behavior.
4. Error `code` values must remain stable once used by frontend.
5. Endpoint paths should not change after frontend implementation starts unless unavoidable.
6. Any API that can expose secret/request/response/artifact data must include masking rules.
7. Any API that changes run state must describe valid state transitions.
8. Any API used by Runner must be backward-compatible across rolling deployments where possible.
```

---

## 18. OpenAPI Recommendation

After the markdown contract stabilizes, backend should expose an OpenAPI document generated from implementation.

Recommended path:

```http
GET /api/v1/openapi.json
```

Rule:

```text
1. Markdown API contract is the product/design source during MVP design.
2. Generated OpenAPI should become implementation verification once controllers exist.
3. If OpenAPI differs from markdown contract, update either implementation or contract explicitly.
```

---

## 19. Implementation Checklist

Before implementing a new API module, verify:

```text
1. Endpoint path follows /api/v1 and kebab-case conventions.
2. Request/response JSON uses camelCase.
3. Permission rule is defined.
4. Main DTOs are documented.
5. Success status codes are documented.
6. Error codes are documented.
7. State transitions are documented if the endpoint mutates lifecycle state.
8. Secret/masking rule is documented if the endpoint touches credentials, request, response or artifacts.
9. Pagination is documented for collection endpoints.
10. Idempotency/retry behavior is documented for command-like endpoints.
11. Related DB entities are clear.
12. Related Redis/SSE behavior is clear if applicable.
```

