# VinFast QC Copilot - Runner Event Consumer Implementation

Version: `v0.2`  
Status: Spring Boot 4.0.7-first Implementation Spec  
Applies to: `apps/api`

---

## 1. Purpose

This document explains backend-side runner orchestration, runner internal APIs, Redis Streams command/event handling, event persistence and SSE handoff.

---

## 2. Design Principles

1. PostgreSQL remains source of truth.
2. Redis Streams are transport, not durable domain state.
3. Commands and events contain references/compact payloads only.
4. Runner events are persisted before SSE broadcast.
5. SSE id equals `runner_events.sequence_no`.
6. Runner internal APIs use runner service authentication.
7. Runtime secrets may be returned only to authenticated runner.
8. Runtime secret bundles are never persisted or logged.
9. Runner config fetch returns locked TestRun config.
10. Event ingestion is idempotent and redelivery-safe.
11. Testcase FAILED is business failure; TestRun FAILED is system/execution failure.
12. Poison messages go to DLQ after retry policy.

---

## 3. Package Layout

```text
runner
  controller/RunnerInstanceController.java
  controller/RunnerConfigController.java
  controller/RunnerRuntimeSecretController.java
  controller/RunnerControlController.java
  controller/RunnerArtifactController.java
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
  enums/RunnerCommandType.java
  enums/RunnerCommandStatus.java
  enums/RunnerEventType.java
  enums/RunnerEventStatus.java
  repository/RunnerInstanceRepository.java
  repository/RunnerCommandRepository.java
  repository/RunnerEventRepository.java
  mapper/RunnerInstanceMapper.java
  mapper/RunnerCommandMapper.java
  mapper/RunnerEventMapper.java
  service/RunnerInstanceService.java
  service/RunnerConfigService.java
  service/RuntimeSecretService.java
  service/RunnerControlStateService.java
  service/RunnerCommandPublisher.java
  service/RunnerEventIngestionService.java
  service/RunnerEventConsumerService.java
  service/RunnerEventSequenceService.java
  service/RunnerDeadLetterService.java
  service/RunnerArtifactIngestService.java
  validator/RunnerEventValidator.java
  validator/RuntimeSecretRequestValidator.java
  assembler/RunnerConfigAssembler.java
  model/RunnerCommandMessage.java
  model/RunnerEventMessage.java
  model/NormalizedTestCaseResultEvent.java
```

---

## 4. Redis Baseline

Use Spring Data Redis 4.x with Lettuce.

Rules:

1. Use `StringRedisTemplate` or configured `RedisTemplate<String, String>`.
2. Do not use JDK serialization.
3. Store stream fields as simple strings / compact JSON.
4. Centralize stream names.
5. ACK after DB persistence succeeds.
6. Use consumer groups.
7. Use pending recovery (`XAUTOCLAIM`) if Redis version supports it.
8. Never place secrets/full configs/artifacts into stream payloads.

Streams:

```text
vfqc:runs:commands
vfqc:runs:events
vfqc:runs:commands:dlq
vfqc:runs:events:dlq
```

Command payload example:

```json
{
  "commandId": "2f3f3f5c-9d3d-4300-99c0-8db5d64b9ad0",
  "testRunId": "6e74a5df-1e54-4d73-ae1a-3d49c8991eb6",
  "commandType": "START_RUN",
  "createdAt": "2026-07-03T10:00:00+07:00"
}
```

Forbidden payload data:

```text
plaintext secret
full dataset rows
full target request with auth headers
full raw response body
full Promptfoo config
artifact file content
```

---

## 5. Runner Internal APIs

### Register/heartbeat

Rules:

1. Runner registers with stable runner id.
2. Heartbeat updates status and last heartbeat time.
3. Capabilities JSON is sanitized operational metadata.
4. Runner service token is not stored.

### Config fetch

Rules:

1. Return locked TestRun config.
2. Do not read mutable current project config.
3. Return sanitized config only.
4. Exclude plaintext secrets; runner resolves secrets through runtime secret endpoint.

### Runtime secret resolution

Rules:

1. Authenticated runner only.
2. Validate runner may access the TestRun.
3. Resolve only referenced secrets needed for this run.
4. Return secret values in memory response only.
5. Do not persist/log response body.
6. Do not include runtime secrets in RunnerEvent/SSE/artifacts.

---

## 6. Entity Rules

### RunnerInstance

Fields:

```text
id
runnerId
hostname
version
promptfooVersion
promptfooAdapterSchemaVersion
capabilitiesJson
status
currentTestRunId
currentCommandId
lastHeartbeatAt
startedAt
createdAt
updatedAt
```

### RunnerCommand

Fields:

```text
id
commandId
testRunId
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

Rules:

1. `commandId` is business idempotency key.
2. Stream id maps to `streamMessageId`.
3. Metadata never contains secrets/full config.

### RunnerEvent

Fields:

```text
id
eventId
testRunId
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

Rules:

1. `eventId` is globally unique idempotency key.
2. `sequenceNo` is allocated by backend per TestRun.
3. Unique `(test_run_id, sequence_no)`.
4. `payloadJson` is sanitized.
5. Persist event before SSE broadcast.

---

## 7. Event Ingestion Flow

For each Redis event:

1. Parse message.
2. Validate schema and event type.
3. Check idempotency by `eventId`.
4. Sanitize payload.
5. Allocate sequence number.
6. Persist RunnerEvent.
7. Apply domain update to TestRun/TestCaseResult/artifact metadata.
8. Commit transaction.
9. ACK Redis message.
10. Broadcast SSE after commit.

If processing fails:

1. Increment attempt count.
2. Leave pending for retry or move to DLQ according to policy.
3. Store sanitized error.
4. Do not ACK before DB state is safe.

---

## 8. Event Types

Recommended:

```text
RUN_STARTED
RUN_PROGRESS
TESTCASE_STARTED
TESTCASE_COMPLETED
TESTCASE_ERROR
ARTIFACT_CREATED
RUN_COMPLETED
RUN_FAILED
RUN_CANCELLED
RUN_LOG
```

Rules:

1. RUN_LOG must be sanitized and short.
2. Full logs belong to sanitized artifact storage if needed.
3. Terminal run events trigger state machine transitions.
4. Testcase events upsert `TestCaseResult` idempotently.

---

## 9. Spring Usage

Use:

1. Spring Data Redis stream operations.
2. `@Scheduled` or application runner for consumer loop if needed.
3. `TaskExecutor` for background consumers.
4. `@Transactional` for DB event ingestion.
5. `@TransactionalEventListener(phase = AFTER_COMMIT)` or equivalent after-commit callback for SSE fanout.
6. Actuator health for runner/Redis/database checks.

Do not:

1. Read Redis directly from SSE module.
2. Use JDK serialization.
3. Store secrets in Redis.
4. ACK before DB commit.
5. Mutate dataset/target/evaluator/provider definitions from runner module.

---

## 10. Tests

Required tests:

1. Duplicate eventId is idempotent.
2. Sequence numbers are per-run and monotonic.
3. Redis payload with secret-like content is sanitized before persistence.
4. Event is not ACKed when DB persistence fails.
5. Terminal events transition TestRun correctly.
6. Runtime secret endpoint rejects user JWT.
7. Runner token cannot call frontend APIs.
