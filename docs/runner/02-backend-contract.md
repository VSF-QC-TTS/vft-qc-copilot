# Backend Contract For Runner

This document captures the current backend contract that the standalone runner must use.

Prefer actual backend Java records/controllers over this file if they differ.

Primary backend source root:

```text
apps/api/src/main/java/vn/vinfast/vfqc/api
```

Primary backend runner files:

```text
runner/controller/RunnerInstanceController.java
runner/controller/RunnerTestRunController.java
runner/controller/RunnerEventController.java
runner/dto/*.java
runner/enums/*.java
runner/service/impl/RunnerTestRunCommandService.java
testrun/sse/TestRunSseEventResponse.java
```

## Authentication

All `/internal/runner/**` endpoints require:

```text
runner service token
Spring role RUNNER
```

Rules:

```text
1. User JWTs cannot call /internal/runner/**.
2. Runner tokens cannot call frontend /api/** endpoints.
3. Runner should only call backend internal runner endpoints.
```

## Existing Internal Runner Endpoints

```text
POST /internal/runner/instances/register
POST /internal/runner/instances/heartbeat
GET  /internal/runner/test-runs/{testRunId}/config
POST /internal/runner/test-runs/{testRunId}/runtime-secrets
GET  /internal/runner/test-runs/{testRunId}/control-state
POST /internal/runner/events
```

The public SSE endpoint is not called by the runner:

```text
GET /api/v1/test-runs/{testRunId}/events
```

Frontend uses this endpoint. Runner events are persisted by backend first, then projected to SSE.

## Redis Command Contract

Backend publishes compact commands to:

```text
vfqc:runs:commands
```

Backend event consumer may read compact runner events from:

```text
vfqc:runs:events
```

Implementation note checked against backend code on 2026-07-09:

```text
RunnerRedisEventConsumerImpl reads vfqc:runs:events with consumer group vfqc-api
and consumer name api-runner-events. It maps Redis fields to RunnerEventRequest
and has retry/DLQ behavior. RunnerRedisEventWorker runs as a long-running backend
worker when vfqc.runner.redis-events.enabled=true, which is the default outside
tests. Keep HTTP event ingestion as a fallback/dev path.
```

DLQ streams:

```text
vfqc:runs:commands:dlq
vfqc:runs:events:dlq
```

Current runner v1 recommendation:

```text
Consume commands from Redis.
Send events through vfqc:runs:events by default.
Use POST /internal/runner/events only as an explicit fallback/dev path.
```

## RunnerCommandMessage

Redis command payload shape:

```ts
export interface RunnerCommandMessage {
  commandId: string; // UUID
  testRunId: string; // UUID
  commandType: 'START_RUN' | 'CANCEL_RUN';
  createdAt: string; // ISO offset date-time
}
```

Rules:

```text
1. Redis payloads contain references only.
2. Redis command payloads must not contain plaintext secrets.
3. Redis command payloads must not contain dataset rows.
4. Redis command payloads must not contain full target/evaluator config.
5. Redis command payloads must not contain raw responses.
6. Redis command payloads must not contain full Promptfoo config.
7. Redis command payloads must not contain artifact contents.
8. START_RUN triggers config fetch from backend.
9. CANCEL_RUN is a best-effort cancellation signal.
10. Runner must also poll control-state during long execution.
```

## Enums

```ts
export type RunnerCommandType = 'START_RUN' | 'CANCEL_RUN';

export type RunnerCommandStatus = 'PENDING' | 'PUBLISHED' | 'ACKNOWLEDGED' | 'FAILED';

export type RunnerInstanceStatus = 'IDLE' | 'BUSY' | 'OFFLINE';

export type RunnerEventStatus = 'APPLIED' | 'DUPLICATE' | 'INVALID_STATE' | 'FAILED';

export type RunnerEventType =
  | 'RUN_STARTED'
  | 'RUN_PROGRESS'
  | 'TESTCASE_COMPLETED'
  | 'TESTCASE_ERROR'
  | 'RUN_COMPLETED'
  | 'RUN_FAILED'
  | 'RUN_CANCELLED'
  | 'RUN_LOG'
  | 'ARTIFACT_CREATED';

export type TestRunStatus =
  | 'CREATED'
  | 'QUEUED'
  | 'RUNNING'
  | 'CANCEL_REQUESTED'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED';

export type TestRunTargetType = 'CHATBOT_TARGET' | 'LLM_BASELINE';

export type TestRunType = 'SINGLE_TARGET' | 'COMPARISON';
```

## Register Runner Instance

Endpoint:

```text
POST /internal/runner/instances/register
```

Request:

```ts
export interface RegisterRunnerInstanceRequest {
  runnerKey: string;
  hostname?: string | null;
  version?: string | null;
}
```

Response:

```ts
export interface RunnerInstanceResponse {
  id: string;
  runnerKey: string;
  hostname?: string | null;
  version?: string | null;
  status: RunnerInstanceStatus;
  currentCommandId?: string | null;
  currentTestRunId?: string | null;
  lastHeartbeatAt?: string | null;
  registeredAt: string;
  updatedAt: string;
}
```

Runner behavior:

```text
1. Call register once during boot.
2. Store returned runner instance id in memory.
3. Use returned id in RunnerEventRequest.runnerId when available.
4. Do not continue execution if registration fails permanently.
```

## Heartbeat

Endpoint:

```text
POST /internal/runner/instances/heartbeat
```

Request:

```ts
export interface RunnerHeartbeatRequest {
  runnerKey: string;
  status?: RunnerInstanceStatus | null;
  currentCommandId?: string | null;
  currentTestRunId?: string | null;
}
```

Response:

```ts
export interface RunnerInstanceResponse {
  id: string;
  runnerKey: string;
  hostname?: string | null;
  version?: string | null;
  status: RunnerInstanceStatus;
  currentCommandId?: string | null;
  currentTestRunId?: string | null;
  lastHeartbeatAt?: string | null;
  registeredAt: string;
  updatedAt: string;
}
```

Heartbeat states:

```text
IDLE:
  No active command/run.

BUSY:
  Runner is executing one START_RUN command.

OFFLINE:
  Backend-owned status when heartbeat is stale.
```

Recommended heartbeat behavior:

```text
1. Send IDLE after registration.
2. Send BUSY with currentCommandId and currentTestRunId while running.
3. Send IDLE after terminal event is sent.
4. Keep heartbeat loop independent from Promptfoo execution.
5. Never include secret/config/result payloads in heartbeat.
```

## Fetch Locked TestRun Config

Endpoint:

```text
GET /internal/runner/test-runs/{testRunId}/config
```

Pagination:

```text
?page=0&size=100
```

Backend handoff says default backend page size is 100 rows.

Response:

```ts
export interface RunnerTestRunConfigResponse {
  testRunId: string;
  projectId: string;
  runType: TestRunType;
  status: TestRunStatus;

  datasetId: string;
  datasetVersionId: string;
  datasetSchemaId: string;
  datasetSchemaVersion: number;

  progressTotal: number;

  domainSnapshot: Record<string, unknown>;
  promptfooSnapshot: Record<string, unknown>;

  targets: TargetSnapshot[];
  evaluators: EvaluatorSnapshot[];
  rows: DatasetRowSnapshot[];
}

export interface TargetSnapshot {
  id: string;
  targetType: TestRunTargetType;
  chatbotTargetId?: string | null;
  providerId?: string | null;
  model?: string | null;
  name: string;
  orderIndex: number;
  config: Record<string, unknown>;
}

export interface EvaluatorSnapshot {
  id: number;
  evaluatorId: string;
  evaluatorVersionId: string;
  evaluatorType: string;
  version: number;
  orderIndex: number;
  config: Record<string, unknown>;
  rules: Array<Record<string, unknown>>;
}

export interface DatasetRowSnapshot {
  id: number;
  rowIndex: number;
  cells: Record<string, unknown>;
  revision: number;
  rowHash: string;
}
```

Rules:

```text
1. This is locked run config.
2. Runner must not read mutable project/target/evaluator state from normal frontend APIs.
3. Config is sanitized.
4. Plaintext secrets are excluded.
5. TargetSnapshot.id is required for testcase result events.
6. DatasetRowSnapshot.id is required for testcase result events.
7. promptfooSnapshot exists, but runner still owns conversion into executable Promptfoo config.
8. If the run has more rows than one page, runner must page through rows or document backend row-page gap.
```

Implementation note:

```text
Before coding row paging, inspect actual backend controller/service behavior.
If endpoint only returns a single page without a clear total/page contract, document a backend gap.
```

## Resolve Runtime Secrets

Endpoint:

```text
POST /internal/runner/test-runs/{testRunId}/runtime-secrets
```

Request:

```ts
export interface RunnerRuntimeSecretsRequest {
  secretIds: string[];
}
```

Response:

```ts
export interface RunnerRuntimeSecretsResponse {
  secrets: Record<string, string>;
}
```

Rules:

```text
1. Request only secret ids referenced by locked snapshots for this run.
2. Backend rejects secret ids not authorized for the run.
3. Returned values are plaintext runtime secrets.
4. Runner must keep them memory-only.
5. Runner must not persist returned values.
6. Runner must not log returned values.
7. Runner must not emit returned values in event payloads.
8. Runner must not include returned values in artifacts.
```

## Control State

Endpoint:

```text
GET /internal/runner/test-runs/{testRunId}/control-state
```

Response:

```ts
export interface RunnerControlStateResponse {
  testRunId: string;
  status: TestRunStatus;
  cancellationRequested: boolean;
}
```

Rules:

```text
1. Poll while executing long runs.
2. If cancellationRequested is true, stop best-effort.
3. Send RUN_CANCELLED if cancellation wins before completion.
4. Backend allows CANCEL_REQUESTED -> COMPLETED if Promptfoo already finished.
5. Do not assume cancellation always wins.
```

## Send Runner Event

Endpoint:

```text
POST /internal/runner/events
```

Request:

```ts
export interface RunnerEventRequest {
  eventId: string;
  testRunId: string;
  eventType: RunnerEventType;
  runnerId?: string | null;
  commandId?: string | null;
  occurredAt?: string | null;
  payload?: Record<string, unknown> | null;
}
```

Response:

```ts
export interface RunnerEventIngestionResponse {
  eventId: string;
  testRunId: string;
  sequenceNo: number;
  status: RunnerEventStatus;
}
```

Rules:

```text
1. eventId is globally idempotent.
2. Re-sending the exact same eventId, testRunId, and eventType returns existing sequence.
3. Reusing eventId with different metadata is a conflict.
4. Backend sanitizes payload before persistence.
5. Runner must still sanitize payload before sending.
6. Backend assigns per-run sequenceNo.
7. Backend persists RunnerEvent before SSE broadcast.
```

Implementation note checked against backend code on 2026-07-09:

```text
RunnerEventStatus includes DUPLICATE, but RunnerEventIngestionServiceImpl currently
returns the existing stored event status for exact idempotent replays, usually
APPLIED. Runner retry logic should treat a successful replay with the same
eventId/testRunId/eventType and existing sequence as success, even when the
response status is APPLIED rather than DUPLICATE.
```

Retry rule:

```text
If HTTP send fails due to transient backend/network error, retry the same logical event with the same eventId.
```

Do not generate a new event id for the same logical retry.

## Event Payloads With Backend Side Effects

Only the keys below have backend side effects today.

Extra keys may be persisted after sanitization, but runner must not rely on them for state transitions unless backend is extended.

### RUN_STARTED

Moves run from:

```text
QUEUED -> RUNNING
```

Payload:

```ts
export interface RunStartedPayload {
  status?: 'RUNNING';
}
```

Recommended payload:

```json
{
  "status": "RUNNING"
}
```

### RUN_PROGRESS

Allowed when run is:

```text
RUNNING
CANCEL_REQUESTED
```

Payload:

```ts
export interface RunProgressPayload {
  completed?: number;
  total?: number;
}
```

Recommended payload:

```json
{
  "completed": 25,
  "total": 100
}
```

### TESTCASE_COMPLETED

Allowed when run is:

```text
RUNNING
CANCEL_REQUESTED
```

Payload:

```ts
export interface TestcaseCompletedPayload {
  testRunTargetId: string;
  datasetRowId: number;

  status?: 'PASSED' | 'FAILED' | 'ERROR' | 'JUDGE_ERROR' | 'TIMEOUT';

  score?: number | string;
  passed?: boolean;
  errorCategory?: string;

  request?: Record<string, unknown>;
  response?: Record<string, unknown>;
  extractedOutput?: Record<string, unknown>;
  gradingResult?: Record<string, unknown>;
  componentResults?: Record<string, unknown>;
  tokenUsage?: Record<string, unknown>;

  latencyMs?: number | string;
  machineSummary?: string;
}
```

Backend behavior:

```text
1. Upserts by (testRunId, testRunTargetId, datasetRowId).
2. If status is omitted, default is PASSED.
3. FAILED means business/evaluator failure, not system failure.
4. request, response, and machineSummary are sanitized before persistence.
```

Runner behavior:

```text
1. Always send testRunTargetId.
2. Always send datasetRowId.
3. Send status explicitly.
4. Sanitize request/response before sending.
5. Do not send runtime secrets.
6. Do not send full Promptfoo config.
7. Do not send artifact content.
```

### TESTCASE_ERROR

Same shape as TESTCASE_COMPLETED.

Backend behavior:

```text
1. If status is omitted, default is ERROR.
2. Use for execution/evaluator errors for a single row/target.
```

Runner should use this for:

```text
target request timeout
target request execution error
judge parse error
judge timeout
single testcase adapter error
```

Do not use this for normal evaluator failure.

### RUN_COMPLETED

Moves run to:

```text
COMPLETED
```

Backend computes summary metrics from persisted testcase results.

Payload:

```ts
export interface RunCompletedPayload {
  summary?: Record<string, unknown>;
}
```

Recommended payload:

```json
{
  "summary": {
    "total": 100,
    "passed": 92,
    "failed": 6,
    "errored": 2,
    "durationMs": 123456
  }
}
```

### RUN_FAILED

Moves run to:

```text
FAILED
```

Payload:

```ts
export interface RunFailedPayload {
  error?: string;
  errorCategory?: string;
  message?: string;
}
```

Use RUN_FAILED for unrecoverable system-level failure.

Examples:

```text
backend config API permanently unavailable
invalid locked config
Promptfoo cannot start
artifact write failure that makes result integrity impossible
unexpected fatal runner error
```

Do not use RUN_FAILED for normal testcase business failures.

### RUN_CANCELLED

Moves run to:

```text
CANCELLED
```

Payload:

```ts
export interface RunCancelledPayload {
  reason?: string;
}
```

Use when cancellation wins before successful completion.

### RUN_LOG

No run state transition.

Payload:

```ts
export interface RunLogPayload {
  level?: 'debug' | 'info' | 'warn' | 'error';
  message: string;
  data?: Record<string, unknown>;
}
```

Rules:

```text
1. Payload is persisted and may be projected to SSE after sanitization.
2. Do not send secrets.
3. Do not send raw request/response bodies.
4. Use sparingly to avoid noisy event streams.
```

### ARTIFACT_CREATED

No complete artifact ingestion API exists in current backend implementation.

Payload:

```ts
export interface ArtifactCreatedPayload {
  artifactType?: string;
  name?: string;
  uri?: string;
  sanitized?: boolean;
  contentHash?: string;
}
```

Treat this event as metadata-only unless backend is extended.

Gap:

```text
Docs mention artifact metadata/content handling, but current implemented backend runner API does not expose a dedicated artifact upload/metadata endpoint.
If Promptfoo reports/config files must be downloadable, design backend artifact ingestion first.
```

## SSE Projection DTO

Frontend subscribers receive projected events from:

```text
GET /api/v1/test-runs/{testRunId}/events
```

Runner does not call this endpoint.

Projection DTO:

```ts
export interface TestRunSseEventResponse {
  eventId: string;
  sequenceNo: number;
  testRunId: string;
  eventType: RunnerEventType;
  occurredAt?: string | null;
  createdAt: string;
  payload: Record<string, unknown>;
}
```

Projection rules:

```text
1. SSE event id equals runner_events.sequence_no.
2. SSE event name equals RunnerEventType.
3. Last-Event-ID replays events after the given sequence.
4. Replay without cursor is capped.
5. SSE strips large/raw unsafe fields such as request, response, headers, raw body, raw cURL, runtime secrets, Promptfoo config, and artifact content.
```

## Suggested Runner-Side DTOs

These do not exist in backend today. They are runner-side implementation DTOs.

```ts
export interface RunnerAppConfig {
  backendBaseUrl: string;
  runnerToken: string;
  runnerKey: string;
  hostname?: string;
  version: string;

  redisUrl?: string;
  commandStream: 'vfqc:runs:commands';
  eventStream?: 'vfqc:runs:events';

  eventTransport: 'http' | 'redis';

  heartbeatIntervalMs: number;
  controlPollIntervalMs: number;
  workingDirectory: string;
}
```

```ts
export interface RunnerExecutionContext {
  command: RunnerCommandMessage;
  runnerInstance: RunnerInstanceResponse;
  config: RunnerTestRunConfigResponse;
  runtimeSecrets: RunnerRuntimeSecretsResponse;
  startedAt: string;
  cancellation: AbortSignal;
}
```

```ts
export interface PromptfooAdapterInput {
  testRunId: string;
  targets: TargetSnapshot[];
  evaluators: EvaluatorSnapshot[];
  rows: DatasetRowSnapshot[];
  promptfooSnapshot: Record<string, unknown>;
  runtimeSecretsById: Record<string, string>;
}
```

```ts
export interface PromptfooGeneratedConfig {
  configPath: string;
  workingDirectory: string;
  sanitizedPreview?: Record<string, unknown>;
  referencedSecretIds: string[];
}
```

```ts
export interface PromptfooCaseResult {
  testRunTargetId: string;
  datasetRowId: number;
  status: 'PASSED' | 'FAILED' | 'ERROR' | 'JUDGE_ERROR' | 'TIMEOUT';
  score?: number;
  passed?: boolean;
  request?: Record<string, unknown>;
  response?: Record<string, unknown>;
  extractedOutput?: Record<string, unknown>;
  gradingResult?: Record<string, unknown>;
  componentResults?: Record<string, unknown>;
  tokenUsage?: Record<string, unknown>;
  latencyMs?: number;
  machineSummary?: string;
}
```

```ts
export interface PromptfooRunSummary {
  total: number;
  passed: number;
  failed: number;
  errored: number;
  durationMs?: number;
}
```

## Backend Gaps

Current known gaps:

```text
1. No dedicated backend artifact upload/metadata endpoint is currently implemented.
2. No explicit command acknowledgement endpoint is currently implemented.
3. Redis command consumer for the standalone runner is not implemented in this repository yet.
4. Exact Promptfoo adapter schema is not finalized.
5. Large dataset row paging may need a more explicit runner row-page contract.
6. HTTP event output is fallback/dev; Redis event output is the runner v1 default.
```

When implementing runner code, do not fill these gaps by inventing backend APIs.

Document backend change requests separately.

## Contract Verification Checklist

Before coding runner implementation, inspect:

```text
apps/api/src/main/java/vn/vinfast/vfqc/api/runner/controller
apps/api/src/main/java/vn/vinfast/vfqc/api/runner/dto
apps/api/src/main/java/vn/vinfast/vfqc/api/runner/enums
apps/api/src/main/java/vn/vinfast/vfqc/api/runner/service/impl
apps/api/src/main/java/vn/vinfast/vfqc/api/testrun/sse
```

Verify:

```text
1. Endpoint paths.
2. Auth requirements.
3. DTO field names.
4. Enum names and values.
5. Event payload keys with side effects.
6. Idempotency behavior.
7. Sanitization behavior.
8. SSE projection behavior.
9. Redis command stream names.
10. Whether Redis event stream is actually supported for standalone runner events.
```

If differences exist, update this document before implementing runner code.
