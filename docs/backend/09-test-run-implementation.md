# VinFast QC Copilot - Test Run Implementation

Version: `v0.2`  
Status: Spring Boot 4.0.7-first Implementation Spec  
Applies to: `apps/api`

---

## 1. Purpose

This document explains TestRun orchestration, snapshots, Redis command boundary, result persistence, QC review, comparison matrix and artifact metadata access.

---

## 2. Design Principles

1. TestRun is an immutable execution record.
2. A run is never restarted in place.
3. Rerun creates a new TestRun.
4. Backend is source of truth for run config and state.
5. Redis Streams carry references only.
6. TestRun creation locks dataset/schema/evaluator/target/provider snapshots.
7. Historical results stay stable after project config changes.
8. Testcase FAILED is business result, not TestRun FAILED.
9. TestRun FAILED means execution/system failure.
10. Machine result is immutable.
11. QC review/manual override is separate layer.
12. Final result uses review override when present, else machine result.
13. Comparison run supports multiple chatbot targets and/or LLM baselines.
14. All snapshots/artifacts exposed to UI must be sanitized.

---

## 3. Package Layout

```text
testrun
  controller/TestRunController.java
  controller/TestRunPreflightController.java
  controller/TestRunResultController.java
  controller/TestRunReviewController.java
  controller/TestRunArtifactController.java
  controller/TestRunComparisonMatrixController.java
  dto/request/RunPreflightRequest.java
  dto/request/CreateTestRunRequest.java
  dto/request/CancelTestRunRequest.java
  dto/request/RunTargetRequest.java
  dto/request/RunEvaluatorVersionRequest.java
  dto/request/RunOptionsRequest.java
  dto/request/UpsertTestCaseResultReviewRequest.java
  dto/response/RunPreflightResponse.java
  dto/response/TestRunSummaryResponse.java
  dto/response/TestRunDetailResponse.java
  dto/response/TestRunTargetResponse.java
  dto/response/TestRunResultPageResponse.java
  dto/response/TestCaseResultDetailResponse.java
  dto/response/TestCaseResultReviewResponse.java
  dto/response/ComparisonMatrixResponse.java
  entity/TestRun.java
  entity/TestRunTarget.java
  entity/TestRunEvaluatorVersion.java
  entity/TestCaseResult.java
  entity/TestCaseResultReview.java
  enums/TestRunType.java
  enums/TestRunStatus.java
  enums/TestRunTargetType.java
  enums/TestCaseResultStatus.java
  enums/ReviewStatus.java
  enums/ResultMode.java
  repository/*Repository.java
  mapper/*Mapper.java
  service/TestRunPreflightService.java
  service/TestRunCommandService.java
  service/TestRunQueryService.java
  service/TestRunResultService.java
  service/TestRunReviewService.java
  service/TestRunComparisonMatrixService.java
  service/TestRunArtifactQueryService.java
  service/TestRunSnapshotService.java
  service/TestRunHashService.java
  state/TestRunStateMachine.java
  model/LockedRunConfig.java
  model/LockedRunTargetConfig.java
  model/LockedEvaluatorConfig.java
```

---

## 4. Entity Rules

### TestRun

Fields:

```text
id
projectId
runType
datasetId
datasetVersionId
datasetSchemaId
datasetSchemaVersion
judgeProviderId
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

Rules:

1. Status changes only through state machine/use-case methods.
2. Progress updated by runner event ingestion.
3. Snapshots are sanitized compact JSON.
4. Full Promptfoo config belongs in artifact storage.

### TestRunTarget

Rules:

1. CHATBOT_TARGET requires `chatbotTargetId`.
2. LLM_BASELINE requires provider/model.
3. `configJson` is locked sanitized target snapshot.
4. Preserve order for comparison matrix columns.

### TestCaseResult

Rules:

1. Unique `(test_run_id, test_run_target_id, dataset_row_id)`.
2. Request/response JSON is sanitized before persistence.
3. FAILED means business evaluation failed.
4. ERROR/JUDGE_ERROR/TIMEOUT are execution/evaluator errors.
5. Result persistence is triggered by runner events/internal fallback, not frontend.

### TestCaseResultReview

Rules:

1. Separate from machine result.
2. Stores reviewer, note, override status/score.
3. Does not mutate machine result.

---

## 5. State Machine

Recommended statuses:

```text
CREATED
QUEUED
RUNNING
CANCEL_REQUESTED
COMPLETED
FAILED
CANCELLED
```

Allowed transitions:

```text
CREATED -> QUEUED
QUEUED -> RUNNING
RUNNING -> COMPLETED
RUNNING -> FAILED
RUNNING -> CANCEL_REQUESTED
CANCEL_REQUESTED -> CANCELLED
CANCEL_REQUESTED -> COMPLETED
QUEUED -> CANCELLED
```

Rules:

1. Invalid transition returns domain error.
2. Terminal states are immutable except review/result summaries.
3. Cancellation is best-effort if runner already completed.

---

## 6. Preflight

Preflight checks:

1. Project accessible and not archived/deleted.
2. Dataset has ACTIVE version and rows.
3. Target(s) are ACTIVE/ready.
4. Evaluator version(s) exist and valid.
5. Judge provider/model available if LLM judge is used.
6. Secret references are present and enabled.
7. Field sources match dataset schema/extraction config.
8. Estimated row/target/evaluator count is within limits.

Preflight may return warnings and blockers. It must not mutate state.

---

## 7. Create Run Flow

Transaction:

1. Check project RUN_START permission.
2. Run authoritative validation.
3. Lock dataset/schema/target/evaluator/provider snapshots.
4. Sanitize domain snapshot.
5. Build sanitized Promptfoo snapshot.
6. Hash canonical sanitized snapshots.
7. Save TestRun, TestRunTarget, TestRunEvaluatorVersion.
8. Save RunnerCommand row.
9. Publish compact START_RUN command after DB state exists.
10. Return TestRun detail.

Redis command payload contains IDs only, not full config.

---

## 8. Review Flow

Rules:

1. Reviewer needs RESULT_REVIEW permission.
2. Machine result remains immutable.
3. Upsert review row by testcase result.
4. Final result read model uses review override if present.
5. Summary metrics recompute from final results.
6. Review note is sanitized before persistence/export.

---

## 9. Comparison Matrix

Rows group by dataset row.

Columns group by TestRunTarget.

Cells include:

1. Final status.
2. Score.
3. Pass/fail.
4. Error category.
5. Review marker.
6. Link/reference to result detail.

Rules:

1. Use stable target order from run creation.
2. Paginate by dataset row.
3. Do not load all testcase results for huge runs.
4. Do not expose raw responses in matrix.

---

## 10. Spring Usage

Use:

1. `@Transactional` for create/cancel/review.
2. Spring Data repositories and `Pageable`.
3. `TransactionSynchronization` or transactional event listener for after-commit command publishing if needed.
4. `Clock` for timestamps.
5. `ProblemDetail` errors.

Do not:

1. Publish Redis command before DB commit.
2. Store full artifacts in `test_runs`.
3. Generate Excel in controller.
4. Start raw threads from controller.

---

## 11. Tests

Required tests:

1. Preflight catches missing dataset/target/evaluator.
2. Create run locks exact version IDs.
3. Snapshot is sanitized and hashed.
4. Invalid state transition is rejected.
5. Testcase FAILED does not make TestRun FAILED.
6. Review override changes final view but not machine result.
7. Comparison matrix paginates correctly.
