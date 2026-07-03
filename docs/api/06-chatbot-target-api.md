# VinFast QC Copilot - Chatbot Target API Contract

Version: `v0.2`  
Status: API Contract Draft  
Base path: `/api/v1`  
Related docs:

- `docs/api/00-api-overview.md`
- `docs/api/01-api-conventions.md`
- `docs/api/02-auth-permission-contract.md`
- `docs/api/04-secret-provider-api.md`
- `docs/api/05-dataset-api.md`
- `prd_v07.md`
- `erd_v05.md`

---

## 1. Purpose

This document defines the REST API contract for configuring chatbot targets in VinFast QC Copilot.

A chatbot target is an internal chatbot/API endpoint that QC wants to evaluate. In MVP, chatbot target setup starts from a cURL-like HTTP request and becomes a sanitized, structured request configuration that the Runner can replay for every dataset row.

This contract covers:

```text
1. Chatbot target CRUD.
2. Optional cURL inspection/parsing helper.
3. Sanitized raw cURL persistence.
4. Secret references and runtime-only secret injection.
5. Dataset variable mapping into request URL/query/header/body.
6. Test connection flow.
7. Example request/response history.
8. Response extraction config.
9. Chatbot target status transitions.
10. API-level security rules for cURL, headers, tokens and artifacts.
```

This document does not define:

```text
1. Frontend cURL parser implementation details.
2. Secret encryption internals.
3. Runner secret resolution internals.
4. Promptfoo HTTP provider implementation internals.
5. Test run execution result persistence.
```

Those details belong in frontend implementation docs, backend security spec, runner internal API spec and test run API spec.

---

## 2. Design Principles

```text
1. Backend must never shell-execute raw cURL.
2. Backend must persist only sanitized raw cURL.
3. Plain Authorization/API key/token/cookie values must not be stored in DB.
4. Secrets must be stored as Secret entities and referenced by id.
5. Chatbot target config must be structured, validated and replayable.
6. UI variables use `$dataset.<columnName>` syntax.
7. Backend domain must not expose Promptfoo template syntax as source of truth.
8. Runner converts domain config to Promptfoo HTTP provider config at run time.
9. Test connection request/response must be masked before persistence.
10. Every test connection attempt creates a ChatbotTargetExample row.
11. Response extraction config is selected from a masked example response.
12. Target status must make it clear whether the target is ready for TestRun.
```

---

## 3. Resource Model Summary

### 3.1. Chatbot Target

A chatbot target is a structured and sanitized HTTP configuration for an internal chatbot API.

Database entity:

```text
chatbot_targets
```

Fields from ERD:

```text
id UUID PK
project_id UUID FK projects(id)
name TEXT NOT NULL
description TEXT
raw_curl_sanitized TEXT
raw_curl_hash TEXT
method TEXT NOT NULL
url TEXT NOT NULL
headers_json JSONB
query_params_json JSONB
body_json JSONB
auth_config_json JSONB
dataset_mapping_json JSONB
latest_example_id BIGINT FK chatbot_target_examples(id)
response_extraction_config_json JSONB
status chatbot_target_status NOT NULL
created_by UUID FK users(id)
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
deleted_at TIMESTAMPTZ
```

Supported statuses:

```text
DRAFT
ACTIVE
INVALID
DISABLED
```

Status meaning:

```text
DRAFT:
  Target has been created but is not confirmed ready for test runs.

ACTIVE:
  Target has a valid request config and response extraction config.
  Target may be used by TestRun.

INVALID:
  Latest test connection or validation failed.
  Target cannot be used by TestRun until fixed.

DISABLED:
  Target is intentionally disabled.
  Target cannot be used by TestRun.
```

---

### 3.2. Chatbot Target Example

A chatbot target example stores one masked test connection attempt.

Database entity:

```text
chatbot_target_examples
```

Fields from ERD:

```text
id BIGINT PK
chatbot_target_id UUID FK chatbot_targets(id)
request_json JSONB
response_status_code INT
response_headers_json JSONB
response_body_json JSONB
extracted_output_json JSONB
latency_ms INT
error_message TEXT
tested_by UUID FK users(id)
created_at TIMESTAMPTZ NOT NULL
```

Rules:

```text
1. Every test connection attempt creates a new example row.
2. request_json must be sanitized.
3. response_headers_json must be sanitized.
4. response_body_json must be sanitized.
5. extracted_output_json must be sanitized.
6. Error messages must not contain raw tokens or credentials.
7. latest_example_id on chatbot_targets points to the latest example.
```

---

## 4. Endpoint Summary

| Method | Endpoint | Purpose |
| ------ | -------- | ------- |
| `GET` | `/api/v1/projects/{projectId}/chatbot-targets` | List chatbot targets in a project |
| `POST` | `/api/v1/projects/{projectId}/chatbot-targets` | Create chatbot target |
| `GET` | `/api/v1/chatbot-targets/{chatbotTargetId}` | Get chatbot target detail |
| `PATCH` | `/api/v1/chatbot-targets/{chatbotTargetId}` | Update chatbot target config or status |
| `DELETE` | `/api/v1/chatbot-targets/{chatbotTargetId}` | Soft delete chatbot target |
| `POST` | `/api/v1/chatbot-targets/inspect-curl` | Optional helper to parse/sanitize cURL without persistence |
| `POST` | `/api/v1/chatbot-targets/{chatbotTargetId}/test-connection` | Execute masked test connection |
| `GET` | `/api/v1/chatbot-targets/{chatbotTargetId}/examples` | List test connection examples |
| `GET` | `/api/v1/chatbot-target-examples/{exampleId}` | Get one masked example detail |
| `PATCH` | `/api/v1/chatbot-targets/{chatbotTargetId}/response-extraction` | Update response extraction config |

Endpoint naming note:

```text
`test-connection` and `inspect-curl` are action endpoints because they execute commands rather than represent long-lived resources.
```

---

## 5. DTO Naming

Use these DTO names in backend and frontend code when possible:

```text
ChatbotTargetSummaryResponse
ChatbotTargetDetailResponse
ChatbotTargetExampleResponse
CreateChatbotTargetRequest
UpdateChatbotTargetRequest
InspectCurlRequest
InspectCurlResponse
TestConnectionRequest
TestConnectionResponse
ResponseExtractionConfigRequest
ResponseExtractionConfigResponse
ParsedHttpRequestConfig
SanitizedHeader
SanitizedQueryParam
SecretBinding
DatasetRequestMapping
```

---

## 6. Common Models

### 6.1. ParsedHttpRequestConfig

```json
{
  "method": "POST",
  "url": "https://internal-chatbot.example.com/api/chat",
  "headers": [
    {
      "name": "Content-Type",
      "value": "application/json",
      "sensitive": false
    },
    {
      "name": "Authorization",
      "value": "Bearer ****MASKED****",
      "sensitive": true,
      "secretBindingKey": "authToken"
    }
  ],
  "queryParams": [],
  "bodyType": "JSON",
  "body": {
    "question": "$dataset.question",
    "context": "$dataset.context"
  }
}
```

Supported methods:

```text
GET
POST
PUT
PATCH
DELETE
```

MVP recommended methods:

```text
POST
GET
```

Supported body types:

```text
NONE
JSON
FORM_URLENCODED
TEXT
```

Out of scope for MVP:

```text
MULTIPART
BINARY
GRAPHQL-specific UI
WebSocket chatbot targets
```

---

### 6.2. SecretBinding

A secret binding defines where a secret should be injected at runtime.

```json
{
  "key": "authToken",
  "secretId": "3e8d1de2-2a26-4d9a-a0dd-9c6ce056d9ea",
  "location": "HEADER",
  "name": "Authorization",
  "valueTemplate": "Bearer {{secret}}"
}
```

Supported locations:

```text
HEADER
QUERY
BODY
```

Rules:

```text
1. `secretId` must reference an ACTIVE Secret visible to the current user/project context.
2. `valueTemplate` may include `{{secret}}` placeholder only.
3. `valueTemplate` must not contain a real secret value.
4. `secretBindingKey` in headers/query/body links sanitized request config to secretBindings[].key.
5. Backend and Runner must inject secret only at runtime.
```

---

### 6.3. DatasetRequestMapping

Dataset mapping defines how dataset variables are placed into request config.

```json
[
  {
    "variable": "$dataset.question",
    "targetLocation": "BODY",
    "targetPath": "$.question",
    "required": true
  },
  {
    "variable": "$dataset.context",
    "targetLocation": "BODY",
    "targetPath": "$.context",
    "required": false
  }
]
```

Supported target locations:

```text
URL
HEADER
QUERY
BODY
```

Rules:

```text
1. `variable` must exist in the selected/current dataset schema.
2. `variable` uses UI syntax `$dataset.<columnName>`.
3. Backend stores domain variable syntax, not Promptfoo `{{columnName}}` syntax.
4. Runner converts `$dataset.question` to Promptfoo vars/template syntax when building generated config.
5. JSON body paths should use JSONPath-like syntax, for example `$.question`.
6. Query/header mapping may use `targetName` instead of `targetPath` if easier for implementation.
```

Alternative for query/header mapping:

```json
{
  "variable": "$dataset.userId",
  "targetLocation": "QUERY",
  "targetName": "user_id",
  "required": false
}
```

---

### 6.4. ResponseExtractionConfig

Response extraction config tells the Runner which fields to extract from chatbot response.

```json
{
  "mainAnswerPath": "$.data.answer",
  "fields": [
    {
      "name": "intent",
      "path": "$.data.intent",
      "required": false
    },
    {
      "name": "confidence",
      "path": "$.data.confidence",
      "required": false
    }
  ]
}
```

Rules:

```text
1. `mainAnswerPath` is required before target can become ACTIVE.
2. Paths must be validated against latest example response when possible.
3. If path does not exist in latest example, return RESPONSE_EXTRACTION_PATH_INVALID.
4. Extracted output must be masked before persistence.
5. Runner converts mainAnswerPath to Promptfoo transformResponse or equivalent adapter config.
```

---

## 7. List Chatbot Targets

```http
GET /api/v1/projects/{projectId}/chatbot-targets
```

### Purpose

List chatbot targets in a project.

### Permission

Project VIEW.

### Query Parameters

| Name | Type | Required | Description |
| ---- | ---- | -------- | ----------- |
| `q` | string | No | Search by name |
| `status` | string | No | Filter by `DRAFT`, `ACTIVE`, `INVALID`, `DISABLED` |
| `page` | integer | No | Page number, default `0` |
| `size` | integer | No | Page size, default `20` |
| `sort` | string | No | Example: `updatedAt,desc` |

### Response - 200 OK

```json
{
  "items": [
    {
      "id": "15e42f5e-88fd-4bcf-b5b2-142c5b82a9d1",
      "projectId": "2d4a9209-6f21-41de-86e1-b3f4cae4c9e1",
      "name": "Internal Sales Chatbot",
      "description": "Sales chatbot staging endpoint",
      "method": "POST",
      "url": "https://internal-chatbot.example.com/api/chat",
      "status": "ACTIVE",
      "hasResponseExtraction": true,
      "latestExampleId": 1024,
      "createdBy": "a38f0878-1ebe-49db-abf2-f6a372b6c246",
      "createdAt": "2026-07-02T10:00:00+07:00",
      "updatedAt": "2026-07-02T10:15:00+07:00"
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

## 8. Create Chatbot Target

```http
POST /api/v1/projects/{projectId}/chatbot-targets
```

### Purpose

Create a chatbot target with sanitized request config and secret references.

### Permission

Project UPDATE / Configure chatbot target.

Allowed workspace roles:

```text
OWNER
ADMIN
MEMBER
```

### Request

```json
{
  "name": "Internal Sales Chatbot",
  "description": "Sales chatbot staging endpoint",
  "rawCurlSanitized": "curl -X POST 'https://internal-chatbot.example.com/api/chat' -H 'Content-Type: application/json' -H 'Authorization: Bearer ****MASKED****' -d '{\"question\":\"$dataset.question\"}'",
  "requestConfig": {
    "method": "POST",
    "url": "https://internal-chatbot.example.com/api/chat",
    "headers": [
      {
        "name": "Content-Type",
        "value": "application/json",
        "sensitive": false
      },
      {
        "name": "Authorization",
        "value": "Bearer ****MASKED****",
        "sensitive": true,
        "secretBindingKey": "authToken"
      }
    ],
    "queryParams": [],
    "bodyType": "JSON",
    "body": {
      "question": "$dataset.question",
      "context": "$dataset.context"
    }
  },
  "secretBindings": [
    {
      "key": "authToken",
      "secretId": "3e8d1de2-2a26-4d9a-a0dd-9c6ce056d9ea",
      "location": "HEADER",
      "name": "Authorization",
      "valueTemplate": "Bearer {{secret}}"
    }
  ],
  "datasetMapping": [
    {
      "variable": "$dataset.question",
      "targetLocation": "BODY",
      "targetPath": "$.question",
      "required": true
    },
    {
      "variable": "$dataset.context",
      "targetLocation": "BODY",
      "targetPath": "$.context",
      "required": false
    }
  ]
}
```

### Response - 201 Created

```http
Location: /api/v1/chatbot-targets/15e42f5e-88fd-4bcf-b5b2-142c5b82a9d1
```

```json
{
  "id": "15e42f5e-88fd-4bcf-b5b2-142c5b82a9d1",
  "projectId": "2d4a9209-6f21-41de-86e1-b3f4cae4c9e1",
  "name": "Internal Sales Chatbot",
  "description": "Sales chatbot staging endpoint",
  "method": "POST",
  "url": "https://internal-chatbot.example.com/api/chat",
  "status": "DRAFT",
  "rawCurlHash": "sha256:0db25a6f7b7d...",
  "hasSecrets": true,
  "hasResponseExtraction": false,
  "latestExampleId": null,
  "createdAt": "2026-07-02T10:00:00+07:00",
  "updatedAt": "2026-07-02T10:00:00+07:00"
}
```

### Creation Rules

```text
1. Backend must validate project permission.
2. Backend must validate URL and method.
3. Backend must reject rawCurlSanitized if it appears to contain plaintext secret.
4. Backend must reject requestConfig if sensitive values appear unmasked.
5. Backend must validate all secretBindings.
6. Backend must validate dataset variables against the current project dataset schema when possible.
7. Backend must compute raw_curl_hash from sanitized raw cURL.
8. Initial status should be DRAFT unless backend can prove config and extraction are complete.
```

---

## 9. Get Chatbot Target Detail

```http
GET /api/v1/chatbot-targets/{chatbotTargetId}
```

### Purpose

Get full chatbot target detail for edit/debug UI.

### Permission

Project VIEW.

### Response - 200 OK

```json
{
  "id": "15e42f5e-88fd-4bcf-b5b2-142c5b82a9d1",
  "projectId": "2d4a9209-6f21-41de-86e1-b3f4cae4c9e1",
  "name": "Internal Sales Chatbot",
  "description": "Sales chatbot staging endpoint",
  "rawCurlSanitized": "curl -X POST 'https://internal-chatbot.example.com/api/chat' -H 'Content-Type: application/json' -H 'Authorization: Bearer ****MASKED****' -d '{\"question\":\"$dataset.question\"}'",
  "rawCurlHash": "sha256:0db25a6f7b7d...",
  "requestConfig": {
    "method": "POST",
    "url": "https://internal-chatbot.example.com/api/chat",
    "headers": [
      {
        "name": "Content-Type",
        "value": "application/json",
        "sensitive": false
      },
      {
        "name": "Authorization",
        "value": "Bearer ****MASKED****",
        "sensitive": true,
        "secretBindingKey": "authToken"
      }
    ],
    "queryParams": [],
    "bodyType": "JSON",
    "body": {
      "question": "$dataset.question",
      "context": "$dataset.context"
    }
  },
  "secretBindings": [
    {
      "key": "authToken",
      "secretId": "3e8d1de2-2a26-4d9a-a0dd-9c6ce056d9ea",
      "secretName": "Internal Chatbot Token",
      "secretScope": "WORKSPACE",
      "location": "HEADER",
      "name": "Authorization",
      "valueTemplate": "Bearer {{secret}}"
    }
  ],
  "datasetMapping": [
    {
      "variable": "$dataset.question",
      "targetLocation": "BODY",
      "targetPath": "$.question",
      "required": true
    }
  ],
  "responseExtraction": {
    "mainAnswerPath": "$.data.answer",
    "fields": [
      {
        "name": "intent",
        "path": "$.data.intent",
        "required": false
      }
    ]
  },
  "status": "ACTIVE",
  "latestExampleId": 1024,
  "createdBy": "a38f0878-1ebe-49db-abf2-f6a372b6c246",
  "createdAt": "2026-07-02T10:00:00+07:00",
  "updatedAt": "2026-07-02T10:15:00+07:00"
}
```

Response rules:

```text
1. Response may include secretId and secretName metadata.
2. Response must not include encryptedValue.
3. Response must not include plaintext secret value.
4. Headers/query/body containing secret must be masked.
```

---

## 10. Update Chatbot Target

```http
PATCH /api/v1/chatbot-targets/{chatbotTargetId}
```

### Purpose

Update chatbot target metadata, sanitized request config, secret bindings, dataset mapping or status.

### Permission

Project UPDATE / Configure chatbot target.

### Request

All fields are optional. Omitted fields must preserve current values.

```json
{
  "name": "Internal Sales Chatbot - Staging",
  "description": "Updated staging chatbot endpoint",
  "rawCurlSanitized": "curl -X POST 'https://internal-chatbot.example.com/api/v2/chat' -H 'Authorization: Bearer ****MASKED****' -d '{\"question\":\"$dataset.question\"}'",
  "requestConfig": {
    "method": "POST",
    "url": "https://internal-chatbot.example.com/api/v2/chat",
    "headers": [
      {
        "name": "Authorization",
        "value": "Bearer ****MASKED****",
        "sensitive": true,
        "secretBindingKey": "authToken"
      }
    ],
    "queryParams": [],
    "bodyType": "JSON",
    "body": {
      "question": "$dataset.question"
    }
  },
  "secretBindings": [
    {
      "key": "authToken",
      "secretId": "3e8d1de2-2a26-4d9a-a0dd-9c6ce056d9ea",
      "location": "HEADER",
      "name": "Authorization",
      "valueTemplate": "Bearer {{secret}}"
    }
  ],
  "datasetMapping": [
    {
      "variable": "$dataset.question",
      "targetLocation": "BODY",
      "targetPath": "$.question",
      "required": true
    }
  ],
  "status": "DRAFT"
}
```

### Response - 200 OK

Returns `ChatbotTargetDetailResponse`.

### Update Rules

```text
1. Updating requestConfig should reset status to DRAFT unless responseExtraction remains valid.
2. Updating secretBindings must validate secret visibility and scope.
3. Setting status to ACTIVE is allowed only when:
   - request config is valid
   - required secrets are active
   - responseExtraction.mainAnswerPath exists
   - target is not deleted
4. Setting status to DISABLED is allowed for OWNER / ADMIN / MEMBER.
5. Setting status to INVALID is usually done by backend after failed test connection or validation.
6. If target is used by old test runs, old runs remain stable because TestRun locks snapshots.
```

---

## 11. Delete Chatbot Target

```http
DELETE /api/v1/chatbot-targets/{chatbotTargetId}
```

### Purpose

Soft delete a chatbot target.

### Permission

Project UPDATE / Configure chatbot target.

### Response - 204 No Content

Rules:

```text
1. Backend sets deleted_at.
2. Deleted target must not appear in default list APIs.
3. Deleted target cannot be used in new test runs.
4. Historical test runs that already locked target config remain viewable.
5. If target is used by RUNNING/PENDING test run, backend should return conflict.
```

Conflict error code:

```text
CHATBOT_TARGET_IN_USE_BY_ACTIVE_RUN
```

---

## 12. Inspect cURL

```http
POST /api/v1/chatbot-targets/inspect-curl
```

### Purpose

Parse and sanitize a cURL command without creating or updating a chatbot target.

This endpoint is optional but useful as a backend fallback for clients that cannot parse cURL reliably.

### Permission

Authenticated ACTIVE user.

### Important Security Rule

```text
This endpoint may receive raw cURL in the request body, but it must not persist raw cURL.
It may only return parsed config, secret candidates and sanitized raw cURL.
```

### Request

```json
{
  "rawCurl": "curl -X POST 'https://internal-chatbot.example.com/api/chat' -H 'Content-Type: application/json' -H 'Authorization: Bearer real-token-value' -d '{\"question\":\"hello\"}'"
}
```

### Response - 200 OK

```json
{
  "rawCurlSanitized": "curl -X POST 'https://internal-chatbot.example.com/api/chat' -H 'Content-Type: application/json' -H 'Authorization: Bearer ****MASKED****' -d '{\"question\":\"hello\"}'",
  "requestConfig": {
    "method": "POST",
    "url": "https://internal-chatbot.example.com/api/chat",
    "headers": [
      {
        "name": "Content-Type",
        "value": "application/json",
        "sensitive": false
      },
      {
        "name": "Authorization",
        "value": "Bearer ****MASKED****",
        "sensitive": true,
        "secretBindingKey": "authorizationHeader"
      }
    ],
    "queryParams": [],
    "bodyType": "JSON",
    "body": {
      "question": "hello"
    }
  },
  "secretCandidates": [
    {
      "key": "authorizationHeader",
      "location": "HEADER",
      "name": "Authorization",
      "detectedType": "BEARER_TOKEN",
      "maskedPreview": "Bearer ****MASKED****",
      "recommendedValueTemplate": "Bearer {{secret}}"
    }
  ],
  "warnings": [
    {
      "code": "BODY_VALUE_NOT_MAPPED_TO_DATASET",
      "message": "Body field $.question contains a literal value. Consider mapping it to $dataset.question."
    }
  ]
}
```

### Rules

```text
1. Backend must not execute cURL.
2. Backend must not persist rawCurl.
3. Backend must return sanitized raw cURL.
4. Backend should detect common sensitive fields.
5. Backend should not automatically create Secret entities from this endpoint.
6. User must explicitly select/create a Secret during create/update target flow.
```

### Error Codes

```text
CURL_PARSE_FAILED
CURL_UNSUPPORTED_OPTION
CURL_CONTAINS_UNSUPPORTED_BODY_TYPE
```

---

## 13. Test Connection

```http
POST /api/v1/chatbot-targets/{chatbotTargetId}/test-connection
```

### Purpose

Execute the chatbot target once with sample dataset values and save a masked example request/response.

### Permission

Project UPDATE / Configure chatbot target.

### Request

```json
{
  "sampleVars": {
    "question": "VF 8 có mấy phiên bản?",
    "expected": "VF 8 có các phiên bản Eco và Plus.",
    "context": "Thông tin sản phẩm VF 8"
  },
  "timeoutMs": 30000
}
```

Request rules:

```text
1. sampleVars keys correspond to dataset schema column names without `$dataset.` prefix.
2. If sampleVars is omitted, backend may use first dataset row sample if available.
3. timeoutMs must not exceed deployment max target timeout.
4. Backend resolves secret only at runtime.
```

### Response - 200 OK

```json
{
  "exampleId": 1024,
  "chatbotTargetId": "15e42f5e-88fd-4bcf-b5b2-142c5b82a9d1",
  "success": true,
  "request": {
    "method": "POST",
    "url": "https://internal-chatbot.example.com/api/chat",
    "headers": {
      "Content-Type": "application/json",
      "Authorization": "Bearer ****MASKED****"
    },
    "body": {
      "question": "VF 8 có mấy phiên bản?",
      "context": "Thông tin sản phẩm VF 8"
    }
  },
  "response": {
    "statusCode": 200,
    "headers": {
      "Content-Type": "application/json"
    },
    "body": {
      "status": "success",
      "data": {
        "answer": "VF 8 có hai phiên bản Eco và Plus.",
        "intent": "product_info",
        "confidence": 0.92
      }
    }
  },
  "extractionCandidates": [
    {
      "path": "$.data.answer",
      "valuePreview": "VF 8 có hai phiên bản Eco và Plus.",
      "recommendedAs": "mainAnswer"
    },
    {
      "path": "$.data.intent",
      "valuePreview": "product_info",
      "recommendedAs": "metadata"
    },
    {
      "path": "$.data.confidence",
      "valuePreview": "0.92",
      "recommendedAs": "metadata"
    }
  ],
  "latencyMs": 1200,
  "createdAt": "2026-07-02T10:10:00+07:00"
}
```

### Failure Response - 200 OK with saved example

When target endpoint returns a non-2xx response or times out, backend should still create an example row when possible and return a structured test result.

```json
{
  "exampleId": 1025,
  "chatbotTargetId": "15e42f5e-88fd-4bcf-b5b2-142c5b82a9d1",
  "success": false,
  "request": {
    "method": "POST",
    "url": "https://internal-chatbot.example.com/api/chat",
    "headers": {
      "Authorization": "Bearer ****MASKED****"
    },
    "body": {
      "question": "VF 8 có mấy phiên bản?"
    }
  },
  "response": {
    "statusCode": 401,
    "headers": {
      "Content-Type": "application/json"
    },
    "body": {
      "error": "Unauthorized"
    }
  },
  "errorMessage": "Target returned HTTP 401.",
  "latencyMs": 320,
  "createdAt": "2026-07-02T10:12:00+07:00"
}
```

### Error Response - 4xx/5xx

Use RFC 9457 only when backend cannot perform/save the test attempt due to API validation, permission or internal system failure.

Example:

```json
{
  "type": "https://vfqc.vinfast.vn/problems/chatbot-target-secret-invalid",
  "title": "Chatbot target secret is invalid",
  "status": 409,
  "detail": "The target references a disabled or deleted secret.",
  "instance": "/api/v1/chatbot-targets/15e42f5e-88fd-4bcf-b5b2-142c5b82a9d1/test-connection",
  "code": "CHATBOT_TARGET_SECRET_INVALID",
  "traceId": "01J0Z7Y4Y4F9E1S2Y9W8Q7P6A5"
}
```

### Side Effects

```text
1. Insert chatbot_target_examples row.
2. Update chatbot_targets.latest_example_id.
3. If request failed because config is invalid, set chatbot_targets.status = INVALID.
4. If request succeeded, backend may keep status DRAFT until response extraction is configured.
```

---

## 14. List Examples

```http
GET /api/v1/chatbot-targets/{chatbotTargetId}/examples
```

### Purpose

List masked test connection examples for a chatbot target.

### Permission

Project VIEW.

### Query Parameters

| Name | Type | Required | Description |
| ---- | ---- | -------- | ----------- |
| `page` | integer | No | Page number, default `0` |
| `size` | integer | No | Page size, default `20` |
| `sort` | string | No | Example: `createdAt,desc` |

### Response - 200 OK

```json
{
  "items": [
    {
      "id": 1024,
      "chatbotTargetId": "15e42f5e-88fd-4bcf-b5b2-142c5b82a9d1",
      "responseStatusCode": 200,
      "latencyMs": 1200,
      "hasError": false,
      "errorMessage": null,
      "testedBy": "a38f0878-1ebe-49db-abf2-f6a372b6c246",
      "createdAt": "2026-07-02T10:10:00+07:00"
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

## 15. Get Example Detail

```http
GET /api/v1/chatbot-target-examples/{exampleId}
```

### Purpose

Get full masked request/response for one test connection example.

### Permission

Project VIEW through target ownership.

### Response - 200 OK

```json
{
  "id": 1024,
  "chatbotTargetId": "15e42f5e-88fd-4bcf-b5b2-142c5b82a9d1",
  "request": {
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
  "responseStatusCode": 200,
  "responseHeaders": {
    "Content-Type": "application/json"
  },
  "responseBody": {
    "status": "success",
    "data": {
      "answer": "VF 8 có hai phiên bản Eco và Plus.",
      "intent": "product_info",
      "confidence": 0.92
    }
  },
  "extractedOutput": {
    "mainAnswer": "VF 8 có hai phiên bản Eco và Plus.",
    "intent": "product_info",
    "confidence": 0.92
  },
  "latencyMs": 1200,
  "errorMessage": null,
  "testedBy": "a38f0878-1ebe-49db-abf2-f6a372b6c246",
  "createdAt": "2026-07-02T10:10:00+07:00"
}
```

---

## 16. Update Response Extraction

```http
PATCH /api/v1/chatbot-targets/{chatbotTargetId}/response-extraction
```

### Purpose

Save extraction paths after QC selects fields from an example response.

### Permission

Project UPDATE / Configure chatbot target.

### Request

```json
{
  "mainAnswerPath": "$.data.answer",
  "fields": [
    {
      "name": "intent",
      "path": "$.data.intent",
      "required": false
    },
    {
      "name": "confidence",
      "path": "$.data.confidence",
      "required": false
    }
  ],
  "validateAgainstExampleId": 1024,
  "activateIfValid": true
}
```

### Response - 200 OK

```json
{
  "chatbotTargetId": "15e42f5e-88fd-4bcf-b5b2-142c5b82a9d1",
  "responseExtraction": {
    "mainAnswerPath": "$.data.answer",
    "fields": [
      {
        "name": "intent",
        "path": "$.data.intent",
        "required": false
      },
      {
        "name": "confidence",
        "path": "$.data.confidence",
        "required": false
      }
    ]
  },
  "status": "ACTIVE",
  "updatedAt": "2026-07-02T10:15:00+07:00"
}
```

### Validation Rules

```text
1. mainAnswerPath is required.
2. Path syntax must be valid.
3. If validateAgainstExampleId is provided, the example must belong to the target.
4. If path does not exist in selected example response, return RESPONSE_EXTRACTION_PATH_INVALID.
5. If activateIfValid is true, backend may set status ACTIVE only when target config is otherwise valid.
```

---

## 17. Status Transition Rules

Allowed transitions:

```text
DRAFT -> ACTIVE
DRAFT -> INVALID
DRAFT -> DISABLED
ACTIVE -> DRAFT
ACTIVE -> INVALID
ACTIVE -> DISABLED
INVALID -> DRAFT
INVALID -> ACTIVE
INVALID -> DISABLED
DISABLED -> DRAFT
DISABLED -> ACTIVE
```

Recommended backend behavior:

```text
1. Create target as DRAFT.
2. Successful test connection does not automatically make target ACTIVE unless response extraction is valid.
3. Failed test connection may set target INVALID.
4. Updating request config should set target DRAFT or keep ACTIVE only after re-validation.
5. Updating response extraction with activateIfValid=true may set target ACTIVE.
6. Disabled target cannot be selected for new TestRun.
7. Deleted target cannot be selected for new TestRun.
```

---

## 18. Secret and Sanitization Rules

### 18.1. Sensitive Field Names

The following names must be treated as sensitive by default:

```text
Authorization
Cookie
Set-Cookie
X-API-Key
api_key
apikey
access_token
refresh_token
id_token
client_secret
password
secret
token
bearer
credential
x-auth-token
proxy-authorization
```

### 18.2. Sanitized Value Format

Use stable masked format:

```text
****MASKED****
```

Examples:

```json
{
  "Authorization": "Bearer ****MASKED****",
  "X-API-Key": "****MASKED****"
}
```

### 18.3. Rejection Rule

Backend must reject create/update target requests if sensitive values appear unmasked in persistent fields.

Reject these:

```json
{
  "headers": [
    {
      "name": "Authorization",
      "value": "Bearer real-token-value",
      "sensitive": true
    }
  ]
}
```

```json
{
  "body": {
    "api_key": "real-api-key"
  }
}
```

Return:

```text
CHATBOT_TARGET_CONTAINS_PLAINTEXT_SECRET
```

---

## 19. Relationship with Dataset API

Chatbot target mapping UI depends on dataset variables.

Frontend should call:

```http
GET /api/v1/dataset-versions/{datasetVersionId}/variables
```

or use current project schema from:

```http
GET /api/v1/projects/{projectId}/dataset-schemas/current
```

Then user maps variables into request config:

```text
$dataset.question
$dataset.expected
$dataset.context
```

Rules:

```text
1. Chatbot target may be configured before a dataset is imported, using current project schema.
2. Dataset variables should be validated against schema when target is saved.
3. TestRun preflight must validate selected target against selected dataset version.
4. If target requires a variable missing from selected dataset version, TestRun preflight must fail.
```

Potential preflight error:

```text
CHATBOT_TARGET_DATASET_MAPPING_INVALID
```

---

## 20. Relationship with Test Run API

TestRun may use only chatbot targets that are:

```text
ACTIVE
not deleted
not disabled
visible in the target project
valid for selected dataset version
```

When creating a TestRun, backend must snapshot:

```text
1. chatbot_target_id
2. sanitized request config
3. auth_config_json with secret references only
4. dataset_mapping_json
5. response_extraction_config_json
6. raw_curl_hash
7. target display name
```

Important rule:

```text
Updating ChatbotTarget after a TestRun is created must not change that TestRun's locked behavior.
```

---

## 21. Error Codes

Chatbot target-specific error codes:

```text
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
CURL_PARSE_FAILED
CURL_UNSUPPORTED_OPTION
CURL_CONTAINS_UNSUPPORTED_BODY_TYPE
RESPONSE_EXTRACTION_PATH_INVALID
RESPONSE_EXTRACTION_CONFIG_INVALID
CHATBOT_TARGET_EXAMPLE_NOT_FOUND
```

---

## 22. Error Examples

### 22.1. Plaintext Secret Detected

```http
400 Bad Request
Content-Type: application/problem+json
```

```json
{
  "type": "https://vfqc.vinfast.vn/problems/chatbot-target-contains-plaintext-secret",
  "title": "Chatbot target contains plaintext secret",
  "status": 400,
  "detail": "The request config contains a plaintext Authorization value. Use a Secret reference instead.",
  "instance": "/api/v1/projects/2d4a9209-6f21-41de-86e1-b3f4cae4c9e1/chatbot-targets",
  "code": "CHATBOT_TARGET_CONTAINS_PLAINTEXT_SECRET",
  "traceId": "01J0Z7Y4Y4F9E1S2Y9W8Q7P6A5",
  "errors": [
    {
      "field": "requestConfig.headers[1].value",
      "message": "Sensitive header value must be masked and linked to a secret binding."
    }
  ]
}
```

### 22.2. Invalid Extraction Path

```http
400 Bad Request
Content-Type: application/problem+json
```

```json
{
  "type": "https://vfqc.vinfast.vn/problems/response-extraction-path-invalid",
  "title": "Response extraction path is invalid",
  "status": 400,
  "detail": "The path $.data.answer does not exist in the selected example response.",
  "instance": "/api/v1/chatbot-targets/15e42f5e-88fd-4bcf-b5b2-142c5b82a9d1/response-extraction",
  "code": "RESPONSE_EXTRACTION_PATH_INVALID",
  "traceId": "01J0Z7Y4Y4F9E1S2Y9W8Q7P6A5"
}
```

### 22.3. Target In Use by Active Run

```http
409 Conflict
Content-Type: application/problem+json
```

```json
{
  "type": "https://vfqc.vinfast.vn/problems/chatbot-target-in-use-by-active-run",
  "title": "Chatbot target is in use by an active run",
  "status": 409,
  "detail": "The chatbot target cannot be deleted while it is used by a pending or running test run.",
  "instance": "/api/v1/chatbot-targets/15e42f5e-88fd-4bcf-b5b2-142c5b82a9d1",
  "code": "CHATBOT_TARGET_IN_USE_BY_ACTIVE_RUN",
  "traceId": "01J0Z7Y4Y4F9E1S2Y9W8Q7P6A5"
}
```

---

## 23. Backend Implementation Notes

```text
1. Controller must not implement cURL parsing by shelling out to system cURL.
2. Use a parser library or safe parser code if backend cURL inspection is implemented.
3. Request config validation should happen before persistence.
4. SensitiveDataMasker must run before writing request/response/example data.
5. Secret resolution must happen inside service/runtime boundary only.
6. Test connection should use timeout and size limit.
7. Response body should have max persisted size to avoid DB bloat.
8. Large debug artifacts belong to artifact storage, not chatbot_target_examples.
9. latest_example_id update should happen in the same transaction as example insert when possible.
10. Updating target config should not mutate historical TestRun snapshots.
```

---

## 24. Acceptance Checklist

Before implementing or reviewing Chatbot Target API, verify:

```text
1. No endpoint returns plaintext secret.
2. No persistent request field accepts unmasked Authorization/API key/token/cookie.
3. raw_curl_sanitized is the only cURL field stored.
4. raw_curl_hash is computed from sanitized cURL.
5. Backend never shell-executes cURL.
6. Test connection creates chatbot_target_examples row.
7. Test connection response is masked before persistence and response.
8. Response extraction validates selected path against example when requested.
9. ACTIVE target requires valid request config and mainAnswerPath.
10. DISABLED/deleted/INVALID targets cannot be selected for new TestRun.
11. Dataset variables use `$dataset.<columnName>` syntax.
12. Runner can convert target config into Promptfoo HTTP provider config.
13. Historical TestRuns are not affected by later target edits.
14. Error responses use RFC 9457 Problem Details with stable `code`.
```
