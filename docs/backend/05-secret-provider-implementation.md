# VinFast QC Copilot - Secret & Provider Implementation

Version: `v0.2`  
Status: Spring Boot 4.0.7-first Implementation Spec  
Applies to: `apps/api`

---

## 1. Purpose

This document explains Secret, LLM Provider, provider client and provider test history implementation.

---

## 2. Design Principles

1. No user-facing API returns plaintext secret values after create/rotate.
2. Secret values are encrypted before persistence.
3. Providers reference secrets by `secretId`.
4. Provider config JSON is sanitized before persistence and logging.
5. Workspace providers may use only workspace secrets in the same workspace.
6. Personal providers may use only personal secrets owned by the current user.
7. Provider test may decrypt secrets only in memory and only for the call duration.
8. Provider test logs store sanitized metadata only.
9. Spring AI may be used only inside provider adapter implementations.
10. Domain entities and DTOs must not depend on Spring AI classes.

---

## 3. Package Layout

```text
secret
  controller/SecretController.java
  dto/request/CreateSecretRequest.java
  dto/request/UpdateSecretRequest.java
  dto/request/RotateSecretRequest.java
  dto/response/SecretResponse.java
  dto/response/SecretSummaryResponse.java
  entity/Secret.java
  enums/SecretScope.java
  enums/SecretStatus.java
  repository/SecretRepository.java
  mapper/SecretMapper.java
  service/SecretService.java
  service/SecretLookupService.java
  service/SecretEncryptionService.java
  service/RuntimeSecretResolver.java
  service/impl/SecretServiceImpl.java
  service/impl/SecretLookupServiceImpl.java
  service/impl/JceSecretEncryptionService.java
  service/impl/RuntimeSecretResolverImpl.java

provider
  controller/LlmProviderController.java
  controller/LlmProviderTestHistoryController.java
  dto/request/CreateLlmProviderRequest.java
  dto/request/UpdateLlmProviderRequest.java
  dto/request/TestLlmProviderRequest.java
  dto/response/LlmProviderResponse.java
  dto/response/LlmProviderSummaryResponse.java
  dto/response/LlmProviderModelResponse.java
  dto/response/LlmProviderTestResponse.java
  dto/response/LlmProviderTestLogResponse.java
  entity/LlmProvider.java
  entity/LlmProviderTestLog.java
  enums/ProviderScope.java
  enums/ProviderType.java
  enums/ProviderStatus.java
  enums/ProviderTestStatus.java
  repository/LlmProviderRepository.java
  repository/LlmProviderTestLogRepository.java
  mapper/LlmProviderMapper.java
  mapper/LlmProviderTestLogMapper.java
  client/LlmProviderClient.java
  client/OpenAiCompatibleProviderClient.java
  client/GoogleGenAiProviderClient.java
  client/CustomHttpProviderClient.java
  service/LlmProviderService.java
  service/LlmProviderLookupService.java
  service/LlmProviderTestService.java
  service/LlmProviderClientFactory.java
  service/LlmProviderModelService.java
  validator/ProviderConfigValidator.java
  assembler/LlmProviderResponseAssembler.java
```

---

## 4. Secret Entity Rules

Fields:

```text
id
scope
ownerUserId
workspaceId
name
nameLookup
encryptedValue
status
createdBy
createdAt
updatedAt
deletedAt
```

Rules:

1. `encryptedValue` is never mapped to response DTOs.
2. Plaintext exists only in request memory and encryption/decryption call memory.
3. Use soft delete.
4. Disabled/deleted secrets cannot be selected for new provider/target config.
5. `SecretResponse` may include masked preview, never value.

---

## 5. Encryption Boundary

Use `SecretEncryptionService` as a small boundary because encryption may later move to KMS.

Allowed implementation for MVP:

1. JCA/JCE AES-GCM using key from environment/secret manager.
2. Separate IV/nonce per encryption.
3. Store encoded payload with version metadata.

Rules:

1. Do not log plaintext or encrypted value.
2. Do not expose encryption internals through public API.
3. Do not decrypt in list/detail endpoints.
4. Decrypt only in provider test, target test and runner runtime secret resolution.

---

## 6. Provider Entity Rules

Fields:

```text
id
scope
ownerUserId
workspaceId
name
nameLookup
providerType
secretId
baseUrl
defaultModel
availableModelsJson
configJson
status
createdBy
createdAt
updatedAt
deletedAt
```

Rules:

1. `configJson` is sanitized before save.
2. `availableModelsJson` contains non-secret model metadata only.
3. Provider response embeds `SecretSummaryResponse` through assembler.
4. Provider status is operational state, not a replacement for permission checks.
5. Soft-deleted providers cannot be selected for evaluators/runs.

---

## 7. Provider Types

Recommended enum:

```text
OPENAI_COMPATIBLE
GOOGLE_GENAI
CUSTOM_HTTP
```

`GEMINI` may be kept as an alias only if API contract already uses it; internally prefer `GOOGLE_GENAI` because Spring AI dependency is `spring-ai-starter-model-google-genai`.

---

## 8. Spring AI Boundary

Spring AI is allowed only in adapter/client code.

Rules:

1. `provider.client.GoogleGenAiProviderClient` may use Spring AI Google GenAI client APIs.
2. `provider.entity.LlmProvider` must not import Spring AI classes.
3. `provider.dto` must not expose Spring AI request/response types.
4. `testrun` stores sanitized domain snapshots, not Spring AI objects.
5. Runner config remains product-owned and language-neutral.

Google GenAI auth modes:

1. API key for Gemini Developer API.
2. Vertex AI credentials/project/location if enabled later.

Both must be modeled as Secret references, not plaintext config fields.

---

## 9. Provider Test Flow

Flow:

1. Check provider permission.
2. Load provider and referenced secret.
3. Decrypt secret in memory.
4. Build provider client request.
5. Execute call through provider client.
6. Sanitize request, response preview and errors.
7. Save `LlmProviderTestLog`.
8. Update provider status if policy requires.
9. Return sanitized test result.

Use Spring `RestClient` for HTTP clients unless Spring AI adapter provides the client.

Do not execute provider test inside a long DB transaction. Persist log after call with sanitized data.

---

## 10. Provider Test Log Rules

Fields:

```text
id
llmProviderId
status
model
requestJsonSanitized
responsePreview
errorCode
errorMessage
latencyMs
tokenUsageJson
testedBy
testedAt
createdAt
```

Rules:

1. One row per test attempt.
2. Store sanitized request metadata only.
3. Store short masked response preview only.
4. Error message must be sanitized.
5. Listing is paginated.

---

## 11. Spring Usage

Use:

1. `RestClient` for custom/OpenAI-compatible HTTP calls.
2. `@ConfigurationProperties` for provider/client timeouts.
3. `Clock` for timestamps.
4. `@Transactional` for DB mutations.
5. `ProblemDetail` for errors.
6. MapStruct + assembler.

Do not:

1. Create custom HTTP client stack.
2. Store raw API keys in provider config.
3. Expose decrypted secrets in DTOs.
4. Log provider request headers before masking.

---

## 12. Tests

Required tests:

1. Secret create stores encrypted value only.
2. Secret response never contains plaintext/encrypted value.
3. Provider cannot reference secret from another workspace/user.
4. Provider config sanitizes sensitive keys.
5. Provider test creates sanitized log on success and failure.
6. Deleted provider cannot be selected for run.
7. Runtime secret resolver does not log values.
