# VinFast QC Copilot - Secret & Provider Implementation

Version: `v0.1`  
Status: Backend Implementation Spec Draft  
Applies to: `apps/api` Spring Boot backend  
Aligned with: PRD v0.7, ERD v0.5, API Contract `04-secret-provider-api.md`

---

## 1. Purpose

This document explains how to implement Secret, LLM Provider and Provider Test History features in the Spring Boot backend.

It is written for AI coding agents and backend developers so generated code follows the same security rules, package structure, transaction boundaries and mapping style across the project.

This file covers:

```text
1. Personal and workspace secrets.
2. Secret create, metadata update, rotation and soft delete.
3. Personal and workspace LLM providers.
4. Provider create, update, soft delete and model listing.
5. Provider connection testing.
6. Provider test history persistence.
7. Secret encryption/decryption boundaries.
8. Sensitive data masking rules.
9. Permission checks for secret/provider management.
10. DTO, mapper, transaction, logging and controller rules.
```

This file does not redefine API request/response contracts. API shapes are defined in `docs/api/04-secret-provider-api.md`.

---

## 2. Design Principles

Secret and Provider code must follow these principles:

```text
1. No API may return plaintext secret values after create/rotate.
2. Secret values are encrypted before persistence.
3. `secrets.encrypted_value` is internal persistence data only.
4. Providers reference secrets by `secretId`; providers do not store API keys directly.
5. Provider config JSON must be sanitized before persistence and before logging.
6. Workspace providers may use only workspace secrets from the same workspace.
7. Personal providers may use only personal secrets owned by the current user.
8. Provider connection tests may decrypt secrets only in memory and only for the duration of the call.
9. Provider test logs store sanitized request/response/error metadata only.
10. Disabled/deleted secrets and providers cannot be selected for new runs.
11. Historical runs rely on locked sanitized snapshots, not current mutable provider config.
```

Security and localization rules:

```text
1. Never log plaintext secret, encrypted secret, Authorization header, API key, token, cookie or password.
2. Never expose encryptedValue or secretValue fields in response records.
3. Backend code/log/error codes should use English.
4. Vietnamese user-facing copy belongs in frontend i18n or a message catalog, not inline service logic.
5. Masking must be applied before saving provider test logs.
```

---

## 3. Package Layout

Recommended package layout:

```text
vn.vinfast.vfqc.api
  secret
    controller
      SecretController.java
    dto
      request
        CreateSecretRequest.java
        UpdateSecretRequest.java
        RotateSecretRequest.java
      response
        SecretResponse.java
        SecretSummaryResponse.java
    entity
      Secret.java
    enums
      SecretScope.java
      SecretStatus.java
    mapper
      SecretMapper.java
    repository
      SecretRepository.java
    service
      SecretService.java
      SecretLookupService.java
      SecretEncryptionService.java
      SecretPreviewService.java
      RuntimeSecretResolver.java
      impl
        SecretServiceImpl.java
        SecretLookupServiceImpl.java
        SecretEncryptionServiceImpl.java
        SecretPreviewServiceImpl.java
        RuntimeSecretResolverImpl.java

  provider
    controller
      LlmProviderController.java
      LlmProviderTestHistoryController.java
    dto
      request
        CreateLlmProviderRequest.java
        UpdateLlmProviderRequest.java
        TestLlmProviderRequest.java
      response
        LlmProviderResponse.java
        LlmProviderSummaryResponse.java
        LlmProviderModelResponse.java
        LlmProviderTestResponse.java
        LlmProviderTestLogResponse.java
    entity
      LlmProvider.java
      LlmProviderTestLog.java
    enums
      ProviderScope.java
      ProviderType.java
      ProviderStatus.java
      ProviderTestStatus.java
    mapper
      LlmProviderMapper.java
      LlmProviderTestLogMapper.java
    assembler
      LlmProviderResponseAssembler.java
    validator
      ProviderConfigValidator.java
      ProviderScopeValidator.java
    client
      LlmProviderClient.java
      OpenAiCompatibleProviderClient.java
      GeminiProviderClient.java
      ClaudeProviderClient.java
      CustomHttpProviderClient.java
    repository
      LlmProviderRepository.java
      LlmProviderTestLogRepository.java
    service
      LlmProviderService.java
      LlmProviderLookupService.java
      LlmProviderTestService.java
      LlmProviderClientFactory.java
      LlmProviderModelService.java
      impl
        LlmProviderServiceImpl.java
        LlmProviderLookupServiceImpl.java
        LlmProviderTestServiceImpl.java
        LlmProviderClientFactoryImpl.java
        LlmProviderModelServiceImpl.java

  common
    masking
      VfqcSensitiveDataMasker.java
      MaskingPolicy.java
    permission
      WorkspacePermissionResolver.java
    naming
      ResourceNameNormalizer.java
```

Rules:

```text
1. Secret and provider modules should be separate because secret handling has stricter security boundaries.
2. Provider code may reference SecretLookupService and RuntimeSecretResolver.
3. Secret API services should never expose decrypted values to provider API response DTOs.
4. RuntimeSecretResolver is allowed to decrypt only for internal runner/provider-test execution paths.
5. MapStruct mappers map entities to responses; services do not define manual toResponse/toEntity helpers.
```

---

## 4. Entity Implementation

### 4.1. Secret

ERD table:

```text
secrets
```

Recommended entity fields:

```java
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "secrets")
public class Secret {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "scope", nullable = false)
  private SecretScope scope;

  @Column(name = "owner_user_id")
  private UUID ownerUserId;

  @Column(name = "workspace_id")
  private UUID workspaceId;

  @Column(name = "name", nullable = false, length = 120)
  private String name;

  @Column(name = "encrypted_value", nullable = false, columnDefinition = "text")
  private String encryptedValue;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private SecretStatus status;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;
}
```

Rules:

```text
1. `encryptedValue` must never be mapped to response DTOs.
2. `encryptedValue` must never appear in logs.
3. Plaintext secret should exist only in request record, encryption service memory and runtime client call memory.
4. Use `SecretStatus.DELETED` and/or `deletedAt` consistently for soft delete.
5. Do not expose deleted secrets from normal list endpoints.
```

### 4.2. LLM Provider

ERD table:

```text
llm_providers
```

Recommended entity fields:

```java
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "llm_providers")
public class LlmProvider {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "scope", nullable = false)
  private ProviderScope scope;

  @Column(name = "owner_user_id")
  private UUID ownerUserId;

  @Column(name = "workspace_id")
  private UUID workspaceId;

  @Column(name = "name", nullable = false, length = 120)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "provider_type", nullable = false)
  private ProviderType providerType;

  @Column(name = "secret_id", nullable = false)
  private UUID secretId;

  @Column(name = "base_url", length = 500)
  private String baseUrl;

  @Column(name = "default_model", length = 200)
  private String defaultModel;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "available_models_json", columnDefinition = "jsonb")
  private JsonNode availableModelsJson;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "config_json", columnDefinition = "jsonb")
  private JsonNode configJson;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private ProviderStatus status;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;
}
```

Rules:

```text
1. `secretId` references a Secret. The response mapper should embed a `SecretSummaryResponse` fetched by service/assembler.
2. `configJson` must be sanitized before persistence.
3. `availableModelsJson` may contain only non-secret model metadata.
4. Provider status is operational state. It should not be used as the only permission check.
5. Soft-deleted providers cannot be selected for new evaluator/run setup.
```

### 4.3. LLM Provider Test Log

ERD table:

```text
llm_provider_test_logs
```

Recommended entity fields:

```java
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "llm_provider_test_logs")
public class LlmProviderTestLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false, updatable = false)
  private Long id;

  @Column(name = "llm_provider_id", nullable = false)
  private UUID llmProviderId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private ProviderTestStatus status;

  @Column(name = "model", length = 200)
  private String model;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "request_json_sanitized", columnDefinition = "jsonb")
  private JsonNode requestJsonSanitized;

  @Column(name = "response_preview", columnDefinition = "text")
  private String responsePreview;

  @Column(name = "error_code", length = 120)
  private String errorCode;

  @Column(name = "error_message", columnDefinition = "text")
  private String errorMessage;

  @Column(name = "latency_ms")
  private Integer latencyMs;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "token_usage_json", columnDefinition = "jsonb")
  private JsonNode tokenUsageJson;

  @Column(name = "tested_by", nullable = false)
  private UUID testedBy;

  @Column(name = "tested_at", nullable = false)
  private OffsetDateTime testedAt;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;
}
```

Rules:

```text
1. Every provider test attempt should create one row.
2. Persist only sanitized request metadata and short masked response preview.
3. Do not store full provider raw response unless a future artifact policy explicitly allows it.
4. Test logs are for troubleshooting provider setup, not for audit evidence of TestRuns.
5. Test log listing should be paginated.
```

---

## 5. Enum Implementation

Recommended enums:

```java
public enum SecretScope {
  PERSONAL,
  WORKSPACE
}

public enum SecretStatus {
  ACTIVE,
  DISABLED,
  DELETED
}

public enum ProviderScope {
  PERSONAL_PROVIDER,
  WORKSPACE_PROVIDER
}

public enum ProviderType {
  OPENAI_COMPATIBLE,
  GEMINI,
  CLAUDE,
  CUSTOM_HTTP
}

public enum ProviderStatus {
  ACTIVE,
  DISABLED,
  ERROR
}

public enum ProviderTestStatus {
  SUCCESS,
  FAILED,
  ERROR
}
```

Rules:

```text
1. Use enum names exactly as API/ERD values.
2. Store enums as strings with `EnumType.STRING`.
3. Do not use ordinal enum persistence.
4. Unknown enum input should produce VALIDATION_ERROR, not a server error.
```

---

## 6. Repository Implementation

### 6.1. SecretRepository

Recommended methods:

```java
public interface SecretRepository extends JpaRepository<Secret, UUID> {

  Optional<Secret> findByIdAndDeletedAtIsNull(UUID id);

  boolean existsByScopeAndOwnerUserIdAndNameIgnoreCaseAndDeletedAtIsNull(
      SecretScope scope, UUID ownerUserId, String name);

  boolean existsByScopeAndWorkspaceIdAndNameIgnoreCaseAndDeletedAtIsNull(
      SecretScope scope, UUID workspaceId, String name);

  Page<Secret> findByOwnerUserIdAndDeletedAtIsNull(UUID ownerUserId, Pageable pageable);

  Page<Secret> findByWorkspaceIdAndDeletedAtIsNull(UUID workspaceId, Pageable pageable);
}
```

Rules:

```text
1. Use explicit deletedAt filters in normal queries.
2. Use case-insensitive duplicate checks for resource names.
3. For complex filters, use Specification/QueryDSL/custom repository rather than huge derived method names.
4. Do not create repository methods that return plaintext/decrypted secret values.
```

### 6.2. LlmProviderRepository

Recommended methods:

```java
public interface LlmProviderRepository extends JpaRepository<LlmProvider, UUID> {

  Optional<LlmProvider> findByIdAndDeletedAtIsNull(UUID id);

  Page<LlmProvider> findByWorkspaceIdAndDeletedAtIsNull(UUID workspaceId, Pageable pageable);

  Page<LlmProvider> findByOwnerUserIdAndDeletedAtIsNull(UUID ownerUserId, Pageable pageable);

  boolean existsByScopeAndWorkspaceIdAndNameIgnoreCaseAndDeletedAtIsNull(
      ProviderScope scope, UUID workspaceId, String name);

  boolean existsByScopeAndOwnerUserIdAndNameIgnoreCaseAndDeletedAtIsNull(
      ProviderScope scope, UUID ownerUserId, String name);
}
```

### 6.3. LlmProviderTestLogRepository

Recommended methods:

```java
public interface LlmProviderTestLogRepository extends JpaRepository<LlmProviderTestLog, Long> {

  Page<LlmProviderTestLog> findByLlmProviderIdOrderByTestedAtDesc(UUID providerId, Pageable pageable);
}
```

Rules:

```text
1. Test history query must first verify caller can access the provider.
2. Test history should be ordered newest-first by testedAt.
3. Do not fetch unbounded logs.
```

---

## 7. DTO Implementation

### 7.1. Request records

Use Java records for request DTOs.

Example secret create request:

```java
@Schema(name = "CreateSecretRequest")
public record CreateSecretRequest(
    @NotNull
    @Schema(example = "WORKSPACE", description = "Secret scope: PERSONAL or WORKSPACE")
    SecretScope scope,

    @Schema(example = "6d4628b0-56f0-4e48-b1a1-fba3e0cbf54a", description = "Required when scope is WORKSPACE")
    UUID workspaceId,

    @NotBlank
    @Size(max = 120)
    @Schema(example = "OpenAI Workspace Key", description = "Human-readable secret name")
    String name,

    @NotBlank
    @Size(max = 4096)
    @Schema(example = "sk-...", description = "Plaintext secret value. Accepted only on create/rotate and never returned.")
    String secretValue) {}
```

Example provider create request:

```java
@Schema(name = "CreateLlmProviderRequest")
public record CreateLlmProviderRequest(
    @NotNull
    @Schema(example = "WORKSPACE_PROVIDER")
    ProviderScope scope,

    @Schema(example = "6d4628b0-56f0-4e48-b1a1-fba3e0cbf54a")
    UUID workspaceId,

    @NotBlank
    @Size(max = 120)
    @Schema(example = "GPT-4.1 Judge")
    String name,

    @NotNull
    @Schema(example = "OPENAI_COMPATIBLE")
    ProviderType providerType,

    @NotNull
    @Schema(example = "3e8d1de2-2a26-4d9a-a0dd-9c6ce056d9ea")
    UUID secretId,

    @Size(max = 500)
    @Schema(example = "https://api.openai.com/v1")
    String baseUrl,

    @NotBlank
    @Size(max = 200)
    @Schema(example = "gpt-4.1-mini")
    String defaultModel,

    @Schema(description = "Sanitized provider-specific config")
    JsonNode config) {}
```

Rules:

```text
1. Request records use Jakarta Bean Validation annotations.
2. Fields should include Swagger `@Schema` examples.
3. Do not include decrypted secret values in response records.
4. Do not use Lombok for records.
5. Do not use entity classes as request/response bodies.
```

### 7.2. Response records

Example secret response:

```java
@Schema(name = "SecretResponse")
public record SecretResponse(
    UUID id,
    SecretScope scope,
    UUID workspaceId,
    UUID ownerUserId,
    String name,
    SecretStatus status,
    boolean hasValue,
    String maskedPreview,
    UUID createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

Example provider response:

```java
@Schema(name = "LlmProviderResponse")
public record LlmProviderResponse(
    UUID id,
    ProviderScope scope,
    UUID workspaceId,
    UUID ownerUserId,
    String name,
    ProviderType providerType,
    SecretSummaryResponse secret,
    String baseUrl,
    String defaultModel,
    JsonNode availableModels,
    JsonNode config,
    ProviderStatus status,
    UUID createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

Example provider test log response:

```java
@Schema(name = "LlmProviderTestLogResponse")
public record LlmProviderTestLogResponse(
    Long id,
    UUID providerId,
    ProviderTestStatus status,
    String model,
    String responsePreview,
    String errorCode,
    String errorMessage,
    Integer latencyMs,
    JsonNode tokenUsage,
    UUID testedBy,
    OffsetDateTime testedAt,
    OffsetDateTime createdAt) {}
```

Rules:

```text
1. `SecretResponse` and `SecretSummaryResponse` must not include `encryptedValue` or `secretValue`.
2. `LlmProviderTestLogResponse` must not include request_json_sanitized unless API contract explicitly exposes it.
3. If request metadata is exposed later, it must be masked and filtered.
```

---

## 8. Mapper Implementation

### 8.1. SecretMapper

Use MapStruct:

```java
@Mapper(componentModel = "spring")
public interface SecretMapper {

  @Mapping(target = "hasValue", expression = "java(secret.getEncryptedValue() != null)")
  @Mapping(target = "maskedPreview", source = "maskedPreview")
  SecretResponse toResponse(Secret secret, String maskedPreview);

  @Mapping(target = "maskedPreview", source = "maskedPreview")
  SecretSummaryResponse toSummary(Secret secret, String maskedPreview);
}
```

Rules:

```text
1. Do not map encryptedValue.
2. Do not compute maskedPreview by decrypting the value in the mapper.
3. The service should pass a safe masked preview from SecretPreviewService.
```

### 8.2. LlmProviderMapper

Use MapStruct for simple fields and an assembler/service for embedded secret summary:

```java
@Mapper(componentModel = "spring")
public interface LlmProviderMapper {

  @Mapping(target = "secret", ignore = true)
  @Mapping(target = "availableModels", source = "availableModelsJson")
  @Mapping(target = "config", source = "configJson")
  LlmProviderResponse toResponseWithoutSecret(LlmProvider provider);

  @Mapping(target = "availableModels", source = "availableModelsJson")
  LlmProviderSummaryResponse toSummary(LlmProvider provider);
}
```

Provider response assembly:

```text
1. Service loads provider.
2. Service loads secret metadata.
3. SecretMapper creates SecretSummaryResponse.
4. LlmProviderMapper maps provider base fields.
5. ProviderResponseAssembler combines the two if needed.
```

Rules:

```text
1. Do not write manual mapping logic inside controllers.
2. Do not write `toResponse()` helper methods inside service implementation.
3. Use an assembler only for aggregate DTOs that combine multiple entities.
```

---

## 9. Secret Services

### 9.1. SecretService interface

Example interface:

```java
public interface SecretService {

  /**
   * Lists secret metadata visible to the current user.
   *
   * @param query filtering and pagination parameters
   * @param currentUser authenticated user performing the read
   * @return paged secret metadata without plaintext or encrypted values
   */
  PageResponse<SecretResponse> listSecrets(ListSecretsQuery query, CurrentUser currentUser);

  /**
   * Creates a personal or workspace secret by encrypting the plaintext secret value.
   *
   * @param request secret creation payload containing scope, name and plaintext value
   * @param currentUser authenticated user creating the secret
   * @return created secret metadata with a safe masked preview
   * @throws ForbiddenException if the user cannot create a secret in the requested scope
   * @throws DuplicateResourceException if another active secret has the same normalized name in scope
   */
  SecretResponse createSecret(CreateSecretRequest request, CurrentUser currentUser);

  /**
   * Updates non-sensitive secret metadata such as display name or status.
   *
   * @param secretId secret identifier
   * @param request metadata update payload
   * @param currentUser authenticated user performing the update
   * @return updated secret metadata without plaintext or encrypted values
   */
  SecretResponse updateSecret(UUID secretId, UpdateSecretRequest request, CurrentUser currentUser);

  /**
   * Replaces a secret value by encrypting the new plaintext value and updating secret metadata.
   *
   * @param secretId secret identifier
   * @param request rotation payload containing the new plaintext secret value
   * @param currentUser authenticated user performing rotation
   * @return updated secret metadata with a safe masked preview
   */
  SecretResponse rotateSecret(UUID secretId, RotateSecretRequest request, CurrentUser currentUser);

  /**
   * Soft deletes a secret when it is safe to do so.
   *
   * @param secretId secret identifier
   * @param currentUser authenticated user performing delete
   */
  void deleteSecret(UUID secretId, CurrentUser currentUser);
}
```

Rules:

```text
1. Interface methods must have Javadoc.
2. Implementation methods should use `@Transactional` or `@Transactional(readOnly = true)`.
3. Throw domain exceptions with stable error codes.
```

### 9.2. SecretServiceImpl transaction rules

Recommended:

```java
@Slf4j
@Service
@RequiredArgsConstructor
class SecretServiceImpl implements SecretService {

  @Override
  @Transactional
  public SecretResponse createSecret(CreateSecretRequest request, CurrentUser currentUser) {
    log.info("Creating secret metadata: scope={}, workspaceId={}, requestedBy={}",
        request.scope(), request.workspaceId(), currentUser.id());

    // validate scope and permission
    // normalize name
    // encrypt plaintext value
    // persist Secret
    // return mapped response without plaintext
  }

  @Override
  @Transactional
  public SecretResponse rotateSecret(UUID secretId, RotateSecretRequest request, CurrentUser currentUser) {
    log.info("Rotating secret value: secretId={}, requestedBy={}", secretId, currentUser.id());

    // load secret
    // validate permission
    // encrypt new plaintext value
    // update encryptedValue and updatedAt
    // do not log old/new value
  }
}
```

Rules:

```text
1. Never log request.secretValue().
2. Never include secretValue in exception detail.
3. Create/rotate methods are transactional.
4. List/detail methods are read-only transactional.
5. Delete must check provider usage if API/business policy blocks deleting active in-use secrets.
```

### 9.3. SecretEncryptionService

Interface:

```java
public interface SecretEncryptionService {

  /**
   * Encrypts a plaintext secret value for persistence.
   *
   * @param plaintext plaintext secret value from create/rotate request
   * @return encrypted value safe to persist in the secrets table
   */
  String encrypt(String plaintext);

  /**
   * Decrypts a persisted encrypted secret for runtime-only execution.
   *
   * @param encryptedValue encrypted value from persistence
   * @return plaintext secret value for in-memory runtime use only
   */
  String decrypt(String encryptedValue);
}
```

Rules:

```text
1. Do not implement custom cryptography manually unless no approved library/KMS is available.
2. Prefer envelope encryption/KMS when platform infrastructure provides it.
3. Plaintext returned by decrypt must not be cached globally.
4. Decryption should be used only by provider test and runner runtime secret resolution paths.
5. Unit tests may use a fake encryption service.
```

### 9.4. RuntimeSecretResolver

RuntimeSecretResolver is internal only.

It is used by:

```text
1. Provider test execution.
2. Runner internal `/runtime-secrets` endpoint.
3. Future controlled internal integrations.
```

Interface:

```java
public interface RuntimeSecretResolver {

  /**
   * Resolves and decrypts an active secret for runtime execution.
   *
   * @param secretId secret identifier
   * @param purpose runtime purpose such as PROVIDER_TEST or TEST_RUN_EXECUTION
   * @return plaintext secret material for immediate in-memory use
   */
  RuntimeSecret resolveSecret(UUID secretId, RuntimeSecretPurpose purpose);
}
```

Rules:

```text
1. This service must not be injected into controllers.
2. This service must not return plaintext values to frontend-facing DTOs.
3. Callers must avoid logging returned RuntimeSecret.
4. Resolve only ACTIVE secrets.
5. Validate scope compatibility before runtime use.
```

---

## 10. Provider Services

### 10.1. LlmProviderService interface

Example interface:

```java
public interface LlmProviderService {

  /**
   * Lists LLM providers visible to the current user in personal or workspace scope.
   *
   * @param query filter and pagination parameters
   * @param currentUser authenticated user performing the read
   * @return paged provider metadata with safe secret summaries
   */
  PageResponse<LlmProviderResponse> listProviders(ListLlmProvidersQuery query, CurrentUser currentUser);

  /**
   * Creates a provider that references an existing active secret in a compatible scope.
   *
   * @param request provider creation payload
   * @param currentUser authenticated user creating the provider
   * @return created provider response with sanitized config and secret summary
   */
  LlmProviderResponse createProvider(CreateLlmProviderRequest request, CurrentUser currentUser);

  /**
   * Updates provider metadata, model defaults, config or status without exposing secret values.
   *
   * @param providerId provider identifier
   * @param request provider update payload
   * @param currentUser authenticated user performing the update
   * @return updated provider response
   */
  LlmProviderResponse updateProvider(UUID providerId, UpdateLlmProviderRequest request, CurrentUser currentUser);

  /**
   * Soft deletes a provider so it cannot be selected for new evaluator/run setup.
   *
   * @param providerId provider identifier
   * @param currentUser authenticated user performing delete
   */
  void deleteProvider(UUID providerId, CurrentUser currentUser);
}
```

### 10.2. Provider scope validation

Provider create/update must enforce:

```text
PERSONAL_PROVIDER:
  owner_user_id = currentUser.id
  workspace_id = null
  referenced secret must be PERSONAL and owned by currentUser.id

WORKSPACE_PROVIDER:
  workspace_id = request.workspaceId
  owner_user_id = null
  currentUser must be OWNER or ADMIN in workspace
  referenced secret must be WORKSPACE and in the same workspace
```

Forbidden examples:

```text
1. PERSONAL_PROVIDER using WORKSPACE secret.
2. WORKSPACE_PROVIDER using PERSONAL secret.
3. WORKSPACE_PROVIDER using secret from another workspace.
4. Provider using DISABLED or DELETED secret.
5. VIEWER creating or updating workspace provider.
```

### 10.3. Provider config sanitization

Before persisting provider config:

```text
1. Recursively scan JSON keys and values.
2. Mask/delete secret-like fields.
3. Reject config if it contains raw Authorization, apiKey, token, cookie, password or secret values.
4. Store only sanitized configJson.
```

Recommended helper:

```java
JsonNode sanitizedConfig = sensitiveDataMasker.maskJson(request.config());
secretLikeFieldValidator.rejectIfContainsSecretLikeData(sanitizedConfig);
```

Rules:

```text
1. Sanitization is not optional even if frontend already masks fields.
2. Raw config must not be logged.
3. If config contains obvious secret material, return VALIDATION_ERROR or SENSITIVE_DATA_NOT_ALLOWED.
```

---

## 11. Provider Test Implementation

### 11.1. Provider test flow

Provider test endpoint is synchronous in MVP.

Recommended flow:

```text
1. User calls POST /api/v1/llm-providers/{providerId}/test-connection.
2. Backend verifies provider visibility and permission.
3. Backend loads provider and referenced secret metadata.
4. Backend validates provider and secret are ACTIVE.
5. Backend resolves/decrypts secret in memory.
6. Backend builds a minimal provider test request.
7. Backend calls provider API outside a long DB transaction.
8. Backend masks request metadata, response preview and error details.
9. Backend persists one llm_provider_test_logs row.
10. Backend updates provider status to ACTIVE or ERROR when useful.
11. Backend returns test result response without plaintext secret.
```

Important transaction boundary:

```text
Do not keep a database transaction open while waiting for an external provider API call.
```

Recommended implementation pattern:

```java
@Override
public LlmProviderTestResponse testProvider(UUID providerId, TestLlmProviderRequest request, CurrentUser currentUser) {
  ProviderRuntimeConfig config = loadProviderRuntimeConfig(providerId, currentUser); // read-only tx

  Instant started = clock.instant();
  ProviderTestAttempt attempt = providerClientFactory.create(config).testConnection(request);

  return persistProviderTestAttempt(providerId, request, attempt, started, currentUser); // short write tx
}

@Transactional(readOnly = true)
ProviderRuntimeConfig loadProviderRuntimeConfig(...) { ... }

@Transactional
LlmProviderTestResponse persistProviderTestAttempt(...) { ... }
```

Rules:

```text
1. `testProvider` orchestration method may be non-transactional.
2. DB read/write helpers should be transactional.
3. External IO should not happen inside a long database transaction.
4. If test call times out, persist a FAILED or ERROR test log with safe error message.
5. Do not use `@Async` for provider test unless API is changed to async job semantics.
```

### 11.2. Provider client abstraction

Create a provider client abstraction so service code does not contain provider-specific HTTP details.

```java
public interface LlmProviderClient {

  /**
   * Executes a minimal provider connection test using runtime secret material.
   *
   * @param request provider test request with model and optional prompt
   * @return normalized provider test attempt result
   */
  ProviderTestAttempt testConnection(TestLlmProviderRuntimeRequest request);

  /**
   * Lists available model metadata when the provider supports model discovery.
   *
   * @return list of model names or normalized model metadata
   */
  List<LlmProviderModelResponse> listModels();
}
```

Provider client implementations may be organized under:

```text
provider/client/OpenAiCompatibleProviderClient.java
provider/client/GeminiProviderClient.java
provider/client/ClaudeProviderClient.java
provider/client/CustomHttpProviderClient.java
```

Rules:

```text
1. Provider clients receive plaintext secret only as runtime parameter or runtime config.
2. Provider clients must not log raw HTTP request/response bodies.
3. Provider clients should normalize provider-specific errors into stable backend error codes.
4. Timeouts and retry counts should come from sanitized provider config with safe defaults.
```

### 11.3. Test log persistence

Persist one row for every attempt:

```java
@Transactional
public LlmProviderTestResponse persistProviderTestAttempt(...) {
  LlmProviderTestLog logEntity = LlmProviderTestLog.builder()
      .llmProviderId(providerId)
      .status(attempt.status())
      .model(attempt.model())
      .requestJsonSanitized(attempt.requestJsonSanitized())
      .responsePreview(attempt.responsePreview())
      .errorCode(attempt.errorCode())
      .errorMessage(attempt.safeErrorMessage())
      .latencyMs(attempt.latencyMs())
      .tokenUsageJson(attempt.tokenUsageJson())
      .testedBy(currentUser.id())
      .testedAt(now)
      .createdAt(now)
      .build();

  testLogRepository.save(logEntity);
  // optionally update provider.status
}
```

Rules:

```text
1. Mask before building entity.
2. Persist safe errors for troubleshooting.
3. Do not store provider raw response payload as-is.
4. Do not store plaintext Authorization header in request_json_sanitized.
```

### 11.4. Provider model listing

Model listing can be implemented in two modes:

```text
1. Static mode: return available_models_json stored on provider.
2. Live mode: call provider model endpoint using runtime secret.
```

MVP recommendation:

```text
Use static mode by default. If live mode is added, follow the same runtime secret and no-long-transaction rules as provider test.
```

---

## 12. Controller Implementation

### 12.1. SecretController

Example:

```java
@RestController
@RequestMapping("/api/v1/secrets")
@RequiredArgsConstructor
@Tag(name = "Secrets", description = "Manage personal and workspace secret metadata")
public class SecretController {

  private final SecretService secretService;

  @Operation(summary = "Create secret", description = "Creates a personal or workspace secret. Plaintext value is never returned.")
  @ApiResponses({
      @ApiResponse(responseCode = "201", description = "Secret created"),
      @ApiResponse(responseCode = "403", description = "Insufficient permission"),
      @ApiResponse(responseCode = "409", description = "Duplicate secret name")
  })
  @PostMapping
  public ResponseEntity<SecretResponse> createSecret(
      @Valid @RequestBody CreateSecretRequest request,
      @AuthenticationPrincipal CurrentUser currentUser) {
    SecretResponse response = secretService.createSecret(request, currentUser);
    return ResponseEntity.created(URI.create("/api/v1/secrets/" + response.id())).body(response);
  }
}
```

Rules:

```text
1. Controllers do not decrypt secrets.
2. Controllers do not call repositories directly.
3. Controllers do not log request body.
4. Controllers use Swagger annotations for summary and important responses.
```

### 12.2. LlmProviderController

Example endpoints:

```text
GET    /api/v1/llm-providers
POST   /api/v1/llm-providers
GET    /api/v1/llm-providers/{providerId}
PATCH  /api/v1/llm-providers/{providerId}
DELETE /api/v1/llm-providers/{providerId}
POST   /api/v1/llm-providers/{providerId}/test-connection
GET    /api/v1/llm-providers/{providerId}/test-history
GET    /api/v1/llm-providers/{providerId}/models
```

Rules:

```text
1. Test history endpoint should be paginated.
2. Test connection endpoint should return a sanitized result.
3. Model listing must not expose runtime secret details.
```

---

## 13. Permission Rules

### 13.1. Secret permission

Use these checks:

```text
PERSONAL secret:
  owner_user_id must equal currentUser.id
  owner can list/create/update/rotate/delete own personal secret

WORKSPACE secret:
  workspace_id is required
  OWNER/ADMIN can list/create/update/rotate/delete workspace secrets
  MEMBER may use visible workspace providers but should not manage workspace secrets in MVP
  VIEWER cannot manage secrets

PLATFORM_ADMIN:
  may manage metadata for operational support
  cannot read plaintext secret value
```

### 13.2. Provider permission

Use these checks:

```text
PERSONAL_PROVIDER:
  owner_user_id must equal currentUser.id
  owner can manage own personal provider

WORKSPACE_PROVIDER:
  workspace_id is required
  OWNER/ADMIN can create/update/delete workspace provider
  MEMBER may view/select usable workspace providers depending on run/evaluator setup permission
  VIEWER may view dashboards/results but should not configure provider

Provider test history:
  same visibility as provider metadata/history access
  do not expose to users outside scope
```

### 13.3. Visibility in run setup

Run/evaluator setup may list usable providers from:

```text
1. Active workspace providers from current workspace.
2. Active personal providers owned by current user.
```

Rules:

```text
1. Disabled/deleted providers are not selectable for new setup.
2. Providers with disabled/deleted secrets are not selectable for new setup.
3. Historical TestRuns remain readable through their locked snapshots even if current provider is disabled.
```

---

## 14. Error Codes

Use stable error codes. Recommended codes:

```text
SECRET_NOT_FOUND
SECRET_NAME_DUPLICATE
SECRET_SCOPE_INVALID
SECRET_DISABLED
SECRET_DELETED
SECRET_IN_USE
SECRET_ROTATION_FAILED
SECRET_VALUE_REQUIRED
SECRET_VALUE_TOO_LONG
SECRET_ACCESS_DENIED

PROVIDER_NOT_FOUND
PROVIDER_NAME_DUPLICATE
PROVIDER_SCOPE_INVALID
PROVIDER_SECRET_SCOPE_MISMATCH
PROVIDER_SECRET_INACTIVE
PROVIDER_DISABLED
PROVIDER_TEST_FAILED
PROVIDER_TEST_TIMEOUT
PROVIDER_MODEL_NOT_FOUND
PROVIDER_ACCESS_DENIED

SENSITIVE_DATA_NOT_ALLOWED
VALIDATION_ERROR
AUTHENTICATION_REQUIRED
FORBIDDEN
INTERNAL_ERROR
```

Rules:

```text
1. Do not expose provider raw error bodies directly.
2. Map provider errors to safe codes and safe English default messages.
3. Include traceId in Problem Details through common error handler.
4. Use messageKey for frontend localization.
```

---

## 15. Logging Rules

Use `@Slf4j` on service implementations.

Recommended logs:

```java
log.info("Creating secret metadata: scope={}, workspaceId={}, requestedBy={}", scope, workspaceId, currentUser.id());
log.info("Secret created: secretId={}, scope={}, workspaceId={}", secretId, scope, workspaceId);
log.info("Creating provider: providerType={}, scope={}, workspaceId={}, requestedBy={}", providerType, scope, workspaceId, currentUser.id());
log.info("Provider test completed: providerId={}, status={}, latencyMs={}, testedBy={}", providerId, status, latencyMs, currentUser.id());
log.warn("Provider test failed: providerId={}, errorCode={}, testedBy={}", providerId, errorCode, currentUser.id());
```

Forbidden logs:

```java
log.info("Secret value: {}", request.secretValue());
log.info("Encrypted secret: {}", secret.getEncryptedValue());
log.info("Provider Authorization: {}", authHeader);
log.info("Provider raw response: {}", rawResponseBody);
log.info("Provider config: {}", request.config());
```

Rules:

```text
1. Never log request body for create/rotate/test provider endpoints.
2. Log identifiers and status, not sensitive payloads.
3. Avoid info logs for every list/detail read; use debug if needed.
```

---

## 16. Transaction and Async Rules

Transaction rules:

```text
1. Secret create/update/rotate/delete use @Transactional.
2. Secret list/detail use @Transactional(readOnly = true).
3. Provider create/update/delete use @Transactional.
4. Provider list/detail/history use @Transactional(readOnly = true).
5. Provider test orchestration should avoid a long transaction around external IO.
6. Runtime secret resolution should be read-only transactional when loading secret, then decrypt in memory.
```

Async rules:

```text
1. Do not use @Async for normal secret CRUD.
2. Do not use @Async for provider test unless API changes to job-based provider test.
3. Provider test is expected to be synchronous in MVP.
4. Redis is not required for secret/provider CRUD in MVP.
5. Runner runtime secret resolution is internal API, not Redis payload content.
```

---

## 17. Security Checklist

Before implementation is accepted:

```text
1. No response record contains encryptedValue or secretValue.
2. No mapper maps encryptedValue to response.
3. No service/controller logs plaintext secret or raw provider payload.
4. Create/rotate endpoints encrypt before persistence.
5. Provider config is sanitized before persistence.
6. Provider test logs are sanitized before persistence.
7. Provider test does not hold long DB transaction during external call.
8. Scope compatibility is enforced for secret/provider combinations.
9. Disabled/deleted secrets and providers cannot be used for new setup/run.
10. RuntimeSecretResolver is not exposed to frontend controllers.
11. Tests verify PLATFORM_ADMIN still cannot read plaintext secret.
```

---

## 18. Testing Checklist

Unit tests:

```text
1. Secret create encrypts value and returns no plaintext.
2. Secret rotate replaces encrypted value and returns no plaintext.
3. Secret duplicate name check is case-insensitive within scope.
4. Personal secret cannot be used by workspace provider.
5. Workspace provider cannot use secret from another workspace.
6. Disabled secret blocks provider creation/update/test.
7. Provider config secret-like field detection rejects unsafe config.
8. Provider test persists sanitized test log on success.
9. Provider test persists sanitized test log on failure/timeout.
10. Provider test does not leak raw provider error body.
```

Integration tests:

```text
1. OWNER/ADMIN can manage workspace secrets/providers.
2. MEMBER cannot create workspace secret/provider if MVP matrix denies it.
3. User can manage own personal secret/provider.
4. User cannot access another user's personal secret/provider.
5. Provider test history is paginated and newest-first.
6. Deleted secret/provider is hidden from normal list endpoint.
7. Refresh/current user auth integrates with permission resolvers.
```

Security regression tests:

```text
1. Search JSON responses for `encryptedValue` and ensure absent.
2. Search logs in tests for known fake secret and ensure absent.
3. Verify masked preview reveals only allowed small prefix/suffix.
4. Verify ProblemDetail detail does not contain plaintext secret or raw provider payload.
```

---

## 19. AI Coding Agent Guardrails

When generating code for this module:

```text
1. Use service interfaces with Javadoc.
2. Put implementations in `service/impl` and annotate with `@Slf4j`, `@Service`, `@RequiredArgsConstructor`.
3. Use MapStruct mappers; do not create manual toResponse/toEntity methods inside services.
4. Use record DTOs with validation and Swagger examples.
5. Use `@Transactional` at service implementation boundaries.
6. Split provider test DB work and external IO to avoid long transactions.
7. Never log or return plaintext secret values.
8. Never store raw Authorization/API key/token/cookie/password in provider config or provider test logs.
9. Do not add Redis for this module unless a future contract explicitly introduces async provider test jobs.
10. Keep backend messages/logs in English and expose messageKey for frontend localization.
```
