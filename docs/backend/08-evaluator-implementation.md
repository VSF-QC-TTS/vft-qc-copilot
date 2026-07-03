# VinFast QC Copilot - Evaluator Implementation

Version: `v0.1`  
Status: Backend Implementation Spec Draft  
Applies to: `apps/api` Spring Boot backend  
Aligned with: PRD v0.7, ERD v0.5, API Contract `07-evaluator-api.md`

---

## 1. Purpose

This document explains how to implement Evaluator configuration in the Spring Boot backend.

It is written for AI coding agents and backend developers so generated code follows the same evaluator versioning model, safe rule constraints, validation boundaries and API behavior.

This file covers:

```text
1. Evaluator CRUD.
2. EvaluatorVersion immutable versioning.
3. Rule-based evaluator configuration.
4. LLM Judge evaluator configuration.
5. Mixed evaluator configuration.
6. Supported safe operators.
7. Evaluator field source discovery.
8. Validation-only API before persistence.
9. Provider/model compatibility for LLM Judge.
10. Permission checks, transaction boundaries, logging and error handling.
11. DTO, mapper, controller and service implementation conventions.
```

This file does not redefine API request/response contracts. API shapes are defined in `docs/api/07-evaluator-api.md`.

---

## 2. Design Principles

Evaluator code must follow these principles:

```text
1. Evaluator belongs to one project.
2. Evaluator type is immutable after creation.
3. EvaluatorVersion is immutable after creation.
4. Editing rules/rubric/config creates a new EvaluatorVersion.
5. TestRun locks exact EvaluatorVersion ids.
6. Rule-based evaluator uses safe declarative operators only.
7. User/QC cannot provide arbitrary JavaScript in MVP.
8. Backend may validate and normalize config, but Runner maps config to Promptfoo execution later.
9. LLM Judge output must use a normalized JSON verdict schema.
10. Provider/model selected for Judge must be validated before run creation.
11. Evaluator configs, rubrics and rules must not contain plaintext secrets.
12. Field sources must come from dataset schema and target response extraction config, not hardcoded UI guesses.
```

Security and localization rules:

```text
1. Do not log full rubric, full expected values or dataset content.
2. Do not accept custom JavaScript snippets in any evaluator request.
3. Backend code/log/error codes should use English.
4. Vietnamese user-facing copy belongs in frontend i18n or a message catalog, not inline service logic.
```

---

## 3. Package Layout

Recommended package layout:

```text
vn.vinfast.vfqc.api
  evaluator
    controller
      EvaluatorController.java
      EvaluatorVersionController.java
      EvaluatorMetadataController.java
      EvaluatorValidationController.java
      EvaluatorFieldSourceController.java
      EvaluatorOperatorController.java
    dto
      request
        CreateEvaluatorRequest.java
        UpdateEvaluatorRequest.java
        CreateEvaluatorVersionRequest.java
        ValidateEvaluatorRequest.java
        EvaluatorRuleRequest.java
        RuleBasedConfigRequest.java
        LlmJudgeConfigRequest.java
        MixedEvaluatorConfigRequest.java
      response
        EvaluatorResponse.java
        EvaluatorVersionResponse.java
        EvaluatorRuleResponse.java
        EvaluatorValidationResponse.java
        EvaluatorFieldSourceResponse.java
        EvaluatorOperatorResponse.java
        EvaluatorConfigResponse.java
    entity
      Evaluator.java
      EvaluatorVersion.java
      EvaluatorRule.java
    enums
      EvaluatorType.java
      EvaluatorOperator.java
      EvaluatorAggregation.java
      EvaluatorPassConditionType.java
      JudgeOutputSchema.java
    mapper
      EvaluatorMapper.java
      EvaluatorVersionMapper.java
      EvaluatorRuleMapper.java
    repository
      EvaluatorRepository.java
      EvaluatorVersionRepository.java
      EvaluatorRuleRepository.java
    service
      EvaluatorService.java
      EvaluatorVersionService.java
      EvaluatorValidationService.java
      EvaluatorFieldSourceService.java
      EvaluatorOperatorService.java
      EvaluatorLookupService.java
      EvaluatorReadinessService.java
      impl
        EvaluatorServiceImpl.java
        EvaluatorVersionServiceImpl.java
        EvaluatorValidationServiceImpl.java
        EvaluatorFieldSourceServiceImpl.java
        EvaluatorOperatorServiceImpl.java
        EvaluatorLookupServiceImpl.java
        EvaluatorReadinessServiceImpl.java
    validator
      EvaluatorConfigValidator.java
      EvaluatorRuleValidator.java
      LlmJudgeConfigValidator.java
      EvaluatorProviderValidator.java
    assembler
      EvaluatorResponseAssembler.java
      EvaluatorVersionResponseAssembler.java
    model
      EvaluatorConfigModel.java
      RuleBasedConfigModel.java
      LlmJudgeConfigModel.java
      EvaluatorFieldSourceModel.java
      EvaluatorValidationIssue.java
```

Cross-module dependencies:

```text
evaluator -> project permission lookup
evaluator -> dataset schema/version lookup for dataset field sources
evaluator -> target response extraction lookup for extracted field sources
evaluator -> provider lookup for judge provider/model validation
evaluator -> common json
evaluator -> common error
evaluator -> common naming
evaluator -> common masking if needed for config safety checks
```

Evaluator module must not depend on Runner implementation classes or Promptfoo JavaScript code.

---

## 4. Entity Implementation

### 4.1. `Evaluator`

Maps to `evaluators`.

Recommended entity rules:

```text
1. id is UUID.
2. project is LAZY ManyToOne.
3. type is immutable after creation.
4. name is mutable with project-level uniqueness.
5. deletedAt means soft delete.
6. latest version can be derived by repository query, not necessarily stored on evaluator.
```

Important fields:

```text
id
project
name
type
createdBy
createdAt
updatedAt
deletedAt
```

### 4.2. `EvaluatorVersion`

Maps to `evaluator_versions`.

Recommended entity rules:

```text
1. id is UUID.
2. evaluator is LAZY ManyToOne.
3. version is monotonically increasing per evaluator.
4. configJson is immutable after insert.
5. createdAt/createdBy are immutable after insert.
6. Never update an existing version when editing evaluator config.
```

Important fields:

```text
id
evaluator
version
configJson
createdBy
createdAt
```

### 4.3. `EvaluatorRule`

Maps to `evaluator_rules`.

Recommended entity rules:

```text
1. id is Long.
2. evaluatorVersion is LAZY ManyToOne.
3. Rule rows belong to exactly one EvaluatorVersion.
4. Rule order is stable through orderIndex.
5. expectedValueJson can represent literal values or dataset variable references.
6. configJson stores operator-specific options only.
```

Important fields:

```text
id
evaluatorVersion
fieldSource
operator
expectedValueJson
weight
required
orderIndex
configJson
createdAt
updatedAt
```

Rule immutability:

```text
Do not update evaluator_rules for an EvaluatorVersion that already exists.
Create a new EvaluatorVersion and new EvaluatorRule rows instead.
```

---

## 5. Evaluator Config Model

Evaluator config is stored in `evaluator_versions.config_json`.

Recommended internal model:

```text
EvaluatorConfigModel
  ruleBased: RuleBasedConfigModel?
  llmJudge: LlmJudgeConfigModel?
```

Rule-based config:

```json
{
  "ruleBased": {
    "aggregation": "WEIGHTED_AVERAGE",
    "passCondition": {
      "type": "SCORE_AT_LEAST",
      "threshold": 0.7
    }
  }
}
```

LLM Judge config:

```json
{
  "llmJudge": {
    "rubric": "Grade correctness, completeness and hallucination risk.",
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

MIXED evaluator:

```text
MIXED evaluator has both ruleBased and llmJudge config, and may have rule rows plus judge rubric.
```

Rules:

```text
1. RULE_BASED must have ruleBased config and at least one rule.
2. LLM_JUDGE must have llmJudge config.
3. MIXED must have at least one of ruleBased rules and llmJudge config; MVP should require both if UI defines MIXED as combined mode.
4. configJson must be canonicalized before hashing/snapshotting in TestRun module.
5. User-provided custom JS is forbidden.
```

---

## 6. DTO Implementation Rules

Use Java records for request/response DTOs.

Request records must include:

```text
1. Jakarta Bean Validation annotations.
2. Swagger @Schema description and example for every user-controlled field.
3. No entity type in request/response DTO.
4. No custom JavaScript/script fields.
```

Example request record:

```java
package vn.vinfast.vfqc.api.evaluator.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "Request to create an evaluator with initial version")
public record CreateEvaluatorRequest(
    @Schema(description = "Evaluator display name", example = "Answer quality evaluator")
    @NotBlank
    @Size(max = 120)
    String name,

    @Schema(description = "Evaluator type", example = "MIXED")
    @NotBlank
    String type,

    @Schema(description = "Evaluator config", implementation = EvaluatorConfigRequest.class)
    @NotNull
    @Valid
    EvaluatorConfigRequest config,

    @Schema(description = "Rule definitions for RULE_BASED or MIXED evaluator")
    @Valid
    List<EvaluatorRuleRequest> rules
) {}
```

Example rule request:

```java
@Schema(description = "Declarative evaluator rule")
public record EvaluatorRuleRequest(
    @Schema(description = "Source field to evaluate", example = "extracted.mainAnswer")
    @NotBlank
    String fieldSource,

    @Schema(description = "Safe evaluator operator", example = "contains")
    @NotBlank
    String operator,

    @Schema(description = "Expected value JSON or dataset variable reference", example = "{\"type\":\"datasetVariable\",\"path\":\"$dataset.expected\"}")
    Object expectedValue,

    @Schema(description = "Rule weight", example = "1.0")
    java.math.BigDecimal weight,

    @Schema(description = "Whether this rule is required to pass", example = "true")
    boolean required,

    @Schema(description = "Stable rule order", example = "1")
    int orderIndex
) {}
```

Response DTO rules:

```text
1. EvaluatorVersionResponse returns immutable version id and version number.
2. Rules in response must be sorted by orderIndex.
3. Do not include deletedAt in normal list response unless API supports includeDeleted later.
4. Do not expose internal DB JSON as raw string if a typed JSON response is expected.
```

---

## 7. Mapper Rules

Use MapStruct for mapping.

Recommended mappers:

```java
@Mapper(componentModel = "spring")
public interface EvaluatorMapper {

  @Mapping(target = "projectId", source = "project.id")
  EvaluatorResponse toResponse(Evaluator entity);
}
```

```java
@Mapper(componentModel = "spring")
public interface EvaluatorRuleMapper {

  EvaluatorRuleResponse toResponse(EvaluatorRule entity);
}
```

Rules:

```text
1. Do not write manual `toResponse` or `toEntity` helpers in service implementations.
2. Use MapStruct for simple entity-to-DTO mapping.
3. Use assembler when response includes latestVersion, sorted rules, config parsing or createdBy summary.
4. Service should build entities when invariants/versioning are complex.
5. Do not let MapStruct blindly map request config directly into persisted configJson without validation/canonicalization.
```

---

## 8. Controller Rules

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
package vn.vinfast.vfqc.api.evaluator.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.common.security.CurrentUserPrincipal;
import vn.vinfast.vfqc.api.evaluator.dto.request.CreateEvaluatorRequest;
import vn.vinfast.vfqc.api.evaluator.dto.response.EvaluatorVersionResponse;
import vn.vinfast.vfqc.api.evaluator.service.EvaluatorService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class EvaluatorController {

  private final EvaluatorService evaluatorService;

  @Operation(summary = "Create evaluator", description = "Creates an evaluator with its initial immutable version.")
  @PostMapping("/projects/{projectId}/evaluators")
  @ResponseStatus(HttpStatus.CREATED)
  public EvaluatorVersionResponse create(
      @CurrentUserPrincipal CurrentUser currentUser,
      @PathVariable UUID projectId,
      @Valid @RequestBody CreateEvaluatorRequest request) {
    return evaluatorService.create(currentUser, projectId, request);
  }
}
```

Controller naming:

```text
EvaluatorController:
  evaluator create/list/detail/update/delete.

EvaluatorVersionController:
  evaluator version list/create/detail.

EvaluatorValidationController:
  POST /api/v1/projects/{projectId}/evaluators/validate.

EvaluatorFieldSourceController:
  GET /api/v1/projects/{projectId}/evaluator-field-sources.

EvaluatorOperatorController:
  GET /api/v1/evaluator-operators.
```

---

## 9. Service Interface Rules

Service interfaces live under `evaluator/service`.

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
package vn.vinfast.vfqc.api.evaluator.service;

import java.util.UUID;
import vn.vinfast.vfqc.api.common.pagination.CursorPageResponse;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.evaluator.dto.request.CreateEvaluatorRequest;
import vn.vinfast.vfqc.api.evaluator.dto.request.UpdateEvaluatorRequest;
import vn.vinfast.vfqc.api.evaluator.dto.response.EvaluatorResponse;
import vn.vinfast.vfqc.api.evaluator.dto.response.EvaluatorVersionResponse;

public interface EvaluatorService {

  /**
   * Creates an evaluator and its initial immutable version inside a project.
   *
   * @param currentUser authenticated user executing the command
   * @param projectId project that will own the evaluator
   * @param request create request containing evaluator type, config and rules
   * @return created evaluator version response
   */
  EvaluatorVersionResponse create(CurrentUser currentUser, UUID projectId, CreateEvaluatorRequest request);

  /**
   * Lists evaluators visible to the current user within the given project.
   *
   * @param currentUser authenticated user executing the query
   * @param projectId project id used for permission and filtering
   * @param limit maximum page size
   * @param cursor opaque pagination cursor
   * @return cursor-paginated evaluator summaries
   */
  CursorPageResponse<EvaluatorResponse> list(CurrentUser currentUser, UUID projectId, int limit, String cursor);

  /**
   * Updates evaluator metadata only. Evaluator type and versioned config are not changed by this method.
   *
   * @param currentUser authenticated user executing the command
   * @param evaluatorId evaluator id
   * @param request metadata patch request
   * @return updated evaluator response
   */
  EvaluatorResponse updateMetadata(CurrentUser currentUser, UUID evaluatorId, UpdateEvaluatorRequest request);
}
```

Implementation classes live under `evaluator/service/impl`.

Implementation classes must:

```text
1. Use @Service.
2. Use @RequiredArgsConstructor.
3. Use @Slf4j.
4. Use @Transactional for DB mutations.
5. Use @Transactional(readOnly = true) for read-only DB operations.
6. Log state changes but not full rubric/config/expected values.
7. Throw ApiException/ErrorCode for expected business errors.
```

---

## 10. Create Evaluator Flow

Endpoint:

```http
POST /api/v1/projects/{projectId}/evaluators
```

Flow:

```text
1. Require Configure Evaluator permission.
2. Normalize evaluator name using common NamingNormalizer.
3. Enforce unique active evaluator name in project by normalized name policy.
4. Validate evaluator type.
5. Validate config for type.
6. Validate rule list for type.
7. Validate field sources exist.
8. Validate supported operators and expected value requirements.
9. Validate LLM Judge provider/model if config includes defaultJudgeProviderId/defaultJudgeModel.
10. Persist Evaluator.
11. Persist EvaluatorVersion version=1.
12. Persist EvaluatorRule rows for version=1 if any.
13. Return EvaluatorVersionResponse.
```

Transaction:

```text
Create evaluator + version + rules in one @Transactional method.
```

Rules:

```text
1. If version creation fails, evaluator creation must roll back.
2. If rules fail validation, do not create evaluator.
3. latestVersion in response should point to version=1.
4. Do not call external LLM provider during evaluator create.
5. Provider/model validation uses provider metadata, not a live provider call.
```

---

## 11. Create Evaluator Version Flow

Endpoint:

```http
POST /api/v1/evaluators/{evaluatorId}/versions
```

Flow:

```text
1. Require Configure Evaluator permission.
2. Load evaluator excluding deleted evaluators.
3. Reject request if request type differs from evaluator.type.
4. Validate new config and rules.
5. Compute next version number using latest version + 1.
6. Persist new EvaluatorVersion.
7. Persist new EvaluatorRule rows.
8. Do not mutate previous EvaluatorVersion or previous EvaluatorRule rows.
9. Return new EvaluatorVersionResponse.
```

Concurrency rule:

```text
Use repository locking or a unique constraint on (evaluator_id, version) with retry/clear error handling to prevent duplicate version numbers under concurrent edits.
```

Recommended DB invariant:

```text
unique(evaluator_id, version)
```

---

## 12. Evaluator Validation API

Endpoint:

```http
POST /api/v1/projects/{projectId}/evaluators/validate
```

Purpose:

```text
Validate transient evaluator config before saving it.
```

Flow:

```text
1. Require Configure Evaluator permission.
2. Validate evaluator type.
3. Validate config shape.
4. Validate rules.
5. Resolve field sources.
6. Validate operator compatibility.
7. Validate provider/model metadata if LLM Judge config is present.
8. Return validation result with issues/warnings.
9. Do not persist evaluator/version/rules.
```

Transaction:

```text
@Transactional(readOnly = true)
```

Response behavior:

```text
1. Return valid=true when config can be saved.
2. Return valid=false with issue codes for user-correctable config errors if API contract supports this.
3. Use 400 ProblemDetail for malformed request or contract-level validation errors.
```

Recommended service interface:

```java
public interface EvaluatorValidationService {

  /**
   * Validates evaluator config without creating or updating evaluator records.
   *
   * @param currentUser authenticated user executing the validation
   * @param projectId project context for field source and provider visibility checks
   * @param request transient evaluator config request
   * @return validation result containing issues and warnings
   */
  EvaluatorValidationResponse validate(CurrentUser currentUser, UUID projectId, ValidateEvaluatorRequest request);
}
```

---

## 13. Field Source Resolution

Endpoint:

```http
GET /api/v1/projects/{projectId}/evaluator-field-sources
```

Purpose:

```text
Return available source fields for evaluator builder UI.
```

Sources:

```text
Dataset schema columns:
  dataset.question
  dataset.expected
  dataset.<columnName>

Chatbot extracted output:
  extracted.mainAnswer
  extracted.<fieldName>

Execution metadata:
  metadata.latencyMs
  metadata.status
  metadata.tokenUsage.total
  metadata.cost
```

Rules:

```text
1. Dataset sources come from current project dataset schema/version context.
2. Extracted sources come from ACTIVE chatbot target responseExtractionConfig.
3. If multiple targets expose different extracted fields, return union with source target metadata when useful.
4. Field source names must be stable because evaluator rules store them.
5. Do not invent field sources in frontend.
```

Recommended response metadata:

```text
fieldSource
displayName
sourceType
valueType
available
reasonIfUnavailable
targetId optional
targetName optional
```

Recommended service interface:

```java
public interface EvaluatorFieldSourceService {

  /**
   * Resolves field sources available for evaluator configuration in a project.
   *
   * @param currentUser authenticated user executing the query
   * @param projectId project id used for permission and source lookup
   * @return available field sources for evaluator builder UI
   */
  List<EvaluatorFieldSourceResponse> listFieldSources(CurrentUser currentUser, UUID projectId);
}
```

---

## 14. Supported Operators

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

Operator metadata should live in code as a stable enum/catalog, not in database for MVP.

Recommended enum:

```java
public enum EvaluatorOperator {
  EQUALS(true),
  NOT_EQUALS(true),
  CONTAINS(true),
  NOT_CONTAINS(true),
  REGEX(true),
  EXISTS(false),
  NOT_EXISTS(false),
  IS_EMPTY(false),
  IS_NOT_EMPTY(false),
  GREATER_THAN(true),
  GREATER_THAN_OR_EQUAL(true),
  LESS_THAN(true),
  LESS_THAN_OR_EQUAL(true),
  IS_JSON(false),
  CONTAINS_JSON(true);

  private final boolean expectedValueRequired;
}
```

Validation rules:

```text
1. Unsupported operator returns EVALUATOR_OPERATOR_UNSUPPORTED.
2. Operators requiring expected value must reject missing expectedValue.
3. Operators not requiring expected value should reject unexpected expectedValue if API contract says so.
4. Numeric operators require numeric-compatible source and expected value.
5. regex operator must validate pattern syntax and safety.
6. contains_json requires JSON-compatible expected value.
7. exists/not_exists can work with any field source.
```

Regex safety:

```text
1. Reject invalid regex patterns.
2. Consider limiting pattern length.
3. Avoid catastrophic regex by using safe evaluation in runner/adapter or timeouts.
4. Backend validation should flag suspicious patterns when possible.
```

---

## 15. Rule Validation Rules

For each EvaluatorRuleRequest:

```text
1. fieldSource is required.
2. fieldSource must exist in resolved field source catalog unless API supports draft/unavailable source warning.
3. operator must be supported.
4. expectedValue must satisfy operator requirement.
5. expectedValue may be literal or dataset variable reference.
6. Dataset variable reference must use `$dataset.<columnName>`.
7. Dataset variable reference column must exist in project schema.
8. weight must be non-negative and within precision limits.
9. required must be boolean.
10. orderIndex must be unique within the version.
11. Rule count must be within configured MVP max.
```

Recommended max limits:

```text
rules per evaluator version: 100
rubric length: 12000 characters
inputTemplate length: 12000 characters
expectedValue JSON size: 100 KB
```

Limits should be constants in code and documented in validation messages.

---

## 16. LLM Judge Validation Rules

LLM Judge config validation:

```text
1. rubric is required and non-blank.
2. scoreScale min must be less than max.
3. threshold must be inside scoreScale range.
4. outputSchema must be supported; MVP uses VFQC_JUDGE_V1.
5. inputTemplate must reference allowed variables only.
6. defaultJudgeProviderId is optional at evaluator version level if run setup will require provider later.
7. If defaultJudgeProviderId is present, provider must be visible and ACTIVE.
8. If defaultJudgeModel is present, it must be available for the provider when availableModels is known.
9. Config must not contain API key, token, password, Authorization or cookie values.
```

Provider visibility:

```text
Usable judge providers in project context:
  1. Active workspace providers from the project workspace.
  2. Active personal providers owned by current user.
```

Do not call provider API during validation.

Reason:

```text
Evaluator validation should be fast and deterministic. Provider live test belongs to provider test endpoints.
```

---

## 17. Lookup and Readiness Services

`EvaluatorLookupService` is used by TestRun preflight/create to lock evaluator versions.

Recommended methods:

```java
public interface EvaluatorLookupService {

  /**
   * Loads evaluator versions that are selectable for a new TestRun in the given project.
   *
   * @param projectId project id
   * @param evaluatorVersionIds evaluator version ids requested by the run setup
   * @return immutable evaluator version domain snapshots for run locking
   */
  List<EvaluatorVersionSnapshot> findRunnableVersions(UUID projectId, List<UUID> evaluatorVersionIds);
}
```

`EvaluatorReadinessService` is used by project setup-status.

Runnable evaluator requirements:

```text
1. Evaluator exists and is not deleted.
2. Evaluator has at least one version.
3. Latest or selected version config is valid.
4. Rule-based config has valid rules.
5. LLM Judge config has valid provider/model if it requires default judge selection.
6. Referenced field sources still exist or are intentionally allowed as warnings.
```

Setup-status should use metadata checks only and should not call external provider APIs.

---

## 18. Permission Rules

Required permissions:

```text
List/View evaluator:
  Project VIEW.

Create evaluator:
  Project UPDATE / Configure evaluator: OWNER, ADMIN, MEMBER.

Create evaluator version:
  Project UPDATE / Configure evaluator: OWNER, ADMIN, MEMBER.

Update evaluator metadata:
  Project UPDATE / Configure evaluator: OWNER, ADMIN, MEMBER.

Delete evaluator:
  Project UPDATE / Configure evaluator: OWNER, ADMIN, MEMBER.

Validate evaluator config:
  Project UPDATE / Configure evaluator: OWNER, ADMIN, MEMBER.

Resolve field sources:
  Project VIEW.

List operators:
  Authenticated ACTIVE user.
```

Rules:

```text
1. VIEWER can inspect evaluator config and historical versions.
2. VIEWER cannot create/update/delete evaluator or evaluator versions.
3. Users outside workspace should receive 404 for evaluator-specific endpoints to avoid leaking existence.
4. PLATFORM_ADMIN can operate across workspaces except reading plaintext secrets.
```

---

## 19. Transaction Rules

Use transactions as follows:

```text
List/get endpoints:
  @Transactional(readOnly = true)

Create evaluator:
  @Transactional

Update evaluator metadata:
  @Transactional

Soft delete evaluator:
  @Transactional

Create evaluator version:
  @Transactional

Validate evaluator config:
  @Transactional(readOnly = true)

Resolve field sources:
  @Transactional(readOnly = true)

List operators:
  no DB transaction required unless implementation reads custom catalog later
```

Do not call external provider APIs inside evaluator transactions.

---

## 20. Async and Redis Rules

Evaluator CRUD does not require Redis.

MVP rules:

```text
1. Do not use Redis for evaluator configuration state.
2. Do not publish Redis events for evaluator updates unless a separate audit/event system is added later.
3. Do not use @Async for evaluator create/update/validate.
4. LLM Judge execution happens during TestRun in Runner, not in evaluator module.
5. TestRun module locks evaluator versions and publishes START_RUN commands through Redis Streams.
```

---

## 21. Logging Rules

Use `@Slf4j` in implementation classes.

Recommended logs:

```text
INFO:
  evaluator created
  evaluator metadata updated
  evaluator version created
  evaluator soft-deleted

WARN:
  validation failed due to unsupported operator/provider/model/field source

DEBUG:
  counts only, such as ruleCount and fieldSourceCount
```

Allowed log fields:

```text
projectId
evaluatorId
evaluatorVersionId
userId
evaluatorType
version
ruleCount
errorCode
```

Forbidden log fields:

```text
full rubric
full inputTemplate
full expectedValueJson
full configJson
full dataset row content
secret/provider token values
```

Example:

```java
log.info(
    "Created evaluator version. evaluatorId={}, evaluatorVersionId={}, version={}, ruleCount={}",
    evaluatorId,
    evaluatorVersionId,
    version,
    ruleCount);
```

---

## 22. Error Codes

Recommended error codes:

```text
EVALUATOR_NOT_FOUND
EVALUATOR_NAME_DUPLICATE
EVALUATOR_TYPE_INVALID
EVALUATOR_TYPE_IMMUTABLE
EVALUATOR_VERSION_NOT_FOUND
EVALUATOR_VERSION_CONFLICT
EVALUATOR_VERSION_IMMUTABLE
EVALUATOR_CONFIG_INVALID
EVALUATOR_RULE_REQUIRED
EVALUATOR_RULE_INVALID
EVALUATOR_RULE_ORDER_DUPLICATE
EVALUATOR_OPERATOR_UNSUPPORTED
EVALUATOR_EXPECTED_VALUE_REQUIRED
EVALUATOR_FIELD_SOURCE_INVALID
EVALUATOR_DATASET_VARIABLE_NOT_FOUND
EVALUATOR_REGEX_INVALID
EVALUATOR_LLM_JUDGE_CONFIG_INVALID
EVALUATOR_JUDGE_PROVIDER_NOT_FOUND
EVALUATOR_JUDGE_PROVIDER_NOT_ACTIVE
EVALUATOR_JUDGE_MODEL_UNAVAILABLE
EVALUATOR_CUSTOM_JS_NOT_ALLOWED
```

Rules:

```text
1. Use common Problem Details factory.
2. Use stable error code and messageKey.
3. Do not leak full config/rubric in error detail.
4. Return 404 instead of 403 when needed to avoid leaking evaluator existence.
```

---

## 23. Integration with Target and TestRun

Target integration:

```text
1. Field source service reads responseExtractionConfig from ACTIVE chatbot targets.
2. Evaluator rules can reference extracted.mainAnswer and extracted.<fieldName>.
3. If target extraction fields change later, existing EvaluatorVersion remains immutable.
4. TestRun preflight should detect stale field source references before execution.
```

Dataset integration:

```text
1. Expected values may reference `$dataset.<columnName>`.
2. Dataset variables are validated against project dataset schema during evaluator config save.
3. TestRun locks dataset version/schema version and evaluator versions together.
```

Provider integration:

```text
1. LLM Judge config may reference defaultJudgeProviderId/defaultJudgeModel.
2. Run setup may override/lock actual judge provider/model depending on API contract.
3. Provider secret is not resolved in evaluator module.
4. Runner resolves runtime secret during execution.
```

TestRun integration:

```text
EvaluatorLookupService returns immutable version snapshots for TestRun creation.
```

Snapshot should include:

```text
evaluatorId
evaluatorVersionId
evaluatorType
version
configJson
rules sorted by orderIndex
createdAt
```

---

## 24. Testing Checklist

Unit tests:

```text
1. EvaluatorConfigValidator validates RULE_BASED config.
2. EvaluatorConfigValidator validates LLM_JUDGE config.
3. EvaluatorConfigValidator validates MIXED config.
4. EvaluatorRuleValidator rejects unsupported operators.
5. EvaluatorRuleValidator enforces expectedValue rules.
6. EvaluatorRuleValidator validates dataset variable references.
7. Regex validation rejects invalid patterns.
8. FieldSourceService returns dataset and extracted sources.
9. OperatorService returns stable MVP operator metadata.
10. Mapper/assembler does not expose deleted fields or raw internals.
```

Service tests:

```text
1. Create evaluator persists evaluator + version 1 + rules in one transaction.
2. Failed version creation rolls back evaluator creation.
3. Create evaluator version does not mutate previous version/rules.
4. Concurrent version creation does not create duplicate version numbers.
5. Soft-deleted evaluator is hidden from normal list.
6. Update metadata does not change evaluator type.
7. Validation API does not persist anything.
8. Provider/model validation rejects inactive provider.
```

Controller tests:

```text
1. VIEWER can list/view but cannot create/update/delete.
2. MEMBER can create evaluator and create new version.
3. Unauthorized request returns 401.
4. User outside workspace receives 404 for evaluator detail.
5. Validation errors return RFC 9457 Problem Details.
```

Security tests:

```text
1. Custom JavaScript fields are rejected.
2. Config containing token/API key-like fields is rejected or masked according to policy.
3. Logs do not contain full rubric or expected values.
4. ProblemDetails do not echo full invalid config.
```

---

## 25. AI Coding Rules

When generating evaluator code:

```text
1. Do not update existing EvaluatorVersion rows.
2. Do not update existing EvaluatorRule rows for an existing version.
3. Do not accept user-provided JavaScript.
4. Do not execute LLM Judge inside evaluator CRUD/validation.
5. Do not convert evaluator config directly to Promptfoo config in backend evaluator module.
6. Do not write manual mapper helpers inside services.
7. Do not log full rubric/config/expected values.
8. Do not bypass ProjectPermissionResolver.
9. Do not make evaluator module depend on runner implementation.
10. Do not use Redis for evaluator CRUD state.
```
