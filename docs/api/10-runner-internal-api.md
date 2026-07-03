# VinFast QC Copilot - Runner Internal API Contract

Version: `v0.2`  
Status: API Contract Draft  
Base path: `/internal/runner`  
Related docs:

```text
00-api-overview.md
01-api-conventions.md
02-auth-permission-contract.md
08-test-run-api.md
09-sse-events-contract.md
PRD v0.7
ERD v0.5
```

---

## 1. Purpose

This document defines the internal API contract between:

```text
apps/api
  Java Spring Boot backend.

apps/runner
  Node.js Promptfoo Runner service.
```

The Runner Internal API is not a frontend-facing API. It exists so the Node Runner can:

```text
1. Register and heartbeat runner instances.
2. Fetch locked test run config from the backend.
3. Resolve runtime secret material through a controlled internal flow.
4. Check whether a run should be cancelled.
5. Save artifact metadata and artifact integrity hashes.
6. Optionally publish runner events through HTTP fallback when Redis event publishing is unavailable.
```

Normal user-facing API contracts are documented in `docs/api/00` through `docs/api/09`.

---

## 2. Design Principles

```text
1. Internal APIs are service-to-service only.
2. Internal APIs are not exposed to apps/web.
3. Internal APIs must use runner service authentication, not user JWT authentication.
4. Redis Streams remain the primary command/event transport.
5. The backend database remains the source of truth.
6. Runner command payloads contain references only.
7. Runner fetches locked config from Backend, not from Redis.
8. Runner must not receive mutable/current project config for an already-created TestRun.
9. Secret material may be returned only to an authenticated runner and only for runtime execution.
10. Internal API responses must never be written directly to logs/artifacts without masking.
```

Important boundary:

```text
Frontend-facing API:
  /api/v1/**

Runner internal API:
  /internal/runner/**
```

Deployment must prevent public internet access to `/internal/runner/**` unless protected by strong service authentication and network controls.

---

## 3. Internal Authentication

### 3.1. Authentication method

MVP uses a Runner service token.

Request header:

```http
Authorization: Bearer <runner-service-token>
```

The token represents a runner service, not a user.

The backend must validate:

```text
1. Token is present.
2. Token is valid.
3. Token is not expired/revoked.
4. Token has runner service scope.
5. Runner identity in request is allowed to use this token.
```

Recommended internal scopes:

```text
runner:register
runner:heartbeat
runner:read-config
runner:read-runtime-secrets
runner:read-control-state
runner:write-artifacts
runner:write-events
```

MVP may use one service token with all runner scopes, but code should keep scope checks explicit so rotation/hardening is easier later.

---

### 3.2. Required headers

All internal runner requests should include:

```http
Authorization: Bearer <runner-service-token>
X-Runner-Id: runner-01
X-Request-Id: 01J0Z7Y4Y4F9E1S2Y9W8Q7P6A5
```

Rules:

```text
1. X-Runner-Id must match runner_instances.runner_id or be registerable.
2. X-Request-Id is used for traceability.
3. Backend should include traceId in Problem Details responses.
4. Backend must never log runner service token.
```

---

### 3.3. Network protection

Recommended deployment controls:

```text
1. Do not expose /internal/runner/** through public nginx route.
2. Allow only internal Docker network, private subnet, VPN, or mTLS gateway.
3. Use separate rate limits for internal APIs.
4. Log runner identity, not service token value.
```

---

## 4. Endpoint Summary

| Method | Path | Purpose |
| ------ | ---- | ------- |
| `POST` | `/internal/runner/instances/register` | Register or upsert runner instance on boot |
| `POST` | `/internal/runner/instances/heartbeat` | Update runner heartbeat/status/current run |
| `GET` | `/internal/runner/test-runs/{testRunId}/config` | Fetch locked sanitized run config |
| `POST` | `/internal/runner/test-runs/{testRunId}/runtime-secrets` | Resolve runtime secret material for execution |
| `GET` | `/internal/runner/test-runs/{testRunId}/control-state` | Check cancel/control state |
| `POST` | `/internal/runner/test-runs/{testRunId}/artifacts` | Save artifact metadata and hashes |
| `POST` | `/internal/runner/test-runs/{testRunId}/events` | Optional HTTP fallback for runner event publish |

Primary event transport remains Redis Streams:

```text
Runner -> XADD vfqc:runs:events
Backend -> XREADGROUP vfqc:runs:events
```

`POST /internal/runner/test-runs/{testRunId}/events` is a fallback/diagnostic internal endpoint. It should not replace Redis Streams in MVP unless explicitly configured.

---

## 5. Related Database Entities

Internal runner APIs touch or read these entities:

```text
runner_instances
runner_commands
runner_events
test_runs
test_run_targets
test_run_evaluator_versions
test_case_results
promptfoo_run_artifacts
secrets
llm_providers
datasets
dataset_versions
dataset_rows
chatbot_targets
evaluator_versions
evaluator_rules
```

Rules:

```text
1. Internal APIs must respect TestRun locked snapshots.
2. Internal APIs must not mutate dataset/evaluator/target definitions directly.
3. Internal APIs may update runner_instances heartbeat/status.
4. Internal APIs may update artifact metadata.
5. Internal APIs may write runner events only through the event ingestion path.
```

---

## 6. Common Internal Models

### 6.1. RunnerInstanceStatus

```text
IDLE
RUNNING
OFFLINE
UNHEALTHY
```

---

### 6.2. TestRunStatus

```text
PENDING
RUNNING
CANCELLING
CANCELLED
COMPLETED
FAILED
```

---

### 6.3. RunnerConfigSecretMode

```text
REFERENCES_ONLY
RUNTIME_SECRET_BUNDLE_REQUIRED
```

Meaning:

```text
REFERENCES_ONLY:
  Config response contains secret references only.

RUNTIME_SECRET_BUNDLE_REQUIRED:
  Runner must call runtime secret resolution endpoint before execution.
```

Default MVP behavior:

```text
GET /config returns REFERENCES_ONLY.
POST /runtime-secrets returns runtime secret material for authenticated runner only.
```

---

### 6.4. Internal problem response

Internal APIs use the same RFC 9457 problem format as public APIs.

Example:

```json
{
  "type": "https://vfqc.vinfast.vn/problems/runner-unauthorized",
  "title": "Runner authentication failed",
  "status": 401,
  "detail": "Runner service token is missing, invalid or expired.",
  "instance": "/internal/runner/test-runs/6e74a5df-1e54-4d73-ae1a-3d49c8991eb6/config",
  "code": "RUNNER_UNAUTHORIZED",
  "traceId": "01J0Z7Y4Y4F9E1S2Y9W8Q7P6A5"
}
```

---

## 7. POST /instances/register

### Purpose

Register or upsert a runner instance when the Node Runner boots.

```http
POST /internal/runner/instances/register
```

---

### Permission

Runner service token with scope:

```text
runner:register
```

---

### Request body

```json
{
  "runnerId": "runner-01",
  "hostname": "vfqc-runner-01",
  "version": "0.1.0",
  "promptfooVersion": "0.121.17",
  "promptfooAdapterSchemaVersion": 1,
  "capabilities": {
    "maxConcurrentRuns": 1,
    "supportsCancel": true,
    "supportsRuntimeSecretBundle": true,
    "supportsArtifactHashing": true
  },
  "startedAt": "2026-07-02T10:00:00+07:00"
}
```

---

### Response - 200 OK

Runner target list rule:

```text
1. `targets` may contain one target for SINGLE runs.
2. `targets` may contain two or more targets for COMPARISON runs.
3. COMPARISON may include multiple CHATBOT_TARGET entries, multiple LLM_BASELINE entries or a mix of both.
4. Runner must preserve target order when producing normalized results so the UI comparison matrix stays stable.
```

```json
{
  "runnerId": "runner-01",
  "status": "IDLE",
  "registered": true,
  "heartbeatIntervalSeconds": 10,
  "runnerOfflineThresholdSeconds": 45,
  "serverTime": "2026-07-02T10:00:01+07:00"
}
```

---

### Backend side effects

```text
1. If runner_instances.runner_id exists, update status and heartbeat metadata.
2. If it does not exist, create runner_instances row.
3. Set status = IDLE unless current_test_run_id is still active and reconciliation says otherwise.
4. Set last_heartbeat_at = now.
```

---

### Error codes

```text
RUNNER_UNAUTHORIZED
RUNNER_FORBIDDEN
RUNNER_INSTANCE_CONFLICT
VALIDATION_ERROR
```

---

## 8. POST /instances/heartbeat

### Purpose

Update runner heartbeat, current status and current test run.

```http
POST /internal/runner/instances/heartbeat
```

Runner should send heartbeat every `heartbeatIntervalSeconds`.

---

### Permission

Runner service token with scope:

```text
runner:heartbeat
```

---

### Request body

```json
{
  "runnerId": "runner-01",
  "status": "RUNNING",
  "currentTestRunId": "6e74a5df-1e54-4d73-ae1a-3d49c8991eb6",
  "currentCommandId": "2f3f3f5c-9d3d-4300-99c0-8db5d64b9ad0",
  "health": {
    "uptimeSeconds": 360,
    "memoryRssMb": 512,
    "activeRunCount": 1,
    "redisConnected": true,
    "backendReachable": true
  },
  "observedAt": "2026-07-02T10:05:00+07:00"
}
```

---

### Response - 200 OK

```json
{
  "runnerId": "runner-01",
  "status": "RUNNING",
  "accepted": true,
  "serverTime": "2026-07-02T10:05:00+07:00",
  "nextHeartbeatInSeconds": 10
}
```

---

### Backend side effects

```text
1. Update runner_instances.status.
2. Update runner_instances.current_test_run_id.
3. Update runner_instances.last_heartbeat_at.
4. Backend monitor may mark runner OFFLINE if last_heartbeat_at exceeds threshold.
```

---

### Validation rules

```text
1. runnerId is required.
2. status must be a valid runner_instance_status.
3. currentTestRunId is required when status = RUNNING.
4. currentTestRunId must be null when status = IDLE unless backend allows reconciliation window.
```

---

### Error codes

```text
RUNNER_UNAUTHORIZED
RUNNER_FORBIDDEN
RUNNER_INSTANCE_NOT_FOUND
RUNNER_HEARTBEAT_INVALID
VALIDATION_ERROR
```

---

## 9. GET /test-runs/{testRunId}/config

### Purpose

Fetch the locked run config needed by the Runner to build Promptfoo configuration.

```http
GET /internal/runner/test-runs/{testRunId}/config
```

This endpoint returns the config locked when the TestRun was created. It must not return mutable current project config that could have changed after the run started.

---

### Permission

Runner service token with scope:

```text
runner:read-config
```

---

### Query parameters

| Parameter | Type | Required | Description |
| --------- | ---- | -------- | ----------- |
| `includeRows` | boolean | No | Whether to include dataset rows inline. Default `true` for MVP. |
| `includePromptfooPreview` | boolean | No | Whether to include sanitized compact promptfoo snapshot. Default `true`. |

MVP default:

```text
includeRows=true
includePromptfooPreview=true
```

For large datasets, future versions may page dataset rows or provide a separate streaming config endpoint.

---

### Response - 200 OK

```json
{
  "testRun": {
    "id": "6e74a5df-1e54-4d73-ae1a-3d49c8991eb6",
    "projectId": "7df96e1c-0174-4302-8276-83db8b52d4bc",
    "workspaceId": "4bcfb80f-b2e1-4449-ad72-5c3b4ecb42a1",
    "runType": "COMPARISON",
    "status": "PENDING",
    "createdAt": "2026-07-02T10:00:00+07:00",
    "startedBy": "a38f0878-1ebe-49db-abf2-f6a372b6c246"
  },
  "locks": {
    "datasetId": "16cbf0b2-55f8-464b-a31d-2ec42a2b8c67",
    "datasetVersionId": "265f4988-9541-442e-9f6c-769e99de4a04",
    "datasetSchemaId": "0d343cc5-1438-4ed0-b643-b33e4ced47cc",
    "datasetSchemaVersion": 2,
    "domainConfigHash": "sha256:8fd1...",
    "promptfooConfigHash": "sha256:5ca2...",
    "promptfooVersion": "0.121.17",
    "promptfooAdapterSchemaVersion": 1
  },
  "dataset": {
    "schema": {
      "id": "0d343cc5-1438-4ed0-b643-b33e4ced47cc",
      "version": 2,
      "columns": [
        {
          "name": "question",
          "dataType": "TEXT",
          "required": true,
          "orderIndex": 0
        },
        {
          "name": "expected",
          "dataType": "TEXT",
          "required": false,
          "orderIndex": 1
        }
      ]
    },
    "version": {
      "id": "265f4988-9541-442e-9f6c-769e99de4a04",
      "version": 3,
      "rowCount": 100,
      "status": "ACTIVE"
    },
    "rows": [
      {
        "id": 10001,
        "rowIndex": 1,
        "data": {
          "question": "VF 8 có mấy phiên bản?",
          "expected": "VF 8 có hai phiên bản Eco và Plus."
        },
        "metadata": {
          "category": "product_info"
        }
      }
    ]
  },
  "targets": [
    {
      "testRunTargetId": "fe656865-33ad-4dbe-b607-6d8387df49d6",
      "targetType": "CHATBOT_TARGET",
      "displayName": "Internal Chatbot",
      "chatbotTargetId": "92087f1f-4ce2-4874-817c-90fbf00b0463",
      "request": {
        "method": "POST",
        "url": "https://internal-chatbot.example.com/api/chat",
        "headers": {
          "Content-Type": "application/json",
          "Authorization": "Bearer {{secret.internal_chatbot_token}}"
        },
        "queryParams": {},
        "body": {
          "question": "$dataset.question"
        }
      },
      "secretBindings": [
        {
          "placeholder": "{{secret.internal_chatbot_token}}",
          "secretId": "0d586cb3-0f1c-4b44-87d4-98a6e31b3c22",
          "field": "headers.Authorization",
          "format": "Bearer ${value}"
        }
      ],
      "responseExtraction": {
        "mainAnswerPath": "$.data.answer",
        "additionalPaths": {
          "intent": "$.data.intent",
          "confidence": "$.data.confidence"
        }
      },
      "timeouts": {
        "requestTimeoutMs": 60000
      }
    },
    {
      "testRunTargetId": "8b7d1c6a-67a2-4707-9e55-d7d03d6dce31",
      "targetType": "LLM_BASELINE",
      "displayName": "GPT baseline",
      "llmProviderId": "732d86d6-2967-4b71-b76b-8efc7134de7d",
      "model": "gpt-4.1-mini",
      "secretBindings": [
        {
          "placeholder": "{{secret.baseline_provider_key}}",
          "secretId": "b89e3461-2c8e-498f-9443-39e899cfa667",
          "field": "provider.apiKey",
          "format": "${value}"
        }
      ]
    }
  ],
  "evaluators": [
    {
      "evaluatorVersionId": "0cec11a5-85db-49b1-b381-ee8bbdb82c30",
      "evaluatorId": "0146ba46-a9f2-4905-9f6a-50f19e076b0e",
      "type": "RULE_BASED",
      "orderIndex": 0,
      "rules": [
        {
          "fieldSource": "extracted.mainAnswer",
          "operator": "contains",
          "expectedValue": "$dataset.expected",
          "weight": 1,
          "required": true
        }
      ]
    },
    {
      "evaluatorVersionId": "268f7681-f0a0-420c-a892-c8719fce071a",
      "evaluatorId": "d517139e-f8f9-4705-b21c-55739c598c8f",
      "type": "LLM_JUDGE",
      "orderIndex": 1,
      "judge": {
        "providerId": "732d86d6-2967-4b71-b76b-8efc7134de7d",
        "model": "gpt-4.1-mini",
        "rubric": "Grade the answer against expected facts. Return JSON only.",
        "threshold": 0.7,
        "outputSchemaVersion": 1
      }
    }
  ],
  "runOptions": {
    "cacheMode": "DISABLED",
    "maxRowsPerRun": 5000,
    "targetRequestTimeoutMs": 60000,
    "llmJudgeTimeoutMs": 120000,
    "testcaseTimeoutMs": 180000,
    "runTimeoutMs": 21600000,
    "maxAttempts": 3
  },
  "artifacts": {
    "basePath": "./storage/artifacts/test-runs/6e74a5df-1e54-4d73-ae1a-3d49c8991eb6/",
    "artifactSchemaVersion": 1
  },
  "security": {
    "secretMode": "RUNTIME_SECRET_BUNDLE_REQUIRED",
    "maskingRequired": true,
    "artifactSanitizationRequired": true,
    "plaintextSecretLoggingAllowed": false
  }
}
```

---

### Response rules

```text
1. Response must represent the locked TestRun config.
2. Response must not include plaintext secret values.
3. Response may include secret references and placeholders.
4. Response may include sanitized compact promptfoo config snapshot if requested.
5. Dataset rows may be inline for MVP because maxRowsPerRun is 5,000.
6. Future versions may page or stream dataset rows if needed.
```

---

### Backend validation

```text
1. testRunId exists.
2. test_run.status is PENDING, RUNNING or CANCELLING.
3. TestRun has locked dataset/schema/evaluator/target references.
4. DatasetVersion is the locked version, not current dataset version.
5. EvaluatorVersion is the locked version, not current evaluator version.
6. Target config is read from locked TestRunTarget snapshot or locked domain snapshot.
7. promptfoo_config_snapshot_json is sanitized compact only.
```

---

### Error codes

```text
RUNNER_UNAUTHORIZED
RUNNER_FORBIDDEN
TEST_RUN_NOT_FOUND
TEST_RUN_INVALID_STATE
RUNNER_CONFIG_NOT_FOUND
RUNNER_CONFIG_SNAPSHOT_INVALID
RUNNER_CONFIG_HASH_MISMATCH
DATASET_VERSION_NOT_FOUND
EVALUATOR_VERSION_NOT_FOUND
CHATBOT_TARGET_NOT_FOUND
PROVIDER_NOT_FOUND
```

---

## 10. POST /test-runs/{testRunId}/runtime-secrets

### Purpose

Resolve runtime secret material needed by Runner to execute the locked run.

```http
POST /internal/runner/test-runs/{testRunId}/runtime-secrets
```

This endpoint is intentionally separate from `GET /config` so config inspection and secret resolution can be audited separately.

---

### Permission

Runner service token with scope:

```text
runner:read-runtime-secrets
```

---

### Request body

```json
{
  "runnerId": "runner-01",
  "commandId": "2f3f3f5c-9d3d-4300-99c0-8db5d64b9ad0",
  "requestedSecretIds": [
    "0d586cb3-0f1c-4b44-87d4-98a6e31b3c22",
    "b89e3461-2c8e-498f-9443-39e899cfa667"
  ],
  "reason": "START_RUN_EXECUTION"
}
```

---

### Response - 200 OK

```json
{
  "testRunId": "6e74a5df-1e54-4d73-ae1a-3d49c8991eb6",
  "expiresAt": "2026-07-02T10:15:00+07:00",
  "secrets": [
    {
      "secretId": "0d586cb3-0f1c-4b44-87d4-98a6e31b3c22",
      "placeholder": "{{secret.internal_chatbot_token}}",
      "value": "real-runtime-token-value",
      "format": "Bearer ${value}"
    },
    {
      "secretId": "b89e3461-2c8e-498f-9443-39e899cfa667",
      "placeholder": "{{secret.baseline_provider_key}}",
      "value": "real-runtime-provider-key",
      "format": "${value}"
    }
  ]
}
```

---

### Security rules

```text
1. This is the only internal endpoint allowed to return plaintext secret material to Runner.
2. Only authenticated runner service may call this endpoint.
3. The response must never be logged.
4. Runner must keep secret values in memory only.
5. Runner must not write secret values to Redis events, DB payloads, artifacts or logs.
6. Runner must run sensitive data masking before emitting events or writing artifacts.
7. Backend should audit secret resolution metadata without logging secret values.
8. Secret values should be short-lived in runner memory and cleared after run if practical.
```

Audit log may include:

```text
- testRunId
- runnerId
- requestedSecretIds
- createdBy/requestedBy service identity
- reason
- timestamp
- success/failure
```

Audit log must not include:

```text
- secret value
- Authorization header value
- Cookie header value
- decrypted provider key
```

---

### Backend validation

```text
1. testRunId exists and is not terminal.
2. requestedSecretIds are referenced by locked run config.
3. Runner identity is allowed to execute this run.
4. Secrets are ACTIVE.
5. Secret scope is compatible with locked target/provider config.
6. Secret decryption succeeds.
```

---

### Error codes

```text
RUNNER_UNAUTHORIZED
RUNNER_FORBIDDEN
TEST_RUN_NOT_FOUND
TEST_RUN_INVALID_STATE
RUNNER_SECRET_RESOLUTION_DENIED
RUNNER_SECRET_NOT_REFERENCED_BY_RUN
SECRET_NOT_FOUND
CHATBOT_TARGET_SECRET_INVALID
PROVIDER_AUTH_FAILED
DEPENDENCY_UNAVAILABLE
```

---

## 11. GET /test-runs/{testRunId}/control-state

### Purpose

Allow Runner to check whether it should continue, cancel or stop execution.

```http
GET /internal/runner/test-runs/{testRunId}/control-state
```

Runner should call this endpoint:

```text
1. Before starting run execution.
2. Before each testcase.
3. After each testcase.
4. Before each target call in comparison runs.
5. Before each LLM Judge call.
6. Before writing final artifact.
```

---

### Permission

Runner service token with scope:

```text
runner:read-control-state
```

---

### Response - 200 OK

```json
{
  "testRunId": "6e74a5df-1e54-4d73-ae1a-3d49c8991eb6",
  "status": "CANCELLING",
  "shouldCancel": true,
  "cancelRequestedAt": "2026-07-02T10:03:00+07:00",
  "cancelReason": "User requested cancellation.",
  "runTimeoutExceeded": false,
  "serverTime": "2026-07-02T10:03:10+07:00"
}
```

---

### Behavior rules

```text
1. If status = CANCELLING, shouldCancel = true.
2. If status = CANCELLED, COMPLETED or FAILED, runner should stop idempotently.
3. If runTimeoutExceeded = true, runner should stop and publish RUN_FAILED unless backend already did.
4. If backend is temporarily unavailable, runner may retry according to internal retry policy.
```

---

### Error codes

```text
RUNNER_UNAUTHORIZED
RUNNER_FORBIDDEN
TEST_RUN_NOT_FOUND
RUNNER_CONTROL_STATE_UNAVAILABLE
DEPENDENCY_UNAVAILABLE
```

---

## 12. POST /test-runs/{testRunId}/artifacts

### Purpose

Save metadata for artifacts written by Runner.

```http
POST /internal/runner/test-runs/{testRunId}/artifacts
```

This endpoint does not upload large artifact files in MVP. Runner writes artifacts to the configured storage path and sends metadata/path/hash to Backend.

---

### Permission

Runner service token with scope:

```text
runner:write-artifacts
```

---

### Request body

```json
{
  "runnerId": "runner-01",
  "artifactSchemaVersion": 1,
  "sanitized": true,
  "basePath": "./storage/artifacts/test-runs/6e74a5df-1e54-4d73-ae1a-3d49c8991eb6/",
  "configFilePath": "./storage/artifacts/test-runs/6e74a5df-1e54-4d73-ae1a-3d49c8991eb6/promptfooconfig.json",
  "resultJsonPath": "./storage/artifacts/test-runs/6e74a5df-1e54-4d73-ae1a-3d49c8991eb6/results.json",
  "resultJsonlPath": "./storage/artifacts/test-runs/6e74a5df-1e54-4d73-ae1a-3d49c8991eb6/results.jsonl",
  "htmlReportPath": "./storage/artifacts/test-runs/6e74a5df-1e54-4d73-ae1a-3d49c8991eb6/report.html",
  "logsPath": "./storage/artifacts/test-runs/6e74a5df-1e54-4d73-ae1a-3d49c8991eb6/runner.log",
  "configSha256": "sha256:e8ad6b5f...",
  "resultSha256": "sha256:c5c303c1...",
  "domainConfigHash": "sha256:8fd1...",
  "promptfooConfigHash": "sha256:5ca2...",
  "metadata": {
    "promptfooVersion": "0.121.17",
    "promptfooAdapterSchemaVersion": 1,
    "createdAt": "2026-07-02T10:10:00+07:00"
  }
}
```

---

### Response - 200 OK

```json
{
  "testRunId": "6e74a5df-1e54-4d73-ae1a-3d49c8991eb6",
  "artifactId": 90001,
  "sanitized": true,
  "configSha256": "sha256:e8ad6b5f...",
  "resultSha256": "sha256:c5c303c1...",
  "createdAt": "2026-07-02T10:10:01+07:00"
}
```

---

### Backend side effects

```text
1. Create or update promptfoo_run_artifacts row for test_run_id.
2. Store artifact paths.
3. Store config_sha256 and result_sha256.
4. Store artifact_schema_version.
5. Store sanitized flag.
```

---

### Validation rules

```text
1. sanitized must be true before artifacts can be exposed through public API.
2. configSha256 and resultSha256 must use sha256: prefix.
3. Artifact path must be under configured artifact root.
4. Path traversal must be rejected.
5. Artifact metadata must not contain secret values.
6. If sanitized = false, backend may store metadata but public artifact API must return TEST_RUN_ARTIFACT_UNSANITIZED.
```

---

### Error codes

```text
RUNNER_UNAUTHORIZED
RUNNER_FORBIDDEN
TEST_RUN_NOT_FOUND
RUNNER_ARTIFACT_METADATA_INVALID
RUNNER_ARTIFACT_PATH_INVALID
RUNNER_ARTIFACT_UNSANITIZED
RUNNER_ARTIFACT_HASH_INVALID
TEST_RUN_ARTIFACT_UNSANITIZED
VALIDATION_ERROR
```

---

## 13. POST /test-runs/{testRunId}/events

### Purpose

Optional HTTP fallback for publishing runner events to Backend.

```http
POST /internal/runner/test-runs/{testRunId}/events
```

Primary MVP path is Redis Streams:

```text
Runner XADD -> vfqc:runs:events
Backend XREADGROUP -> persist -> SSE broadcast
```

This endpoint exists for controlled fallback/debug mode only.

---

### Permission

Runner service token with scope:

```text
runner:write-events
```

---

### Request body

```json
{
  "eventId": "5aa843e2-3569-4f64-94cf-68445ecfd21f",
  "eventType": "TESTCASE_COMPLETED",
  "sequenceNo": 12,
  "runnerId": "runner-01",
  "payload": {
    "testRunId": "6e74a5df-1e54-4d73-ae1a-3d49c8991eb6",
    "testRunTargetId": "fe656865-33ad-4dbe-b607-6d8387df49d6",
    "datasetRowId": 10001,
    "status": "PASSED",
    "passed": true,
    "score": 0.86,
    "latencyMs": 1200,
    "completed": 1,
    "total": 100
  },
  "createdAt": "2026-07-02T10:05:00+07:00"
}
```

---

### Response - 202 Accepted

```json
{
  "eventId": "5aa843e2-3569-4f64-94cf-68445ecfd21f",
  "testRunId": "6e74a5df-1e54-4d73-ae1a-3d49c8991eb6",
  "accepted": true,
  "sequenceNo": 12
}
```

---

### Behavior rules

```text
1. Backend must apply the same idempotency rule as Redis event consumer.
2. event_id must be unique.
3. unique(test_run_id, sequence_no) must be enforced.
4. Backend must persist side effects before broadcasting SSE.
5. Payload must be compact normalized result only.
6. Payload must not contain raw request/response, raw cURL, secret or artifact body.
```

---

### Error codes

```text
RUNNER_UNAUTHORIZED
RUNNER_FORBIDDEN
TEST_RUN_NOT_FOUND
RUNNER_EVENT_INVALID
RUNNER_EVENT_DUPLICATE
RUNNER_EVENT_SEQUENCE_CONFLICT
RUNNER_EVENT_PAYLOAD_CONTAINS_SECRET
VALIDATION_ERROR
```

---

## 14. Runner Lifecycle Flow

### 14.1. Boot flow

```text
1. Runner process starts.
2. Runner reads runner service token from environment/secret manager.
3. Runner calls POST /internal/runner/instances/register.
4. Runner starts heartbeat loop.
5. Runner joins Redis consumer group vfqc-runners.
6. Runner waits for START_RUN command using XREADGROUP.
```

---

### 14.2. Start run flow

```text
1. Runner receives START_RUN command from vfqc:runs:commands.
2. Runner validates command payload shape.
3. Runner checks idempotency using testRunId/status.
4. Runner calls GET /internal/runner/test-runs/{testRunId}/config.
5. Runner calls POST /internal/runner/test-runs/{testRunId}/runtime-secrets if required.
6. Runner publishes RUN_STARTED through Redis events stream.
7. Runner builds sanitized Promptfoo config.
8. Runner writes promptfooconfig.json artifact.
9. Runner executes Promptfoo Node API.
10. Runner publishes testcase events.
11. Runner writes final artifacts.
12. Runner calls POST /internal/runner/test-runs/{testRunId}/artifacts.
13. Runner publishes final event.
14. Runner ACKs START_RUN command only after final event publish succeeds.
```

---

### 14.3. Cancel check flow

```text
1. User calls POST /api/v1/test-runs/{testRunId}/cancel.
2. Backend sets test_run.status = CANCELLING.
3. Backend publishes CANCEL_RUN command.
4. Runner either receives CANCEL_RUN or detects cancellation by control-state polling.
5. Runner stops at a safe boundary.
6. Runner writes partial artifact if needed.
7. Runner publishes RUN_CANCELLED.
8. Runner ACKs command.
```

---

### 14.4. Failure flow

```text
1. Runner catches system-level failure.
2. Runner masks error details.
3. Runner writes error artifact/log if possible.
4. Runner publishes RUN_FAILED event.
5. Runner saves artifact metadata if artifact exists.
6. Runner ACKs START_RUN only after final failure event publish succeeds.
7. If final event publish fails, command remains pending for recovery.
```

---

## 15. Config Locking Rules

`GET /config` must return locked config only.

Locked fields include:

```text
dataset_id
dataset_version_id
dataset_schema_id
dataset_schema_version
evaluator versions
judge_provider_id
judge_model
test_run_targets
domain_config_snapshot_json
promptfoo_config_snapshot_json sanitized compact
promptfoo_version
promptfoo_adapter_schema_version
```

Rules:

```text
1. If project target/evaluator/dataset changes after TestRun creation, Runner config must not change.
2. If TestRun is retried/recovered, Runner must receive the same locked config.
3. Backend must not rebuild config from mutable current project state without using locked snapshot references.
4. domain_config_hash and promptfoo_config_hash must match the locked snapshots.
```

---

## 16. Secret Handling Rules

```text
1. Public APIs never return plaintext secret.
2. GET /config never returns plaintext secret.
3. POST /runtime-secrets may return plaintext secret only to authenticated Runner.
4. Runtime secret response must not be logged.
5. Runtime secret response must not be persisted by Runner.
6. Runner must replace placeholders in memory only.
7. Runner must mask secrets before writing Redis event, artifact, log or HTTP fallback payload.
8. Full artifact must pass VfqcSensitiveDataMasker before writing final file metadata with sanitized=true.
```

Forbidden in runner events/artifacts:

```text
Authorization: Bearer real-token
Cookie: session=real-cookie
X-API-Key: real-key
api_key: real-key
access_token: real-token
refresh_token: real-token
password: real-password
secret: real-secret
```

Allowed:

```text
Authorization: Bearer ****MASKED****
X-API-Key: ****MASKED****
{{secret.internal_chatbot_token}}
```

---

## 17. Idempotency Rules

### 17.1. Config fetch

`GET /config` is idempotent.

```text
Same testRunId + same locked snapshot => same response except transient serverTime/trace metadata.
```

---

### 17.2. Runtime secret resolution

`POST /runtime-secrets` is safe to call multiple times for the same active run, but every call should be audited.

Rules:

```text
1. Returned secret material must match locked references.
2. Calling it again must not create new secret values.
3. If run is terminal, return TEST_RUN_INVALID_STATE or a safe empty response depending policy.
```

MVP rule:

```text
Terminal TestRun => 409 TEST_RUN_INVALID_STATE.
```

---

### 17.3. Artifact metadata

`POST /artifacts` is upsert by `testRunId`.

Rules:

```text
1. If no artifact row exists, create one.
2. If artifact row exists with same paths/hashes, return existing row.
3. If artifact row exists with different hashes, return 409 RUNNER_ARTIFACT_HASH_CONFLICT unless backend is explicitly reconciling.
```

---

### 17.4. HTTP event fallback

`POST /events` is idempotent by `eventId`.

Rules:

```text
1. Same eventId + same payload => accepted idempotently.
2. Same eventId + different payload => 409 RUNNER_EVENT_DUPLICATE.
3. Same testRunId + same sequenceNo + different eventId => 409 RUNNER_EVENT_SEQUENCE_CONFLICT.
```

---

## 18. State Transition Responsibilities

Runner internal APIs do not directly expose arbitrary state transition endpoints.

Allowed state effects:

```text
1. Runner heartbeat updates runner_instances only.
2. Runner event ingestion may update test_runs through normal event processing.
3. Artifact metadata endpoint updates promptfoo_run_artifacts only.
4. Control state endpoint is read-only.
5. Config endpoint is read-only.
6. Runtime secret endpoint is read-only from domain perspective, but audited.
```

State transition source:

```text
Runner publishes events.
Backend event consumer persists event side effects.
```

---

## 19. Error Examples

### 19.1. Runner unauthorized

```http
HTTP/1.1 401 Unauthorized
Content-Type: application/problem+json
```

```json
{
  "type": "https://vfqc.vinfast.vn/problems/runner-unauthorized",
  "title": "Runner authentication failed",
  "status": 401,
  "detail": "Runner service token is missing, invalid or expired.",
  "instance": "/internal/runner/instances/heartbeat",
  "code": "RUNNER_UNAUTHORIZED",
  "traceId": "01J0Z7Y4Y4F9E1S2Y9W8Q7P6A5"
}
```

---

### 19.2. Secret not referenced by run

```http
HTTP/1.1 403 Forbidden
Content-Type: application/problem+json
```

```json
{
  "type": "https://vfqc.vinfast.vn/problems/runner-secret-resolution-denied",
  "title": "Secret resolution denied",
  "status": 403,
  "detail": "One or more requested secrets are not referenced by the locked test run config.",
  "instance": "/internal/runner/test-runs/6e74a5df-1e54-4d73-ae1a-3d49c8991eb6/runtime-secrets",
  "code": "RUNNER_SECRET_NOT_REFERENCED_BY_RUN",
  "traceId": "01J0Z7Y4Y4F9E1S2Y9W8Q7P6A5"
}
```

---

### 19.3. Artifact path invalid

```http
HTTP/1.1 422 Unprocessable Content
Content-Type: application/problem+json
```

```json
{
  "type": "https://vfqc.vinfast.vn/problems/runner-artifact-path-invalid",
  "title": "Artifact path is invalid",
  "status": 422,
  "detail": "Artifact paths must be under the configured artifact root and must not contain path traversal.",
  "instance": "/internal/runner/test-runs/6e74a5df-1e54-4d73-ae1a-3d49c8991eb6/artifacts",
  "code": "RUNNER_ARTIFACT_PATH_INVALID",
  "traceId": "01J0Z7Y4Y4F9E1S2Y9W8Q7P6A5"
}
```

---

### 19.4. Event sequence conflict

```http
HTTP/1.1 409 Conflict
Content-Type: application/problem+json
```

```json
{
  "type": "https://vfqc.vinfast.vn/problems/runner-event-sequence-conflict",
  "title": "Runner event sequence conflict",
  "status": 409,
  "detail": "The sequence number is already used by another event in this test run.",
  "instance": "/internal/runner/test-runs/6e74a5df-1e54-4d73-ae1a-3d49c8991eb6/events",
  "code": "RUNNER_EVENT_SEQUENCE_CONFLICT",
  "traceId": "01J0Z7Y4Y4F9E1S2Y9W8Q7P6A5"
}
```

---

## 20. Stable Error Codes

```text
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
```

These codes should also be listed in `01-api-conventions.md`.

---

## 21. Logging Rules

Allowed logs:

```text
- runnerId
- testRunId
- commandId
- eventId
- sequenceNo
- status
- artifact path
- hash values
- error code
- traceId
```

Forbidden logs:

```text
- runner service token
- plaintext secret
- Authorization header value
- Cookie header value
- decrypted API key
- raw runtime secret bundle
- unsanitized request/response body
```

---

## 22. MVP Acceptance Criteria

The Runner Internal API is acceptable for MVP if:

```text
1. Runner can register on boot.
2. Runner can heartbeat every 10 seconds.
3. Backend can mark runner OFFLINE if heartbeat is stale.
4. Runner can fetch locked TestRun config.
5. Config response does not contain plaintext secret.
6. Runner can resolve runtime secret material through a separate audited endpoint.
7. Runtime secret response is not logged or persisted.
8. Runner can poll control-state and stop on cancellation.
9. Runner can save artifact metadata with sanitized=true and SHA-256 hashes.
10. Artifact paths are validated under artifact root.
11. Optional HTTP event fallback is idempotent by eventId.
12. Internal APIs are protected by runner service token.
13. Public frontend cannot access /internal/runner/**.
14. All internal errors use RFC 9457 Problem Details.
15. Backend and Runner logs do not contain plaintext secret or token values.
```
