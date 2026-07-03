# VinFast QC Copilot - Runner Event Consumer Implementation

Version: `v0.1`  
Status: Backend Implementation Spec Draft  
Applies to: `apps/api` Spring Boot backend  
Aligned with: PRD v0.7, ERD v0.5, API Contracts `09-sse-events-contract.md` and `10-runner-internal-api.md`

---

## 1. Purpose

This document explains how to implement the backend side of Runner orchestration, Redis Streams event consumption, runner internal APIs and event persistence.

It is written for AI coding agents and backend developers so generated code follows the same command/event boundary, idempotency, recovery, persistence and SSE handoff rules.

This file covers:

```text
1. Runner instance registration and heartbeat.
2. Runner internal authentication boundary.
3. Runner config fetch from locked TestRun state.
4. Runtime secret resolution for authenticated runner only.
5. Runner command publishing boundary.
6. Redis Streams event consumption.
7. RunnerEvent persistence and sequence allocation.
8. TestRun/TestCaseResult update from runner events.
9. Event replay foundation for SSE.
10. Pending recovery and DLQ handling.
11. Artifact metadata ingest.
12. Permission, transaction, logging and error handling rules.
```

This file does not define frontend SSE subscription behavior in detail. That belongs to `docs/backend/11-sse-implementation.md` and `docs/api/09-sse-events-contract.md`.

This file does not define the Node Runner implementation. It defines only the Spring Boot backend side.

---

## 2. Design Principles

Runner backend code must follow these principles:

```text
1. Backend database remains the source of truth.
2. Redis Streams are transport, not durable domain state.
3. Runner command payloads contain references only.
4. Runner events are persisted before they are broadcast to SSE clients.
5. SSE id equals runner_events.sequence_no.
6. Runner internal APIs use runner service authentication, not user JWT.
7. Runtime secrets may be returned only to authenticated runner and only for execution.
8. Runtime secret bundles must never be persisted or logged.
9. Runner config fetch must return locked TestRun config, not mutable project current config.
10. Event ingestion must be idempotent and safe for Redis redelivery.
11. Testcase FAILED is business failure; TestRun FAILED is execution/system failure.
12. Poison events should move to DLQ after configured retries.
```

Security and localization rules:

```text
1. Never log runner service token.
2. Never log runtime secret bundle.
3. Never put plaintext secret values into RunnerEvent, Redis payloads, artifacts metadata or SSE data.
4. Backend code/log/error codes should use English.
5. Vietnamese user-facing copy belongs in frontend i18n or a message catalog, not inline backend logic.
```

---

## 3. Package Layout

Recommended package layout:

```text
vn.vinfast.vfqc.api
  runner
    controller
      RunnerInstanceController.java
      RunnerConfigController.java
      RunnerRuntimeSecretController.java
      RunnerControlController.java
      RunnerArtifactController.java
      RunnerEventFallbackController.java
    dto
      request
        RunnerRegisterRequest.java
        RunnerHeartbeatRequest.java
        RuntimeSecretResolutionRequest.java
        RunnerArtifactMetadataRequest.java
        RunnerEventIngestRequest.java
        RunnerEventPayloadRequest.java
      response
        RunnerRegisterResponse.java
        RunnerHeartbeatResponse.java
        RunnerConfigResponse.java
        RunnerConfigTargetResponse.java
        RunnerConfigEvaluatorResponse.java
        RuntimeSecretBundleResponse.java
        RuntimeSecretValueResponse.java
        RunnerControlStateResponse.java
        RunnerArtifactMetadataResponse.java
        RunnerEventIngestResponse.java
    entity
      RunnerInstance.java
      RunnerCommand.java
      RunnerEvent.java
    enums
      RunnerInstanceStatus.java
      RunnerCommandType.java
      RunnerCommandStatus.java
      RunnerEventType.java
      RunnerEventStatus.java
      RunnerConfigSecretMode.java
    mapper
      RunnerInstanceMapper.java
      RunnerCommandMapper.java
      RunnerEventMapper.java
    repository
      RunnerInstanceRepository.java
      RunnerCommandRepository.java
      RunnerEventRepository.java
    service
      RunnerInstanceService.java
      RunnerConfigService.java
      RuntimeSecretService.java
      RunnerControlStateService.java
      RunnerCommandPublisher.java
      RunnerCommandOutboxService.java
      RunnerEventIngestionService.java
      RunnerEventConsumerService.java
      RunnerEventSequenceService.java
      RunnerPendingRecoveryService.java
      RunnerDeadLetterService.java
      RunnerArtifactIngestService.java
      impl
        RunnerInstanceServiceImpl.java
        RunnerConfigServiceImpl.java
        RuntimeSecretServiceImpl.java
        RunnerControlStateServiceImpl.java
        RunnerCommandPublisherImpl.java
        RunnerCommandOutboxServiceImpl.java
        RunnerEventIngestionServiceImpl.java
        RunnerEventConsumerServiceImpl.java
        RunnerEventSequenceServiceImpl.java
        RunnerPendingRecoveryServiceImpl.java
        RunnerDeadLetterServiceImpl.java
        RunnerArtifactIngestServiceImpl.java
    validator
      RunnerAuthValidator.java
      RunnerEventValidator.java
      RunnerArtifactValidator.java
      RuntimeSecretRequestValidator.java
    assembler
      RunnerConfigAssembler.java
      RuntimeSecretBundleAssembler.java
      RunnerControlStateAssembler.java
    model
      RunnerCommandMessage.java
      RunnerEventMessage.java
      NormalizedTestCaseResultEvent.java
      NormalizedRunTerminalEvent.java
      RunnerEventProcessingResult.java
```

Related common packages:

```text
common/redis
common/security
common/masking
common/json
common/error
common/time
```

Cross-module dependencies:

```text
runner -> testrun command/result/state services
runner -> secret runtime resolver
runner -> provider lookup if needed for runtime secret validation
runner -> target locked config lookup through TestRun snapshots
runner -> evaluator locked config lookup through TestRun snapshots
runner -> artifact service for metadata persistence
runner -> sse service for event broadcast after persistence
runner -> common redis
runner -> common masking
```

Rules:

```text
1. Runner module may update TestRun/TestCaseResult through TestRun service interfaces.
2. Runner module must not mutate dataset/target/evaluator/provider definitions.
3. Runner module must not expose runner internal DTOs through frontend APIs.
```

---

## 4. Redis Stream Names and Payload Boundary

Centralize stream names in `common/redis/RedisStreamNames`.

Recommended streams:

```text
vfqc:runs:commands
vfqc:runs:events
vfqc:runs:commands:dlq
vfqc:runs:events:dlq
```

Command message example:

```json
{
  "commandId": "2f3f3f5c-9d3d-4300-99c0-8db5d64b9ad0",
  "testRunId": "6e74a5df-1e54-4d73-ae1a-3d49c8991eb6",
  "commandType": "START_RUN",
  "createdAt": "2026-07-03T10:00:00+07:00"
}
```

Event message example:

```json
{
  "eventId": "3d601f75-f7f2-43fd-911c-b6a7e447e2d4",
  "testRunId": "6e74a5df-1e54-4d73-ae1a-3d49c8991eb6",
  "eventType": "TESTCASE_COMPLETED",
  "runnerId": "runner-01",
  "occurredAt": "2026-07-03T10:01:30+07:00",
  "payload": {
    "testRunTargetId": "e5f65eda-7302-4738-99d1-1c5e9d63c8f2",
    "datasetRowId": 101,
    "status": "PASSED",
    "score": 0.86
  }
}
```

Forbidden in Redis payloads:

```text
plaintext secret
full dataset rows
full target request with Authorization header
full raw response body
full Promptfoo config
artifact file content
```

Rules:

```text
1. Redis commands/events are compact transport messages.
2. Runner fetches full locked config through internal backend API.
3. Runner resolves runtime secrets through internal backend API.
4. Backend persists normalized event data before SSE broadcast.
```

---

## 5. Entity Implementation

### 5.1. `RunnerInstance`

Maps to `runner_instances`.

Important fields:

```text
id or runnerId depending on ERD implementation
runnerId
hostname
version
promptfooVersion
promptfooAdapterSchemaVersion
capabilitiesJson
status
currentTestRun
currentCommandId
lastHeartbeatAt
startedAt
createdAt
updatedAt
```

Implementation rules:

```text
1. runnerId is stable and supplied by runner service.
2. Heartbeat updates lastHeartbeatAt and status.
3. currentTestRun may be null when IDLE.
4. capabilitiesJson is sanitized operational metadata.
5. Do not store runner service token.
```

### 5.2. `RunnerCommand`

Maps to `runner_commands`.

Important fields:

```text
id
commandId
testRun
commandType
streamMessageId
status
attemptCount
lastError
consumerName
createdAt
publishedAt
consumedAt
acknowledgedAt
failedAt
dlqAt
```

Implementation rules:

```text
1. id is BIGINT internal primary key.
2. commandId is UUID business idempotency key and maps to runner_commands.command_id.
3. The database row stores command metadata and lifecycle state; the Redis Stream message carries the compact command payload.
4. Redis Stream id maps to streamMessageId, not streamMessageId.
5. Status must use RunnerCommandStatus values from ERD: PUBLISHED, CONSUMED, ACKED, FAILED, SKIPPED.
6. Never store plaintext secrets, full dataset, full config or artifacts in command metadata or stream payload.
```

### 5.3. `RunnerEvent`

Maps to `runner_events`.

Important fields:

```text
id
eventId
testRun
runnerId
eventType
sequenceNo
streamMessageId
status
payloadJson
attemptCount
lastError
consumerName
createdAt
processedAt
acknowledgedAt
dlqAt
```

Implementation rules:

```text
1. id is BIGINT internal primary key.
2. eventId is runner-provided UUID idempotency key and must be globally unique as defined by ERD.
3. sequenceNo is allocated by backend per testRun.
4. unique(test_run_id, sequence_no) is required for SSE replay.
5. Status must use RunnerEventStatus values from ERD: PUBLISHED, PROCESSING, PROCESSED, FAILED, DLQ, SKIPPED.
6. payloadJson must be sanitized and compact.
7. RunnerEvent must be persisted before SSE broadcast.
```

---

## 6. Runner Internal Authentication

Runner internal APIs use service authentication.

Required header:

```http
Authorization: Bearer <runner-service-token>
X-Runner-Id: runner-01
X-Request-Id: 01J0Z7Y4Y4F9E1S2Y9W8Q7P6A5
```

Implementation rules:

```text
1. Use a separate RunnerServiceAuthenticationFilter.
2. Do not authenticate runner as a normal User.
3. Validate token scope such as runner:read-config or runner:write-events.
4. Never log runner service token.
5. CurrentUser must not be required for /internal/runner/**.
6. Runner identity should be represented by a RunnerPrincipal or similar internal principal.
```

Internal routes:

```text
POST /internal/runner/instances/register
POST /internal/runner/instances/heartbeat
GET  /internal/runner/test-runs/{testRunId}/config
POST /internal/runner/test-runs/{testRunId}/runtime-secrets
GET  /internal/runner/test-runs/{testRunId}/control-state
POST /internal/runner/test-runs/{testRunId}/artifacts
POST /internal/runner/test-runs/{testRunId}/events
```

Network rule:

```text
Deployment should prevent public internet access to /internal/runner/** when possible. API authentication is still required even on internal network.
```

---

## 7. Runner Instance Registration and Heartbeat

`RunnerInstanceService` owns register/heartbeat.

Register flow:

```text
1. Authenticate runner service token.
2. Validate X-Runner-Id matches request runnerId.
3. Upsert RunnerInstance by runnerId.
4. Store version/promptfooVersion/adapter schema/capabilities.
5. Set status=IDLE unless reconciliation says current run is active.
6. Set lastHeartbeatAt.
7. Return heartbeat interval and offline threshold.
```

Heartbeat flow:

```text
1. Authenticate runner service token.
2. Validate runner id.
3. Update status/currentTestRun/currentCommandId/health metadata.
4. Set lastHeartbeatAt.
5. Return next heartbeat interval.
```

Rules:

```text
1. Heartbeat must not update TestRun status directly unless a reconciliation policy explicitly allows it.
2. Health metadata must not include secret values.
3. Offline detection should be done by scheduled reconciliation worker, not by every user request.
```

---

## 8. Runner Config Fetch

`RunnerConfigService` owns:

```text
GET /internal/runner/test-runs/{testRunId}/config
```

Config response must be built from locked TestRun state:

```text
TestRun
TestRunTarget rows
TestRunEvaluatorVersion rows
locked domain_config_snapshot_json
sanitized target snapshots
dataset version id/schema id/schema version
runtime secret reference metadata only
artifact output policy
promptfoo adapter schema version
```

Rules:

```text
1. Do not read mutable latest project config for an already-created run.
2. Do not return plaintext secret values in config response.
3. Use secret references and RunnerConfigSecretMode.
4. Do not include deleted/disabled current provider state to invalidate a historical run after creation; rely on locked run data.
5. If runtime secret cannot be resolved later, runner event should fail the run or affected testcase according to error type.
```

`RunnerConfigAssembler` may build the internal response because it aggregates data across TestRun, target, evaluator and dataset modules.

---

## 9. Runtime Secret Resolution

`RuntimeSecretService` owns:

```text
POST /internal/runner/test-runs/{testRunId}/runtime-secrets
```

Runtime secret flow:

```text
1. Authenticate runner service token with runner:read-runtime-secrets.
2. Validate testRunId exists and is non-terminal or still executable.
3. Validate requested secret references belong to locked run config.
4. Decrypt secret values using SecretEncryptionService.
5. Return runtime secret bundle to runner.
6. Do not persist plaintext secret values.
7. Do not log plaintext secret values.
```

Rules:

```text
1. Runtime secrets are returned only over internal API.
2. Runtime secrets must not be exposed to frontend APIs.
3. Runtime secret response should be as narrow as possible.
4. Runtime secret request must not allow arbitrary secret id enumeration.
5. If a requested secret is not part of the locked run, reject with RUNNER_SECRET_REFERENCE_INVALID.
```

Recommended logging:

```text
INFO runtime secret bundle resolved: testRunId, runnerId, secretReferenceCount
WARN runtime secret reference denied: testRunId, runnerId, referenceKey
```

Do not log secret values or masked previews in this flow.

---

## 10. Control State

`RunnerControlStateService` owns:

```text
GET /internal/runner/test-runs/{testRunId}/control-state
```

Response should tell runner whether to continue or cancel:

```json
{
  "testRunId": "6e74a5df-1e54-4d73-ae1a-3d49c8991eb6",
  "status": "CANCELLING",
  "cancelRequested": true,
  "cancelRequestedAt": "2026-07-03T10:05:00+07:00"
}
```

Rules:

```text
1. Control state is read-only.
2. Runner should poll this between batches/testcases.
3. Backend cancel API sets CANCELLING and publishes CANCEL_RUN command.
4. Runner terminal event confirms CANCELLED.
```

---

## 11. Runner Command Publishing

`RunnerCommandPublisher` is the only service that publishes run commands to Redis.

Recommended interface:

```java
public interface RunnerCommandPublisher {

  /**
   * Persists and schedules a START_RUN command for Redis publication after the surrounding
   * database transaction commits.
   *
   * @param testRunId run id to start
   * @param requestedBy user id that requested the command; use for audit/log context only unless ERD adds created_by
   * @return persisted runner command id
   */
  Long publishStartRunAfterCommit(UUID testRunId, UUID requestedBy);

  /**
   * Persists and schedules a CANCEL_RUN command for Redis publication after the surrounding
   * database transaction commits.
   *
   * @param testRunId run id to cancel
   * @param requestedBy user id that requested cancellation; use for audit/log context only unless ERD adds created_by
   * @return persisted runner command id
   */
  Long publishCancelRunAfterCommit(UUID testRunId, UUID requestedBy);
}
```

Implementation rules:

```text
1. Persist RunnerCommand in the same transaction as TestRun state change.
2. XADD to Redis only after commit.
3. Store streamMessageId after successful XADD.
4. If Redis publish fails after commit, mark command as FAILED, increment attemptCount/lastError, and let the outbox retry according to policy.
5. Do not put full config or secrets into the Redis command payload.
6. Any after-commit status update must run in a separate transaction, for example through a dedicated service method using Propagation.REQUIRES_NEW.
7. Do not call a @Transactional status-update method by same-class self-invocation from the after-commit callback.
8. If Redis publish succeeds but DB update of streamMessageId fails, the outbox retry must be idempotent by commandId/testRunId.

```

Outbox worker:

```text
RunnerCommandOutboxService can periodically retry unpublished RunnerCommand rows.
Use @Async or @Scheduled depending on project configuration.
```

---

## 12. Runner Event Types

Backend should support these event types first:

```text
RUN_STARTED
TESTCASE_STARTED
TESTCASE_COMPLETED
TESTCASE_ERROR
RUN_PROGRESS
ARTIFACT_WRITTEN
RUN_COMPLETED
RUN_FAILED
RUN_CANCELLED
RUN_LOG
```

Event type handling:

```text
RUN_STARTED:
  Transition TestRun PENDING -> RUNNING, set startedAt.

TESTCASE_STARTED:
  Optional lightweight event; may persist RunnerEvent only.

TESTCASE_COMPLETED:
  Persist/merge TestCaseResult, update progress.

TESTCASE_ERROR:
  Persist TestCaseResult with ERROR/JUDGE_ERROR/TIMEOUT as appropriate, update progress.

RUN_PROGRESS:
  Update progress counters if monotonic.

ARTIFACT_WRITTEN:
  Persist PromptfooRunArtifact metadata through artifact service.

RUN_COMPLETED:
  Recompute summary metrics, transition to COMPLETED, set finishedAt.

RUN_FAILED:
  Transition to FAILED, set finishedAt, store sanitized error summary.

RUN_CANCELLED:
  Transition to CANCELLED, set finishedAt.

RUN_LOG:
  Persist compact sanitized event only if useful; do not expose large logs through SSE.
```

Rules:

```text
1. Unknown event types should be rejected or DLQ'd based on compatibility policy.
2. Event payloads must be validated before applying side effects.
3. Terminal events should be idempotent.
4. Do not downgrade terminal state with a late non-terminal event.
```

---

## 13. Event Ingestion Flow

`RunnerEventIngestionService` should handle both Redis events and optional HTTP fallback events.

Recommended processing sequence:

```text
1. Receive event message from Redis or internal HTTP fallback.
2. Parse and validate event envelope.
3. Check idempotency using testRunId + eventId.
4. Allocate next sequenceNo for testRun if new event.
5. Sanitize payload.
6. Persist RunnerEvent with sequenceNo.
7. Apply domain side effects in the same transaction when possible:
   - TestRun status transition
   - TestCaseResult persistence
   - artifact metadata persistence
   - progress updates
8. Commit transaction.
9. Broadcast persisted event to SSE service after commit.
10. ACK Redis message only after successful processing.
```

Important ordering rule:

```text
Persist RunnerEvent before broadcasting to SSE clients.
```

ACK rule:

```text
ACK Redis message only after database transaction commits and side effects are durable.
```

If SSE broadcast fails:

```text
Do not fail event ingestion only because live SSE broadcast failed. Clients can reconnect and replay from persisted RunnerEvent.
```

---

## 14. Sequence Number Allocation

`RunnerEventSequenceService` owns per-run sequence allocation.

Rules:

```text
1. sequenceNo starts at 1 per TestRun.
2. sequenceNo increases monotonically per TestRun.
3. Use database locking or atomic sequence table/query to avoid duplicates.
4. Enforce unique(test_run_id, sequence_no).
5. Replayed duplicate eventId should return existing sequenceNo and avoid duplicate side effects.
```

Possible implementation without changing ERD:

```text
Option A:
  Use a PostgreSQL transaction-level advisory lock keyed by testRunId.
  Then compute next sequence as coalesce(max(sequence_no), 0) + 1 for that testRunId.

Option B:
  Compute next sequence and rely on unique(test_run_id, sequence_no), retrying the allocation once or twice on duplicate-key conflict.
```

Future high-volume option:

```text
Add an explicit per-run sequence allocator column/table in a future ERD revision. Do not silently add test_runs.last_event_sequence_no unless ERD is updated.
```

Required constraints/indexes from ERD:

```text
unique(event_id)
unique(test_run_id, sequence_no)
index(test_run_id)
index(test_run_id, sequence_no)
index(stream_message_id)
index(status)
```

---

## 15. Idempotency and Redelivery

Redis Streams may redeliver messages when consumers crash or pending entries are claimed.

Idempotency rules:

```text
1. Runner event must include stable eventId.
2. Backend must deduplicate by eventId, which is globally unique in ERD.
3. If an event with the same eventId exists for another testRunId, treat it as a runner protocol error and send the message to DLQ after retry policy.
4. Duplicate event should not create duplicate RunnerEvent.
5. Duplicate TESTCASE_COMPLETED should not create duplicate TestCaseResult.
6. Terminal event duplicate should not re-transition terminal state.
7. If duplicate event is already processed, ACK Redis message and return success.
```

Database constraints:

```text
runner_events unique(event_id)
runner_events unique(test_run_id, sequence_no)
test_case_results unique(test_run_id, test_run_target_id, dataset_row_id)
```

When eventId is missing:

```text
Reject event with RUNNER_EVENT_ID_MISSING and send to DLQ after configured retry policy.
```

---

## 16. Redis Consumer Implementation

`RunnerEventConsumerService` may run as a background listener.

Implementation options:

```text
1. @Scheduled poll loop using Redis Stream consumer group.
2. Dedicated SmartLifecycle bean.
3. ApplicationRunner that starts a managed executor.
```

Rules:

```text
1. Keep consumer startup/shutdown graceful.
2. Do not block Spring shutdown indefinitely.
3. Use consumer group for vfqc:runs:events.
4. Read bounded batches.
5. Process each event in its own transaction or small batch transaction.
6. ACK only after durable processing.
7. Move poison messages to DLQ after retry threshold.
```

Spring/Redis note:

```text
Use Spring Data Redis Stream operations or a project-standard Redis client. Keep serialization explicit and tested. Do not rely on Java object serialization for cross-service Node.js messages.
```

Recommended JSON serialization:

```text
Redis field: payload
Value: compact JSON string
```

---

## 17. Pending Recovery and DLQ

`RunnerPendingRecoveryService` handles pending messages after consumer crash.

Baseline:

```text
Redis XAUTOCLAIM is available in Redis 6.2+.
```

Recovery flow:

```text
1. Periodically scan pending entries older than configured idle timeout.
2. Claim pending events for active backend consumer.
3. Reprocess with idempotency checks.
4. ACK if already processed.
5. Increment retry metadata if processing fails.
6. Move poison event to vfqc:runs:events:dlq after retry threshold.
```

DLQ payload should include:

```text
originalRedisMessageId
testRunId if parseable
eventId if parseable
eventType if parseable
failureCode
sanitizedFailureMessage
failedAt
retryCount
```

Rules:

```text
1. DLQ must not contain plaintext secrets or raw responses.
2. DLQ entries should be observable by ops/admin later.
3. Do not delete pending messages without either ACK or DLQ policy.
```

---

## 18. Artifact Metadata Ingest

`RunnerArtifactIngestService` owns:

```text
POST /internal/runner/test-runs/{testRunId}/artifacts
```

and handles `ARTIFACT_WRITTEN` events when using Redis.

Rules:

```text
1. Validate runner is authenticated.
2. Validate artifact belongs to testRunId.
3. Validate sanitized=true before exposing to frontend later.
4. Store metadata/path/hash only.
5. Do not store artifact file content in DB.
6. Do not trust runner-provided paths blindly; apply StoragePathPolicy.
7. configSha256/resultSha256 should be stored for audit.
```

If runner reports sanitized=false:

```text
1. Persist metadata only if needed for internal debugging policy.
2. Frontend artifact API must not expose download/read links.
3. Log WARN with testRunId and artifactType, no content.
```

---

## 19. HTTP Event Fallback

`RunnerEventFallbackController` handles:

```text
POST /internal/runner/test-runs/{testRunId}/events
```

Purpose:

```text
Allow runner to publish events over authenticated HTTP if Redis event publishing is unavailable or disabled for diagnostics.
```

Rules:

```text
1. Redis Streams remain primary transport in MVP.
2. HTTP fallback must use the same RunnerEventIngestionService.
3. HTTP fallback must enforce same validation/idempotency/sanitization.
4. HTTP fallback should not bypass sequence allocation.
5. HTTP fallback response should return accepted=true and sequenceNo when persisted.
```

---

## 20. SSE Handoff Boundary

Runner module should not own SSE connection management.

Handoff flow:

```text
RunnerEventIngestionService commits RunnerEvent
  -> after commit publish application event
    -> TestRunSseService broadcasts to active subscribers
```

Rules:

```text
1. SSE broadcast happens after RunnerEvent persistence.
2. SSE id equals RunnerEvent.sequenceNo.
3. If no active SSE subscribers exist, ingestion still succeeds.
4. Reconnect/replay reads from runner_events table.
5. Heartbeat comments are SSE-only and are not RunnerEvents.
```

Detailed SSE implementation belongs to `docs/backend/11-sse-implementation.md`.

---

## 21. Transaction Rules

Use transactions intentionally:

```text
Register runner:
  @Transactional

Heartbeat:
  @Transactional

Config fetch:
  @Transactional(readOnly = true)

Runtime secret resolution:
  @Transactional(readOnly = true)
  Do not persist plaintext secret.

Control state:
  @Transactional(readOnly = true)

Command persist:
  @Transactional
  Redis publish after commit.

Event ingestion:
  @Transactional
  ACK Redis after commit.

Pending recovery:
  Each claimed event should process in its own transaction or bounded batch.

Artifact ingest:
  @Transactional
```

Avoid:

```text
1. Holding DB transaction while waiting indefinitely on Redis.
2. ACKing Redis before DB commit.
3. Broadcasting SSE before RunnerEvent persistence.
4. Persisting runtime secret bundle.
```

---

## 22. Service Interface Rules

Service interfaces live in `runner/service`.

Every public method must have Javadoc.

Example:

```java
public interface RunnerEventIngestionService {

  /**
   * Validates, deduplicates, persists and applies a runner event. The returned sequence number is
   * the durable per-run sequence used for SSE replay.
   *
   * @param message normalized runner event message from Redis or HTTP fallback
   * @return processing result including event id, sequence number and duplicate flag
   * @throws ApiException when the event is invalid or cannot be applied
   */
  RunnerEventProcessingResult ingestEvent(RunnerEventMessage message);
}
```

Implementation rules:

```text
1. Implementation classes live in service/impl.
2. Implementation classes use @Service, @RequiredArgsConstructor and @Slf4j.
3. DB methods use @Transactional.
4. Read-only methods use @Transactional(readOnly = true).
5. Async/background workers use @Async, @Scheduled or lifecycle components only when appropriate.
6. Logs must be concise and non-sensitive.
```

---

## 23. Controller Implementation

Internal controllers are thin HTTP adapters.

Example:

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/runner")
@Tag(name = "Runner Internal", description = "Internal APIs used by the Promptfoo runner service")
public class RunnerConfigController {

  private final RunnerConfigService runnerConfigService;

  @Operation(summary = "Fetch locked test run config")
  @GetMapping("/test-runs/{testRunId}/config")
  public RunnerConfigResponse getConfig(
      @PathVariable UUID testRunId,
      @AuthenticationPrincipal RunnerPrincipal runnerPrincipal) {
    return runnerConfigService.getLockedConfig(testRunId, runnerPrincipal);
  }
}
```

Rules:

```text
1. Do not expose internal controllers under /api/v1.
2. Do not use CurrentUser for runner internal APIs.
3. Do not add frontend CORS behavior to internal endpoints unless explicitly required.
4. Use Swagger annotations but mark endpoints as internal if OpenAPI grouping supports it.
```

---

## 24. Logging Rules

Use `@Slf4j` in implementation classes.

Recommended logs:

```text
INFO runner registered: runnerId, version, promptfooVersion
INFO runner heartbeat: runnerId, status, currentTestRunId
INFO command published: commandId, testRunId, commandType, streamMessageId
INFO event persisted: testRunId, eventId, sequenceNo, eventType
INFO event applied: testRunId, eventType, statusChange, completed, total
WARN duplicate event ignored: testRunId, eventId, existingSequenceNo
WARN event moved to DLQ: streamMessageId, testRunId, eventType, failureCode
ERROR event processing failed: streamMessageId, testRunId, eventType, errorCode
```

Forbidden logs:

```text
1. Runner service token.
2. Runtime secret bundle.
3. Plain Authorization/API key/token/cookie values.
4. Full raw response body.
5. Full Promptfoo config/result artifact content.
```

---

## 25. Error Codes

Recommended error codes:

```text
RUNNER_UNAUTHORIZED
RUNNER_FORBIDDEN
RUNNER_INSTANCE_CONFLICT
RUNNER_CONFIG_NOT_FOUND
RUNNER_CONFIG_NOT_EXECUTABLE
RUNNER_SECRET_REFERENCE_INVALID
RUNNER_RUNTIME_SECRET_DENIED
RUNNER_CONTROL_STATE_NOT_FOUND
RUNNER_COMMAND_PUBLISH_FAILED
RUNNER_EVENT_INVALID
RUNNER_EVENT_ID_MISSING
RUNNER_EVENT_DUPLICATE
RUNNER_EVENT_TYPE_UNSUPPORTED
RUNNER_EVENT_SEQUENCE_CONFLICT
RUNNER_EVENT_PROCESSING_FAILED
RUNNER_EVENT_DLQ_WRITE_FAILED
RUNNER_ARTIFACT_INVALID
RUNNER_ARTIFACT_NOT_SANITIZED
```

Rules:

```text
1. Internal ProblemDetail responses still use code, messageKey and traceId.
2. Do not expose stack traces to runner.
3. Do not expose secret ids the runner is not allowed to know.
4. Use sanitized error messages in DLQ.
```

---

## 26. Testing Checklist

Unit tests:

```text
- RunnerEventValidator rejects missing eventId/testRunId/eventType.
- RunnerEventSequenceService returns monotonic sequence numbers.
- RuntimeSecretRequestValidator rejects secret references outside locked run.
- RunnerCommandPublisher builds reference-only payloads.
- RunnerEventIngestionService handles duplicate event id idempotently.
```

Service tests:

```text
- register upserts RunnerInstance.
- heartbeat updates status and lastHeartbeatAt.
- config fetch uses locked TestRun rows/snapshots.
- runtime secret resolution does not persist plaintext secret.
- RUN_STARTED transitions PENDING -> RUNNING.
- TESTCASE_COMPLETED persists TestCaseResult and progress.
- RUN_COMPLETED transitions RUNNING/CANCELLING -> COMPLETED when valid.
- late duplicate terminal event does not corrupt state.
```

Integration tests:

```text
- POST /internal/runner/instances/register requires runner token.
- GET /internal/runner/test-runs/{id}/config rejects user JWT.
- POST /internal/runner/test-runs/{id}/runtime-secrets returns only requested locked references.
- Redis event processing persists RunnerEvent before SSE replay query can read it.
- Poison event goes to DLQ after retry threshold.
```

Security tests:

```text
- Runner service token is never logged.
- Runtime secret bundle is never persisted.
- Redis command/event payloads do not contain plaintext secrets.
- Artifact sanitized=false is not exposed to frontend artifact APIs.
```

---

## 27. AI Coding Agent Checklist

Before generating runner backend code:

```text
1. Read docs/api/10-runner-internal-api.md.
2. Read docs/api/09-sse-events-contract.md for replay/sequence expectations.
3. Read docs/backend/09-test-run-implementation.md for TestRun state/result boundaries.
4. Implement runner internal DTOs as records with validation and @Schema examples.
5. Implement service interfaces in runner/service with Javadocs.
6. Implement service implementations in runner/service/impl with @Slf4j.
7. Use RunnerPrincipal, not CurrentUser, for internal APIs.
8. Persist RunnerEvent before SSE broadcast.
9. ACK Redis only after durable DB processing.
10. Deduplicate by testRunId + eventId.
11. Allocate sequenceNo per testRun and enforce uniqueness.
12. Never log or persist runtime secret bundle.
13. Keep command/event payloads compact and reference-only.
14. Use DLQ for poison events after retry threshold.
15. Keep Redis helpers in common/redis and domain side effects in runner/testrun services.
```
