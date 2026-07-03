# VinFast QC Copilot - Test Run Implementation

Version: `v0.1`  
Status: Backend Implementation Spec Draft  
Applies to: `apps/api` Spring Boot backend  
Aligned with: PRD v0.7, ERD v0.5, API Contract `08-test-run-api.md`

---

## 1. Purpose

This document explains how to implement Test Run orchestration, result reading, QC review, comparison matrix and artifact metadata access in the Spring Boot backend.

It is written for AI coding agents and backend developers so generated code follows the same immutable run model, state machine, Redis command boundary, result persistence and review semantics.

This file covers:

```text
1. Run preflight.
2. TestRun creation and locked config snapshots.
3. Single-target and multi-target comparison runs.
4. Redis START_RUN / CANCEL_RUN command publishing boundary.
5. TestRun state machine.
6. TestCaseResult persistence and reading.
7. QC review/manual override workflow.
8. Comparison matrix read model.
9. Artifact metadata access.
10. Permission checks, transaction boundaries, logging and error handling.
11. DTO, mapper, controller and service implementation conventions.
```

This file does not define the wire contract for Redis runner events. That belongs to `docs/backend/10-runner-event-consumer.md` and `docs/api/10-runner-internal-api.md`.

This file does not redefine API request/response contracts. API shapes are defined in `docs/api/08-test-run-api.md`.

---

## 2. Design Principles

Test Run code must follow these principles:

```text
1. TestRun is an immutable execution record.
2. A run is never restarted in place.
3. Rerun means creating a new TestRun.
4. Backend Java is the source of truth for run config and state.
5. Redis Streams carry references only, not full config, dataset rows, artifacts or secrets.
6. TestRun creation locks dataset/schema/evaluator/target/provider snapshots.
7. Historical run results stay stable even if project config changes later.
8. Testcase FAILED is a valid business result and must not make the TestRun FAILED.
9. TestRun FAILED is reserved for unrecoverable execution/system failure.
10. Machine result is immutable.
11. QC review/manual override is stored as a separate review layer.
12. Final result is computed from QC review when present, otherwise machine result.
13. Comparison run supports multiple CHATBOT_TARGET and/or multiple LLM_BASELINE targets.
14. Backend should expose comparison matrix grouping for stable pagination on large runs.
15. All snapshots, resolved requests, raw responses and artifact metadata exposed to UI must be sanitized.
```

Security and localization rules:

```text
1. Do not log dataset row contents, prompts, raw responses or full Promptfoo config.
2. Do not log Authorization/API key/token/cookie values.
3. Do not store unsanitized Promptfoo config/result artifacts in DB.
4. Backend code/log/error codes should use English.
5. Vietnamese user-facing copy belongs in frontend i18n or a message catalog, not inline service logic.
```

---

## 3. Package Layout

Recommended package layout:

```text
vn.vinfast.vfqc.api
  testrun
    controller
      TestRunController.java
      TestRunPreflightController.java
      TestRunResultController.java
      TestRunReviewController.java
      TestRunArtifactController.java
      TestRunComparisonMatrixController.java
    dto
      request
        RunPreflightRequest.java
        CreateTestRunRequest.java
        CancelTestRunRequest.java
        RunTargetRequest.java
        RunEvaluatorVersionRequest.java
        RunOptionsRequest.java
        UpsertTestCaseResultReviewRequest.java
        BulkReviewTestCaseResultsRequest.java
      response
        RunPreflightResponse.java
        PreflightWarningResponse.java
        TestRunSummaryResponse.java
        TestRunDetailResponse.java
        TestRunTargetResponse.java
        TestRunEvaluatorVersionResponse.java
        TestRunResultPageResponse.java
        TestCaseResultSummaryResponse.java
        TestCaseResultDetailResponse.java
        TestCaseResultReviewResponse.java
        BulkReviewTestCaseResultsResponse.java
        ReviewSummaryResponse.java
        CancelTestRunResponse.java
        TestRunArtifactResponse.java
        ComparisonMatrixResponse.java
        ComparisonMatrixRowResponse.java
        ComparisonMatrixCellResponse.java
    entity
      TestRun.java
      TestRunTarget.java
      TestRunEvaluatorVersion.java
      TestCaseResult.java
      TestCaseResultReview.java
    enums
      TestRunType.java
      TestRunStatus.java
      TestRunTargetType.java
      TestCaseResultStatus.java
      ReviewStatus.java
      ResultMode.java
    mapper
      TestRunMapper.java
      TestRunTargetMapper.java
      TestRunEvaluatorVersionMapper.java
      TestCaseResultMapper.java
      TestCaseResultReviewMapper.java
    repository
      TestRunRepository.java
      TestRunTargetRepository.java
      TestRunEvaluatorVersionRepository.java
      TestCaseResultRepository.java
      TestCaseResultReviewRepository.java
    service
      TestRunPreflightService.java
      TestRunCommandService.java
      TestRunQueryService.java
      TestRunResultService.java
      TestRunReviewService.java
      TestRunComparisonMatrixService.java
      TestRunArtifactQueryService.java
      TestRunSnapshotService.java
      TestRunHashService.java
      impl
        TestRunPreflightServiceImpl.java
        TestRunCommandServiceImpl.java
        TestRunQueryServiceImpl.java
        TestRunResultServiceImpl.java
        TestRunReviewServiceImpl.java
        TestRunComparisonMatrixServiceImpl.java
        TestRunArtifactQueryServiceImpl.java
        TestRunSnapshotServiceImpl.java
        TestRunHashServiceImpl.java
    validator
      TestRunValidator.java
      RunTargetValidator.java
      RunEvaluatorValidator.java
      TestRunCancellationValidator.java
      TestCaseReviewValidator.java
    assembler
      TestRunDetailAssembler.java
      TestRunResultAssembler.java
      ReviewSummaryAssembler.java
      ComparisonMatrixAssembler.java
      TestRunArtifactAssembler.java
    state
      TestRunStateMachine.java
      TestRunTransition.java
      TestRunTransitionResult.java
    model
      LockedRunConfig.java
      LockedRunTargetConfig.java
      LockedEvaluatorConfig.java
      RunPreflightContext.java
      RunPreflightIssue.java
      FinalResultView.java
      ComparisonMatrixPage.java
```

Cross-module dependencies:

```text
testrun -> project permission lookup
testrun -> dataset version/schema/row lookup
testrun -> target lookup/readiness
testrun -> evaluator version lookup/readiness
testrun -> provider lookup/readiness
testrun -> runner command publisher
testrun -> artifact query service
testrun -> exportjob service for result export creation
testrun -> common json
testrun -> common masking
testrun -> common error
testrun -> common pagination
testrun -> common permission
testrun -> common redis through runner command publisher only
```

Rules:

```text
1. TestRun service implementations may read other modules through query/lookup services.
2. TestRun services must not mutate dataset/target/evaluator/provider definitions.
3. Redis publishing should be hidden behind RunnerCommandPublisher, not called directly from controllers.
4. Result export creation should go through exportjob service, not generate Excel inside TestRunController.
```

---

## 4. Entity Implementation

### 4.1. `TestRun`

Maps to `test_runs`.

Important fields:

```text
id
project
runType
dataset
datasetVersion
datasetSchema
datasetSchemaVersion
judgeProvider
judgeModel
status
progressCompleted
progressTotal
summaryMetricsJson
domainConfigSnapshotJson
promptfooConfigSnapshotJson
domainConfigHash
promptfooConfigHash
startedBy
startedAt
finishedAt
cancelRequestedAt
createdAt
updatedAt
```

Implementation rules:

```text
1. id is UUID.
2. project, dataset, datasetVersion and datasetSchema are LAZY ManyToOne.
3. status is controlled only by TestRunStateMachine or dedicated state transition methods.
4. progress fields are updated by runner event ingestion, not by frontend requests.
5. summaryMetricsJson is computed from persisted testcase results and reviews.
6. domainConfigSnapshotJson and promptfooConfigSnapshotJson must be sanitized compact snapshots.
7. Full Promptfoo config belongs in artifact storage, not in test_runs.
```

### 4.2. `TestRunTarget`

Maps to `test_run_targets`.

Important fields:

```text
id
testRun
targetType
chatbotTarget
llmProvider
model
displayName
providerLabel
configJson
createdAt
```

Implementation rules:

```text
1. id is UUID.
2. For CHATBOT_TARGET, chatbotTarget is required and llmProvider/model are normally null.
3. For LLM_BASELINE, llmProvider and model are required and chatbotTarget is null.
4. configJson is a locked sanitized target snapshot.
5. Preserve order from CreateTestRunRequest for stable comparison matrix columns.
```

### 4.3. `TestRunEvaluatorVersion`

Maps to `test_run_evaluator_versions`.

Important fields:

```text
id
testRun
evaluatorVersion
evaluatorType
orderIndex
createdAt
```

Implementation rules:

```text
1. id is BIGINT.
2. TestRun locks exact evaluator versions.
3. orderIndex preserves user-selected evaluator order.
4. Do not point to evaluator latest dynamically after run creation.
```

### 4.4. `TestCaseResult`

Maps to `test_case_results`.

Important fields:

```text
id
testRun
testRunTarget
datasetRow
status
inputVarsJson
renderedPrompt
resolvedRequestJson
rawResponseJson
extractedOutputJson
gradingResultJson
componentResultsJson
score
passed
latencyMs
tokenUsageJson
cost
errorMessage
startedAt
finishedAt
attemptCount
createdAt
```

Implementation rules:

```text
1. id is BIGINT.
2. unique(test_run_id, test_run_target_id, dataset_row_id) must be enforced.
3. resolvedRequestJson and rawResponseJson must be sanitized before persistence.
4. renderedPrompt may be null for pure HTTP target runs.
5. FAILED means business evaluation failed.
6. ERROR/JUDGE_ERROR/TIMEOUT mean execution/evaluator errors.
7. Result persistence is normally triggered by runner events, not by frontend APIs.
```

### 4.5. `TestCaseResultReview`

Maps to `test_case_result_reviews`.

Important fields:

```text
id
testCaseResult
reviewStatus
overrideStatus
overridePassed
overrideScore
note
reviewedBy
reviewedAt
createdAt
updatedAt
```

Implementation rules:

```text
1. id is BIGINT.
2. unique(test_case_result_id) means one review row per testcase result.
3. Review does not mutate TestCaseResult machine fields.
4. Final result is derived from review override if present.
5. Deleting review clears manual override and returns final result to machine result fallback.
```

---

## 5. DTO Implementation Rules

DTOs must be Java records.

Request record example:

```java
public record CreateTestRunRequest(
    @NotNull
    @Schema(example = "COMPARISON", description = "Run type: SINGLE or COMPARISON")
    TestRunType runType,

    @NotNull
    @Schema(example = "0c9f95ce-d1b4-4e2d-a5c8-f7c6c38dcf10")
    UUID datasetVersionId,

    @NotEmpty
    @Valid
    @Schema(description = "Targets to execute. SINGLE requires exactly one target; COMPARISON requires at least two targets.")
    List<RunTargetRequest> targets,

    @NotEmpty
    @Valid
    @Schema(description = "Evaluator versions locked for this run.")
    List<RunEvaluatorVersionRequest> evaluatorVersions,

    @Valid
    @Schema(description = "Optional execution controls.")
    RunOptionsRequest options
) {}
```

Review request example:

```java
public record UpsertTestCaseResultReviewRequest(
    @NotNull
    @Schema(example = "REVIEWED")
    ReviewStatus reviewStatus,

    @Schema(example = "PASSED")
    TestCaseResultStatus overrideStatus,

    @Schema(example = "true")
    Boolean overridePassed,

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    @Schema(example = "0.85")
    BigDecimal overrideScore,

    @Size(max = 2000)
    @Schema(example = "Machine judge missed an acceptable synonym.")
    String note
) {}
```

Rules:

```text
1. Use validation annotations on request records.
2. Add @Schema descriptions/examples for Swagger.
3. Keep response records free of JPA entities.
4. Use MapStruct for simple response mapping.
5. Use assemblers for detail, result, review summary and comparison matrix responses.
```

---

## 6. Mapper and Assembler Rules

Use MapStruct for simple mappings:

```java
@Mapper(componentModel = "spring")
public interface TestRunMapper {
    TestRunSummaryResponse toSummaryResponse(TestRun entity);
}
```

Use assembler for aggregate read models:

```text
TestRunDetailAssembler:
  TestRun + targets + evaluator versions + locked summaries.

TestRunResultAssembler:
  TestCaseResult + optional TestCaseResultReview + final result fields.

ReviewSummaryAssembler:
  counts reviewed/unreviewed/needs discussion/final metrics.

ComparisonMatrixAssembler:
  dataset row -> target cells grouping.
```

Rules:

```text
1. Do not create manual toResponse/toEntity methods inside services.
2. Mappers must not run permission checks.
3. Mappers must not read repositories.
4. Assemblers may call query services for aggregate read models.
5. Masking must happen before persistence or before assembler exposes any sensitive content.
```

---

## 7. Service Interface Rules

Service interfaces live in `testrun/service`.

Every public method must have Javadoc.

Example:

```java
public interface TestRunCommandService {

  /**
   * Creates a new immutable TestRun, locks all selected dataset, target, provider and evaluator
   * configuration, persists a START_RUN command reference and schedules command publishing after
   * the database transaction commits.
   *
   * @param projectId project that owns the run
   * @param request run creation request validated by the controller
   * @param currentUser authenticated user starting the run
   * @return created test run detail response
   * @throws ApiException when permission is denied, preflight fails or selected config is invalid
   */
  TestRunDetailResponse createRun(UUID projectId, CreateTestRunRequest request, CurrentUser currentUser);

  /**
   * Requests cancellation for a non-terminal run and publishes a compact CANCEL_RUN command.
   *
   * @param testRunId run id
   * @param request optional cancellation note/reason
   * @param currentUser authenticated user requesting cancellation
   * @return cancellation response with accepted state
   * @throws ApiException when the run is not visible or not cancellable
   */
  CancelTestRunResponse cancelRun(UUID testRunId, CancelTestRunRequest request, CurrentUser currentUser);
}
```

Implementation rules:

```text
1. Implementation classes live in service/impl.
2. Implementation classes use @Service, @RequiredArgsConstructor and @Slf4j.
3. DB methods use @Transactional.
4. Read-only query methods use @Transactional(readOnly = true).
5. Do not log full request bodies.
6. Log stable ids, status changes and counts.
```

---

## 8. Run Preflight Implementation

`TestRunPreflightService` validates whether a run can be created before any TestRun row is persisted.

Preflight checks:

```text
1. Current user has Project RUN_TEST permission.
2. Project exists and is not deleted/archived.
3. DatasetVersion exists, belongs to a dataset visible in the project context and is ACTIVE.
4. DatasetVersion row count > 0.
5. Dataset schema is compatible with selected target mappings and evaluator rules.
6. For SINGLE, exactly one target is selected.
7. For COMPARISON, at least two targets are selected.
8. CHATBOT_TARGET entries are ACTIVE and have response extraction config.
9. LLM_BASELINE entries reference ACTIVE usable providers and valid model names.
10. Selected evaluator versions exist and are not soft-deleted.
11. LLM_JUDGE/MIXED evaluators have usable judge provider/model when required.
12. Estimated row_count * target_count does not exceed configured MVP limits.
13. Promptfoo cache default is disabled for internal chatbot target runs.
14. Promptfoo cache may be enabled only when options.cacheMode = CONTROLLED_BASELINE_DEBUG and the run does not risk mixing live target outputs with cached responses.
```

Preflight response should include:

```text
canStart
estimatedTestcaseCount
estimatedTargetCount
warnings
blockers
lockedConfigPreview
```

Rules:

```text
1. Preflight must not mutate database state.
2. Preflight should be read-only transaction.
3. Preflight may reuse readiness services from dataset/target/evaluator/provider modules.
4. Preflight does not replace createRun validation; createRun must validate again to avoid race conditions.
```

---

## 9. Create TestRun Transaction

`TestRunCommandServiceImpl.createRun` should execute the database mutation in one transaction.

Recommended sequence:

```text
1. Check Project RUN_TEST permission.
2. Re-run critical preflight validation.
3. Load and lock selected DatasetVersion metadata.
4. Load selected targets and provider/baseline metadata.
5. Load selected EvaluatorVersions.
6. Build canonical domain locked snapshot.
7. Sanitize compact domain snapshot.
8. Build compact Promptfoo config snapshot preview if available.
9. Sanitize compact Promptfoo snapshot.
10. Compute domainConfigHash and promptfooConfigHash.
11. Create TestRun status=PENDING.
12. Create ordered TestRunTarget rows.
13. Create ordered TestRunEvaluatorVersion rows.
14. Create RunnerCommand row for START_RUN with references only.
15. Register after-commit Redis publish action.
16. Commit transaction.
17. Publish START_RUN command to Redis after commit.
```

Important consistency rule:

```text
Do not publish Redis START_RUN before the TestRun and RunnerCommand rows commit.
```

Recommended implementation options:

```text
Option A:
  Use @TransactionalEventListener(phase = AFTER_COMMIT) to publish Redis command.

Option B:
  Use a small command outbox worker that publishes unpublished RunnerCommand rows.
```

Avoid:

```text
1. Publishing Redis inside transaction and then rolling back DB.
2. Putting full locked config into Redis payload.
3. Returning success before the TestRun row exists.
4. Resolving plaintext runtime secrets during createRun.
```

---

## 10. Locked Snapshot and Hashing

Use `TestRunSnapshotService` to build canonical snapshots.

Domain snapshot should include sanitized compact data:

```text
project id/name
dataset id/version/schema id/schema version/row count
target ids/display names/types/sanitized config summaries
evaluator version ids/types/version numbers/sanitized config summaries
judge provider id/model if applicable
run options
promptfoo adapter schema version if known
```

Hashing rules:

```text
1. Use canonical JSON serialization with stable property ordering.
2. Hash sanitized canonical snapshot.
3. Use SHA-256.
4. Store hash text in domainConfigHash/promptfooConfigHash.
5. Do not include plaintext secret values in any hash input stored for audit.
```

`promptfoo_config_snapshot_json` in DB is a compact sanitized preview. Full generated config file is written by Runner to artifact storage after masking.

---

## 11. State Machine

Use `TestRunStateMachine` to centralize allowed transitions.

Allowed transitions:

```text
PENDING -> RUNNING
PENDING -> CANCELLING
PENDING -> CANCELLED
PENDING -> FAILED

RUNNING -> CANCELLING
RUNNING -> COMPLETED
RUNNING -> FAILED
RUNNING -> CANCELLED

CANCELLING -> CANCELLED
CANCELLING -> COMPLETED
CANCELLING -> FAILED
```

Terminal states:

```text
COMPLETED
FAILED
CANCELLED
```

Rules:

```text
1. Terminal states cannot transition back to non-terminal states.
2. RUNNING is set by RUN_STARTED event from runner.
3. CANCELLING is set by backend cancel API.
4. COMPLETED/FAILED/CANCELLED are set by runner terminal events or reconciliation worker.
5. Testcase business failures do not transition TestRun to FAILED.
```

Implementation example:

```java
public final class TestRunStateMachine {
  public boolean canTransition(TestRunStatus from, TestRunStatus to) {
    return switch (from) {
      case PENDING -> Set.of(RUNNING, CANCELLING, CANCELLED, FAILED).contains(to);
      case RUNNING -> Set.of(CANCELLING, COMPLETED, FAILED, CANCELLED).contains(to);
      case CANCELLING -> Set.of(CANCELLED, COMPLETED, FAILED).contains(to);
      case COMPLETED, FAILED, CANCELLED -> false;
    };
  }
}
```

---

## 12. Cancel Run Implementation

Cancel flow:

```text
1. Check Project CANCEL_RUN permission.
2. Load TestRun with project.
3. If terminal, return accepted=false or throw TEST_RUN_NOT_CANCELLABLE based on API contract.
4. Transition PENDING/RUNNING to CANCELLING when valid.
5. Set cancelRequestedAt.
6. Create RunnerCommand row for CANCEL_RUN with references only.
7. Publish Redis command after commit.
8. Return CancelTestRunResponse.
```

Rules:

```text
1. Cancel API does not directly mark RUNNING run as CANCELLED unless runner confirms or run is still PENDING and not yet picked.
2. Cancel command payload must not contain full config or secrets.
3. Cancel is idempotent enough for repeated user clicks.
4. Logs should include testRunId, previousStatus, newStatus and requester id only.
```

---

## 13. Result Persistence Boundary

TestCaseResult creation/update is normally invoked by runner event ingestion from `docs/backend/10-runner-event-consumer.md`.

`TestRunResultService` responsibilities:

```text
1. Upsert one testcase result from normalized runner event.
2. Enforce uniqueness by testRunId + testRunTargetId + datasetRowId.
3. Sanitize resolved request/raw response/extracted output before persistence.
4. Update progress counters.
5. Recompute summary metrics incrementally or schedule recomputation.
6. Provide read APIs for paginated result list and detail.
```

Status handling:

```text
PASSED:
  Business pass.

FAILED:
  Business fail.

ERROR:
  Target execution or adapter error.

JUDGE_ERROR:
  LLM Judge execution/normalization error.

TIMEOUT:
  Target or evaluator timeout.

SKIPPED:
  Not executed due to pre-run filtering or dependency.

CANCELLED:
  Cancelled before completion.
```

Rules:

```text
1. Do not trust runner event payload blindly; validate ids belong to the run.
2. Do not persist unsanitized request/response body.
3. Do not expose raw DB JSON directly without response shaping.
4. Use batch insert/update where possible for high-volume runs.
```

---

## 14. Result Query APIs

`TestRunResultService` reads results for:

```text
GET /api/v1/test-runs/{testRunId}/results
GET /api/v1/test-runs/{testRunId}/results/{testCaseResultId}
```

Query filters should support API contract fields such as:

```text
status
passed
targetId
rowIndex
reviewStatus
resultMode
limit/cursor
```

Result mode semantics:

```text
MACHINE:
  Return machine result only.

FINAL:
  Return review override when present, otherwise machine result fallback.
```

Implementation rules:

```text
1. Permission: Project VIEW.
2. Use cursor pagination for large runs.
3. Use projections/DTO queries for list views to avoid loading large JSON columns unnecessarily.
4. Detail endpoint may load sanitized request/response/extraction/grading JSON.
5. Do not load artifact file content through result list endpoint.
```

---

## 15. QC Review Implementation

`TestRunReviewService` owns review/manual override logic.

Endpoints:

```text
GET    /api/v1/test-runs/{testRunId}/review-summary
PUT    /api/v1/test-case-results/{testCaseResultId}/review
DELETE /api/v1/test-case-results/{testCaseResultId}/review
PATCH  /api/v1/test-runs/{testRunId}/results/reviews/bulk
```

Review rules:

```text
1. Permission: Project REVIEW_TEST_RESULT.
2. Review upsert creates or updates TestCaseResultReview.
3. Review delete removes manual override row or clears override fields based on API contract.
4. Review never mutates TestCaseResult machine status/score/passed.
5. reviewedBy/reviewedAt update on every review mutation.
6. Final metrics must derive from review override when present.
```

Validation rules:

```text
1. testCaseResult must exist and be visible to the user.
2. overrideScore must be inside score scale.
3. overridePassed should be consistent with overrideStatus when both are provided.
4. note length must be bounded.
5. Bulk review should cap selected result ids per request.
```

Logging rules:

```text
1. Log review mutation with testCaseResultId, reviewStatus and reviewer id.
2. Do not log review note content by default.
3. Do not log result JSON.
```

---

## 16. Comparison Matrix Implementation

`TestRunComparisonMatrixService` owns:

```text
GET /api/v1/test-runs/{testRunId}/comparison-matrix
```

Purpose:

```text
Return a backend-grouped read model where each row is one dataset row and each cell is one target result.
```

Recommended response shape:

```json
{
  "testRunId": "1b95f0b0-7795-4c0f-a745-d60bfa8e4cd37",
  "datasetVersionId": "28c5b295-c7cb-4989-8f6a-2a2b22a87577",
  "resultMode": "FINAL",
  "targets": [
    {
      "testRunTargetId": "e5f65eda-7302-4738-99d1-1c5e9d63c8f2",
      "displayName": "Internal Chatbot v1",
      "targetType": "CHATBOT_TARGET",
      "orderIndex": 1
    }
  ],
  "rows": [
    {
      "datasetRowId": 101,
      "rowIndex": 1,
      "datasetPreview": {
        "question": "How do I book a service appointment?"
      },
      "cells": [
        {
          "testRunTargetId": "e5f65eda-7302-4738-99d1-1c5e9d63c8f2",
          "testCaseResultId": 5001,
          "status": "PASSED",
          "passed": true,
          "score": 0.86,
          "reviewStatus": "UNREVIEWED",
          "finalPassed": true,
          "finalScore": 0.86,
          "latencyMs": 1200
        }
      ]
    }
  ],
  "page": {
    "limit": 50,
    "nextCursor": null,
    "hasMore": false
  }
}
```

Implementation rules:

```text
1. Permission: Project VIEW.
2. Only COMPARISON runs should use this endpoint; SINGLE may return validation error or one-column matrix if contract allows.
3. Preserve TestRunTarget order.
4. Group by datasetRowId and rowIndex.
5. Use final result semantics when resultMode=FINAL.
6. Keep datasetPreview small and configurable.
7. Do not include full raw response in matrix cells.
8. Use optimized repository queries/projections to avoid N+1 queries.
```

---

## 17. Artifact Metadata Access

`TestRunArtifactQueryService` owns:

```text
GET /api/v1/test-runs/{testRunId}/artifacts
GET /api/v1/test-runs/{testRunId}/artifacts/{artifactType}/content
```

Rules:

```text
1. Permission: Project VIEW.
2. Only expose artifacts where sanitized=true.
3. DB stores metadata/path/hash, not large file content.
4. Artifact content reads go through artifact module/storage service.
5. Validate artifactType against allowed values.
6. Prevent path traversal.
7. Do not expose internal absolute filesystem paths in API responses.
```

TestRun module should depend on artifact query service rather than directly reading files.

---

## 18. Controller Implementation

Controller rules:

```text
1. Controllers are thin HTTP adapters.
2. Controllers use @RestController and @RequestMapping.
3. Add Swagger/OpenAPI annotations.
4. Use @Valid on request bodies.
5. Resolve CurrentUser through security component.
6. Do not implement business logic in controllers.
```

Example:

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/projects/{projectId}/test-runs")
@Tag(name = "Test Runs", description = "Create and manage chatbot evaluation runs")
public class TestRunController {

  private final TestRunCommandService testRunCommandService;
  private final TestRunQueryService testRunQueryService;

  @Operation(summary = "Create test run")
  @PostMapping
  public ResponseEntity<TestRunDetailResponse> createRun(
      @PathVariable UUID projectId,
      @Valid @RequestBody CreateTestRunRequest request,
      @AuthenticationPrincipal CurrentUser currentUser) {
    TestRunDetailResponse response = testRunCommandService.createRun(projectId, request, currentUser);
    return ResponseEntity.created(URI.create("/api/v1/test-runs/" + response.id())).body(response);
  }
}
```

---

## 19. Transaction Rules

Use transactions intentionally:

```text
Preflight:
  @Transactional(readOnly = true)

Create run:
  @Transactional
  publishes Redis after commit only

Cancel run:
  @Transactional
  publishes Redis after commit only

Result list/detail:
  @Transactional(readOnly = true)

Persist testcase result from runner event:
  @Transactional

Review upsert/delete/bulk:
  @Transactional

Comparison matrix:
  @Transactional(readOnly = true)

Artifact metadata list:
  @Transactional(readOnly = true)
```

Avoid:

```text
1. Holding DB transaction while doing network calls.
2. Publishing Redis before DB commit.
3. Reading large artifact file contents inside long DB transactions.
4. Updating TestRun status outside the state machine.
```

---

## 20. Redis Boundary

TestRun module should only publish commands through runner module boundary:

```text
TestRunCommandServiceImpl
  -> RunnerCommandPublisher
    -> RunnerCommandRepository
    -> RedisStreamPublisher after commit
```

Command payloads contain:

```text
commandId
testRunId
commandType
createdAt
```

Command payloads must not contain:

```text
full dataset rows
full target config
full evaluator config
Promptfoo config
plaintext secret
artifact content
```

---

## 21. Logging Rules

Use `@Slf4j` in implementation classes.

Recommended logs:

```text
INFO createRun accepted: projectId, testRunId, runType, targetCount, rowCount, startedBy
INFO cancel requested: testRunId, previousStatus, requestedBy
INFO terminal state applied: testRunId, status, completed, total
WARN preflight blocked: projectId, blockerCodes, userId
WARN invalid transition ignored/rejected: testRunId, from, to, eventType
ERROR createRun failed: projectId, userId, errorCode
```

Forbidden logs:

```text
1. Full request body containing dataset/evaluator content.
2. Raw response JSON.
3. Raw Promptfoo config.
4. Authorization/API key/token/cookie values.
5. Plaintext runtime secret bundle.
```

---

## 22. Error Codes

Recommended error codes:

```text
TEST_RUN_NOT_FOUND
TEST_RUN_PRECHECK_FAILED
TEST_RUN_INVALID_TYPE
TEST_RUN_TARGET_COUNT_INVALID
TEST_RUN_TARGET_NOT_READY
TEST_RUN_DATASET_VERSION_INVALID
TEST_RUN_EVALUATOR_VERSION_INVALID
TEST_RUN_PROVIDER_NOT_READY
TEST_RUN_NOT_CANCELLABLE
TEST_RUN_STATE_TRANSITION_INVALID
TEST_CASE_RESULT_NOT_FOUND
TEST_CASE_REVIEW_INVALID
TEST_RUN_COMPARISON_MATRIX_UNAVAILABLE
TEST_RUN_ARTIFACT_NOT_FOUND
TEST_RUN_ARTIFACT_NOT_SANITIZED
```

Rules:

```text
1. Use 404 when user cannot access a run to avoid existence leakage.
2. Use 409 for invalid state transitions or non-cancellable run.
3. Use 422 for semantically invalid run setup.
4. Do not expose database constraint names.
```

---

## 23. Testing Checklist

Unit tests:

```text
- TestRunStateMachine allowed/denied transitions.
- RunTargetValidator SINGLE/COMPARISON target counts.
- Snapshot canonicalization and hash stability.
- Final result derivation from review override.
- ComparisonMatrixAssembler grouping and target order.
- TestRunCancellationValidator terminal/non-terminal statuses.
```

Service tests:

```text
- createRun locks dataset/schema/evaluator/target/provider snapshots.
- createRun creates TestRunTarget rows in request order.
- createRun creates RunnerCommand row with references only.
- cancelRun transitions RUNNING -> CANCELLING and creates CANCEL_RUN command.
- review upsert does not mutate TestCaseResult machine fields.
- result list respects Project VIEW permission.
```

Integration tests:

```text
- POST /projects/{projectId}/test-runs/preflight.
- POST /projects/{projectId}/test-runs.
- POST /test-runs/{testRunId}/cancel.
- GET /test-runs/{testRunId}/results.
- PUT /test-case-results/{testCaseResultId}/review.
- GET /test-runs/{testRunId}/comparison-matrix.
- GET /test-runs/{testRunId}/artifacts hides sanitized=false artifacts.
```

Security tests:

```text
- User outside workspace receives 404/403 according to contract.
- VIEWER cannot start/cancel/review.
- Redis command payload does not contain secret/config/dataset content.
- Responses do not contain plaintext secret values.
```

---

## 24. AI Coding Agent Checklist

Before generating TestRun code:

```text
1. Read docs/api/08-test-run-api.md.
2. Read docs/backend/01-package-architecture.md section testrun.
3. Read docs/backend/02-domain-model-mapping.md TestRun/TestCaseResult sections.
4. Implement DTOs as records with validation and @Schema examples.
5. Implement MapStruct mappers for simple mappings.
6. Implement assemblers for detail/result/review/matrix responses.
7. Put service interfaces in service/ with Javadocs.
8. Put implementations in service/impl/ with @Slf4j.
9. Use @Transactional correctly.
10. Route Redis publishing through RunnerCommandPublisher after DB commit.
11. Never put full config, dataset rows or secrets in Redis payloads.
12. Never mutate machine result during QC review.
13. Use TestRunStateMachine for status transitions.
14. Keep logs concise and non-sensitive.
```
