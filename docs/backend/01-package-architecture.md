# VinFast QC Copilot - Backend Package Architecture

Version: `v0.1`  
Status: Backend Implementation Spec Draft  
Applies to: `apps/api` Spring Boot backend  
Aligned with: PRD v0.7, ERD v0.5, API Contracts `00` to `10`

---

## 1. Purpose

This document defines the package architecture for the Spring Boot backend.

It gives AI coding agents a stable structure so generated code does not become inconsistent across modules.

Goals:

```text
1. Keep feature modules easy to find.
2. Keep controllers, DTOs, services, entities, repositories and mappers separated.
3. Keep business logic in service implementations.
4. Keep common infrastructure reusable but not overloaded.
5. Make cross-module dependencies explicit.
6. Avoid duplicated helper methods and random utility classes.
```

---

## 2. Root Package

Use this root package unless the existing project already uses another one:

```text
vn.vinfast.vfqc.api
```

All backend code should live under this root.

If the actual project root package differs, keep the same sub-package structure under the actual root.

---

## 3. High-Level Package Tree

Recommended package tree:

```text
vn.vinfast.vfqc.api
  VfqCApplication.java

  common
    config
    constants
    error
    validation
    pagination
    security
    permission
    naming
    masking
    audit
    json
    time
    async
    redis
    storage
    util

  auth
  user
  workspace
  project
  secret
  provider
  dataset
  target
  evaluator
  testrun
  exportjob
  runner
  sse
  artifact
```

Module names should be short and domain-oriented.

Use:

```text
provider
```

not:

```text
llmproviderconfigmanagement
```

Use:

```text
testrun
```

not:

```text
test_run
```

---

## 4. Common Package Responsibilities

### 4.1. `common/config`

Contains Spring configuration classes.

Examples:

```text
OpenApiConfig
JpaConfig
AsyncConfig
RedisConfig
WebMvcConfig
SecurityConfig
ClockConfig
```

Rules:

```text
1. Keep configuration classes focused.
2. Do not put business logic in config classes.
3. Put bean definitions here only when they are truly cross-cutting.
```

---

### 4.2. `common/error`

Contains shared error handling and Problem Details support.

Recommended files:

```text
ApiException.java
ErrorCode.java
GlobalExceptionHandler.java
ProblemDetailFactory.java
FieldErrorResponse.java
```

Responsibilities:

```text
1. Convert domain exceptions to RFC 9457 Problem Details.
2. Attach code, messageKey and traceId.
3. Normalize validation errors.
4. Prevent raw exception messages from leaking to users.
```

Rules:

```text
1. Error codes are stable API contract values.
2. Error messages should be English defaults.
3. Frontend may localize using code/messageKey.
4. Do not throw generic RuntimeException for expected business errors.
```

---

### 4.3. `common/pagination`

Contains cursor/offset pagination helpers.

Recommended files:

```text
PageRequestParam.java
PageResponse.java
CursorPageResponse.java
CursorCodec.java
```

Rules:

```text
1. Match docs/api/01-api-conventions.md.
2. Do not define a different pagination shape per module.
3. Keep cursor opaque to frontend.
```

---

### 4.4. `common/security`

Contains authentication-level classes.

Recommended files:

```text
CurrentUser.java
JwtAuthenticationFilter.java
SecurityUserDetails.java
TokenService.java
PasswordHasher.java
RunnerServiceAuthenticationFilter.java
```

Rules:

```text
1. User JWT authentication is separate from runner service authentication.
2. Runner service tokens must not be treated as normal users.
3. CurrentUser should expose id, email, systemRole and status.
```

---

### 4.5. `common/permission`

Contains authorization helpers.

Recommended files:

```text
PermissionService.java
WorkspacePermissionResolver.java
ProjectAccessResolver.java
PermissionAction.java
RequireProjectPermission.java
RequireWorkspacePermission.java
```

Responsibilities:

```text
1. Resolve workspace/project access.
2. Enforce system role + workspace role matrix.
3. Deny plaintext secret reads for every role.
4. Return 404 when existence must not be leaked.
```

Rules:

```text
1. Permission logic must be centralized.
2. Controllers should not implement permission matrix manually.
3. Services may call permission service before sensitive actions.
```

---

### 4.6. `common/naming`

Contains name normalization and duplicate checking support.

Recommended files:

```text
NameNormalizer.java
NamePolicy.java
NormalizedName.java
DuplicateNameChecker.java
```

Responsibilities:

```text
1. Trim user-provided names.
2. Collapse repeated whitespace where policy requires it.
3. Enforce max length.
4. Enforce allowed character policy.
5. Provide normalized value for uniqueness checks.
```

Rules:

```text
1. Use the naming policy defined in docs/api/01-api-conventions.md.
2. Store display name as user intended after trimming.
3. Use normalized name for case-insensitive uniqueness.
4. Duplicate errors should use module-specific error codes.
```

---

### 4.7. `common/masking`

Contains sensitive data masking.

Recommended files:

```text
VfqcSensitiveDataMasker.java
SensitiveFieldDetector.java
SensitiveValueDetector.java
MaskingPolicy.java
MaskingContext.java
MaskingTarget.java
MaskedValue.java
SanitizationResult.java
SanitizationFinding.java
JsonSanitizer.java
HeaderSanitizer.java
CurlSanitizer.java
ArtifactSanitizer.java
FormulaInjectionSanitizer.java
```

Responsibilities:

```text
1. Mask raw cURL before persistence.
2. Mask request/response examples.
3. Mask provider test logs.
4. Mask Promptfoo config and artifacts.
5. Prevent logs from exposing secrets.
```

Rules:

```text
1. Masking must be reusable across target/provider/testrun/runner/artifact modules.
2. Do not implement one-off masking regexes in controllers/services.
3. Keep a safe default denylist: authorization, api-key, x-api-key, token, cookie, password, secret.
4. Do not decrypt secrets or access database from masking components.
5. Return SanitizationResult metadata without original sensitive values.
```

---

### 4.8. `common/redis`

Contains Redis client and stream helpers used by runner orchestration.

Recommended files:

```text
RedisStreamNames.java
RedisStreamPublisher.java
RedisStreamConsumerSupport.java
RedisPendingRecoveryService.java
RedisDeadLetterPublisher.java
```

Responsibilities:

```text
1. Publish compact START_RUN and CANCEL_RUN commands.
2. Read runner events.
3. Claim pending messages during recovery.
4. Send poison messages to DLQ.
```

Rules:

```text
1. Do not put business logic in generic Redis helpers.
2. Do not put secrets or full configs in stream payloads.
3. Keep stream names centralized.
```

---

### 4.9. `common/storage`

Contains abstraction for local artifact/file storage.

Recommended files:

```text
StorageService.java
LocalStorageService.java
StoragePathPolicy.java
StoredFileMetadata.java
```

Responsibilities:

```text
1. Save uploaded Excel source files if needed.
2. Save sanitized generated artifacts.
3. Resolve safe download/read paths.
4. Prevent path traversal.
```

---

## 5. Feature Module Standard Layout

Each feature module should follow this layout where applicable:

```text
<module>/
  controller/
  dto/
    request/
    response/
  entity/
  enums/
  repository/
  mapper/
  service/
    impl/
  validator/
  assembler/
  event/
```

Not every module needs every folder.

Guidelines:

```text
controller   HTTP adapter only.
dto          API request/response records.
entity       JPA persistence entities.
enums        Java enums matching PostgreSQL enum values.
repository   Spring Data JPA repositories.
mapper       MapStruct mappers.
service      Service interfaces.
service/impl Service implementations.
validator    Module-specific validation components.
assembler    Complex response builders that are not simple mappings.
event        Internal domain/application events.
```

---

## 6. Module List and Responsibilities

### 6.1. `auth`

Implements API contract `02-auth-permission-contract.md`.

Responsibilities:

```text
1. Login.
2. Refresh access token.
3. Logout.
4. Current user endpoint.
5. Refresh token persistence if rotation/revoke is implemented.
```

Recommended layout:

```text
auth/
  controller/AuthController.java
  dto/request/LoginRequest.java
  dto/response/LoginResponse.java
  dto/response/RefreshTokenResponse.java
  dto/response/CurrentUserResponse.java
  entity/AuthRefreshToken.java
  repository/AuthRefreshTokenRepository.java
  service/AuthService.java
  service/TokenRotationService.java
  service/impl/AuthServiceImpl.java
  service/impl/TokenRotationServiceImpl.java
  mapper/AuthMapper.java
```

Rules:

```text
1. Refresh token must never be returned in JSON body.
2. Store only refresh token hash if persistence is implemented.
3. Access token is returned to frontend.
4. Refresh token is set/cleared through HttpOnly cookie.
```

---

### 6.2. `user`

Owns user persistence and user lookup.

Recommended layout:

```text
user/
  entity/User.java
  enums/SystemRole.java
  enums/UserStatus.java
  repository/UserRepository.java
  mapper/UserMapper.java
  service/UserQueryService.java
  service/impl/UserQueryServiceImpl.java
```

Rules:

```text
1. Keep user profile/basic lookup here.
2. Do not put workspace permission logic here.
3. Do not expose password hash in any response.
```

---

### 6.3. `workspace`

Implements workspace and member management from `03-workspace-project-api.md`.

Recommended layout:

```text
workspace/
  controller/WorkspaceController.java
  controller/WorkspaceMemberController.java
  dto/request/CreateWorkspaceRequest.java
  dto/request/UpdateWorkspaceRequest.java
  dto/request/AddWorkspaceMemberRequest.java
  dto/response/WorkspaceResponse.java
  dto/response/WorkspaceMemberResponse.java
  entity/Workspace.java
  entity/WorkspaceMember.java
  enums/WorkspaceType.java
  enums/WorkspaceRole.java
  enums/WorkspaceMemberStatus.java
  repository/WorkspaceRepository.java
  repository/WorkspaceMemberRepository.java
  mapper/WorkspaceMapper.java
  mapper/WorkspaceMemberMapper.java
  service/WorkspaceService.java
  service/WorkspaceMemberService.java
  service/impl/WorkspaceServiceImpl.java
  service/impl/WorkspaceMemberServiceImpl.java
```

Rules:

```text
1. Create TEAM workspace manually.
2. Create PERSONAL workspace during onboarding if implemented.
3. Creator becomes OWNER.
4. Delete/archive is soft delete.
```

---

### 6.4. `project`

Implements project APIs and setup status.

Recommended layout:

```text
project/
  controller/ProjectController.java
  controller/ProjectSetupStatusController.java
  dto/request/CreateProjectRequest.java
  dto/request/UpdateProjectRequest.java
  dto/response/ProjectResponse.java
  dto/response/ProjectSetupStatusResponse.java
  dto/response/ProjectSetupStepResponse.java
  dto/response/ProjectSetupBlockerResponse.java
  entity/Project.java
  repository/ProjectRepository.java
  mapper/ProjectMapper.java
  service/ProjectService.java
  service/ProjectSetupStatusService.java
  service/impl/ProjectServiceImpl.java
  service/impl/ProjectSetupStatusServiceImpl.java
  assembler/ProjectSetupStatusAssembler.java
```

Rules:

```text
1. Project belongs to one workspace.
2. Creating a project also creates default PROJECT dataset schema in the same transaction.
3. Setup status is read-only aggregation and should not mutate state.
4. Setup status should help frontend wizard decide next action before preflight.
```

---

### 6.5. `secret`

Implements secret metadata and secret rotation from `04-secret-provider-api.md`.

Recommended layout:

```text
secret/
  controller/SecretController.java
  dto/request/CreateSecretRequest.java
  dto/request/RotateSecretRequest.java
  dto/request/UpdateSecretRequest.java
  dto/response/SecretResponse.java
  dto/response/SecretSummaryResponse.java
  entity/Secret.java
  enums/SecretScope.java
  enums/SecretStatus.java
  repository/SecretRepository.java
  mapper/SecretMapper.java
  service/SecretService.java
  service/SecretEncryptionService.java
  service/impl/SecretServiceImpl.java
  service/impl/SecretEncryptionServiceImpl.java
```

Rules:

```text
1. Plaintext secret may be accepted only in create/rotate/runtime-only flows.
2. API responses never contain plaintext secret.
3. Encrypted value is internal only.
4. Masked preview must be safe.
```

---

### 6.6. `provider`

Implements LLM provider APIs and provider test history from `04-secret-provider-api.md`.

Recommended layout:

```text
provider/
  controller/LlmProviderController.java
  controller/LlmProviderTestHistoryController.java
  dto/request/CreateLlmProviderRequest.java
  dto/request/UpdateLlmProviderRequest.java
  dto/request/TestLlmProviderRequest.java
  dto/response/LlmProviderResponse.java
  dto/response/LlmProviderSummaryResponse.java
  dto/response/LlmProviderTestResponse.java
  dto/response/LlmProviderTestLogResponse.java
  entity/LlmProvider.java
  entity/LlmProviderTestLog.java
  enums/ProviderScope.java
  enums/ProviderType.java
  enums/ProviderStatus.java
  repository/LlmProviderRepository.java
  repository/LlmProviderTestLogRepository.java
  mapper/LlmProviderMapper.java
  mapper/LlmProviderTestLogMapper.java
  service/LlmProviderService.java
  service/LlmProviderTestService.java
  service/impl/LlmProviderServiceImpl.java
  service/impl/LlmProviderTestServiceImpl.java
```

Rules:

```text
1. Provider references Secret by id.
2. Provider config is sanitized.
3. Provider test history stores masked request/response/error metadata.
4. Provider test should not expose plaintext secret in response or logs.
```

---

### 6.7. `dataset`

Implements dataset schema/version/row/import/export APIs from `05-dataset-api.md`.

Recommended layout:

```text
dataset/
  controller/DatasetSchemaController.java
  controller/DatasetController.java
  controller/DatasetVersionController.java
  controller/DatasetRowController.java
  controller/DatasetImportJobController.java
  controller/DatasetExportController.java
  dto/request/...
  dto/response/...
  entity/DatasetSchema.java
  entity/DatasetSchemaColumn.java
  entity/Dataset.java
  entity/DatasetVersion.java
  entity/DatasetRow.java
  entity/DatasetImportJob.java
  enums/...
  repository/...
  mapper/...
  service/DatasetSchemaService.java
  service/DatasetService.java
  service/DatasetVersionService.java
  service/DatasetRowService.java
  service/DatasetImportService.java
  service/DatasetExportService.java
  service/impl/...
  validator/DatasetSchemaValidator.java
  validator/DatasetVersionValidator.java
  assembler/DatasetVariableAssembler.java
```

Rules:

```text
1. ACTIVE DatasetVersion is immutable.
2. Editing happens only on DRAFT DatasetVersion.
3. Publish DRAFT creates ACTIVE current version.
4. TestRun locks dataset version and schema version.
5. Excel export uses common exportjob module.
```

---

### 6.8. `target`

Implements chatbot target APIs from `06-chatbot-target-api.md`.

Recommended layout:

```text
target/
  controller/ChatbotTargetController.java
  controller/ChatbotTargetExampleController.java
  controller/ChatbotTargetCurlController.java
  controller/ChatbotTargetConnectionController.java
  controller/ChatbotTargetExtractionController.java
  dto/request/InspectCurlRequest.java
  dto/request/CreateChatbotTargetRequest.java
  dto/request/UpdateChatbotTargetRequest.java
  dto/request/TestConnectionRequest.java
  dto/request/ResponseExtractionConfigRequest.java
  dto/response/ChatbotTargetSummaryResponse.java
  dto/response/ChatbotTargetDetailResponse.java
  dto/response/ChatbotTargetExampleResponse.java
  dto/response/InspectCurlResponse.java
  dto/response/TestConnectionResponse.java
  dto/response/ResponseExtractionConfigResponse.java
  entity/ChatbotTarget.java
  entity/ChatbotTargetExample.java
  enums/ChatbotTargetStatus.java
  enums/HttpBodyType.java
  enums/SecretBindingLocation.java
  enums/DatasetTargetLocation.java
  model/ParsedHttpRequestConfig.java
  model/SecretBinding.java
  model/DatasetRequestMapping.java
  model/ResponseExtractionConfig.java
  repository/ChatbotTargetRepository.java
  repository/ChatbotTargetExampleRepository.java
  mapper/ChatbotTargetMapper.java
  mapper/ChatbotTargetExampleMapper.java
  service/ChatbotTargetService.java
  service/CurlInspectionService.java
  service/ChatbotTargetTestService.java
  service/ResponseExtractionService.java
  service/ChatbotTargetLookupService.java
  service/ChatbotTargetReadinessService.java
  service/impl/...
  validator/ChatbotTargetValidator.java
  validator/DatasetRequestMappingValidator.java
  validator/SecretBindingValidator.java
  validator/ResponseExtractionValidator.java
  assembler/ChatbotTargetResponseAssembler.java
  client/HttpTargetClient.java
```

Rules:

```text
1. Never shell-execute raw cURL.
2. Persist only sanitized raw cURL.
3. Secret values become SecretBinding references.
4. Test connection examples are masked before persistence.
5. ACTIVE target requires valid request config and response extraction config.
```

---

### 6.9. `evaluator`

Implements evaluator APIs from `07-evaluator-api.md`.

Recommended layout:

```text
evaluator/
  controller/EvaluatorController.java
  controller/EvaluatorVersionController.java
  controller/EvaluatorMetadataController.java
  controller/EvaluatorValidationController.java
  controller/EvaluatorFieldSourceController.java
  controller/EvaluatorOperatorController.java
  dto/request/CreateEvaluatorRequest.java
  dto/request/UpdateEvaluatorRequest.java
  dto/request/CreateEvaluatorVersionRequest.java
  dto/request/ValidateEvaluatorRequest.java
  dto/request/EvaluatorRuleRequest.java
  dto/request/RuleBasedConfigRequest.java
  dto/request/LlmJudgeConfigRequest.java
  dto/response/EvaluatorResponse.java
  dto/response/EvaluatorVersionResponse.java
  dto/response/EvaluatorRuleResponse.java
  dto/response/EvaluatorValidationResponse.java
  dto/response/EvaluatorOperatorResponse.java
  dto/response/EvaluatorFieldSourceResponse.java
  entity/Evaluator.java
  entity/EvaluatorVersion.java
  entity/EvaluatorRule.java
  enums/EvaluatorType.java
  enums/EvaluatorOperator.java
  enums/EvaluatorAggregation.java
  enums/JudgeOutputSchema.java
  model/EvaluatorConfigModel.java
  model/RuleBasedConfigModel.java
  model/LlmJudgeConfigModel.java
  repository/EvaluatorRepository.java
  repository/EvaluatorVersionRepository.java
  repository/EvaluatorRuleRepository.java
  mapper/EvaluatorMapper.java
  mapper/EvaluatorVersionMapper.java
  mapper/EvaluatorRuleMapper.java
  service/EvaluatorService.java
  service/EvaluatorVersionService.java
  service/EvaluatorValidationService.java
  service/EvaluatorFieldSourceService.java
  service/EvaluatorOperatorService.java
  service/EvaluatorLookupService.java
  service/EvaluatorReadinessService.java
  service/impl/...
  validator/EvaluatorConfigValidator.java
  validator/EvaluatorRuleValidator.java
  validator/LlmJudgeConfigValidator.java
  validator/EvaluatorProviderValidator.java
  assembler/EvaluatorResponseAssembler.java
  assembler/EvaluatorVersionResponseAssembler.java
```

Rules:

```text
1. EvaluatorVersion is immutable.
2. Editing evaluator config creates a new version.
3. User-provided custom JavaScript is not accepted in MVP.
4. Field sources are resolved from dataset schema and chatbot response extraction config.
```

---

### 6.10. `testrun`

Implements test run APIs from `08-test-run-api.md`.

Recommended layout:

```text
testrun/
  controller/TestRunController.java
  controller/TestRunResultController.java
  controller/TestRunReviewController.java
  controller/TestRunArtifactController.java
  controller/TestRunComparisonMatrixController.java
  dto/request/RunPreflightRequest.java
  dto/request/CreateTestRunRequest.java
  dto/request/CancelTestRunRequest.java
  dto/request/UpsertTestCaseResultReviewRequest.java
  dto/request/BulkReviewTestCaseResultsRequest.java
  dto/response/...
  entity/TestRun.java
  entity/TestRunTarget.java
  entity/TestRunEvaluatorVersion.java
  entity/TestCaseResult.java
  entity/TestCaseResultReview.java
  enums/...
  repository/...
  mapper/...
  service/TestRunPreflightService.java
  service/TestRunCommandService.java
  service/TestRunQueryService.java
  service/TestRunResultService.java
  service/TestRunReviewService.java
  service/TestRunComparisonMatrixService.java
  service/impl/...
  assembler/TestRunDetailAssembler.java
  assembler/ComparisonMatrixAssembler.java
  state/TestRunStateMachine.java
  validator/TestRunValidator.java
```

Rules:

```text
1. TestRun is immutable execution record.
2. Rerun creates a new TestRun.
3. Create TestRun locks dataset/schema/evaluator/target/provider snapshots.
4. Comparison may include multiple CHATBOT_TARGET and/or multiple LLM_BASELINE targets.
5. Testcase FAILED is business result, not TestRun FAILED.
6. Comparison matrix should be assembled by backend for stable pagination/grouping.
```

---

### 6.11. `runner`

Implements backend side of runner orchestration and internal APIs from `10-runner-internal-api.md`.

Recommended layout:

```text
runner/
  controller/RunnerInternalController.java
  controller/RunnerEventFallbackController.java
  dto/request/RunnerRegisterRequest.java
  dto/request/RunnerHeartbeatRequest.java
  dto/request/RuntimeSecretResolutionRequest.java
  dto/request/RunnerArtifactMetadataRequest.java
  dto/request/RunnerEventIngestRequest.java
  dto/response/RunnerRegisterResponse.java
  dto/response/RunnerHeartbeatResponse.java
  dto/response/RunnerConfigResponse.java
  dto/response/RuntimeSecretBundleResponse.java
  dto/response/RunnerControlStateResponse.java
  entity/RunnerInstance.java
  entity/RunnerCommand.java
  entity/RunnerEvent.java
  enums/RunnerInstanceStatus.java
  repository/RunnerInstanceRepository.java
  repository/RunnerCommandRepository.java
  repository/RunnerEventRepository.java
  mapper/RunnerMapper.java
  service/RunnerInstanceService.java
  service/RunnerConfigService.java
  service/RuntimeSecretService.java
  service/RunnerCommandPublisher.java
  service/RunnerEventIngestionService.java
  service/RunnerEventConsumerService.java
  service/RunnerPendingRecoveryService.java
  service/impl/...
```

Rules:

```text
1. Runner internal API uses runner service token, not user JWT.
2. Runner config response uses locked TestRun snapshots.
3. Runtime secrets are returned only to authenticated runner.
4. Runner events are persisted before SSE broadcast.
5. Redis Streams are primary transport; HTTP event endpoint is fallback.
```

---

### 6.12. `sse`

Implements SSE subscription from `09-sse-events-contract.md` and `docs/backend/11-sse-implementation.md`.

Recommended layout:

```text
sse/
  controller/TestRunSseController.java
  dto/TestRunSseEventEnvelope.java
  dto/TestRunSsePayload.java
  dto/TestRunSseReplayRequest.java
  enums/SseEventType.java
  service/TestRunSseService.java
  service/SseReplayService.java
  service/SseEmitterRegistry.java
  service/SseHeartbeatService.java
  service/SseEventSerializer.java
  service/impl/TestRunSseServiceImpl.java
  service/impl/SseReplayServiceImpl.java
  service/impl/SseEmitterRegistryImpl.java
  service/impl/SseHeartbeatServiceImpl.java
  service/impl/SseEventSerializerImpl.java
  model/RegisteredSseClient.java
  model/SseClientKey.java
  model/ReplayCursor.java
```

Rules:

```text
1. SSE is read-only.
2. Use Authorization Bearer access token.
3. Do not put token in query string.
4. Replay using runner_events.sequence_no.
5. SSE id equals runner_events.sequence_no.
6. Heartbeat is not persisted as runner event.
7. SSE code must not read Redis directly.
8. Broadcast only persisted RunnerEvent records after database commit.
```

---

### 6.13. `exportjob`

Implements common export job infrastructure used by dataset export and test run result export. See `docs/backend/12-export-job-implementation.md`.

Recommended layout:

```text
exportjob/
  controller/ExportJobController.java
  dto/request/CreateDatasetExportRequest.java
  dto/request/CreateTestRunExportRequest.java
  dto/request/ExportJobQueryRequest.java
  dto/response/ExportJobResponse.java
  dto/response/ExportJobDownloadResponse.java
  entity/ExportJob.java
  enums/ExportJobType.java
  enums/ExportFormat.java
  enums/ExportJobStatus.java
  enums/ExportResultMode.java
  repository/ExportJobRepository.java
  mapper/ExportJobMapper.java
  service/ExportJobService.java
  service/ExportJobQueryService.java
  service/ExportJobWorker.java
  service/ExportFileWriter.java
  service/ExportFileNamePolicy.java
  service/ExportDownloadService.java
  service/impl/ExportJobServiceImpl.java
  service/impl/ExportJobQueryServiceImpl.java
  service/impl/ExportJobWorkerImpl.java
  service/impl/XlsxExportFileWriter.java
  service/impl/ExportFileNamePolicyImpl.java
  service/impl/ExportDownloadServiceImpl.java
```

Rules:

```text
1. Dataset export and run result export reuse the same ExportJobResponse DTO.
2. Export execution may be async.
3. Export files must be generated from authorized visible data.
4. Result export defaults to FINAL reviewed result unless request explicitly asks for MACHINE.
5. Export APIs must return downloadUrl, not internal filePath.
6. Download endpoint must check permission again.
7. Export files must not contain plaintext secrets or unsanitized raw payloads.
```

---

### 6.14. `artifact`

Owns artifact metadata and sanitized artifact content access.

Recommended layout:

```text
artifact/
  controller/ArtifactController.java
  dto/response/TestRunArtifactResponse.java
  entity/PromptfooRunArtifact.java
  repository/PromptfooRunArtifactRepository.java
  mapper/PromptfooRunArtifactMapper.java
  service/ArtifactService.java
  service/ArtifactContentService.java
  service/impl/...
```

Rules:

```text
1. DB stores artifact metadata and hashes only.
2. File content lives in artifact storage.
3. API must not expose artifacts when sanitized=false.
4. Sanitize before writing file.
```

---

## 7. Dependency Direction

Recommended dependency direction:

```text
controller -> service interface -> service implementation -> repository/entity
                                  -> mapper
                                  -> validator
                                  -> common services
```

Allowed dependencies:

```text
1. Feature modules may depend on common/*.
2. Feature service implementations may depend on other feature service interfaces for cross-module use cases.
3. Repositories should usually be used only inside their owning module service implementation.
4. Assemblers may depend on repositories or query services when building complex read models.
```

Avoid:

```text
1. Controllers calling repositories directly.
2. One module writing another module's entity state directly without a service boundary.
3. Common package depending on feature modules.
4. Mappers doing permission checks.
5. Entities depending on DTOs.
```

---

## 8. Cross-Module Access Rules

Use query services for cross-module reads.

Examples:

```text
ProjectSetupStatusService may query:
- Dataset readiness through DatasetReadinessQueryService.
- Target readiness through ChatbotTargetReadinessQueryService.
- Evaluator readiness through EvaluatorReadinessQueryService.
- Provider readiness through ProviderReadinessQueryService.

TestRunPreflightService may query:
- DatasetVersionService.
- ChatbotTargetService.
- EvaluatorVersionService.
- LlmProviderService.
```

Recommended query service naming:

```text
<Domain>QueryService
<Domain>ReadinessQueryService
<Domain>LookupService
```

Rules:

```text
1. Query services may be read-only and transactional.
2. Mutations should go through the owning module's command service.
3. Avoid directly injecting many foreign repositories into a service if a query service can express the dependency better.
```

---

## 9. Controller Naming

Controller names should match resource areas.

Examples:

```text
WorkspaceController
WorkspaceMemberController
ProjectController
ProjectSetupStatusController
SecretController
LlmProviderController
DatasetController
DatasetVersionController
ChatbotTargetController
EvaluatorController
TestRunController
TestRunResultController
TestRunComparisonMatrixController
RunnerInternalController
```

Rules:

```text
1. Do not put all endpoints of a module into one huge controller if it becomes hard to read.
2. Split by resource when it improves readability.
3. Keep route base paths aligned with API contracts.
```

---

## 10. Service Naming

Service interfaces should represent use cases or cohesive domain capabilities.

Good:

```text
ProjectService
ProjectSetupStatusService
DatasetImportService
TestRunPreflightService
TestRunCommandService
RuntimeSecretService
```

Avoid vague names:

```text
Manager
Helper
Processor
CommonService
ProjectUtils
```

Implementation naming:

```text
ProjectServiceImpl
DatasetImportServiceImpl
TestRunCommandServiceImpl
```

Rules:

```text
1. Interface in service/.
2. Implementation in service/impl/.
3. Interface methods have Javadoc.
4. Implementation has @Slf4j.
5. Implementation methods have @Transactional when touching DB.
```

---

## 11. DTO Naming

Request records:

```text
Create<Project>Request
Update<Project>Request
Patch<Project>Request
RunPreflightRequest
CancelTestRunRequest
```

Response records:

```text
<Project>Response
<Project>SummaryResponse
<Project>DetailResponse
CollectionResponse<T>
ExportJobResponse
```

Rules:

```text
1. Use records for DTOs.
2. Request records require validation annotations.
3. Request fields should have @Schema examples.
4. Do not suffix records with Dto if API contract already names them Request/Response.
5. Do not expose entity objects from response records.
```

---

## 12. Entity Naming

Entities should map to ERD names but use Java singular class names.

Examples:

```text
users                  -> User
workspaces             -> Workspace
workspace_members      -> WorkspaceMember
llm_providers          -> LlmProvider
dataset_versions       -> DatasetVersion
chatbot_targets        -> ChatbotTarget
test_case_results      -> TestCaseResult
llm_provider_test_logs -> LlmProviderTestLog
```

Rules:

```text
1. Java class singular.
2. Table name plural snake_case.
3. Field names camelCase.
4. Column names snake_case.
5. Enums match PostgreSQL enum values exactly.
```

---

## 13. Repository Naming

Repository names:

```text
UserRepository
WorkspaceRepository
ProjectRepository
DatasetVersionRepository
TestRunRepository
```

Rules:

```text
1. Repository belongs to owning module.
2. Prefer explicit query methods for common lookup.
3. Use @Query for complex queries.
4. Keep business rule naming in service, not repository.
5. Avoid returning deleted/archived records from normal queries unless method name says so.
```

Example:

```java
Optional<Project> findByIdAndDeletedAtIsNull(UUID id);
boolean existsByWorkspaceIdAndNormalizedNameAndDeletedAtIsNull(UUID workspaceId, String normalizedName);
```

---

## 14. Mapper Naming

Mapper names:

```text
ProjectMapper
SecretMapper
LlmProviderMapper
DatasetVersionMapper
TestRunMapper
```

Rules:

```text
1. Use MapStruct with componentModel = "spring".
2. Keep simple entity-to-response mapping in mapper.
3. Use assembler for complex aggregated response.
4. Do not access repositories from MapStruct mapper.
5. Do not hide masking or permission logic in mapper.
```

---

## 15. Validator Naming

Validators should contain domain validation that is too complex for Bean Validation.

Examples:

```text
DatasetSchemaValidator
ChatbotTargetValidator
ResponseExtractionValidator
EvaluatorConfigValidator
TestRunValidator
```

Rules:

```text
1. Bean Validation handles simple request field validation.
2. Validators handle cross-field and cross-entity validation.
3. Validators throw domain ApiException/ErrorCode, not generic exceptions.
4. Services call validators before mutation.
```

---

## 16. Assembler Naming

Assemblers build complex responses that are not simple entity mappings.

Examples:

```text
ProjectSetupStatusAssembler
TestRunDetailAssembler
ComparisonMatrixAssembler
ReviewSummaryAssembler
DashboardSummaryAssembler
```

Use assembler when:

```text
1. Response needs data from multiple modules.
2. Response includes computed metrics.
3. Response has nested grouping.
4. Response requires permission-aware field omission.
```

Do not use assembler for simple entity-to-response mapping.

---

## 17. Error Code Location

Common error enum may live in:

```text
common/error/ErrorCode.java
```

Rules:

```text
1. Keep API-visible error codes stable.
2. Group codes by module using prefixes.
3. Use English code names.
4. Do not expose database constraint names directly.
```

Examples:

```text
AUTHENTICATION_REQUIRED
USER_DISABLED
WORKSPACE_NOT_FOUND
PROJECT_NAME_DUPLICATE
SECRET_SCOPE_INVALID
PROVIDER_TEST_FAILED
DATASET_VERSION_NOT_DRAFT
CHATBOT_TARGET_NOT_READY
EVALUATOR_VERSION_NOT_FOUND
TEST_RUN_NOT_CANCELLABLE
EXPORT_JOB_NOT_READY
```

---

## 18. Naming Policy Location

Name normalization must be centralized.

Recommended package:

```text
common/naming
```

Use it in:

```text
WorkspaceServiceImpl
ProjectServiceImpl
SecretServiceImpl
LlmProviderServiceImpl
DatasetServiceImpl
ChatbotTargetServiceImpl
EvaluatorServiceImpl
```

Rules:

```text
1. Do not trim/normalize names ad hoc in each controller.
2. Normalize before duplicate check.
3. Store normalized name if ERD/table has a column for it.
4. If ERD does not yet have normalized_name, either add it in migration or use functional lower(name) index consistently.
```

---

## 19. Redis Package Boundary

Redis usage should be isolated to runner/test run orchestration.

Recommended dependencies:

```text
testrun/service/impl/TestRunCommandServiceImpl
  -> runner/service/RunnerCommandPublisher
    -> common/redis/RedisStreamPublisher

runner/service/impl/RunnerEventConsumerServiceImpl
  -> common/redis/RedisStreamConsumerSupport
  -> testrun/service/TestRunResultService
  -> sse/service/TestRunSseService
```

Rules:

```text
1. Workspace/project/dataset CRUD should not depend on Redis.
2. Runner commands/events use references only.
3. Persist command/event metadata in PostgreSQL when required by ERD.
4. Do not let frontend read Redis directly.
```

---

## 20. Async Package Boundary

Async execution should be explicit.

Recommended package:

```text
common/async
```

Feature-specific async workers:

```text
exportjob/service/ExportJobWorker
dataset/service/DatasetImportWorker if large import is async
runner/service/RunnerEventConsumerService if running as background listener
```

Rules:

```text
1. Async methods must be easy to locate.
2. Async workers must log start, success and failure with job/run id.
3. Async workers must persist failure status for user-visible jobs.
4. Do not make normal CRUD async.
```

---

## 21. Suggested Source Directory Layout

Recommended physical layout:

```text
apps/api/src/main/java/vn/vinfast/vfqc/api/
  common/
  auth/
  user/
  workspace/
  project/
  secret/
  provider/
  dataset/
  target/
  evaluator/
  testrun/
  exportjob/
  runner/
  sse/
  artifact/

apps/api/src/main/resources/
  application.yml
  application-local.yml
  application-prod.yml
  db/migration/
```

Tests:

```text
apps/api/src/test/java/vn/vinfast/vfqc/api/
  common/
  auth/
  workspace/
  project/
  ...
```

---

## 22. AI Coding Agent Checklist

Before generating code for any backend module:

```text
1. Identify the owning module.
2. Read the relevant API contract.
3. Read the relevant ERD entity/table fields.
4. Create DTO records under dto/request and dto/response.
5. Create or update entity under entity.
6. Create repository under repository.
7. Create MapStruct mapper under mapper.
8. Create service interface under service with Javadoc.
9. Create service implementation under service/impl with @Slf4j and @Transactional.
10. Create controller under controller with Swagger annotations.
11. Use common permission, naming, masking and error components.
12. Do not introduce cross-module repository access unless justified.
13. Do not log sensitive data.
14. Do not create manual toResponse/toEntity helpers.
```
