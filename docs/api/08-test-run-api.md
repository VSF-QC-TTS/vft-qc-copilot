# VinFast QC Copilot - Test Run API Contract

Version: `v0.2`  
Status: API Contract Draft  
Base path: `/api/v1`  
Related docs:

- `docs/api/00-api-overview.md`
- `docs/api/01-api-conventions.md`
- `docs/api/02-auth-permission-contract.md`
- `docs/api/04-secret-provider-api.md`
- `docs/api/05-dataset-api.md`
- `docs/api/06-chatbot-target-api.md`
- `docs/api/07-evaluator-api.md`
- `docs/api/09-sse-events-contract.md`
- `docs/api/10-runner-internal-api.md`
- `prd_v07.md`
- `erd_v05.md`

---

## 1. Purpose

This document defines the REST API contract for creating, monitoring, cancelling and reading test runs in VinFast QC Copilot.

A TestRun is an immutable execution record created when QC starts a single-target or comparison evaluation. The backend locks dataset version, schema version, evaluator versions, target/provider selection and sanitized run snapshots. The Runner then executes the locked run through Promptfoo Node API.

This contract covers:

```text
1. Run preflight.
2. Single target run creation.
3. Comparison run creation.
4. Run listing and filtering.
5. Run detail and summary.
6. Testcase result listing, detail and comparison matrix view.
7. Run cancellation.
8. Artifact metadata access.
9. State machine and idempotency rules.
10. Result masking and artifact safety rules.
```

This document does not define:

```text
1. SSE wire format for realtime events.
2. Internal Runner config/control/heartbeat APIs.
3. Redis Streams consumer implementation.
4. Promptfoo adapter internals.
5. Backend service/package implementation.
```

Those details belong in `09-sse-events-contract.md`, `10-runner-internal-api.md`, backend specs and runner specs.

---

## 2. Design Principles

```text
1. A TestRun is created once and is never restarted in place.
2. Rerun means creating a new TestRun.
3. Backend Java is the source of truth for run config and state.
4. Redis Streams only carry START_RUN/CANCEL_RUN references and compact result events.
5. START_RUN command must not contain full dataset, full config, artifacts or secrets.
6. TestRun creation must lock dataset/schema/evaluator/target/provider snapshots.
7. Historical TestRun result must remain stable even if dataset, target or evaluator changes later.
8. Testcase FAILED is a valid business result and must not make TestRun FAILED.
9. TestRun FAILED is reserved for unrecoverable execution/system failure.
10. Resolved request, raw response, Promptfoo config snapshot and artifacts must be sanitized before exposure.
11. Promptfoo cache is disabled by default for internal chatbot target runs.
12. Frontend uses SSE for realtime progress and REST snapshot APIs for reconnect fallback.
```

---

## 3. Resource Model Summary

### 3.1. TestRun

Database entity:

```text
test_runs
```

Fields from ERD:

```text
id UUID PK
project_id UUID FK projects(id)
run_type test_run_type NOT NULL
dataset_id UUID FK datasets(id)
dataset_version_id UUID FK dataset_versions(id)
dataset_schema_id UUID FK dataset_schemas(id)
dataset_schema_version INT NOT NULL
judge_provider_id UUID FK llm_providers(id)
judge_model TEXT
status test_run_status NOT NULL
progress_completed INT NOT NULL DEFAULT 0
progress_total INT NOT NULL DEFAULT 0
summary_metrics_json JSONB
domain_config_snapshot_json JSONB
promptfoo_config_snapshot_json JSONB
domain_config_hash TEXT
promptfoo_config_hash TEXT
started_by UUID FK users(id)
started_at TIMESTAMPTZ
finished_at TIMESTAMPTZ
cancel_requested_at TIMESTAMPTZ
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
```

Supported run types:

```text
SINGLE
COMPARISON
```

Supported statuses:

```text
PENDING
RUNNING
CANCELLING
CANCELLED
COMPLETED
FAILED
```

Rules:

```text
1. TestRun is created in PENDING state.
2. Runner changes PENDING to RUNNING through RUN_STARTED event.
3. Backend changes RUNNING to CANCELLING when cancel is requested.
4. Terminal statuses are COMPLETED, FAILED and CANCELLED.
5. Terminal TestRun must not transition back to RUNNING.
6. TestRun must store sanitized compact snapshots and hashes.
7. Full generated Promptfoo config belongs in artifact storage, not directly in test_runs.
```

---

### 3.2. TestRunTarget

Database entity:

```text
test_run_targets
```

Fields from ERD:

```text
id UUID PK
test_run_id UUID FK test_runs(id)
target_type test_run_target_type NOT NULL
chatbot_target_id UUID FK chatbot_targets(id)
llm_provider_id UUID FK llm_providers(id)
model TEXT
display_name TEXT NOT NULL
provider_label TEXT
config_json JSONB
created_at TIMESTAMPTZ NOT NULL
```

Supported target types:

```text
CHATBOT_TARGET
LLM_BASELINE
```

Target rules:

```text
CHATBOT_TARGET:
  chatbot_target_id is required.
  llm_provider_id must be null.
  model must be null unless future target config requires it.

LLM_BASELINE:
  chatbot_target_id must be null.
  llm_provider_id is required.
  model is required.
```

Run target count rules:

```text
SINGLE:
  exactly 1 test_run_target.

COMPARISON:
  at least 2 test_run_targets.
```

Comparison composition rules:

```text
1. COMPARISON is multi-target, not only chatbot-vs-baseline.
2. A comparison run may include any valid mix of:
   - CHATBOT_TARGET
   - LLM_BASELINE
3. Valid MVP compositions:
   - CHATBOT_TARGET + CHATBOT_TARGET
   - CHATBOT_TARGET + LLM_BASELINE
   - CHATBOT_TARGET + CHATBOT_TARGET + LLM_BASELINE
   - LLM_BASELINE + LLM_BASELINE, if the project explicitly needs model-only baseline evaluation
4. Backend must not hard-code "only one CHATBOT_TARGET" for COMPARISON.
5. UI should call all selected entries "comparison targets". LLM baseline is only one target kind.
```

---

### 3.3. TestRunEvaluatorVersion

Database entity:

```text
test_run_evaluator_versions
```

Fields from ERD:

```text
id BIGINT PK
test_run_id UUID FK test_runs(id)
evaluator_version_id UUID FK evaluator_versions(id)
evaluator_type evaluator_type NOT NULL
order_index INT NOT NULL
created_at TIMESTAMPTZ NOT NULL
```

Rules:

```text
1. TestRun locks exact evaluator versions.
2. Order is stable through order_index.
3. A run may use multiple evaluators.
4. EvaluatorVersion is immutable after it is locked by a TestRun.
```

---

### 3.4. TestCaseResult

Database entity:

```text
test_case_results
```

Fields from ERD:

```text
id BIGINT PK
test_run_id UUID FK test_runs(id)
test_run_target_id UUID FK test_run_targets(id)
dataset_row_id BIGINT FK dataset_rows(id)
status test_case_result_status NOT NULL
input_vars_json JSONB
rendered_prompt TEXT
resolved_request_json JSONB
raw_response_json JSONB
extracted_output_json JSONB
grading_result_json JSONB
component_results_json JSONB
score NUMERIC(8,4)
passed BOOLEAN
latency_ms INT
token_usage_json JSONB
cost NUMERIC(18,8)
error_message TEXT
started_at TIMESTAMPTZ
finished_at TIMESTAMPTZ
attempt_count INT NOT NULL DEFAULT 0
created_at TIMESTAMPTZ NOT NULL
```

Supported statuses:

```text
PASSED
FAILED
ERROR
JUDGE_ERROR
TIMEOUT
SKIPPED
CANCELLED
```

Rules:

```text
1. PASSED/FAILED are business evaluation results.
2. ERROR/JUDGE_ERROR/TIMEOUT are execution/evaluator/system errors.
3. SKIPPED/CANCELLED are run-control results.
4. A comparison run has multiple results for the same dataset row, one per target.
5. unique(test_run_id, test_run_target_id, dataset_row_id) prevents duplicate results.
6. resolved_request_json and raw_response_json must be sanitized before persistence and exposure.
```

---

### 3.5. PromptfooRunArtifact

Database entity:

```text
promptfoo_run_artifacts
```

Fields from ERD:

```text
id BIGINT PK
test_run_id UUID FK test_runs(id)
config_file_path TEXT
result_json_path TEXT
result_jsonl_path TEXT
html_report_path TEXT
logs_path TEXT
config_sha256 TEXT
result_sha256 TEXT
artifact_schema_version INT NOT NULL DEFAULT 1
sanitized BOOLEAN NOT NULL DEFAULT true
created_at TIMESTAMPTZ NOT NULL
```

Rules:

```text
1. DB stores artifact metadata, paths and hashes only.
2. DB must not store large artifact file content.
3. Artifact files must be sanitized before they are exposed to UI.
4. API must not return download/read links when sanitized=false.
5. config_sha256/result_sha256 are integrity hashes of final sanitized files.
```

---

## 4. QC Review and Final Result Principle

Promptfoo, rule-based evaluators and LLM Judge produce the initial machine result. QC owns the final business judgment used for reporting and Excel handoff.

```text
1. Machine result is advisory and immutable.
2. QC review/manual override is stored separately from test_case_results.
3. Final result is computed from QC review when present; otherwise it falls back to machine result.
4. Dashboard may show machine metrics and final reviewed metrics separately.
5. Excel export should default to final reviewed result because QC uses it for handoff/reporting.
```

Recommended ERD extension:

```text
test_case_result_reviews
- id BIGINT PK
- test_case_result_id BIGINT FK test_case_results(id)
- review_status review_status NOT NULL
- override_status test_case_result_status
- override_passed BOOLEAN
- override_score NUMERIC(8,4)
- note TEXT
- reviewed_by UUID FK users(id)
- reviewed_at TIMESTAMPTZ
- created_at TIMESTAMPTZ
- updated_at TIMESTAMPTZ

unique(test_case_result_id)
```

Suggested `review_status` values:

```text
UNREVIEWED
REVIEWED
NEEDS_DISCUSSION
NEEDS_RERUN
```

---

## 5. Endpoint Summary

| Method | Endpoint | Purpose |
| ------ | -------- | ------- |
| `POST` | `/api/v1/projects/{projectId}/test-runs/preflight` | Estimate and validate a future run before creating it |
| `POST` | `/api/v1/projects/{projectId}/test-runs` | Create TestRun and publish START_RUN command |
| `GET` | `/api/v1/projects/{projectId}/test-runs` | List test runs in a project |
| `GET` | `/api/v1/test-runs/{testRunId}` | Get run detail and locked config summary |
| `GET` | `/api/v1/test-runs/{testRunId}/results` | List testcase results for a run |
| `GET` | `/api/v1/test-runs/{testRunId}/comparison-matrix` | Get grouped comparison matrix by dataset row and target |
| `GET` | `/api/v1/test-runs/{testRunId}/results/{testCaseResultId}` | Get testcase result detail |
| `GET` | `/api/v1/test-runs/{testRunId}/review-summary` | Get QC review progress and final metrics |
| `PUT` | `/api/v1/test-case-results/{testCaseResultId}/review` | Upsert QC review/manual override for one testcase result |
| `DELETE` | `/api/v1/test-case-results/{testCaseResultId}/review` | Clear QC review/manual override for one testcase result |
| `PATCH` | `/api/v1/test-runs/{testRunId}/results/reviews/bulk` | Apply the same QC review/override to selected testcase results |
| `POST` | `/api/v1/test-runs/{testRunId}/exports` | Create Excel export job for run results |
| `GET` | `/api/v1/export-jobs/{exportJobId}` | Get export job status |
| `GET` | `/api/v1/export-jobs/{exportJobId}/download` | Download completed export file |
| `GET` | `/api/v1/test-runs/{testRunId}/artifacts` | Get sanitized artifact metadata |
| `GET` | `/api/v1/test-runs/{testRunId}/artifacts/{artifactType}/content` | Read or download sanitized artifact content |
| `POST` | `/api/v1/test-runs/{testRunId}/cancel` | Request cancellation |

Related but defined elsewhere:

| Method | Endpoint | Defined in |
| ------ | -------- | ---------- |
| `GET` | `/api/v1/test-runs/{testRunId}/events` | `09-sse-events-contract.md` |
| `GET` | `/internal/runner/test-runs/{testRunId}/config` | `10-runner-internal-api.md` |
| `GET` | `/internal/runner/test-runs/{testRunId}/control-state` | `10-runner-internal-api.md` |

Endpoint naming note:

```text
`preflight` and `cancel` are action endpoints because they represent commands, not normal resource CRUD.
```

---

## 6. Permission Summary

| Action | Required permission |
| ------ | ------------------- |
| Run preflight | Project RUN_TEST: OWNER / ADMIN / MEMBER |
| Create TestRun | Project RUN_TEST: OWNER / ADMIN / MEMBER |
| List TestRuns | Project VIEW |
| View TestRun detail | Project VIEW |
| View TestRun results | Project VIEW |
| View TestRun artifacts | Project VIEW |
| Review/override testcase result | Project REVIEW_TEST_RESULT: OWNER / ADMIN / MEMBER |
| Bulk review selected testcase results | Project REVIEW_TEST_RESULT: OWNER / ADMIN / MEMBER |
| Export TestRun results | Project EXPORT_TEST_RESULT: OWNER / ADMIN / MEMBER |
| Cancel own/visible running TestRun | Project CANCEL_RUN: OWNER / ADMIN / MEMBER |

Rules:

```text
1. VIEWER can view run dashboard and results but cannot start or cancel runs.
2. MEMBER can start and cancel runs in projects they can update/use.
3. PLATFORM_ADMIN can operate across workspaces except reading plaintext secrets.
4. Access to a run is derived from access to its project/workspace.
5. Artifacts are visible only if sanitized=true.
6. Machine results are visible to VIEWER, but QC final verdict changes require REVIEW_TEST_RESULT.
7. Excel export creates a file/job and therefore requires EXPORT_TEST_RESULT.
```

---

## 7. DTO Naming

Use these DTO names in backend and frontend code when possible:

```text
RunPreflightRequest
RunPreflightResponse
CreateTestRunRequest
TestRunSummaryResponse
TestRunDetailResponse
TestRunTargetResponse
TestRunEvaluatorVersionResponse
TestRunResultPageResponse
TestCaseResultSummaryResponse
TestCaseResultDetailResponse
ComparisonMatrixResponse
ComparisonMatrixRowResponse
ComparisonMatrixTargetCellResponse
TestCaseResultReviewResponse
UpsertTestCaseResultReviewRequest
BulkReviewTestCaseResultsRequest
BulkReviewTestCaseResultsResponse
ReviewSummaryResponse
CreateTestRunExportRequest
ExportJobResponse
TestRunArtifactResponse
CancelTestRunRequest
CancelTestRunResponse
RunTargetRequest
RunEvaluatorVersionRequest
RunOptionsRequest
PreflightWarningResponse
```

---

## 8. Common Models

### 7.1. RunTargetRequest

Chatbot target:

```json
{
  "targetType": "CHATBOT_TARGET",
  "chatbotTargetId": "88ab33e0-c4c7-4b15-86f1-605455c02e73",
  "displayName": "Internal Chatbot v1"
}
```

Another chatbot target in the same comparison run:

```json
{
  "targetType": "CHATBOT_TARGET",
  "chatbotTargetId": "e6f0d59a-5732-4865-b9b6-8cbdbfb36f74",
  "displayName": "Internal Chatbot v2"
}
```

LLM baseline target:

```json
{
  "targetType": "LLM_BASELINE",
  "llmProviderId": "3d6bb241-2416-4b8e-bc92-975ce1477e33",
  "model": "gpt-4.1-mini",
  "displayName": "GPT-4.1 mini baseline"
}
```

Rules:

```text
1. CHATBOT_TARGET must reference an ACTIVE chatbot target in the same project.
2. LLM_BASELINE must reference a visible active provider.
3. LLM_BASELINE model must be allowed by the provider config if model allowlist exists.
4. displayName is copied into test_run_targets for stable historical display.
5. COMPARISON may contain multiple CHATBOT_TARGET entries.
6. COMPARISON may contain multiple LLM_BASELINE entries.
7. Backend should preserve target order from request for stable comparison matrix display.
```

---

### 7.2. RunEvaluatorVersionRequest

```json
{
  "evaluatorVersionId": "e36ae48f-86d7-42cc-9461-c4265e7aa6d6",
  "orderIndex": 0
}
```

Rules:

```text
1. evaluatorVersionId must belong to an evaluator in the same project.
2. Deleted evaluators cannot be selected for new runs.
3. orderIndex must be unique in the request.
4. At least one evaluator version is required in MVP.
```

---

### 7.3. RunOptionsRequest

```json
{
  "cacheMode": "DISABLED",
  "cacheNamespace": null,
  "targetRequestTimeoutSeconds": 60,
  "llmJudgeTimeoutSeconds": 120,
  "testcaseTimeoutSeconds": 180,
  "runTimeoutHours": 6
}
```

Supported cache modes:

```text
DISABLED
CONTROLLED_BASELINE_DEBUG
```

Rules:

```text
1. cacheMode defaults to DISABLED.
2. Internal chatbot target cache must be disabled by default.
3. CONTROLLED_BASELINE_DEBUG may be allowed only for controlled baseline/debug mode.
4. Timeout values may be omitted to use backend defaults.
5. Backend may clamp timeout values to deployment-level min/max limits.
```

---

### 7.4. PreflightWarningResponse

```json
{
  "code": "LARGE_JUDGE_CALL_COUNT",
  "severity": "WARNING",
  "message": "This run may execute 10000 LLM judge calls.",
  "details": {
    "estimatedJudgeCalls": 10000
  }
}
```

Supported severities:

```text
INFO
WARNING
BLOCKER
```

Rules:

```text
1. BLOCKER warnings prevent run creation.
2. WARNING warnings require user awareness but may still allow run creation.
3. INFO warnings are non-blocking hints.
```

---

## 9. POST /projects/{projectId}/test-runs/preflight

```http
POST /api/v1/projects/{projectId}/test-runs/preflight
```

### Purpose

Validate selected dataset, targets, evaluator versions and judge settings before creating a TestRun.

Preflight must not create `test_runs` or publish Redis commands.

### Permission

Project RUN_TEST permission:

```text
OWNER
ADMIN
MEMBER
```

### Request body

```json
{
  "runType": "COMPARISON",
  "datasetVersionId": "b8f45a2c-3f52-4059-a72d-11e33b9dcb64",
  "targets": [
    {
      "targetType": "CHATBOT_TARGET",
      "chatbotTargetId": "88ab33e0-c4c7-4b15-86f1-605455c02e73",
      "displayName": "Internal Chatbot v1"
    },
    {
      "targetType": "CHATBOT_TARGET",
      "chatbotTargetId": "e6f0d59a-5732-4865-b9b6-8cbdbfb36f74",
      "displayName": "Internal Chatbot v2"
    },
    {
      "targetType": "LLM_BASELINE",
      "llmProviderId": "3d6bb241-2416-4b8e-bc92-975ce1477e33",
      "model": "gpt-4.1-mini",
      "displayName": "GPT-4.1 mini baseline"
    }
  ],
  "evaluatorVersions": [
    {
      "evaluatorVersionId": "e36ae48f-86d7-42cc-9461-c4265e7aa6d6",
      "orderIndex": 0
    }
  ],
  "judgeProviderId": "3d6bb241-2416-4b8e-bc92-975ce1477e33",
  "judgeModel": "gpt-4.1-mini",
  "options": {
    "cacheMode": "DISABLED"
  }
}
```

### Response - 200 OK

```json
{
  "projectId": "0ddf0c99-c632-4ef8-9044-8e37c940d1b9",
  "runType": "COMPARISON",
  "dataset": {
    "id": "647f2d7c-7f06-4cc5-bd85-f0f1cf53f1e8",
    "versionId": "b8f45a2c-3f52-4059-a72d-11e33b9dcb64",
    "name": "VF Product QA Dataset",
    "version": 3,
    "schemaId": "24f4c378-1a3b-4617-a6db-3c07625da0fd",
    "schemaVersion": 2,
    "rowCount": 100
  },
  "targets": [
    {
      "targetType": "CHATBOT_TARGET",
      "displayName": "Internal Chatbot v1",
      "ready": true,
      "estimatedCalls": 100
    },
    {
      "targetType": "CHATBOT_TARGET",
      "displayName": "Internal Chatbot v2",
      "ready": true,
      "estimatedCalls": 100
    },
    {
      "targetType": "LLM_BASELINE",
      "displayName": "GPT-4.1 mini baseline",
      "ready": true,
      "estimatedCalls": 100
    }
  ],
  "evaluators": [
    {
      "evaluatorVersionId": "e36ae48f-86d7-42cc-9461-c4265e7aa6d6",
      "evaluatorType": "LLM_JUDGE",
      "name": "Answer correctness judge",
      "version": 1,
      "requiresJudgeModel": true
    }
  ],
  "rowCount": 100,
  "targetCount": 3,
  "evaluatorCount": 1,
  "llmJudgeEvaluatorCount": 1,
  "estimatedTargetCalls": 300,
  "estimatedJudgeCalls": 300,
  "estimatedMaxDurationSeconds": 36000,
  "estimatedCostRange": {
    "currency": "USD",
    "min": 0.5,
    "max": 3.0,
    "available": true
  },
  "canStart": true,
  "warnings": [
    {
      "code": "COST_ESTIMATE_APPROXIMATE",
      "severity": "INFO",
      "message": "Cost estimate is approximate because token usage is unknown before execution.",
      "details": {}
    }
  ]
}
```

### Validation rules

```text
1. datasetVersionId must belong to a dataset visible from the project.
2. DatasetVersion must be ACTIVE.
3. rowCount must be <= maxRowsPerRun.
4. SINGLE run must have exactly one target.
5. COMPARISON run must have at least two targets.
6. COMPARISON may include multiple CHATBOT_TARGET entries and multiple LLM_BASELINE entries.
7. CHATBOT_TARGET must be ACTIVE.
8. LLM_BASELINE provider must be ACTIVE and visible to the user/workspace.
9. evaluatorVersions must belong to the project.
10. If any selected evaluator requires LLM Judge, judgeProviderId and judgeModel are required.
11. cacheMode must default to DISABLED.
```

### Multi-target comparison examples

Internal chatbot version comparison:

```json
{
  "runType": "COMPARISON",
  "datasetVersionId": "b8f45a2c-3f52-4059-a72d-11e33b9dcb64",
  "targets": [
    {
      "targetType": "CHATBOT_TARGET",
      "chatbotTargetId": "88ab33e0-c4c7-4b15-86f1-605455c02e73",
      "displayName": "Internal Chatbot v1"
    },
    {
      "targetType": "CHATBOT_TARGET",
      "chatbotTargetId": "e6f0d59a-5732-4865-b9b6-8cbdbfb36f74",
      "displayName": "Internal Chatbot v2"
    }
  ],
  "evaluatorVersions": [
    {
      "evaluatorVersionId": "e36ae48f-86d7-42cc-9461-c4265e7aa6d6",
      "orderIndex": 0
    }
  ],
  "judgeProviderId": "3d6bb241-2416-4b8e-bc92-975ce1477e33",
  "judgeModel": "gpt-4.1-mini",
  "options": {
    "cacheMode": "DISABLED"
  }
}
```

Internal chatbot plus LLM baseline comparison:

```json
{
  "runType": "COMPARISON",
  "datasetVersionId": "b8f45a2c-3f52-4059-a72d-11e33b9dcb64",
  "targets": [
    {
      "targetType": "CHATBOT_TARGET",
      "chatbotTargetId": "88ab33e0-c4c7-4b15-86f1-605455c02e73",
      "displayName": "Internal Chatbot"
    },
    {
      "targetType": "LLM_BASELINE",
      "llmProviderId": "3d6bb241-2416-4b8e-bc92-975ce1477e33",
      "model": "gpt-4.1-mini",
      "displayName": "GPT-4.1 mini baseline"
    }
  ],
  "evaluatorVersions": [
    {
      "evaluatorVersionId": "e36ae48f-86d7-42cc-9461-c4265e7aa6d6",
      "orderIndex": 0
    }
  ],
  "judgeProviderId": "3d6bb241-2416-4b8e-bc92-975ce1477e33",
  "judgeModel": "gpt-4.1-mini",
  "options": {
    "cacheMode": "DISABLED"
  }
}
```

### Error codes

```text
PROJECT_NOT_FOUND
DATASET_VERSION_NOT_FOUND
DATASET_VERSION_NOT_ACTIVE
TEST_RUN_ROW_LIMIT_EXCEEDED
TEST_RUN_TARGET_INVALID
TEST_RUN_EVALUATOR_INVALID
TEST_RUN_JUDGE_PROVIDER_REQUIRED
TEST_RUN_COMPARISON_TARGET_REQUIRED
TEST_RUN_PRECHECK_FAILED
PERMISSION_DENIED
```

---

## 10. POST /projects/{projectId}/test-runs

```http
POST /api/v1/projects/{projectId}/test-runs
```

### Purpose

Create a TestRun, lock run config and publish a START_RUN command to Redis Streams.

### Permission

Project RUN_TEST permission:

```text
OWNER
ADMIN
MEMBER
```

### Headers

`Idempotency-Key` is recommended.

```http
Idempotency-Key: 7c1cf9ea-fc53-4c21-a3cb-9d0a1564d7aa
```

Idempotency behavior:

```text
Key scope:
  projectId + authenticatedUserId + Idempotency-Key

Recommended retention:
  24 hours

Same key + same body:
  return the previously created TestRun response.

Same key + different body:
  return 409 Conflict with IDEMPOTENCY_KEY_REUSED.
```

### Request body

Same shape as preflight request:

```json
{
  "runType": "SINGLE",
  "datasetVersionId": "b8f45a2c-3f52-4059-a72d-11e33b9dcb64",
  "targets": [
    {
      "targetType": "CHATBOT_TARGET",
      "chatbotTargetId": "88ab33e0-c4c7-4b15-86f1-605455c02e73",
      "displayName": "Internal Chatbot"
    }
  ],
  "evaluatorVersions": [
    {
      "evaluatorVersionId": "e36ae48f-86d7-42cc-9461-c4265e7aa6d6",
      "orderIndex": 0
    }
  ],
  "judgeProviderId": "3d6bb241-2416-4b8e-bc92-975ce1477e33",
  "judgeModel": "gpt-4.1-mini",
  "options": {
    "cacheMode": "DISABLED"
  }
}
```

### Response - 202 Accepted

Run creation is accepted asynchronously.

```json
{
  "id": "4a2bb4d2-fb85-4fb5-9496-1e2e09179813",
  "projectId": "0ddf0c99-c632-4ef8-9044-8e37c940d1b9",
  "runType": "SINGLE",
  "status": "PENDING",
  "dataset": {
    "id": "647f2d7c-7f06-4cc5-bd85-f0f1cf53f1e8",
    "versionId": "b8f45a2c-3f52-4059-a72d-11e33b9dcb64",
    "name": "VF Product QA Dataset",
    "version": 3,
    "schemaId": "24f4c378-1a3b-4617-a6db-3c07625da0fd",
    "schemaVersion": 2,
    "rowCount": 100
  },
  "progress": {
    "completed": 0,
    "total": 100
  },
  "targets": [
    {
      "id": "f58a8460-e828-43ce-b64b-92a928b416d0",
      "targetType": "CHATBOT_TARGET",
      "displayName": "Internal Chatbot v1",
      "providerLabel": "Internal Chatbot"
    }
  ],
  "evaluatorVersions": [
    {
      "evaluatorVersionId": "e36ae48f-86d7-42cc-9461-c4265e7aa6d6",
      "evaluatorType": "LLM_JUDGE",
      "orderIndex": 0
    }
  ],
  "judgeProviderId": "3d6bb241-2416-4b8e-bc92-975ce1477e33",
  "judgeModel": "gpt-4.1-mini",
  "domainConfigHash": "sha256:7cc945b0...",
  "promptfooConfigHash": "sha256:c33cfc11...",
  "eventsUrl": "/api/v1/test-runs/4a2bb4d2-fb85-4fb5-9496-1e2e09179813/events",
  "createdAt": "2026-07-02T10:00:00+07:00",
  "startedBy": {
    "id": "a38f0878-1ebe-49db-abf2-f6a372b6c246",
    "name": "QC Member"
  }
}
```

### Backend side effects

```text
1. Validate permission and request body.
2. Re-run preflight checks server-side.
3. Lock dataset_id, dataset_version_id, dataset_schema_id and dataset_schema_version.
4. Lock evaluator versions into test_run_evaluator_versions.
5. Lock targets into test_run_targets.
6. Create sanitized domain_config_snapshot_json.
7. Create sanitized compact promptfoo_config_snapshot_json.
8. Compute domain_config_hash and promptfoo_config_hash.
9. Insert test_runs with status PENDING.
10. Insert runner_commands row with command_type START_RUN.
11. XADD START_RUN into vfqc:runs:commands.
12. Persist stream_message_id on runner_commands.
```

### Redis START_RUN payload

```json
{
  "commandId": "7f3069c1-5e94-402d-a0d5-7afc8399a0ce",
  "commandType": "START_RUN",
  "testRunId": "4a2bb4d2-fb85-4fb5-9496-1e2e09179813",
  "projectId": "0ddf0c99-c632-4ef8-9044-8e37c940d1b9",
  "requestedBy": "a38f0878-1ebe-49db-abf2-f6a372b6c246",
  "createdAt": "2026-07-02T10:00:00+07:00"
}
```

Redis command payload rule:

```text
1. Include reference ids only.
2. Do not include dataset rows.
3. Do not include full Promptfoo config.
4. Do not include secret values.
5. Do not include raw request/response or artifact content.
```

### Error codes

```text
TEST_RUN_PRECHECK_FAILED
TEST_RUN_ROW_LIMIT_EXCEEDED
TEST_RUN_TARGET_INVALID
TEST_RUN_EVALUATOR_INVALID
TEST_RUN_JUDGE_PROVIDER_REQUIRED
TEST_RUN_COMPARISON_TARGET_REQUIRED
RUNNER_COMMAND_PUBLISH_FAILED
IDEMPOTENCY_KEY_REUSED
PERMISSION_DENIED
```

---

## 11. GET /projects/{projectId}/test-runs

```http
GET /api/v1/projects/{projectId}/test-runs
```

### Purpose

List TestRuns in a project for run history/dashboard.

### Permission

Project VIEW.

### Query parameters

| Parameter | Type | Required | Description |
| --------- | ---- | -------- | ----------- |
| `page` | integer | No | Zero-based page index |
| `size` | integer | No | Page size |
| `sort` | string | No | Example: `createdAt,desc` |
| `status` | string | No | Filter by TestRun status |
| `runType` | string | No | `SINGLE` or `COMPARISON` |
| `datasetId` | UUID | No | Filter by dataset |
| `datasetVersionId` | UUID | No | Filter by dataset version |
| `startedBy` | UUID | No | Filter by user id |
| `createdFrom` | datetime | No | Inclusive created_at filter |
| `createdTo` | datetime | No | Exclusive created_at filter |
| `q` | string | No | Optional free-text query for future run labels/notes |

### Response - 200 OK

```json
{
  "items": [
    {
      "id": "4a2bb4d2-fb85-4fb5-9496-1e2e09179813",
      "projectId": "0ddf0c99-c632-4ef8-9044-8e37c940d1b9",
      "runType": "SINGLE",
      "status": "COMPLETED",
      "datasetName": "VF Product QA Dataset",
      "datasetVersion": 3,
      "progress": {
        "completed": 100,
        "total": 100
      },
      "summary": {
        "totalTestcases": 100,
        "passedCount": 86,
        "failedCount": 10,
        "errorCount": 4,
        "passRate": 0.86,
        "averageScore": 0.82,
        "averageLatencyMs": 1250
      },
      "startedBy": {
        "id": "a38f0878-1ebe-49db-abf2-f6a372b6c246",
        "name": "QC Member"
      },
      "startedAt": "2026-07-02T10:00:05+07:00",
      "finishedAt": "2026-07-02T10:06:20+07:00",
      "createdAt": "2026-07-02T10:00:00+07:00"
    }
  ],
  "page": {
    "number": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

---

## 12. GET /test-runs/{testRunId}

```http
GET /api/v1/test-runs/{testRunId}
```

### Purpose

Get TestRun detail, locked config summary, progress and summary metrics.

### Permission

Project VIEW through TestRun ownership.

### Response - 200 OK

```json
{
  "id": "4a2bb4d2-fb85-4fb5-9496-1e2e09179813",
  "projectId": "0ddf0c99-c632-4ef8-9044-8e37c940d1b9",
  "runType": "COMPARISON",
  "status": "RUNNING",
  "dataset": {
    "id": "647f2d7c-7f06-4cc5-bd85-f0f1cf53f1e8",
    "versionId": "b8f45a2c-3f52-4059-a72d-11e33b9dcb64",
    "name": "VF Product QA Dataset",
    "version": 3,
    "schemaId": "24f4c378-1a3b-4617-a6db-3c07625da0fd",
    "schemaVersion": 2,
    "rowCount": 100
  },
  "targets": [
    {
      "id": "f58a8460-e828-43ce-b64b-92a928b416d0",
      "targetType": "CHATBOT_TARGET",
      "displayName": "Internal Chatbot",
      "providerLabel": "Internal Chatbot"
    },
    {
      "id": "c9224750-df4c-4216-bc23-6fd14551be52",
      "targetType": "CHATBOT_TARGET",
      "displayName": "Internal Chatbot v2",
      "providerLabel": "Internal Chatbot"
    },
    {
      "id": "a9149809-2266-44df-b2ff-c521ceec0109",
      "targetType": "LLM_BASELINE",
      "displayName": "GPT-4.1 mini baseline",
      "providerLabel": "OpenAI Workspace Provider",
      "model": "gpt-4.1-mini"
    }
  ],
  "evaluatorVersions": [
    {
      "evaluatorVersionId": "e36ae48f-86d7-42cc-9461-c4265e7aa6d6",
      "evaluatorType": "LLM_JUDGE",
      "name": "Answer correctness judge",
      "version": 1,
      "orderIndex": 0
    }
  ],
  "judgeProviderId": "3d6bb241-2416-4b8e-bc92-975ce1477e33",
  "judgeModel": "gpt-4.1-mini",
  "progress": {
    "completed": 31,
    "total": 300,
    "percent": 0.1033
  },
  "summary": {
    "totalTestcases": 200,
    "completedTestcases": 31,
    "passedCount": 24,
    "failedCount": 5,
    "errorCount": 2,
    "passRate": 0.7742,
    "averageScore": 0.81,
    "averageLatencyMs": 1290
  },
  "snapshots": {
    "domainConfigHash": "sha256:7cc945b0...",
    "promptfooConfigHash": "sha256:c33cfc11...",
    "promptfooVersion": "0.121.17",
    "promptfooAdapterSchemaVersion": 1,
    "cacheMode": "DISABLED"
  },
  "canCancel": true,
  "eventsUrl": "/api/v1/test-runs/4a2bb4d2-fb85-4fb5-9496-1e2e09179813/events",
  "startedBy": {
    "id": "a38f0878-1ebe-49db-abf2-f6a372b6c246",
    "name": "QC Member"
  },
  "startedAt": "2026-07-02T10:00:05+07:00",
  "finishedAt": null,
  "cancelRequestedAt": null,
  "createdAt": "2026-07-02T10:00:00+07:00",
  "updatedAt": "2026-07-02T10:01:00+07:00"
}
```

### Snapshot exposure rule

```text
1. API may expose compact sanitized snapshot metadata.
2. API must not expose plaintext secrets.
3. API must not expose full unsanitized Promptfoo config.
4. API may expose hashes and version metadata for audit/debug.
```

---

## 13. GET /test-runs/{testRunId}/results

```http
GET /api/v1/test-runs/{testRunId}/results
```

### Purpose

List testcase results for result table, card/library view and multi-target comparison matrix.

### Permission

Project VIEW through TestRun ownership.

### Query parameters

| Parameter | Type | Required | Description |
| --------- | ---- | -------- | ----------- |
| `page` | integer | No | Zero-based page index |
| `size` | integer | No | Page size |
| `sort` | string | No | Example: `datasetRowIndex,asc` |
| `targetId` | UUID | No | Filter by test_run_targets.id |
| `targetType` | string | No | CHATBOT_TARGET/LLM_BASELINE |
| `status` | string | No | Machine status: PASSED/FAILED/ERROR/JUDGE_ERROR/TIMEOUT/SKIPPED/CANCELLED |
| `passed` | boolean | No | Filter by machine pass/fail |
| `finalStatus` | string | No | Final QC-reviewed status; falls back to machine status when no review exists |
| `finalPassed` | boolean | No | Filter by final pass/fail |
| `reviewStatus` | string | No | UNREVIEWED/REVIEWED/NEEDS_DISCUSSION/NEEDS_RERUN |
| `hasOverride` | boolean | No | Filter results with explicit QC override |
| `datasetRowId` | integer | No | Filter by dataset row |
| `viewMode` | string | No | TABLE/CARD/MATRIX hint for response preview needs; backend may ignore for same schema |
| `q` | string | No | Optional text search over question/expected/extracted output summary/QC note |

### Response - 200 OK

```json
{
  "items": [
    {
      "id": 9122,
      "testRunId": "4a2bb4d2-fb85-4fb5-9496-1e2e09179813",
      "testRunTargetId": "f58a8460-e828-43ce-b64b-92a928b416d0",
      "targetDisplayName": "Internal Chatbot",
      "datasetRowId": 18001,
      "datasetRowIndex": 0,
      "status": "PASSED",
      "passed": true,
      "score": 0.86,
      "machineStatus": "PASSED",
      "machinePassed": true,
      "machineScore": 0.86,
      "finalStatus": "PASSED",
      "finalPassed": true,
      "finalScore": 0.86,
      "review": {
        "reviewStatus": "UNREVIEWED",
        "hasOverride": false,
        "overrideStatus": null,
        "overridePassed": null,
        "overrideScore": null,
        "note": null,
        "reviewedBy": null,
        "reviewedAt": null
      },
      "latencyMs": 1200,
      "inputPreview": {
        "question": "VF 8 có mấy phiên bản?",
        "expected": "VF 8 có các phiên bản Eco và Plus."
      },
      "extractedOutputPreview": {
        "answer": "VF 8 có hai phiên bản Eco và Plus."
      },
      "judgeReasonPreview": "Answer matches the expected product information.",
      "errorMessage": null,
      "startedAt": "2026-07-02T10:00:10+07:00",
      "finishedAt": "2026-07-02T10:00:12+07:00"
    }
  ],
  "page": {
    "number": 0,
    "size": 20,
    "totalElements": 200,
    "totalPages": 10
  }
}
```

### Result list rule

```text
1. List response should return previews, not huge raw payloads.
2. Full request/response/grading detail belongs in result detail endpoint.
3. Previews must still be sanitized.
4. Large JSON fields may be truncated with explicit preview fields.
5. `status`, `passed`, and `score` represent machine result for backward compatibility.
6. New UI should prefer `machineStatus/machinePassed/machineScore` and `finalStatus/finalPassed/finalScore`.
7. Final result must equal QC review override when present; otherwise it must equal machine result.
8. For large multi-target comparison tables, frontend should use the dedicated comparison-matrix endpoint instead of grouping pages client-side.
```

---

## 14. GET /test-runs/{testRunId}/comparison-matrix

```http
GET /api/v1/test-runs/{testRunId}/comparison-matrix
```

### Purpose

Return comparison results grouped by dataset row, then by target. This powers large comparison matrix UI without forcing frontend to group a paginated flat result list.

Use this endpoint for COMPARISON runs and for SINGLE runs when UI still wants a row-first table shape.

### Permission

Project VIEW through TestRun ownership.

### Query parameters

| Name | Type | Required | Description |
| ---- | ---- | -------- | ----------- |
| `page` | integer | No | Zero-based dataset-row page index |
| `size` | integer | No | Number of dataset rows per page |
| `sort` | string | No | Example: `rowIndex,asc` |
| `finalStatus` | string/list | No | Filter rows where any target final status matches |
| `finalPassed` | boolean | No | Filter rows where any target final pass/fail matches |
| `reviewStatus` | string/list | No | Filter rows where any target review status matches |
| `targetIds` | UUID list | No | Limit returned target cells to selected test_run_targets |
| `q` | string | No | Search over dataset row preview, extracted output preview or QC note |

### Response - 200 OK

```json
{
  "testRunId": "4a2bb4d2-fb85-4fb5-9496-1e2e09179813",
  "runType": "COMPARISON",
  "targets": [
    {
      "testRunTargetId": "f58a8460-e828-43ce-b64b-92a928b416d0",
      "targetType": "CHATBOT_TARGET",
      "displayName": "Internal Chatbot v1",
      "providerLabel": "Internal Chatbot"
    },
    {
      "testRunTargetId": "0a430a9c-b693-4f1a-aa8b-928061772312",
      "targetType": "LLM_BASELINE",
      "displayName": "GPT-4.1 Mini baseline",
      "providerLabel": "OpenAI-compatible"
    }
  ],
  "rows": [
    {
      "datasetRowId": 18001,
      "datasetRowIndex": 0,
      "inputPreview": {
        "question": "VF 8 có mấy phiên bản?",
        "expected": "VF 8 có các phiên bản Eco và Plus."
      },
      "cells": {
        "f58a8460-e828-43ce-b64b-92a928b416d0": {
          "testCaseResultId": 9122,
          "status": "PASSED",
          "passed": true,
          "score": 0.86,
          "machineStatus": "PASSED",
          "machinePassed": true,
          "machineScore": 0.86,
          "finalStatus": "PASSED",
          "finalPassed": true,
          "finalScore": 0.86,
          "reviewStatus": "UNREVIEWED",
          "hasOverride": false,
          "latencyMs": 1200,
          "extractedOutputPreview": {
            "answer": "VF 8 có hai phiên bản Eco và Plus."
          },
          "judgeReasonPreview": "Answer matches the expected product information.",
          "errorMessage": null
        },
        "0a430a9c-b693-4f1a-aa8b-928061772312": {
          "testCaseResultId": 9123,
          "status": "FAILED",
          "passed": false,
          "score": 0.42,
          "machineStatus": "FAILED",
          "machinePassed": false,
          "machineScore": 0.42,
          "finalStatus": "FAILED",
          "finalPassed": false,
          "finalScore": 0.42,
          "reviewStatus": "UNREVIEWED",
          "hasOverride": false,
          "latencyMs": 980,
          "extractedOutputPreview": {
            "answer": "VF 8 has one version."
          },
          "judgeReasonPreview": "Answer misses the expected variants.",
          "errorMessage": null
        }
      },
      "rowSummary": {
        "bestTargetId": "f58a8460-e828-43ce-b64b-92a928b416d0",
        "worstTargetId": "0a430a9c-b693-4f1a-aa8b-928061772312",
        "allPassed": false,
        "anyFailed": true,
        "anyError": false,
        "reviewedCount": 0,
        "targetCount": 2
      }
    }
  ],
  "page": {
    "number": 0,
    "size": 20,
    "totalElements": 100,
    "totalPages": 5
  }
}
```

### Matrix rules

```text
1. Top-level `targets` defines stable target column order.
2. Each row represents one dataset row.
3. `cells` is keyed by `testRunTargetId`.
4. Missing cells should be returned as null when a target was skipped/cancelled before creating a TestCaseResult.
5. Cell fields are compact previews only. Full debugging data still belongs in result detail endpoint.
6. `finalStatus/finalPassed/finalScore` must apply QC review override when present; otherwise machine result.
7. Filtering by status/review applies row-level semantics: include the row if any returned target cell matches the filter.
8. The endpoint must not return raw unsanitized request/response/artifact data.
```

### Error codes

```text
AUTHENTICATION_REQUIRED
USER_DISABLED
PERMISSION_DENIED
TEST_RUN_NOT_FOUND
COMPARISON_MATRIX_NOT_AVAILABLE
VALIDATION_ERROR
```

---

## 15. GET /test-runs/{testRunId}/results/{testCaseResultId}

```http
GET /api/v1/test-runs/{testRunId}/results/{testCaseResultId}
```

### Purpose

Get one testcase result detail for debugging.

### Permission

Project VIEW through TestRun ownership.

### Response - 200 OK

```json
{
  "id": 9122,
  "testRunId": "4a2bb4d2-fb85-4fb5-9496-1e2e09179813",
  "testRunTargetId": "f58a8460-e828-43ce-b64b-92a928b416d0",
  "target": {
    "id": "f58a8460-e828-43ce-b64b-92a928b416d0",
    "targetType": "CHATBOT_TARGET",
    "displayName": "Internal Chatbot",
    "providerLabel": "Internal Chatbot"
  },
  "datasetRow": {
    "id": 18001,
    "rowIndex": 0,
    "data": {
      "question": "VF 8 có mấy phiên bản?",
      "expected": "VF 8 có các phiên bản Eco và Plus.",
      "category": "product_info"
    }
  },
  "status": "PASSED",
  "passed": true,
  "score": 0.86,
  "machineStatus": "PASSED",
  "machinePassed": true,
  "machineScore": 0.86,
  "finalStatus": "PASSED",
  "finalPassed": true,
  "finalScore": 0.86,
  "review": {
    "reviewStatus": "UNREVIEWED",
    "hasOverride": false,
    "overrideStatus": null,
    "overridePassed": null,
    "overrideScore": null,
    "note": null,
    "reviewedBy": null,
    "reviewedAt": null
  },
  "latencyMs": 1200,
  "inputVars": {
    "question": "VF 8 có mấy phiên bản?",
    "expected": "VF 8 có các phiên bản Eco và Plus.",
    "category": "product_info"
  },
  "renderedPrompt": "VF 8 có mấy phiên bản?",
  "resolvedRequest": {
    "method": "POST",
    "url": "https://internal-chatbot.example.com/api/chat",
    "headers": {
      "Content-Type": "application/json",
      "Authorization": "Bearer ****MASKED****"
    },
    "body": {
      "question": "VF 8 có mấy phiên bản?"
    }
  },
  "rawResponse": {
    "statusCode": 200,
    "headers": {
      "content-type": "application/json"
    },
    "body": {
      "data": {
        "answer": "VF 8 có hai phiên bản Eco và Plus."
      }
    }
  },
  "extractedOutput": {
    "answer": "VF 8 có hai phiên bản Eco và Plus."
  },
  "gradingResult": {
    "score": 0.86,
    "passed": true,
    "reason": "Answer matches the expected product information and does not hallucinate."
  },
  "componentResults": [
    {
      "componentType": "LLM_JUDGE",
      "evaluatorVersionId": "e36ae48f-86d7-42cc-9461-c4265e7aa6d6",
      "status": "PASSED",
      "score": 0.86,
      "passed": true,
      "reason": "Answer matches the expected product information."
    }
  ],
  "tokenUsage": {
    "promptTokens": 320,
    "completionTokens": 90,
    "totalTokens": 410
  },
  "cost": 0.0012,
  "errorMessage": null,
  "attemptCount": 1,
  "startedAt": "2026-07-02T10:00:10+07:00",
  "finishedAt": "2026-07-02T10:00:12+07:00",
  "createdAt": "2026-07-02T10:00:12+07:00"
}
```

### Sensitive data rule

```text
1. resolvedRequest must be sanitized.
2. rawResponse must be sanitized.
3. gradingResult and componentResults must not include plaintext secrets.
4. If a payload is too large, API may return a truncated field plus artifact reference.
5. Error messages must not include stack traces or secret values.
```

---

## 16. GET /test-runs/{testRunId}/review-summary

```http
GET /api/v1/test-runs/{testRunId}/review-summary
```

### Purpose

Return QC review progress and final reviewed metrics for the run. This powers review dashboards and export readiness indicators.

### Permission

Project VIEW through TestRun ownership.

### Response - 200 OK

```json
{
  "testRunId": "4a2bb4d2-fb85-4fb5-9496-1e2e09179813",
  "totalResults": 200,
  "reviewedCount": 80,
  "unreviewedCount": 120,
  "needsDiscussionCount": 3,
  "needsRerunCount": 2,
  "overrideCount": 12,
  "machineMetrics": {
    "passedCount": 150,
    "failedCount": 40,
    "errorCount": 10,
    "passRate": 0.75,
    "averageScore": 0.78
  },
  "finalMetrics": {
    "passedCount": 158,
    "failedCount": 32,
    "errorCount": 10,
    "passRate": 0.79,
    "averageScore": 0.81
  }
}
```

Rules:

```text
1. Machine metrics are computed from test_case_results.
2. Final metrics are computed from review override when present, otherwise machine result.
3. Review summary must not mutate results.
```

---

## 17. PUT /test-case-results/{testCaseResultId}/review

```http
PUT /api/v1/test-case-results/{testCaseResultId}/review
```

### Purpose

Create or replace QC review/manual override for one testcase result. This is the authoritative QC final judgment used for reporting/export.

### Permission

Project REVIEW_TEST_RESULT permission through TestCaseResult -> TestRun -> Project:

```text
OWNER
ADMIN
MEMBER
```

### Request body

```json
{
  "reviewStatus": "REVIEWED",
  "overrideStatus": "PASSED",
  "overridePassed": true,
  "overrideScore": 0.9,
  "note": "LLM Judge marked this as failed, but the actual answer contains all facts required by expected."
}
```

Validation rules:

```text
1. reviewStatus is required.
2. note is required when overrideStatus/overridePassed/overrideScore is provided.
3. overrideStatus must be a valid test_case_result_status.
4. overrideScore must be between 0 and the configured score scale.
5. Terminal machine result can still be reviewed; review does not reopen or rerun the TestRun.
6. Review must not update original test_case_results machine fields.
```

### Response - 200 OK

```json
{
  "testCaseResultId": 9122,
  "reviewStatus": "REVIEWED",
  "hasOverride": true,
  "overrideStatus": "PASSED",
  "overridePassed": true,
  "overrideScore": 0.9,
  "note": "LLM Judge marked this as failed, but the actual answer contains all facts required by expected.",
  "reviewedBy": {
    "id": "a38f0878-1ebe-49db-abf2-f6a372b6c246",
    "name": "QC Member"
  },
  "reviewedAt": "2026-07-03T10:30:00+07:00",
  "finalStatus": "PASSED",
  "finalPassed": true,
  "finalScore": 0.9
}
```

---

## 18. DELETE /test-case-results/{testCaseResultId}/review

```http
DELETE /api/v1/test-case-results/{testCaseResultId}/review
```

### Purpose

Clear QC review/manual override and make final result fall back to the machine result.

### Permission

Project REVIEW_TEST_RESULT permission.

### Response - 204 No Content

No response body.

Rules:

```text
1. Deleting review must not delete or mutate test_case_results.
2. If review does not exist, backend may return 204 for idempotency or 404 with TEST_CASE_RESULT_REVIEW_NOT_FOUND. Contract should choose one behavior during implementation.
```

---

## 19. PATCH /test-runs/{testRunId}/results/reviews/bulk

```http
PATCH /api/v1/test-runs/{testRunId}/results/reviews/bulk
```

### Purpose

Apply the same QC review/manual override to multiple selected testcase results. This reduces Excel-like bottlenecks when QC finds many rows with the same review decision or note.

### Permission

Project REVIEW_TEST_RESULT permission.

### Request body

```json
{
  "testCaseResultIds": [9122, 9123, 9124],
  "reviewStatus": "REVIEWED",
  "overrideStatus": "PASSED",
  "overridePassed": true,
  "overrideScore": 0.9,
  "note": "Same judge issue: expected only requires version names, not pricing information."
}
```

Validation rules:

```text
1. testCaseResultIds is required and must not be empty.
2. All result ids must belong to the same testRunId path parameter.
3. Max selected result ids per request defaults to 500.
4. note is required when applying an override.
5. Backend should apply the operation transactionally where practical.
6. Backend must return per-row failures if partial success is allowed by implementation.
```

### Response - 200 OK

```json
{
  "testRunId": "4a2bb4d2-fb85-4fb5-9496-1e2e09179813",
  "requestedCount": 3,
  "updatedCount": 3,
  "failedCount": 0,
  "failures": []
}
```

Rules:

```text
1. Bulk review is for selected rows, not arbitrary unbounded filters in MVP.
2. Filter-based bulk review may be added later but needs stronger confirmation UX.
3. Bulk review must still audit reviewedBy and reviewedAt for every affected result.
```

---

## 20. POST /test-runs/{testRunId}/exports

```http
POST /api/v1/test-runs/{testRunId}/exports
```

### Purpose

Create an Excel export job for run results. This supports QC reporting/handoff after reviewing machine judgments.

### Permission

Project EXPORT_TEST_RESULT permission.

### Request body

```json
{
  "format": "XLSX",
  "resultMode": "FINAL",
  "includeMachineResult": true,
  "includeReviewResult": true,
  "includeRequestResponse": false,
  "filters": {
    "targetId": null,
    "finalStatus": ["FAILED"],
    "reviewStatus": ["REVIEWED", "UNREVIEWED"]
  },
  "columns": [
    "rowIndex",
    "question",
    "expected",
    "actualAnswer",
    "machineStatus",
    "machineScore",
    "machineReason",
    "finalStatus",
    "finalScore",
    "qcNote",
    "reviewedBy",
    "reviewedAt",
    "target",
    "latencyMs"
  ]
}
```

Supported `resultMode` values:

```text
MACHINE
FINAL
BOTH
```

Default:

```text
FINAL
```

Rules:

```text
1. FINAL export uses QC review override when present; otherwise machine result.
2. BOTH export includes machine result columns and final reviewed result columns.
3. includeRequestResponse=false by default because request/response can be large and sensitive.
4. If includeRequestResponse=true, exported fields must be sanitized.
5. Export must use the same filtering semantics as result table.
6. Export must not expose server-local paths.
```

### Response - 202 Accepted

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

Export job status/download endpoints return the shared `ExportJobResponse` defined in the common Export Job Convention in `01-api-conventions.md`.

---

## 21. GET /test-runs/{testRunId}/artifacts

```http
GET /api/v1/test-runs/{testRunId}/artifacts
```

### Purpose

Return artifact metadata for a run.

### Permission

Project VIEW through TestRun ownership.

### Response - 200 OK

```json
{
  "testRunId": "4a2bb4d2-fb85-4fb5-9496-1e2e09179813",
  "available": true,
  "sanitized": true,
  "artifactSchemaVersion": 1,
  "configSha256": "sha256:8bf1c7...",
  "resultSha256": "sha256:91d33e...",
  "files": [
    {
      "type": "PROMPTFOO_CONFIG",
      "fileName": "promptfooconfig.json",
      "contentType": "application/json",
      "available": true,
      "contentUrl": "/api/v1/test-runs/4a2bb4d2-fb85-4fb5-9496-1e2e09179813/artifacts/PROMPTFOO_CONFIG/content"
    },
    {
      "type": "RESULT_JSON",
      "fileName": "results.json",
      "contentType": "application/json",
      "available": true,
      "contentUrl": "/api/v1/test-runs/4a2bb4d2-fb85-4fb5-9496-1e2e09179813/artifacts/RESULT_JSON/content"
    },
    {
      "type": "RESULT_JSONL",
      "fileName": "results.jsonl",
      "contentType": "application/x-ndjson",
      "available": true,
      "contentUrl": "/api/v1/test-runs/4a2bb4d2-fb85-4fb5-9496-1e2e09179813/artifacts/RESULT_JSONL/content"
    },
    {
      "type": "HTML_REPORT",
      "fileName": "report.html",
      "contentType": "text/html",
      "available": true,
      "contentUrl": "/api/v1/test-runs/4a2bb4d2-fb85-4fb5-9496-1e2e09179813/artifacts/HTML_REPORT/content"
    },
    {
      "type": "RUNNER_LOG",
      "fileName": "runner.log",
      "contentType": "text/plain",
      "available": true,
      "contentUrl": "/api/v1/test-runs/4a2bb4d2-fb85-4fb5-9496-1e2e09179813/artifacts/RUNNER_LOG/content"
    }
  ],
  "createdAt": "2026-07-02T10:06:20+07:00"
}
```

### Artifact exposure rule

```text
1. If promptfoo_run_artifacts row does not exist, return available=false.
2. If sanitized=false, do not expose file names, server paths, content URLs or download links to normal users.
3. Public API must not expose absolute or relative server-local paths such as `./storage/...`.
4. `contentUrl` is generated by Backend and must enforce project VIEW permission.
5. HTML report content should be served with safe headers or as attachment unless inline rendering is explicitly sandboxed.
```

---

## 22. GET /test-runs/{testRunId}/artifacts/{artifactType}/content

```http
GET /api/v1/test-runs/{testRunId}/artifacts/{artifactType}/content
```

### Purpose

Read or download sanitized artifact content for a run.

### Permission

Project VIEW through TestRun ownership.

### Path parameters

| Field | Type | Required | Notes |
| ----- | ---- | -------- | ----- |
| `testRunId` | UUID | Yes | TestRun id |
| `artifactType` | enum | Yes | `PROMPTFOO_CONFIG`, `RESULT_JSON`, `RESULT_JSONL`, `HTML_REPORT`, `RUNNER_LOG` |

### Query parameters

| Field | Type | Required | Notes |
| ----- | ---- | -------- | ----- |
| `download` | boolean | No | If `true`, return `Content-Disposition: attachment` |

### Response - 200 OK

Response content type depends on `artifactType`:

```text
PROMPTFOO_CONFIG -> application/json
RESULT_JSON      -> application/json
RESULT_JSONL     -> application/x-ndjson
HTML_REPORT      -> text/html or attachment
RUNNER_LOG       -> text/plain
```

### Artifact content exposure rule

```text
1. Only expose content when promptfoo_run_artifacts.sanitized = true.
2. Validate artifactType against allowlist.
3. Resolve file from artifact metadata; never trust a user-provided path.
4. Reject path traversal and files outside configured artifact root.
5. Do not expose plaintext secrets, raw cURL, Authorization headers or unmasked cookies.
6. For HTML_REPORT inline rendering, backend should set restrictive security headers or frontend should render inside sandboxed iframe.
```

### Error codes

```text
TEST_RUN_ARTIFACT_NOT_FOUND
TEST_RUN_ARTIFACT_UNSANITIZED
TEST_RUN_ARTIFACT_TYPE_INVALID
TEST_RUN_EXPORT_NOT_ALLOWED
TEST_RUN_EXPORT_FAILED
EXPORT_JOB_NOT_FOUND
EXPORT_JOB_INVALID_STATE
EXPORT_JOB_EXPIRED
EXPORT_JOB_DOWNLOAD_UNAVAILABLE
PERMISSION_DENIED
```

---

## 23. POST /test-runs/{testRunId}/cancel

```http
POST /api/v1/test-runs/{testRunId}/cancel
```

### Purpose

Request cancellation for a PENDING or RUNNING TestRun.

Cancellation is asynchronous. Backend sets status to CANCELLING and publishes CANCEL_RUN. Runner stops safely and publishes RUN_CANCELLED.

### Permission

Project CANCEL_RUN permission:

```text
OWNER
ADMIN
MEMBER
```

### Request body

```json
{
  "reason": "Wrong dataset selected."
}
```

`reason` is optional and may be stored in future audit logs. MVP may ignore it if audit log is not implemented yet.

### Response - 202 Accepted

```json
{
  "testRunId": "4a2bb4d2-fb85-4fb5-9496-1e2e09179813",
  "previousStatus": "RUNNING",
  "status": "CANCELLING",
  "cancelRequestedAt": "2026-07-02T10:02:00+07:00",
  "eventsUrl": "/api/v1/test-runs/4a2bb4d2-fb85-4fb5-9496-1e2e09179813/events"
}
```

### Idempotency behavior

```text
1. Calling cancel on CANCELLING returns current CANCELLING state.
2. Calling cancel on CANCELLED returns current CANCELLED state.
3. Calling cancel on COMPLETED/FAILED returns 409 TEST_RUN_CANCEL_NOT_ALLOWED.
4. Duplicate CANCEL_RUN command must not break runner behavior.
```

### Backend side effects

```text
1. Validate permission.
2. Check current test_run.status.
3. If PENDING or RUNNING, update status to CANCELLING.
4. Set cancel_requested_at.
5. Create runner_commands row with command_type CANCEL_RUN.
6. XADD CANCEL_RUN into vfqc:runs:commands.
7. Return 202 Accepted.
```

### Redis CANCEL_RUN payload

```json
{
  "commandId": "3a77d64b-bc2a-4781-9739-2cb04322a934",
  "commandType": "CANCEL_RUN",
  "testRunId": "4a2bb4d2-fb85-4fb5-9496-1e2e09179813",
  "requestedBy": "a38f0878-1ebe-49db-abf2-f6a372b6c246",
  "createdAt": "2026-07-02T10:02:00+07:00"
}
```

### Error codes

```text
TEST_RUN_NOT_FOUND
TEST_RUN_CANCEL_NOT_ALLOWED
TEST_RUN_INVALID_STATE
RUNNER_COMMAND_PUBLISH_FAILED
PERMISSION_DENIED
```

---

## 24. State Machine

Valid TestRun transitions:

```text
PENDING -> RUNNING
PENDING -> CANCELLED
PENDING -> FAILED
RUNNING -> CANCELLING
CANCELLING -> CANCELLED
RUNNING -> COMPLETED
RUNNING -> FAILED
CANCELLING -> FAILED
```

Invalid transitions:

```text
COMPLETED -> RUNNING
FAILED -> RUNNING
CANCELLED -> RUNNING
COMPLETED -> CANCELLING
FAILED -> CANCELLING
```

Status meaning:

```text
PENDING:
  Run row exists but runner has not started.

RUNNING:
  Runner is executing testcases.

CANCELLING:
  User requested cancellation; runner is stopping safely.

CANCELLED:
  Runner confirmed cancellation or pending run was cancelled before execution.

COMPLETED:
  Run finished normally.

FAILED:
  Run failed due to unrecoverable system/execution failure.
```

Important distinction:

```text
Testcase status FAILED means chatbot/evaluator business failure.
TestRun status FAILED means system/execution failure.
```

---

## 25. Result and Summary Metrics

### 17.1. Summary metrics shape

`test_runs.summary_metrics_json` should normalize at least:

```json
{
  "totalTestcases": 200,
  "completedTestcases": 200,
  "passedCount": 172,
  "failedCount": 20,
  "errorCount": 8,
  "cancelledCount": 0,
  "skippedCount": 0,
  "passRate": 0.86,
  "averageScore": 0.82,
  "averageLatencyMs": 1250,
  "totalCost": 0.42,
  "totalTokenUsage": {
    "promptTokens": 120000,
    "completionTokens": 30000,
    "totalTokens": 150000
  },
  "byTarget": [
    {
      "testRunTargetId": "f58a8460-e828-43ce-b64b-92a928b416d0",
      "displayName": "Internal Chatbot",
      "passedCount": 86,
      "failedCount": 10,
      "errorCount": 4,
      "passRate": 0.86,
      "averageScore": 0.82,
      "averageLatencyMs": 1250
    }
  ]
}
```

### 17.2. Metric rules

```text
1. totalTestcases = row_count * target_count.
2. completedTestcases should count terminal testcase result rows.
3. passRate = passedCount / (passedCount + failedCount + errorCount) unless product decides to exclude errors.
4. Average score should ignore null score unless product decides otherwise.
5. Average latency should use completed testcase rows with latency_ms not null.
6. Comparison dashboard should group metrics by test_run_target_id and preserve test_run_targets order for matrix columns.
```

---

## 26. Snapshot and Hash Rules

When creating a TestRun, backend must store:

```text
domain_config_snapshot_json
promptfoo_config_snapshot_json
domain_config_hash
promptfoo_config_hash
```

Rules:

```text
1. domain_config_snapshot_json must be sanitized.
2. promptfoo_config_snapshot_json must be sanitized compact snapshot only.
3. Full generated Promptfoo config belongs in artifact storage.
4. Hashes use SHA-256 over canonical sanitized JSON.
5. Hash is for audit/integrity/compare only, not for recovering original data.
6. Snapshot must include Promptfoo version and adapter schema version when known.
7. Snapshot must include cacheMode so UI can identify cached/debug runs.
```

Minimum snapshot metadata exposed to UI:

```json
{
  "domainConfigHash": "sha256:7cc945b0...",
  "promptfooConfigHash": "sha256:c33cfc11...",
  "promptfooVersion": "0.121.17",
  "promptfooAdapterSchemaVersion": 1,
  "cacheMode": "DISABLED"
}
```

---

## 27. Security Rules

```text
1. TestRun API must not expose plaintext secret values.
2. TestRun API must not expose encrypted secret values.
3. resolvedRequest must be masked before persistence and response.
4. rawResponse must be masked before persistence and response.
5. promptfoo_config_snapshot_json must be sanitized compact only.
6. Artifacts must be sanitized before exposed through artifact API.
7. Redis command/event payloads must not include secret values or large artifacts.
8. Error messages must not include raw Authorization/API key/token/cookie values.
9. UI-facing responses may include masked previews only.
10. Promptfoo cache usage must be visible in snapshot metadata if enabled.
```

---

## 28. Error Examples

### 21.1. Row Limit Exceeded

```http
HTTP/1.1 422 Unprocessable Content
Content-Type: application/problem+json
```

```json
{
  "type": "https://vfqc.vinfast.vn/problems/test-run-row-limit-exceeded",
  "title": "Dataset row limit exceeded",
  "status": 422,
  "detail": "Dataset version has 8000 rows but the maximum allowed rows per run is 5000.",
  "instance": "/api/v1/projects/0ddf0c99-c632-4ef8-9044-8e37c940d1b9/test-runs/preflight",
  "code": "TEST_RUN_ROW_LIMIT_EXCEEDED",
  "traceId": "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00",
  "errors": [
    {
      "field": "datasetVersionId",
      "message": "Selected dataset version exceeds maxRowsPerRun."
    }
  ]
}
```

---

### 21.2. Invalid State for Cancel

```http
HTTP/1.1 409 Conflict
Content-Type: application/problem+json
```

```json
{
  "type": "https://vfqc.vinfast.vn/problems/test-run-cancel-not-allowed",
  "title": "Test run cannot be cancelled",
  "status": 409,
  "detail": "Completed test runs cannot be cancelled.",
  "instance": "/api/v1/test-runs/4a2bb4d2-fb85-4fb5-9496-1e2e09179813/cancel",
  "code": "TEST_RUN_CANCEL_NOT_ALLOWED",
  "traceId": "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00"
}
```

---

### 21.3. Runner Command Publish Failed

```http
HTTP/1.1 503 Service Unavailable
Content-Type: application/problem+json
```

```json
{
  "type": "https://vfqc.vinfast.vn/problems/runner-command-publish-failed",
  "title": "Runner command could not be published",
  "status": 503,
  "detail": "The test run was not started because the runner command stream is temporarily unavailable.",
  "instance": "/api/v1/projects/0ddf0c99-c632-4ef8-9044-8e37c940d1b9/test-runs",
  "code": "RUNNER_COMMAND_PUBLISH_FAILED",
  "traceId": "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00"
}
```

---

## 29. Stable Error Codes

Test Run APIs may return the following domain-specific codes:

```text
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
COMPARISON_MATRIX_NOT_AVAILABLE
TEST_CASE_RESULT_REVIEW_NOT_FOUND
TEST_CASE_RESULT_REVIEW_INVALID
TEST_CASE_RESULT_REVIEW_CONFLICT
TEST_CASE_RESULT_REVIEW_REASON_REQUIRED
TEST_CASE_RESULT_REVIEW_NOT_ALLOWED
TEST_CASE_RESULT_REVIEW_BULK_LIMIT_EXCEEDED
TEST_RUN_ARTIFACT_NOT_FOUND
TEST_RUN_ARTIFACT_UNSANITIZED
TEST_RUN_ARTIFACT_TYPE_INVALID
TEST_RUN_EXPORT_NOT_ALLOWED
TEST_RUN_EXPORT_FAILED
EXPORT_JOB_NOT_FOUND
EXPORT_JOB_INVALID_STATE
EXPORT_JOB_EXPIRED
EXPORT_JOB_DOWNLOAD_UNAVAILABLE
RUNNER_COMMAND_PUBLISH_FAILED
SSE_REPLAY_UNAVAILABLE
IDEMPOTENCY_KEY_REUSED
```

Common error codes from `01-api-conventions.md` also apply:

```text
VALIDATION_ERROR
AUTHENTICATION_REQUIRED
ACCESS_TOKEN_EXPIRED
PERMISSION_DENIED
RESOURCE_NOT_FOUND
CONFLICT
UNPROCESSABLE_CONTENT
DEPENDENCY_UNAVAILABLE
INTERNAL_ERROR
```

---

## 30. Implementation Notes

```text
1. Create TestRun and publish START_RUN should be handled transactionally as much as possible.
2. If DB insert succeeds but Redis publish fails, backend must mark command/run failed or recover with outbox-like retry.
3. runner_commands.command_id is the execution idempotency key for runner consumption.
4. Idempotency-Key is a client/API idempotency key for duplicate POST protection.
5. Backend should compute progress_total = row_count * target_count.
6. Backend should not calculate final summary only from SSE memory; DB is source of truth.
7. test_case_results should be upserted idempotently by unique(test_run_id, test_run_target_id, dataset_row_id).
8. Result list APIs should be paginated and should not return huge raw JSON by default.
9. Result detail APIs may return full sanitized JSON, but can truncate if payload is too large.
10. Cancel must be cooperative; runner checks control state between testcase/target/judge calls.
11. Partial rerun failed testcases is out of scope for MVP.
12. Pause/resume run is out of scope for MVP.
13. Queue priority is out of scope for MVP.
```

---

## 31. QC Review Implementation Notes

```text
1. test_case_results stores immutable machine result.
2. test_case_result_reviews stores QC final judgment and notes.
3. Review data should not change promptfoo_run_artifacts hashes.
4. Review data should not change runner event history.
5. Final metrics can be materialized in summary_metrics_json if needed, but must be recomputable from test_case_results + reviews.
6. Comparison run review applies per test_case_result, not globally per dataset row.
7. Bulk review for explicitly selected testcase results is part of the MVP contract; unbounded filter-based bulk review is deferred.
8. Export result should default to FINAL because QC export/report should reflect QC's final decision.
```

---

## 32. MVP Acceptance Criteria

Test Run API is ready for MVP when:

```text
1. UI can call preflight and see row/target/judge call estimates.
2. Preflight blocks datasets over maxRowsPerRun.
3. UI can start SINGLE run with one chatbot target.
4. UI can start COMPARISON run for chatbot-vs-chatbot, chatbot-vs-baseline and mixed multi-target cases.
5. Backend locks dataset/schema/evaluator/target/provider snapshot at creation time.
6. Backend creates test_runs, test_run_targets and test_run_evaluator_versions correctly.
7. Backend publishes START_RUN command to Redis Streams.
8. UI receives TestRun id and can open SSE endpoint.
9. UI can list run history by project.
10. UI can fetch run detail snapshot after SSE reconnect.
11. UI can list testcase results with pagination/filtering.
12. UI can open testcase detail with sanitized request/response/grading result.
13. QC can override final PASS/FAIL/score and add note without mutating machine result.
14. UI can compute/show final reviewed metrics separately from machine metrics.
15. QC can create Excel export job for final reviewed results.
16. UI can cancel RUNNING run.
17. Cancel flow moves RUNNING -> CANCELLING -> CANCELLED through runner event.
18. Terminal runs cannot be restarted or cancelled incorrectly.
19. Artifact metadata is visible only when sanitized=true.
20. Testcase business failure does not make TestRun FAILED.
21. All errors use RFC 9457 Problem Details from API conventions.
```
