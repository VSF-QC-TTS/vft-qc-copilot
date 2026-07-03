# VinFast QC Copilot - Secret & Provider API Contract

Version: `v0.2`  
Status: API Contract Draft  
Base path: `/api/v1`  
Related docs:

- `docs/api/00-api-overview.md`
- `docs/api/01-api-conventions.md`
- `docs/api/02-auth-permission-contract.md`
- `docs/api/03-workspace-project-api.md`
- `prd_v07.md`
- `erd_v05.md`

---

## 1. Purpose

This document defines the REST API contract for managing secrets and LLM providers in VinFast QC Copilot.

This contract covers:

```text
1. Personal secrets.
2. Workspace secrets.
3. Personal LLM providers.
4. Workspace LLM providers.
5. Provider connection testing.
6. Provider test history for troubleshooting.
7. Secret replacement/rotation.
8. Provider visibility and permission rules.
9. Security rules for plaintext secret handling.
```

This document does not define:

```text
1. Runtime secret decryption implementation.
2. KMS/envelope encryption internals.
3. Runner secret resolution API internals.
4. Chatbot target cURL secret extraction.
```

Those details belong in backend security and runner internal specs.

---

## 2. Design Principles

```text
1. No API may return plaintext secret values after creation.
2. Secret values must be encrypted before persistence.
3. `secrets.encrypted_value` is internal persistence data and must never be exposed through API responses.
4. Providers reference secrets by `secretId`; providers must not store API keys directly.
5. Provider configs must be sanitized and must not contain Authorization/API key/token/cookie values.
6. Workspace providers must use workspace secrets from the same workspace.
7. Personal providers must use personal secrets owned by the authenticated user.
8. Provider test endpoints must mask request/response/error data.
9. Provider test history must store sanitized troubleshooting records only.
10. Soft delete is used for secrets and providers.
11. Disabled/deleted secrets cannot be used by active providers or test runs.
```

---

## 3. Resource Model Summary

### 3.1. Secret

A secret stores encrypted credentials such as API keys or provider tokens.

Database entity:

```text
secrets
```

Fields from ERD:

```text
id UUID PK
scope secret_scope NOT NULL
owner_user_id UUID FK users(id)
workspace_id UUID FK workspaces(id)
name TEXT NOT NULL
encrypted_value TEXT NOT NULL
status secret_status NOT NULL
created_by UUID FK users(id)
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
deleted_at TIMESTAMPTZ
```

Supported scopes:

```text
PERSONAL
WORKSPACE
```

Supported statuses:

```text
ACTIVE
DISABLED
DELETED
```

Scope rules:

```text
PERSONAL:
  owner_user_id = authenticated user id
  workspace_id = null

WORKSPACE:
  workspace_id = target workspace id
  owner_user_id = null
```

Security rule:

```text
Plaintext secret value may be accepted in create/rotate requests.
Plaintext secret value must never be returned in any response.
```

---

### 3.2. LLM Provider

An LLM provider defines a callable model provider for LLM Judge, baseline comparison, and future dataset generation.

Database entity:

```text
llm_providers
```

Fields from ERD:

```text
id UUID PK
scope provider_scope NOT NULL
owner_user_id UUID FK users(id)
workspace_id UUID FK workspaces(id)
name TEXT NOT NULL
provider_type provider_type NOT NULL
secret_id UUID FK secrets(id)
base_url TEXT
default_model TEXT
available_models_json JSONB
config_json JSONB
status provider_status NOT NULL
created_by UUID FK users(id)
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
deleted_at TIMESTAMPTZ
```

Supported scopes:

```text
PERSONAL_PROVIDER
WORKSPACE_PROVIDER
```

Supported provider types:

```text
OPENAI_COMPATIBLE
GEMINI
CLAUDE
CUSTOM_HTTP
```

Supported statuses:

```text
ACTIVE
DISABLED
ERROR
```

Provider usage:

```text
1. LLM Judge.
2. Baseline model in comparison runs.
3. Dataset generation in a later phase.
```

---

### 3.3. LLM Provider Test Log

A provider test log stores one sanitized connection test attempt. It helps QC/admins trace provider failures without exposing plaintext secrets or full provider payloads.

Database entity:

```text
llm_provider_test_logs
```

Recommended fields:

```text
id BIGINT PK
llm_provider_id UUID FK llm_providers(id)
status provider_test_status NOT NULL
model TEXT
request_json_sanitized JSONB
response_preview TEXT
error_code TEXT
error_message TEXT
latency_ms INT
token_usage_json JSONB
tested_by UUID FK users(id)
tested_at TIMESTAMPTZ NOT NULL
created_at TIMESTAMPTZ NOT NULL
```

Supported statuses:

```text
SUCCESS
FAILED
ERROR
```

Rules:

```text
1. Every provider test attempt should create one test log row.
2. request_json_sanitized must not contain plaintext secret, Authorization value, API key, token, cookie or password.
3. response_preview must be short and masked.
4. error_message must be safe to show to workspace admins/QC members with provider access.
5. Provider test logs are operational troubleshooting data, not TestRun evidence.
6. Historical TestRuns must still rely on their own locked snapshots and artifacts.
```

---

## 4. Scope Compatibility Rules

### 4.1. Secret Scope Compatibility

```text
WORKSPACE secret:
  can be used only by WORKSPACE_PROVIDER in the same workspace.

PERSONAL secret:
  can be used only by PERSONAL_PROVIDER owned by the same user.
```

A provider must not reference a secret from a different scope.

Invalid examples:

```text
1. WORKSPACE_PROVIDER using PERSONAL secret.
2. PERSONAL_PROVIDER using WORKSPACE secret.
3. WORKSPACE_PROVIDER using a workspace secret from another workspace.
4. PERSONAL_PROVIDER using another user's personal secret.
```

### 4.2. Provider Visibility in Run Setup

When QC configures LLM Judge or baseline comparison inside a workspace, UI may show usable providers from:

```text
1. Active workspace providers from the current workspace.
2. Active personal providers owned by the current user.
```

Viewer users may view dashboard/results but must not configure evaluator/run setup.

### 4.3. Disabled or Deleted Resources

```text
1. DISABLED secret cannot be selected for a new provider.
2. DISABLED secret cannot be used by new test runs.
3. DISABLED provider cannot be selected for new evaluator/run setup.
4. DELETED resources must not appear in normal list endpoints.
5. Existing historical test runs keep their sanitized snapshots and labels, but runtime must not resolve deleted secrets for new execution.
```

---

## 5. Common DTOs

### 5.1. SecretResponse

```json
{
  "id": "3e8d1de2-2a26-4d9a-a0dd-9c6ce056d9ea",
  "scope": "WORKSPACE",
  "workspaceId": "6d4628b0-56f0-4e48-b1a1-fba3e0cbf54a",
  "ownerUserId": null,
  "name": "OpenAI Workspace Key",
  "status": "ACTIVE",
  "hasValue": true,
  "maskedPreview": "sk-****abcd",
  "createdBy": "d9714e4d-08f7-4cd0-b426-91e4bdc2a2f5",
  "createdAt": "2026-07-02T10:00:00+07:00",
  "updatedAt": "2026-07-02T10:00:00+07:00"
}
```

Rules:

```text
1. `encryptedValue` must never appear in API responses.
2. `secretValue` must never appear in API responses.
3. `maskedPreview` is optional and must be safe to expose.
4. `maskedPreview` should reveal at most a short non-sensitive suffix/prefix for user recognition.
```

---

### 5.2. SecretSummary

Used inside provider responses.

```json
{
  "id": "3e8d1de2-2a26-4d9a-a0dd-9c6ce056d9ea",
  "scope": "WORKSPACE",
  "name": "OpenAI Workspace Key",
  "status": "ACTIVE",
  "maskedPreview": "sk-****abcd"
}
```

---

### 5.3. LlmProviderResponse

```json
{
  "id": "9c7a1c71-6985-4f89-b19e-d3ef16d19999",
  "scope": "WORKSPACE_PROVIDER",
  "workspaceId": "6d4628b0-56f0-4e48-b1a1-fba3e0cbf54a",
  "ownerUserId": null,
  "name": "GPT-4.1 Judge",
  "providerType": "OPENAI_COMPATIBLE",
  "secret": {
    "id": "3e8d1de2-2a26-4d9a-a0dd-9c6ce056d9ea",
    "scope": "WORKSPACE",
    "name": "OpenAI Workspace Key",
    "status": "ACTIVE",
    "maskedPreview": "sk-****abcd"
  },
  "baseUrl": "https://api.openai.com/v1",
  "defaultModel": "gpt-4.1-mini",
  "availableModels": [
    "gpt-4.1-mini",
    "gpt-4.1"
  ],
  "config": {
    "timeoutMs": 120000,
    "retryCount": 2
  },
  "status": "ACTIVE",
  "createdBy": "d9714e4d-08f7-4cd0-b426-91e4bdc2a2f5",
  "createdAt": "2026-07-02T10:00:00+07:00",
  "updatedAt": "2026-07-02T10:00:00+07:00"
}
```

Rules:

```text
1. `config` must be sanitized.
2. `config` must not contain raw Authorization, Cookie, API key, token, password or secret values.
3. `availableModels` maps to `available_models_json`.
4. `config` maps to `config_json`.
```

---

### 5.4. ProviderSummary

Used in run setup, evaluator setup, and comparison target selection.

```json
{
  "id": "9c7a1c71-6985-4f89-b19e-d3ef16d19999",
  "scope": "WORKSPACE_PROVIDER",
  "name": "GPT-4.1 Judge",
  "providerType": "OPENAI_COMPATIBLE",
  "defaultModel": "gpt-4.1-mini",
  "availableModels": [
    "gpt-4.1-mini",
    "gpt-4.1"
  ],
  "status": "ACTIVE"
}
```

---

### 5.5. CollectionResponse

List endpoints use the collection response convention from `01-api-conventions.md`.

```json
{
  "items": [],
  "page": {
    "limit": 20,
    "offset": 0,
    "total": 0
  }
}
```

---

## 6. Secret APIs

### 6.1. List Secrets

```http
GET /api/v1/secrets
```

#### Purpose

List secret metadata visible/manageable by the authenticated user.

This endpoint never returns plaintext secret values.

#### Permission

```text
PERSONAL secrets:
  authenticated ACTIVE user can list own personal secrets.

WORKSPACE secrets:
  OWNER / ADMIN of the workspace can list workspace secrets.
  PLATFORM_ADMIN can list metadata, but cannot read plaintext values.
```

#### Query parameters

| Name | Type | Required | Description |
| ---- | ---- | :------: | ----------- |
| `scope` | enum | No | `PERSONAL` or `WORKSPACE` |
| `workspaceId` | UUID | Required when `scope=WORKSPACE` | Workspace id |
| `status` | enum | No | `ACTIVE`, `DISABLED`, `DELETED` |
| `limit` | integer | No | Page size |
| `offset` | integer | No | Page offset |

#### Response - 200 OK

```json
{
  "items": [
    {
      "id": "3e8d1de2-2a26-4d9a-a0dd-9c6ce056d9ea",
      "scope": "WORKSPACE",
      "workspaceId": "6d4628b0-56f0-4e48-b1a1-fba3e0cbf54a",
      "ownerUserId": null,
      "name": "OpenAI Workspace Key",
      "status": "ACTIVE",
      "hasValue": true,
      "maskedPreview": "sk-****abcd",
      "createdBy": "d9714e4d-08f7-4cd0-b426-91e4bdc2a2f5",
      "createdAt": "2026-07-02T10:00:00+07:00",
      "updatedAt": "2026-07-02T10:00:00+07:00"
    }
  ],
  "page": {
    "limit": 20,
    "offset": 0,
    "total": 1
  }
}
```

#### Error codes

```text
AUTHENTICATION_REQUIRED
USER_DISABLED
PERMISSION_DENIED
WORKSPACE_NOT_FOUND
VALIDATION_ERROR
```

---

### 6.2. Create Secret

```http
POST /api/v1/secrets
```

#### Purpose

Create a personal or workspace secret.

Plaintext secret value is accepted only in this request body and must be encrypted before persistence.

#### Permission

```text
PERSONAL:
  OWNER / ADMIN / MEMBER can create their own personal secret.
  VIEWER cannot create personal secret.

WORKSPACE:
  OWNER / ADMIN can create workspace secret.
  MEMBER / VIEWER cannot create workspace secret.

PLATFORM_ADMIN:
  can create metadata and encrypted secret value, but still cannot read plaintext later.
```

#### Request body

```json
{
  "scope": "WORKSPACE",
  "workspaceId": "6d4628b0-56f0-4e48-b1a1-fba3e0cbf54a",
  "name": "OpenAI Workspace Key",
  "secretValue": "sk-real-secret-value"
}
```

For personal secret:

```json
{
  "scope": "PERSONAL",
  "name": "My Gemini Key",
  "secretValue": "real-secret-value"
}
```

#### Field rules

| Field | Rule |
| ----- | ---- |
| `scope` | Required. `PERSONAL` or `WORKSPACE`. |
| `workspaceId` | Required for `WORKSPACE`; must be omitted/null for `PERSONAL`. |
| `name` | Required, non-empty, unique within scope. |
| `secretValue` | Required on create. Must not be logged. |

#### Response - 201 Created

```http
Location: /api/v1/secrets/3e8d1de2-2a26-4d9a-a0dd-9c6ce056d9ea
```

```json
{
  "id": "3e8d1de2-2a26-4d9a-a0dd-9c6ce056d9ea",
  "scope": "WORKSPACE",
  "workspaceId": "6d4628b0-56f0-4e48-b1a1-fba3e0cbf54a",
  "ownerUserId": null,
  "name": "OpenAI Workspace Key",
  "status": "ACTIVE",
  "hasValue": true,
  "maskedPreview": "sk-****abcd",
  "createdBy": "d9714e4d-08f7-4cd0-b426-91e4bdc2a2f5",
  "createdAt": "2026-07-02T10:00:00+07:00",
  "updatedAt": "2026-07-02T10:00:00+07:00"
}
```

#### State changes

```text
1. Validate permission.
2. Validate scope fields.
3. Encrypt `secretValue`.
4. Insert row into `secrets`.
5. Return only metadata and masked preview.
```

#### Entities touched

```text
secrets
```

#### Error codes

```text
AUTHENTICATION_REQUIRED
USER_DISABLED
PERMISSION_DENIED
WORKSPACE_NOT_FOUND
VALIDATION_ERROR
CONFLICT
```

---

### 6.3. Get Secret Detail

```http
GET /api/v1/secrets/{secretId}
```

#### Purpose

Get secret metadata.

This endpoint never returns plaintext or encrypted secret values.

#### Permission

```text
PERSONAL:
  owner user only.

WORKSPACE:
  OWNER / ADMIN of the workspace.

PLATFORM_ADMIN:
  can read metadata only.
```

#### Response - 200 OK

Returns `SecretResponse`.

#### Error codes

```text
AUTHENTICATION_REQUIRED
USER_DISABLED
SECRET_NOT_FOUND
PERMISSION_DENIED
```

#### Security rule

Forbidden response fields:

```text
secretValue
encryptedValue
encrypted_value
```

---

### 6.4. Update Secret Metadata

```http
PATCH /api/v1/secrets/{secretId}
```

#### Purpose

Update secret metadata such as name or status.

This endpoint should not accept `secretValue`. Use rotate endpoint to replace the secret value.

#### Permission

```text
PERSONAL:
  owner user only.

WORKSPACE:
  OWNER / ADMIN of the workspace.

PLATFORM_ADMIN:
  can update metadata, but cannot read plaintext secret.
```

#### Request body

```json
{
  "name": "OpenAI Workspace Key - Production",
  "status": "ACTIVE"
}
```

#### Field rules

| Field | Rule |
| ----- | ---- |
| `name` | Optional. Unique within secret scope. |
| `status` | Optional. `ACTIVE` or `DISABLED`. `DELETED` is set by DELETE endpoint. |

#### Response - 200 OK

Returns `SecretResponse`.

#### Error codes

```text
AUTHENTICATION_REQUIRED
USER_DISABLED
SECRET_NOT_FOUND
PERMISSION_DENIED
VALIDATION_ERROR
CONFLICT
```

---

### 6.5. Rotate Secret Value

```http
POST /api/v1/secrets/{secretId}/rotate
```

#### Purpose

Replace the encrypted secret value with a newly submitted plaintext value.

This is an action endpoint because rotating a secret value is a security-sensitive operation and should be explicit.

#### Permission

Same as update secret metadata.

#### Request body

```json
{
  "secretValue": "new-real-secret-value"
}
```

#### Response - 200 OK

Returns `SecretResponse` with updated `maskedPreview` and `updatedAt`.

#### State changes

```text
1. Validate permission.
2. Validate secret is not DELETED.
3. Encrypt new secret value.
4. Replace `secrets.encrypted_value`.
5. Update `updated_at`.
6. Return metadata only.
```

#### Runtime note

```text
Existing in-flight test runs should continue using their already-resolved runtime secret if resolution already happened.
New test runs must use the rotated value.
```

#### Error codes

```text
AUTHENTICATION_REQUIRED
USER_DISABLED
SECRET_NOT_FOUND
PERMISSION_DENIED
VALIDATION_ERROR
INVALID_STATE_TRANSITION
```

---

### 6.6. Delete Secret

```http
DELETE /api/v1/secrets/{secretId}
```

#### Purpose

Soft delete a secret.

#### Permission

Same as update secret metadata.

#### Response - 204 No Content

No response body.

#### Rules

```text
1. Soft delete by setting deleted_at and status = DELETED.
2. If active providers still reference this secret, backend should reject delete unless forced deletion is explicitly supported later.
3. MVP should prefer disabling secret first before deletion.
4. Deleted secret must not be resolvable at runtime.
```

#### Error codes

```text
AUTHENTICATION_REQUIRED
USER_DISABLED
SECRET_NOT_FOUND
PERMISSION_DENIED
CONFLICT
```

Suggested conflict code detail:

```text
SECRET_IN_USE_BY_PROVIDER
```

If not added as stable code yet, return common `CONFLICT` with field-level detail.

---

## 7. LLM Provider APIs

### 7.1. List LLM Providers

```http
GET /api/v1/llm-providers
```

#### Purpose

List provider metadata visible to the authenticated user.

This endpoint is used by:

```text
1. Provider management UI.
2. Evaluator setup UI.
3. Run setup UI.
4. Comparison baseline selection UI.
```

#### Permission

```text
PERSONAL_PROVIDER:
  owner user can list own personal providers.

WORKSPACE_PROVIDER:
  workspace members can list active workspace providers for selection.
  OWNER / ADMIN can list all statuses for management.
```

#### Query parameters

| Name | Type | Required | Description |
| ---- | ---- | :------: | ----------- |
| `scope` | enum | No | `PERSONAL_PROVIDER` or `WORKSPACE_PROVIDER` |
| `workspaceId` | UUID | Required to list workspace providers | Workspace id |
| `providerType` | enum | No | Filter by provider type |
| `status` | enum | No | `ACTIVE`, `DISABLED`, `ERROR` |
| `usage` | enum | No | `MANAGEMENT`, `JUDGE_SELECTION`, `BASELINE_SELECTION` |
| `limit` | integer | No | Page size |
| `offset` | integer | No | Page offset |

#### Response - 200 OK

```json
{
  "items": [
    {
      "id": "9c7a1c71-6985-4f89-b19e-d3ef16d19999",
      "scope": "WORKSPACE_PROVIDER",
      "workspaceId": "6d4628b0-56f0-4e48-b1a1-fba3e0cbf54a",
      "ownerUserId": null,
      "name": "GPT-4.1 Judge",
      "providerType": "OPENAI_COMPATIBLE",
      "secret": {
        "id": "3e8d1de2-2a26-4d9a-a0dd-9c6ce056d9ea",
        "scope": "WORKSPACE",
        "name": "OpenAI Workspace Key",
        "status": "ACTIVE",
        "maskedPreview": "sk-****abcd"
      },
      "baseUrl": "https://api.openai.com/v1",
      "defaultModel": "gpt-4.1-mini",
      "availableModels": [
        "gpt-4.1-mini",
        "gpt-4.1"
      ],
      "config": {
        "timeoutMs": 120000,
        "retryCount": 2
      },
      "status": "ACTIVE",
      "createdBy": "d9714e4d-08f7-4cd0-b426-91e4bdc2a2f5",
      "createdAt": "2026-07-02T10:00:00+07:00",
      "updatedAt": "2026-07-02T10:00:00+07:00"
    }
  ],
  "page": {
    "limit": 20,
    "offset": 0,
    "total": 1
  }
}
```

#### Error codes

```text
AUTHENTICATION_REQUIRED
USER_DISABLED
PERMISSION_DENIED
WORKSPACE_NOT_FOUND
VALIDATION_ERROR
```

---

### 7.2. Create LLM Provider

```http
POST /api/v1/llm-providers
```

#### Purpose

Create a personal or workspace provider.

#### Permission

```text
PERSONAL_PROVIDER:
  OWNER / ADMIN / MEMBER can create own personal provider.
  VIEWER cannot create personal provider.

WORKSPACE_PROVIDER:
  OWNER / ADMIN can create workspace provider.
  MEMBER / VIEWER cannot create workspace provider.
```

#### Request body

```json
{
  "scope": "WORKSPACE_PROVIDER",
  "workspaceId": "6d4628b0-56f0-4e48-b1a1-fba3e0cbf54a",
  "name": "GPT-4.1 Judge",
  "providerType": "OPENAI_COMPATIBLE",
  "secretId": "3e8d1de2-2a26-4d9a-a0dd-9c6ce056d9ea",
  "baseUrl": "https://api.openai.com/v1",
  "defaultModel": "gpt-4.1-mini",
  "availableModels": [
    "gpt-4.1-mini",
    "gpt-4.1"
  ],
  "config": {
    "timeoutMs": 120000,
    "retryCount": 2
  }
}
```

Personal provider example:

```json
{
  "scope": "PERSONAL_PROVIDER",
  "name": "My Gemini Provider",
  "providerType": "GEMINI",
  "secretId": "76a563bd-b0dc-4d69-bb44-cdc9f3bbcb92",
  "baseUrl": null,
  "defaultModel": "gemini-1.5-pro",
  "availableModels": [
    "gemini-1.5-pro",
    "gemini-1.5-flash"
  ],
  "config": {
    "timeoutMs": 120000,
    "retryCount": 2
  }
}
```

#### Field rules

| Field | Rule |
| ----- | ---- |
| `scope` | Required. `PERSONAL_PROVIDER` or `WORKSPACE_PROVIDER`. |
| `workspaceId` | Required for `WORKSPACE_PROVIDER`; must be omitted/null for `PERSONAL_PROVIDER`. |
| `name` | Required, unique within provider scope. |
| `providerType` | Required. One of supported provider types. |
| `secretId` | Required for provider calls that need authentication. Must be scope-compatible. |
| `baseUrl` | Required for `OPENAI_COMPATIBLE` and `CUSTOM_HTTP`; optional/null for built-in providers if default is known. |
| `defaultModel` | Required for LLM Judge/baseline usage. |
| `availableModels` | Optional but recommended for UI model selection. |
| `config` | Optional sanitized provider-specific config. |

#### Provider type config guidance

```text
OPENAI_COMPATIBLE:
  baseUrl required.
  defaultModel required.
  secret is usually Bearer token.

GEMINI:
  baseUrl optional if backend has default integration URL.
  defaultModel required.

CLAUDE:
  baseUrl optional if backend has default integration URL.
  defaultModel required.

CUSTOM_HTTP:
  baseUrl required.
  config must describe request format safely.
  Secret values must still be referenced by secretId, not embedded in config.
```

#### Response - 201 Created

```http
Location: /api/v1/llm-providers/9c7a1c71-6985-4f89-b19e-d3ef16d19999
```

Returns `LlmProviderResponse`.

#### State changes

```text
1. Validate permission.
2. Validate provider scope fields.
3. Validate secret exists and is scope-compatible.
4. Validate config contains no raw secret-like values.
5. Insert row into `llm_providers`.
6. Default status should be ACTIVE unless initial validation fails.
```

#### Entities touched

```text
llm_providers
secrets
```

#### Error codes

```text
AUTHENTICATION_REQUIRED
USER_DISABLED
PERMISSION_DENIED
WORKSPACE_NOT_FOUND
SECRET_NOT_FOUND
VALIDATION_ERROR
CONFLICT
```

Recommended additional details:

```text
PROVIDER_SECRET_SCOPE_MISMATCH
PROVIDER_CONFIG_CONTAINS_SECRET
```

If not added as stable codes yet, use `VALIDATION_ERROR` with field-level errors.

---

### 7.3. Get LLM Provider Detail

```http
GET /api/v1/llm-providers/{providerId}
```

#### Purpose

Get provider metadata and sanitized config.

#### Permission

```text
PERSONAL_PROVIDER:
  owner user only.

WORKSPACE_PROVIDER:
  workspace members can view provider metadata.
  OWNER / ADMIN can use detail for management.
```

#### Response - 200 OK

Returns `LlmProviderResponse`.

#### Error codes

```text
AUTHENTICATION_REQUIRED
USER_DISABLED
PROVIDER_NOT_FOUND
PERMISSION_DENIED
```

---

### 7.4. Update LLM Provider

```http
PATCH /api/v1/llm-providers/{providerId}
```

#### Purpose

Update provider metadata, selected secret reference, model list, or sanitized config.

#### Permission

```text
PERSONAL_PROVIDER:
  owner user only.

WORKSPACE_PROVIDER:
  OWNER / ADMIN of the workspace.
```

#### Request body

```json
{
  "name": "GPT-4.1 Judge - Production",
  "secretId": "3e8d1de2-2a26-4d9a-a0dd-9c6ce056d9ea",
  "baseUrl": "https://api.openai.com/v1",
  "defaultModel": "gpt-4.1-mini",
  "availableModels": [
    "gpt-4.1-mini",
    "gpt-4.1"
  ],
  "config": {
    "timeoutMs": 120000,
    "retryCount": 2
  },
  "status": "ACTIVE"
}
```

#### Field rules

```text
1. Omitted fields keep current value.
2. `scope`, `workspaceId`, and `ownerUserId` cannot be changed after creation.
3. If `secretId` changes, new secret must be scope-compatible.
4. `config` must be sanitized and must not include raw secret values.
5. `status` can be ACTIVE, DISABLED, or ERROR.
```

#### Response - 200 OK

Returns `LlmProviderResponse`.

#### Error codes

```text
AUTHENTICATION_REQUIRED
USER_DISABLED
PROVIDER_NOT_FOUND
SECRET_NOT_FOUND
PERMISSION_DENIED
VALIDATION_ERROR
CONFLICT
```

---

### 7.5. Delete LLM Provider

```http
DELETE /api/v1/llm-providers/{providerId}
```

#### Purpose

Soft delete a provider.

#### Permission

```text
PERSONAL_PROVIDER:
  owner user only.

WORKSPACE_PROVIDER:
  OWNER / ADMIN of the workspace.
```

#### Response - 204 No Content

No response body.

#### Rules

```text
1. Soft delete by setting deleted_at.
2. Deleted provider must not appear in normal list endpoints.
3. Deleted provider must not be selectable for new evaluator/run setup.
4. If evaluator versions or historical test runs reference this provider, keep historical data intact.
5. If active draft/run setup references this provider, backend should reject or require user to choose another provider.
```

#### Error codes

```text
AUTHENTICATION_REQUIRED
USER_DISABLED
PROVIDER_NOT_FOUND
PERMISSION_DENIED
CONFLICT
```

---

### 7.6. Test LLM Provider Connection

```http
POST /api/v1/llm-providers/{providerId}/test-connection
```

#### Purpose

Verify that the provider config and secret can call the target provider successfully.

This endpoint is used before selecting provider for LLM Judge or baseline comparison.

#### Permission

```text
PERSONAL_PROVIDER:
  owner user only.

WORKSPACE_PROVIDER:
  OWNER / ADMIN can test during management.
  MEMBER can test only if using the provider in evaluator/run setup.
  VIEWER cannot test provider.
```

#### Request body

```json
{
  "model": "gpt-4.1-mini",
  "testPrompt": "Reply with OK only.",
  "timeoutMs": 30000
}
```

Field rules:

| Field | Rule |
| ----- | ---- |
| `model` | Optional. If omitted, use provider `defaultModel`. |
| `testPrompt` | Optional. Backend may use a safe default prompt. |
| `timeoutMs` | Optional. Must not exceed backend configured max. |

#### Response - 200 OK

```json
{
  "providerId": "9c7a1c71-6985-4f89-b19e-d3ef16d19999",
  "testLogId": 8841,
  "status": "SUCCESS",
  "model": "gpt-4.1-mini",
  "latencyMs": 1320,
  "responsePreview": "OK",
  "tokenUsage": {
    "inputTokens": 8,
    "outputTokens": 1,
    "totalTokens": 9
  },
  "testedAt": "2026-07-02T10:30:00+07:00"
}
```

Failure response can still be `200 OK` if the endpoint call was handled and the provider test failed as a business result:

```json
{
  "providerId": "9c7a1c71-6985-4f89-b19e-d3ef16d19999",
  "testLogId": 8842,
  "status": "FAILED",
  "model": "gpt-4.1-mini",
  "latencyMs": 900,
  "errorCode": "PROVIDER_AUTH_FAILED",
  "errorMessage": "Provider rejected the request. Check secret or provider configuration.",
  "testedAt": "2026-07-02T10:30:00+07:00"
}
```

Use RFC 9457 only when VFQC backend cannot process the request itself, such as invalid provider id, permission error, malformed request, or backend dependency failure.

#### State changes

```text
1. Resolve secret runtime only.
2. Call provider test request.
3. Mask request/response/error details.
4. Persist sanitized provider test log in llm_provider_test_logs.
5. Optionally update llm_providers.status:
   - SUCCESS -> ACTIVE
   - auth/config failure -> ERROR
6. Do not persist raw prompt/response unless explicitly required and masked.
```

#### Error codes

```text
AUTHENTICATION_REQUIRED
USER_DISABLED
PROVIDER_NOT_FOUND
SECRET_NOT_FOUND
PERMISSION_DENIED
VALIDATION_ERROR
PROVIDER_TEST_FAILED
DEPENDENCY_UNAVAILABLE
```

---


### 7.7. List Provider Test History

```http
GET /api/v1/llm-providers/{providerId}/test-history
```

#### Purpose

Return sanitized provider connection test attempts for troubleshooting provider/key/model setup.

This endpoint is useful when QC/admins need to answer:

```text
1. Did the provider test fail because of auth, model name, timeout or provider outage?
2. When was the provider last tested successfully?
3. Which user tested it?
4. Is the provider currently safe to select for LLM Judge or baseline comparison?
```

#### Permission

Same as provider detail, but limited to users allowed to view/manage that provider metadata.

```text
PERSONAL_PROVIDER:
  owner user only.

WORKSPACE_PROVIDER:
  OWNER / ADMIN can view full sanitized test history.
  MEMBER can view sanitized history when provider is visible for evaluator/run setup.
  VIEWER cannot view provider test history in MVP.
```

#### Query parameters

| Name | Type | Required | Description |
| ---- | ---- | :------: | ----------- |
| `status` | enum | No | SUCCESS/FAILED/ERROR |
| `limit` | integer | No | Default 20, max 100 |
| `cursor` | string | No | Opaque cursor |

#### Response - 200 OK

```json
{
  "items": [
    {
      "id": 8842,
      "providerId": "9c7a1c71-6985-4f89-b19e-d3ef16d19999",
      "status": "FAILED",
      "model": "gpt-4.1-mini",
      "latencyMs": 900,
      "responsePreview": null,
      "errorCode": "PROVIDER_AUTH_FAILED",
      "errorMessage": "Provider rejected the request. Check secret or provider configuration.",
      "tokenUsage": null,
      "testedBy": {
        "id": "a38f0878-1ebe-49db-abf2-f6a372b6c246",
        "name": "QC Member"
      },
      "testedAt": "2026-07-02T10:30:00+07:00"
    }
  ],
  "page": {
    "limit": 20,
    "nextCursor": null,
    "hasMore": false
  }
}
```

#### Security rules

```text
1. Never return plaintext secret, Authorization header, API key, token, cookie or password.
2. request_json_sanitized may be omitted from list response; if added later, it must be masked.
3. responsePreview must be short and sanitized.
4. Error messages must be normalized when provider raw errors include sensitive data.
```

#### Error codes

```text
AUTHENTICATION_REQUIRED
USER_DISABLED
PROVIDER_NOT_FOUND
PERMISSION_DENIED
VALIDATION_ERROR
```

---

### 7.8. List Provider Models

```http
GET /api/v1/llm-providers/{providerId}/models
```

#### Purpose

Return models currently known/configured for this provider.

This endpoint does not have to call the external provider in MVP. It can return `available_models_json` stored in DB.

#### Permission

Same as get provider detail.

#### Response - 200 OK

```json
{
  "providerId": "9c7a1c71-6985-4f89-b19e-d3ef16d19999",
  "defaultModel": "gpt-4.1-mini",
  "models": [
    {
      "id": "gpt-4.1-mini",
      "label": "gpt-4.1-mini",
      "status": "AVAILABLE"
    },
    {
      "id": "gpt-4.1",
      "label": "gpt-4.1",
      "status": "AVAILABLE"
    }
  ]
}
```

#### Error codes

```text
AUTHENTICATION_REQUIRED
USER_DISABLED
PROVIDER_NOT_FOUND
PERMISSION_DENIED
```

---

## 8. Endpoint Permission Summary

### 8.1. Secret APIs

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

### 8.2. Provider APIs

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
| `GET /api/v1/llm-providers/{providerId}/test-history` | Provider metadata/history access; not VIEWER in MVP |
| `GET /api/v1/llm-providers/{providerId}/models` | Provider metadata access |

---

## 9. Validation Rules

### 9.1. Secret Validation

```text
1. name is required and trimmed.
2. secretValue is required on create and rotate.
3. secretValue must not be empty.
4. secretValue must not be logged.
5. PERSONAL secret must not contain workspaceId.
6. WORKSPACE secret must contain workspaceId.
7. Secret name must be unique within owner/scope or workspace/scope excluding deleted rows.
```

### 9.2. Provider Validation

```text
1. name is required and trimmed.
2. providerType is required.
3. defaultModel is required for MVP.
4. secretId is required unless provider type explicitly supports anonymous calls.
5. Provider and secret scopes must be compatible.
6. baseUrl is required for OPENAI_COMPATIBLE and CUSTOM_HTTP.
7. baseUrl must be HTTPS unless local/dev profile explicitly allows HTTP.
8. availableModels should include defaultModel if provided.
9. config must not contain secret-like keys or values.
10. status must be ACTIVE, DISABLED, or ERROR.
```

### 9.3. Secret-like Field Detection

Provider `config` and other API payloads must reject or sanitize keys such as:

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

If detected in provider config:

```text
1. Reject request with VALIDATION_ERROR, or
2. Move the value into Secret entity if the endpoint explicitly supports secret extraction.
```

For this API contract, provider create/update should reject secret-like config values and require `secretId`.

---

## 10. Common Error Examples

### 10.1. Attempt to Read Plaintext Secret

No endpoint should exist for this operation.

If an internal or accidental endpoint receives such a request, return:

```http
HTTP/1.1 403 Forbidden
Content-Type: application/problem+json
```

```json
{
  "type": "https://vfqc.vinfast.vn/problems/secret-plaintext-read-denied",
  "title": "Plaintext secret access is denied",
  "status": 403,
  "detail": "Plaintext secrets cannot be read after creation.",
  "instance": "/api/v1/secrets/3e8d1de2-2a26-4d9a-a0dd-9c6ce056d9ea/value",
  "code": "SECRET_PLAINTEXT_READ_DENIED",
  "traceId": "01J0Z7Y4Y4F9E1S2Y9W8Q7P6A5"
}
```

---

### 10.2. Secret Scope Mismatch

```http
HTTP/1.1 400 Bad Request
Content-Type: application/problem+json
```

```json
{
  "type": "https://vfqc.vinfast.vn/problems/validation-error",
  "title": "Validation failed",
  "status": 400,
  "detail": "Provider secret scope is not compatible with provider scope.",
  "instance": "/api/v1/llm-providers",
  "code": "VALIDATION_ERROR",
  "traceId": "01J0Z7Y4Y4F9E1S2Y9W8Q7P6A5",
  "errors": [
    {
      "field": "secretId",
      "code": "PROVIDER_SECRET_SCOPE_MISMATCH",
      "message": "WORKSPACE_PROVIDER must use a WORKSPACE secret from the same workspace."
    }
  ]
}
```

---

### 10.3. Provider Config Contains Secret-like Value

```http
HTTP/1.1 400 Bad Request
Content-Type: application/problem+json
```

```json
{
  "type": "https://vfqc.vinfast.vn/problems/validation-error",
  "title": "Validation failed",
  "status": 400,
  "detail": "Provider config contains secret-like fields.",
  "instance": "/api/v1/llm-providers",
  "code": "VALIDATION_ERROR",
  "traceId": "01J0Z7Y4Y4F9E1S2Y9W8Q7P6A5",
  "errors": [
    {
      "field": "config.headers.Authorization",
      "code": "SECRET_FIELD_NOT_ALLOWED",
      "message": "Do not put Authorization values in provider config. Use secretId instead."
    }
  ]
}
```

---

### 10.4. Provider Test Failed

Provider test can return a handled failed result:

```http
HTTP/1.1 200 OK
Content-Type: application/json
```

```json
{
  "providerId": "9c7a1c71-6985-4f89-b19e-d3ef16d19999",
  "testLogId": 8842,
  "status": "FAILED",
  "model": "gpt-4.1-mini",
  "latencyMs": 900,
  "errorCode": "PROVIDER_AUTH_FAILED",
  "errorMessage": "Provider rejected the request. Check secret or provider configuration.",
  "testedAt": "2026-07-02T10:30:00+07:00"
}
```

Backend request errors still use RFC 9457.

---

## 11. Implementation Notes

### 11.1. Encryption vs Hashing

```text
Encryption:
  used for secrets that must be recovered at runtime to call providers.

SHA-256 hash:
  used for audit, integrity check, dedup or comparison.
  not used to recover secret values.
```

Secret value persistence rule:

```text
1. Receive plaintext secret from create/rotate request.
2. Encrypt plaintext secret using backend SecretService.
3. Store ciphertext in `secrets.encrypted_value`.
4. Clear plaintext from logs and avoid storing it in DTO/entity snapshots.
5. Return only metadata and masked preview.
```

### 11.2. Runtime Secret Resolution

```text
1. UI never receives plaintext secret.
2. Backend/Runner trusted flow may resolve secret runtime only.
3. Resolved plaintext token must be injected into provider request in memory only.
4. Resolved plaintext token must not be written to logs, Redis events, DB snapshots, or artifacts.
```

### 11.3. Provider Status Handling

```text
ACTIVE:
  Provider can be selected for new evaluator/run setup.

DISABLED:
  Provider is intentionally disabled and cannot be selected for new runs.

ERROR:
  Provider failed test or validation and should not be selected until fixed.
```

### 11.4. Audit Expectations

Backend should audit:

```text
1. Secret created.
2. Secret metadata updated.
3. Secret rotated.
4. Secret disabled/deleted.
5. Provider created.
6. Provider updated.
7. Provider tested and provider test log created.
8. Provider disabled/deleted.
```

Audit logs must not contain plaintext secret values.

---

## 12. Open Questions

```text
1. Should secret rotation create version history instead of replacing encrypted_value in place?
2. Should workspace admins be able to see personal provider metadata used in their workspace runs?
3. Should provider available models be manually configured only, or can backend sync from provider API?
4. Should deleted secrets be blocked when historical providers reference them, or should soft delete only disable runtime resolution?
```

Recommended MVP decision:

```text
1. Store sanitized provider test history in llm_provider_test_logs.
2. Rotate secret by replacing encrypted_value in place.
3. Show personal provider label in run snapshots, but not personal secret metadata to workspace admins.
4. Use manually configured availableModels first.
5. Reject deletion if active providers reference the secret; allow DISABLED status as safer first step.
```

---

## 13. Implementation Checklist

```text
1. No response DTO contains secretValue or encryptedValue.
2. Create secret encrypts secretValue before saving.
3. Rotate secret replaces encrypted_value and returns metadata only.
4. Secret list/detail returns maskedPreview only.
5. Workspace secret create/list/update requires OWNER or ADMIN.
6. Personal secret access is owner-only.
7. Provider create validates secret scope compatibility.
8. Provider config rejects secret-like fields.
9. Provider test resolves secret runtime only.
10. Provider test masks provider errors.
11. Disabled/deleted providers cannot be selected for new run setup.
12. Deleted secrets cannot be resolved at runtime.
13. RFC 9457 errors are used for API-level failures.
14. Provider test handled failures return sanitized test result.
15. Audit logs never include plaintext secrets.
```
