# VinFast QC Copilot - Chatbot Target Implementation

Version: `v0.1`  
Status: Backend Implementation Spec Draft  
Applies to: `apps/api` Spring Boot backend  
Aligned with: PRD v0.7, ERD v0.5, API Contract `06-chatbot-target-api.md`

---

## 1. Purpose

This document explains how to implement Chatbot Target configuration in the Spring Boot backend.

It is written for AI coding agents and backend developers so generated code follows the same security rules, package structure, validation boundaries and persistence behavior.

This file covers:

```text
1. Chatbot target CRUD.
2. cURL inspection/parsing helper.
3. Sanitized raw cURL persistence.
4. Secret binding references for target request credentials.
5. Dataset variable mapping into URL, header, query and body.
6. Test connection flow.
7. Masked request/response example history.
8. Response extraction configuration.
9. Status transition rules.
10. Permission checks, transaction boundaries, logging and error handling.
11. DTO, mapper, controller and service implementation conventions.
```

This file does not redefine API request/response contracts. API shapes are defined in `docs/api/06-chatbot-target-api.md`.

---

## 2. Design Principles

Chatbot Target code must follow these principles:

```text
1. Backend must never shell-execute raw cURL.
2. Backend must persist only sanitized raw cURL.
3. Plain Authorization/API key/token/cookie values must not be stored in DB.
4. Secrets must be stored as Secret entities and referenced by id.
5. Target request config must be structured, validated and replayable.
6. UI variables use `$dataset.<columnName>` syntax.
7. Backend stores domain variable syntax, not Promptfoo `{{columnName}}` syntax.
8. Runner converts target config to Promptfoo-compatible config at run time.
9. Test connection request/response must be masked before persistence.
10. Every test connection attempt creates a ChatbotTargetExample row.
11. Response extraction config is selected from a masked example response.
12. ACTIVE target requires both valid request config and valid response extraction config.
13. Disabled/deleted targets cannot be selected for new TestRuns.
```

Security and localization rules:

```text
1. Never log plaintext Authorization, API key, token, cookie or password.
2. Never log raw unsanitized cURL.
3. Never persist unsanitized request/response bodies from test connection.
4. Backend code/log/error codes should use English.
5. Vietnamese user-facing copy belongs in frontend i18n or a message catalog, not inline service logic.
```

---

## 3. Package Layout

Recommended package layout:

```text
vn.vinfast.vfqc.api
  target
    controller
      ChatbotTargetController.java
      ChatbotTargetExampleController.java
      ChatbotTargetCurlController.java
      ChatbotTargetConnectionController.java
      ChatbotTargetExtractionController.java
    dto
      request
        InspectCurlRequest.java
        CreateChatbotTargetRequest.java
        UpdateChatbotTargetRequest.java
        TestConnectionRequest.java
        ResponseExtractionConfigRequest.java
      response
        ChatbotTargetSummaryResponse.java
        ChatbotTargetDetailResponse.java
        ChatbotTargetExampleResponse.java
        InspectCurlResponse.java
        TestConnectionResponse.java
        ResponseExtractionConfigResponse.java
        ParsedHttpRequestConfigResponse.java
        SanitizedHeaderResponse.java
        SanitizedQueryParamResponse.java
        SecretBindingResponse.java
        DatasetRequestMappingResponse.java
    entity
      ChatbotTarget.java
      ChatbotTargetExample.java
    enums
      ChatbotTargetStatus.java
      HttpBodyType.java
      SecretBindingLocation.java
      DatasetTargetLocation.java
    mapper
      ChatbotTargetMapper.java
      ChatbotTargetExampleMapper.java
    repository
      ChatbotTargetRepository.java
      ChatbotTargetExampleRepository.java
    service
      ChatbotTargetService.java
      CurlInspectionService.java
      ChatbotTargetTestService.java
      ResponseExtractionService.java
      ChatbotTargetLookupService.java
      ChatbotTargetReadinessService.java
      impl
        ChatbotTargetServiceImpl.java
        CurlInspectionServiceImpl.java
        ChatbotTargetTestServiceImpl.java
        ResponseExtractionServiceImpl.java
        ChatbotTargetLookupServiceImpl.java
        ChatbotTargetReadinessServiceImpl.java
    validator
      ChatbotTargetValidator.java
      DatasetRequestMappingValidator.java
      ResponseExtractionValidator.java
      SecretBindingValidator.java
    assembler
      ChatbotTargetResponseAssembler.java
    client
      HttpTargetClient.java
      RestClientHttpTargetClient.java
```

Cross-module dependencies:

```text
target -> project permission lookup
target -> dataset schema/version lookup for variable validation
target -> secret lookup/runtime resolver for test connection
target -> common masking
target -> common naming
target -> common json
target -> common error
```

Target module must not depend on Runner implementation classes.

---

## 4. Entity Implementation

### 4.1. `ChatbotTarget`

Maps to `chatbot_targets`.

Recommended entity rules:

```text
1. id is UUID.
2. project is LAZY ManyToOne.
3. latestExample is optional LAZY ManyToOne.
4. JSONB fields should use a shared JSON mapping strategy already used in other modules.
5. deletedAt means soft delete.
6. rawCurlSanitized may be null if target was configured manually without cURL paste.
7. rawCurlHash is SHA-256 of sanitized canonical cURL text, not raw secret-bearing cURL.
```

Important fields:

```text
id
project
name
description
rawCurlSanitized
rawCurlHash
method
url
headersJson
queryParamsJson
bodyJson
authConfigJson
datasetMappingJson
latestExample
responseExtractionConfigJson
status
createdBy
createdAt
updatedAt
deletedAt
```

Implementation rule:

```text
Do not model headersJson/queryParamsJson/bodyJson/authConfigJson/datasetMappingJson as random Map in business logic.
Parse them into typed internal config records/classes before validation and execution.
```

Recommended internal config models:

```text
ParsedHttpRequestConfig
SanitizedHeader
SanitizedQueryParam
SecretBinding
DatasetRequestMapping
ResponseExtractionConfig
ResponseExtractionField
```

These may live under:

```text
target/model
```

or under:

```text
target/dto/model
```

Prefer `target/model` for internal domain config objects.

### 4.2. `ChatbotTargetExample`

Maps to `chatbot_target_examples`.

Recommended entity rules:

```text
1. id is Long.
2. chatbotTarget is LAZY ManyToOne.
3. requestJson, responseHeadersJson, responseBodyJson and extractedOutputJson must already be sanitized before save.
4. responseStatusCode is nullable because network/timeout errors may happen before response.
5. errorMessage must be sanitized.
6. Every test connection attempt saves one row, including failed attempts.
```

Important fields:

```text
id
chatbotTarget
requestJson
responseStatusCode
responseHeadersJson
responseBodyJson
extractedOutputJson
latencyMs
errorMessage
testedBy
createdAt
```

---

## 5. DTO Implementation Rules

Use Java records for request/response DTOs.

Request records must include:

```text
1. Jakarta Bean Validation annotations.
2. Swagger @Schema description and example for each user-controlled field.
3. No plaintext secret field unless it is a create/test request that explicitly accepts a secret value.
4. No entity type in request/response DTO.
```

Example request record:

```java
package vn.vinfast.vfqc.api.target.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "Request to create a chatbot target from a structured HTTP config")
public record CreateChatbotTargetRequest(
    @Schema(description = "Target display name", example = "VF Sales Chatbot v1")
    @NotBlank
    @Size(max = 120)
    String name,

    @Schema(description = "Optional target description", example = "Internal sales assistant chatbot endpoint")
    @Size(max = 1000)
    String description,

    @Schema(description = "Sanitized cURL text if target was created from cURL paste")
    @Size(max = 20000)
    String rawCurlSanitized,

    @Schema(description = "HTTP method", example = "POST")
    @NotBlank
    String method,

    @Schema(description = "Target URL", example = "https://internal-chatbot.example.com/api/chat")
    @NotBlank
    @Size(max = 2048)
    String url,

    @Schema(description = "Structured request config")
    @NotNull
    @Valid
    ParsedHttpRequestConfigRequest requestConfig,

    @Schema(description = "Dataset variables mapped into request config")
    @NotEmpty
    @Valid
    List<DatasetRequestMappingRequest> datasetMappings,

    @Schema(description = "Secret bindings used at runtime")
    @Valid
    List<SecretBindingRequest> secretBindings
) {}
```

Example response record:

```java
package vn.vinfast.vfqc.api.target.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Chatbot target summary")
public record ChatbotTargetSummaryResponse(
    @Schema(example = "88ab33e0-c4c7-4b15-86f1-605455c02e73")
    UUID id,

    @Schema(example = "VF Sales Chatbot v1")
    String name,

    @Schema(example = "POST")
    String method,

    @Schema(example = "https://internal-chatbot.example.com/api/chat")
    String url,

    @Schema(example = "ACTIVE")
    String status,

    @Schema(example = "true")
    boolean hasResponseExtraction,

    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
```

Rules:

```text
1. Response DTO must not include plaintext secret values.
2. Response DTO may include secretId, binding key, maskedPreview and sensitive=true markers.
3. Response DTO must not include unsanitized raw cURL.
4. Use MapStruct for entity-to-response mapping.
5. If response needs computed fields, use an assembler after MapStruct mapping.
```

---

## 6. Mapper Rules

Use MapStruct for mapping.

Recommended mapper:

```java
package vn.vinfast.vfqc.api.target.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vn.vinfast.vfqc.api.target.dto.response.ChatbotTargetDetailResponse;
import vn.vinfast.vfqc.api.target.dto.response.ChatbotTargetSummaryResponse;
import vn.vinfast.vfqc.api.target.entity.ChatbotTarget;

@Mapper(componentModel = "spring")
public interface ChatbotTargetMapper {

  @Mapping(target = "projectId", source = "project.id")
  @Mapping(target = "latestExampleId", source = "latestExample.id")
  ChatbotTargetDetailResponse toDetailResponse(ChatbotTarget entity);

  ChatbotTargetSummaryResponse toSummaryResponse(ChatbotTarget entity);
}
```

Rules:

```text
1. Do not write `toResponse`, `toEntity` helper methods inside services.
2. Do not map create/update request directly to entity if validation/sanitization is required first.
3. Prefer service-level construction for entities when many invariants must be enforced.
4. Use mapper for stable field transformation only.
5. Use assembler for response fields that depend on multiple entities or parsed JSON config.
```

---

## 7. Controller Rules

Controllers must be thin.

They should:

```text
1. Bind path/query/body parameters.
2. Apply @Valid.
3. Call service interfaces.
4. Return response DTOs.
5. Contain OpenAPI/Swagger annotations.
6. Not contain business validation or persistence logic.
```

Example:

```java
package vn.vinfast.vfqc.api.target.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.common.security.CurrentUserPrincipal;
import vn.vinfast.vfqc.api.target.dto.request.CreateChatbotTargetRequest;
import vn.vinfast.vfqc.api.target.dto.response.ChatbotTargetDetailResponse;
import vn.vinfast.vfqc.api.target.service.ChatbotTargetService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class ChatbotTargetController {

  private final ChatbotTargetService chatbotTargetService;

  @Operation(summary = "Create chatbot target", description = "Creates a sanitized chatbot target config inside a project.")
  @PostMapping("/projects/{projectId}/chatbot-targets")
  @ResponseStatus(HttpStatus.CREATED)
  public ChatbotTargetDetailResponse create(
      @CurrentUserPrincipal CurrentUser currentUser,
      @PathVariable UUID projectId,
      @Valid @RequestBody CreateChatbotTargetRequest request) {
    return chatbotTargetService.create(currentUser, projectId, request);
  }

  @Operation(summary = "Get chatbot target detail")
  @GetMapping("/chatbot-targets/{chatbotTargetId}")
  public ChatbotTargetDetailResponse getDetail(
      @CurrentUserPrincipal CurrentUser currentUser,
      @PathVariable UUID chatbotTargetId) {
    return chatbotTargetService.getDetail(currentUser, chatbotTargetId);
  }
}
```

Controller naming:

```text
ChatbotTargetController:
  CRUD endpoints.

ChatbotTargetCurlController:
  POST /api/v1/chatbot-targets/inspect-curl.

ChatbotTargetConnectionController:
  POST /api/v1/chatbot-targets/{id}/test-connection.

ChatbotTargetExtractionController:
  PATCH /api/v1/chatbot-targets/{id}/response-extraction.

ChatbotTargetExampleController:
  example list/detail endpoints.
```

---

## 8. Service Interface Rules

Service interfaces live under `target/service`.

Each public method must have Javadoc describing:

```text
1. What the method does.
2. Main validation/permission expectation.
3. Parameters.
4. Return value.
5. Important errors when useful.
```

Example:

```java
package vn.vinfast.vfqc.api.target.service;

import java.util.UUID;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.target.dto.request.CreateChatbotTargetRequest;
import vn.vinfast.vfqc.api.target.dto.request.UpdateChatbotTargetRequest;
import vn.vinfast.vfqc.api.target.dto.response.ChatbotTargetDetailResponse;
import vn.vinfast.vfqc.api.target.dto.response.ChatbotTargetSummaryResponse;
import vn.vinfast.vfqc.api.common.pagination.CursorPageResponse;

public interface ChatbotTargetService {

  /**
   * Creates a chatbot target in the given project after validating permissions, sanitizing cURL/config
   * data, validating dataset mappings and checking secret binding compatibility.
   *
   * @param currentUser authenticated user executing the command
   * @param projectId project that will own the target
   * @param request create request containing structured HTTP config and mappings
   * @return created chatbot target detail response
   */
  ChatbotTargetDetailResponse create(CurrentUser currentUser, UUID projectId, CreateChatbotTargetRequest request);

  /**
   * Returns chatbot targets visible to the current user within the given project.
   *
   * @param currentUser authenticated user executing the query
   * @param projectId project id used for permission and filtering
   * @param limit maximum page size
   * @param cursor opaque pagination cursor
   * @return cursor-paginated chatbot target summaries
   */
  CursorPageResponse<ChatbotTargetSummaryResponse> list(CurrentUser currentUser, UUID projectId, int limit, String cursor);

  /**
   * Updates mutable chatbot target metadata or request configuration. When request config changes, target
   * readiness may be reset to DRAFT/INVALID until response extraction is validated again.
   *
   * @param currentUser authenticated user executing the command
   * @param chatbotTargetId target id
   * @param request patch request
   * @return updated chatbot target detail response
   */
  ChatbotTargetDetailResponse update(CurrentUser currentUser, UUID chatbotTargetId, UpdateChatbotTargetRequest request);
}
```

Implementation classes live under `target/service/impl`.

Implementation classes must:

```text
1. Use @Service.
2. Use @RequiredArgsConstructor.
3. Use @Slf4j.
4. Use @Transactional for DB mutations.
5. Use @Transactional(readOnly = true) for read-only DB operations.
6. Log start/end important state changes, not full payloads.
7. Throw ApiException/ErrorCode for expected business errors.
```

Example:

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatbotTargetServiceImpl implements ChatbotTargetService {

  private final ProjectPermissionResolver projectPermissionResolver;
  private final ChatbotTargetRepository chatbotTargetRepository;
  private final ChatbotTargetValidator chatbotTargetValidator;
  private final DatasetRequestMappingValidator datasetRequestMappingValidator;
  private final SecretBindingValidator secretBindingValidator;
  private final ChatbotTargetMapper chatbotTargetMapper;

  @Override
  @Transactional
  public ChatbotTargetDetailResponse create(
      CurrentUser currentUser, UUID projectId, CreateChatbotTargetRequest request) {
    log.info("Creating chatbot target. projectId={}, userId={}", projectId, currentUser.id());

    projectPermissionResolver.requireConfigureTarget(currentUser, projectId);
    chatbotTargetValidator.validateCreateRequest(request);
    datasetRequestMappingValidator.validateForProject(projectId, request.datasetMappings());
    secretBindingValidator.validateForProjectContext(currentUser, projectId, request.secretBindings());

    ChatbotTarget entity = buildEntity(projectId, currentUser, request);
    ChatbotTarget saved = chatbotTargetRepository.save(entity);

    log.info("Created chatbot target. targetId={}, projectId={}", saved.getId(), projectId);
    return chatbotTargetMapper.toDetailResponse(saved);
  }
}
```

Do not log:

```text
1. rawCurl original value.
2. headersJson full value.
3. bodyJson full value.
4. authConfigJson.
5. decrypted secret.
6. response body from test connection.
```

---

## 9. cURL Inspection Flow

Endpoint:

```http
POST /api/v1/chatbot-targets/inspect-curl
```

Purpose:

```text
Parse user-pasted cURL into structured HTTP config for UI review without persistence.
```

Flow:

```text
1. Validate authenticated ACTIVE user.
2. Accept cURL text from request body.
3. Parse cURL using safe parser logic.
4. Do not shell-execute cURL.
5. Detect sensitive headers/query/body fields.
6. Replace sensitive values with masked placeholders.
7. Suggest SecretBinding entries for sensitive values.
8. Return ParsedHttpRequestConfig response.
9. Do not persist anything.
```

Sensitive fields include names containing:

```text
authorization
api-key
apikey
x-api-key
token
access-token
refresh-token
secret
password
cookie
set-cookie
```

Rules:

```text
1. Parsing errors return VALIDATION_ERROR or CURL_PARSE_FAILED.
2. Raw input cURL must not be logged.
3. Raw input cURL must not be persisted.
4. Sanitized output may be returned to frontend.
5. Backend must still revalidate sanitized config during create/update.
```

Recommended service interface:

```java
public interface CurlInspectionService {

  /**
   * Parses and sanitizes a user-provided cURL string without executing it or persisting it.
   *
   * @param currentUser authenticated user executing the inspection
   * @param request cURL inspection request
   * @return parsed and sanitized HTTP request config for frontend review
   */
  InspectCurlResponse inspect(CurrentUser currentUser, InspectCurlRequest request);
}
```

Transaction:

```text
No database transaction is required unless implementation stores temporary audit metadata later.
```

---

## 10. Create/Update Target Flow

### 10.1. Create

Flow:

```text
1. Resolve project and require Configure Target permission.
2. Normalize target name using common NamingNormalizer.
3. Enforce unique active target name in project by normalized name policy.
4. Validate method and URL.
5. Validate body type and body shape.
6. Sanitize rawCurlSanitized again if provided.
7. Validate headers/query/body contain no plaintext secret values.
8. Validate SecretBinding entries reference ACTIVE visible secrets.
9. Validate dataset mapping variables exist in current project dataset schema.
10. Validate all required request variables have mapping.
11. Persist ChatbotTarget as DRAFT unless full extraction config is already valid.
12. Return target detail.
```

Initial status rules:

```text
DRAFT:
  default for newly created target.

ACTIVE:
  allowed only when request config is valid and responseExtractionConfig is valid.

INVALID:
  used when validation/test connection fails after creation.
```

### 10.2. Update

Flow:

```text
1. Require Configure Target permission.
2. Lock or load target by id excluding deleted targets.
3. Apply allowed metadata/config patch fields.
4. Re-run config validation for changed fields.
5. If request config changed, reset latest readiness state if extraction config may no longer match.
6. Persist updatedAt.
7. Return detail response.
```

Update status behavior:

```text
1. Name/description update does not reset status.
2. URL/method/header/query/body/dataset mapping update should usually set status to DRAFT until test connection/extraction is validated again.
3. Explicit disable sets DISABLED.
4. DISABLED target cannot be used for new TestRuns.
5. Soft delete sets deletedAt and excludes target from normal lists.
```

---

## 11. Secret Binding Rules

SecretBinding shape:

```json
{
  "key": "authToken",
  "secretId": "3e8d1de2-2a26-4d9a-a0dd-9c6ce056d9ea",
  "location": "HEADER",
  "name": "Authorization",
  "valueTemplate": "Bearer {{secret}}"
}
```

Validation rules:

```text
1. key is required and unique per target.
2. secretId must reference ACTIVE Secret visible to current user/project context.
3. location must be HEADER, QUERY or BODY.
4. valueTemplate may include only `{{secret}}` placeholder.
5. valueTemplate must not contain a real secret value.
6. Header/query/body config uses secretBindingKey to point to the binding key.
7. Backend persists secret reference, not plaintext secret.
8. Runner/test connection resolves plaintext secret only at runtime.
```

Scope compatibility:

```text
WORKSPACE secret:
  usable if it belongs to the target project workspace and user has permission.

PERSONAL secret:
  usable if it belongs to current user.
```

Runtime resolution:

```text
1. Target module may call RuntimeSecretResolver only in test connection flow.
2. Normal CRUD must not decrypt secrets.
3. Runner internal runtime secret resolution is implemented in runner/internal API docs, not here.
```

---

## 12. Dataset Mapping Rules

DatasetRequestMapping shape:

```json
{
  "variable": "$dataset.question",
  "targetLocation": "BODY",
  "targetPath": "$.question",
  "required": true
}
```

Validation rules:

```text
1. variable must start with `$dataset.`.
2. variable column name must exist in the current project dataset schema used by setup UI.
3. targetLocation must be URL, HEADER, QUERY or BODY.
4. BODY mapping should use JSONPath-like targetPath.
5. QUERY/HEADER mapping may use targetName.
6. Required variables must be checked during preflight and test connection.
7. Backend stores `$dataset.<columnName>` syntax, not Promptfoo syntax.
```

Important distinction:

```text
Target setup validates mapping against schema.
TestRun preflight validates mapping against the selected dataset version and actual row count.
```

---

## 13. Test Connection Flow

Endpoint:

```http
POST /api/v1/chatbot-targets/{chatbotTargetId}/test-connection
```

Purpose:

```text
Call the configured target once using safe sample variables and runtime secret injection, then persist a masked example.
```

Flow:

```text
1. Require Configure Target permission.
2. Load target excluding deleted targets.
3. Validate target request config.
4. Build sample variable map from request or latest dataset preview row.
5. Resolve required runtime secrets in memory.
6. Render request config using sample variables and secrets.
7. Call target HTTP endpoint with timeout/retry policy.
8. Capture response status, headers, body, latency and error if any.
9. Apply VfqcSensitiveDataMasker to request, response and error.
10. Extract output using existing responseExtractionConfig if present.
11. Persist ChatbotTargetExample row for success or failure.
12. Update latestExampleId.
13. Set status:
    - ACTIVE if call succeeds and responseExtractionConfig is valid.
    - DRAFT if call succeeds but extraction is missing.
    - INVALID if call fails or validation fails.
14. Return TestConnectionResponse.
```

Transaction boundary:

```text
Do not keep a database transaction open while calling the external/internal chatbot endpoint.
```

Recommended implementation split:

```text
ChatbotTargetTestServiceImpl.testConnection
  1. Read target and permission in read-only transaction.
  2. Resolve secrets and call HTTP target outside transaction.
  3. Save sanitized example/status in a short write transaction.
```

Possible methods:

```java
public interface ChatbotTargetTestService {

  /**
   * Executes a single masked test connection for a chatbot target and persists a sanitized example.
   *
   * @param currentUser authenticated user executing the test
   * @param chatbotTargetId target id
   * @param request optional sample variables and execution options
   * @return sanitized test connection response
   */
  TestConnectionResponse testConnection(CurrentUser currentUser, UUID chatbotTargetId, TestConnectionRequest request);
}
```

HTTP client rules:

```text
1. Use Spring RestClient/WebClient consistently with project baseline.
2. Configure timeout.
3. Limit response body size saved for examples.
4. Mask before logging/persistence.
5. Do not follow redirects unless explicitly allowed.
6. Do not allow local metadata/IP-sensitive SSRF targets unless deployment explicitly permits internal subnet calls.
```

SSRF safety note:

```text
Because this product calls internal chatbot APIs, strict public-only URL validation may be too restrictive.
However, backend should still block dangerous targets such as localhost metadata endpoints, file URLs and unsupported schemes.
Allowed schemes should be http/https only.
```

---

## 14. Response Extraction Flow

Endpoint:

```http
PATCH /api/v1/chatbot-targets/{chatbotTargetId}/response-extraction
```

Purpose:

```text
Configure which field(s) from chatbot response become extracted output used by evaluators and test results.
```

ResponseExtractionConfig shape:

```json
{
  "mainAnswerPath": "$.data.answer",
  "fields": [
    {
      "name": "intent",
      "path": "$.data.intent",
      "required": false
    }
  ]
}
```

Validation rules:

```text
1. mainAnswerPath is required before target can become ACTIVE.
2. Field names must be unique.
3. Paths must be valid JSONPath-like expressions.
4. If latest example exists, validate paths against latest masked response body.
5. If required path does not exist in latest example, return RESPONSE_EXTRACTION_PATH_INVALID.
6. extractedOutputJson must be masked before persistence.
7. Updating extraction config may make target ACTIVE if request config is valid.
```

Recommended service interface:

```java
public interface ResponseExtractionService {

  /**
   * Validates and saves response extraction config for a chatbot target.
   *
   * @param currentUser authenticated user executing the command
   * @param chatbotTargetId target id
   * @param request response extraction config request
   * @return updated response extraction config response
   */
  ResponseExtractionConfigResponse updateExtraction(
      CurrentUser currentUser,
      UUID chatbotTargetId,
      ResponseExtractionConfigRequest request);
}
```

---

## 15. Readiness Service

`ChatbotTargetReadinessService` is used by:

```text
1. Project setup-status.
2. TestRun preflight.
3. Create TestRun validation.
```

Recommended methods:

```java
public interface ChatbotTargetReadinessService {

  /**
   * Checks whether a project has at least one ACTIVE chatbot target ready for single run setup.
   *
   * @param projectId project id
   * @return true if an active target with extraction config exists
   */
  boolean hasActiveReadyTarget(UUID projectId);

  /**
   * Checks whether a target can be selected as a TestRun CHATBOT_TARGET.
   *
   * @param chatbotTargetId target id
   * @return readiness result with blocker codes if target is not runnable
   */
  TargetReadinessResult checkTargetReadiness(UUID chatbotTargetId);
}
```

Runnable target requirements:

```text
1. Target exists and is not soft-deleted.
2. Target status is ACTIVE.
3. Project is not archived/deleted.
4. Request config is valid.
5. Response extraction config has mainAnswerPath.
6. Referenced secrets are ACTIVE and resolvable for execution context.
```

Do not decrypt secrets during simple setup-status. Use metadata/status checks only.

---

## 16. Permission Rules

Required permissions:

```text
List/View target:
  Project VIEW.

Create/Update/Delete target:
  Project UPDATE / Configure target: OWNER, ADMIN, MEMBER.

Inspect cURL:
  Authenticated ACTIVE user.

Test connection:
  Project UPDATE / Configure target: OWNER, ADMIN, MEMBER.

Update response extraction:
  Project UPDATE / Configure target: OWNER, ADMIN, MEMBER.

View examples:
  Project VIEW.
```

Rules:

```text
1. VIEWER can inspect target configuration and examples but cannot modify or test connection.
2. Users outside workspace should receive 404 for target-specific endpoints to avoid leaking existence.
3. PLATFORM_ADMIN can operate except reading plaintext secrets.
4. Test connection may use runtime secret resolution but must not expose plaintext to user.
```

---

## 17. Transaction Rules

Use transactions as follows:

```text
List/get endpoints:
  @Transactional(readOnly = true)

Create/update/delete target:
  @Transactional

Update response extraction:
  @Transactional

List/get examples:
  @Transactional(readOnly = true)

Inspect cURL:
  no transaction required

Test connection:
  do not hold one long transaction around external HTTP call
```

Recommended test connection transaction split:

```text
1. loadContextForTestConnection() -> @Transactional(readOnly = true)
2. executeHttpCall() -> no transaction
3. persistExampleAndStatus() -> @Transactional
```

---

## 18. Async and Redis Rules

Chatbot target CRUD does not require Redis.

MVP rules:

```text
1. Do not use Redis for target CRUD state.
2. Do not publish Redis events for every target update unless an audit/event system is added later.
3. Test connection can be synchronous in MVP because it is a user-triggered validation call.
4. If test connection becomes slow, move it to an async job endpoint later instead of blocking UI indefinitely.
5. TestRun execution uses Redis Streams in testrun/runner modules, not target module.
```

Use `@Async` only if:

```text
1. A future test connection job API is added.
2. The method does not need request transaction context.
3. The method writes progress/status through a clear job model.
```

Do not add `@Async` just because the method calls HTTP.

---

## 19. Logging Rules

Use `@Slf4j` in implementation classes.

Recommended logs:

```text
INFO:
  target created/updated/deleted
  extraction updated
  test connection started/completed with status only

WARN:
  validation failed for suspicious target config
  test connection failed due to timeout/network/client error

DEBUG:
  high-level parsed config shape, counts of headers/mappings only
```

Allowed log fields:

```text
projectId
chatbotTargetId
exampleId
userId
status
method
host only if safe
latencyMs
responseStatusCode
errorCode
```

Forbidden log fields:

```text
raw cURL
Authorization header
Cookie header
API key/token/password
full request body
full response body
resolved secret
raw authConfigJson
```

Example:

```java
log.info(
    "Completed chatbot target test connection. targetId={}, exampleId={}, responseStatusCode={}, latencyMs={}",
    targetId,
    exampleId,
    responseStatusCode,
    latencyMs);
```

---

## 20. Error Codes

Recommended error codes:

```text
CHATBOT_TARGET_NOT_FOUND
CHATBOT_TARGET_NAME_DUPLICATE
CHATBOT_TARGET_INVALID_STATUS
CHATBOT_TARGET_DISABLED
CHATBOT_TARGET_NOT_READY
CURL_PARSE_FAILED
CURL_CONTAINS_UNSUPPORTED_FEATURE
TARGET_METHOD_UNSUPPORTED
TARGET_URL_INVALID
TARGET_BODY_TYPE_UNSUPPORTED
TARGET_CONFIG_INVALID
TARGET_SECRET_BINDING_INVALID
TARGET_SECRET_NOT_ACTIVE
TARGET_DATASET_MAPPING_INVALID
TARGET_DATASET_VARIABLE_NOT_FOUND
TARGET_TEST_CONNECTION_FAILED
TARGET_TEST_CONNECTION_TIMEOUT
TARGET_RESPONSE_TOO_LARGE
RESPONSE_EXTRACTION_MISSING
RESPONSE_EXTRACTION_PATH_INVALID
RESPONSE_EXTRACTION_FIELD_DUPLICATE
RESPONSE_EXTRACTION_CONFIG_INVALID
```

Error response must use the common Problem Details factory.

Rules:

```text
1. Do not leak raw downstream error body to ProblemDetail detail.
2. Mask downstream error before persistence.
3. Use messageKey for frontend localization.
4. Return 404 instead of 403 when needed to avoid leaking target existence.
```

---

## 21. Integration with Evaluator and TestRun

Target module provides these stable outputs for other modules:

```text
1. Active target summaries for run setup.
2. Response extraction field sources for evaluator builder.
3. Locked target config for TestRun snapshot generation.
4. Target readiness result for setup-status/preflight.
```

Evaluator module uses:

```text
Response extraction field names:
  extracted.mainAnswer
  extracted.<fieldName>
```

TestRun module uses:

```text
ChatbotTargetLookupService.findRunnableTargetForRun(chatbotTargetId)
```

Returned object should include:

```text
target id
display name
method
url
sanitized request config
dataset mappings
secret reference metadata
response extraction config
status
```

Do not return decrypted secrets to TestRun orchestration. Runner runtime secret resolution is separate.

---

## 22. Testing Checklist

Unit tests:

```text
1. cURL parser masks Authorization/API key/Cookie.
2. cURL parser rejects unsupported features safely.
3. SecretBindingValidator rejects cross-scope secrets.
4. DatasetRequestMappingValidator rejects missing dataset variables.
5. ResponseExtractionValidator rejects missing mainAnswerPath.
6. ResponseExtractionValidator rejects invalid path against latest example.
7. ChatbotTargetReadinessService returns blocker codes correctly.
8. Mapper does not expose secret/plaintext fields.
```

Service tests:

```text
1. Create target persists sanitized raw curl only.
2. Create target sets DRAFT by default.
3. Update request config resets readiness as expected.
4. Soft delete hides target from normal list.
5. Test connection persists example for success.
6. Test connection persists sanitized failed example.
7. Test connection does not keep transaction open during HTTP call.
8. Update response extraction can transition target to ACTIVE.
```

Controller tests:

```text
1. VIEWER can list/view but cannot create/update/test.
2. MEMBER can create/update/test.
3. Unauthorized request returns 401.
4. User outside workspace receives 404 for target detail.
5. Validation errors return RFC 9457 Problem Details.
```

Security tests:

```text
1. No plaintext secret in API response.
2. No plaintext secret in chatbot_target_examples.
3. Masker runs before example persistence.
4. Raw cURL with Authorization is not persisted unchanged.
```

---

## 23. AI Coding Rules

When generating chatbot target code:

```text
1. Do not shell out to curl.
2. Do not store raw unsanitized cURL.
3. Do not use entity classes as request/response DTOs.
4. Do not write manual mapper helpers in services.
5. Do not decrypt secrets in CRUD paths.
6. Do not log headers/body/config JSON.
7. Do not convert `$dataset.question` to `{{question}}` in backend target module.
8. Do not make target module depend on Promptfoo classes.
9. Do not use @Async for synchronous test connection unless a job model is introduced.
10. Do not bypass permission resolver in any target endpoint.
```
