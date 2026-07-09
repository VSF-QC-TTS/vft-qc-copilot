# Cancellation, Retry, Timeout, And Idempotency

This document defines how the runner handles cancellation, retry, timeout, finalization, and idempotency.

## Source Basis

This document is based on:

```text
AI_RUNNER_DTO_HANDOFF.md
API_IMPLEMENTATION_CONTEXT.md
PRD v0.7
```

Current backend status enum values are:

```ts
export type TestRunStatus =
  | 'CREATED'
  | 'QUEUED'
  | 'RUNNING'
  | 'CANCEL_REQUESTED'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED';
```

Do not use older PRD names such as `PENDING` or `CANCELLING` in runner code unless actual backend code changes.

## Scope

This document covers:

```text
1. Cancellation signals.
2. Cancellation checkpoints.
3. Timeout policy.
4. Retry policy.
5. Event idempotency.
6. Command idempotency.
7. Final event behavior.
8. Failure classification.
```

## Cancellation Signals

The runner has two cancellation signals:

```text
1. CANCEL_RUN command from Redis.
2. Backend control-state polling.
```

Control-state endpoint:

```text
GET /internal/runner/test-runs/{testRunId}/control-state
```

Response shape:

```ts
export interface RunnerControlStateResponse {
  testRunId: string;
  status: TestRunStatus;
  cancellationRequested: boolean;
}
```

Rules:

```text
1. CANCEL_RUN is best-effort.
2. Control-state polling is required during long runs.
3. If cancellationRequested is true, stop best-effort.
4. If Promptfoo already finished before cancellation is observed, RUN_COMPLETED is valid.
5. Backend allows CANCEL_REQUESTED -> COMPLETED.
```

## Cancellation Checkpoints

Check cancellation at least:

```text
1. Before fetching runtime secrets.
2. Before building Promptfoo config.
3. Before starting Promptfoo execution.
4. Before each testcase.
5. After each testcase.
6. Before each target request.
7. Before each LLM judge call.
8. Before writing final artifacts.
9. Before sending final event.
```

Suggested component:

```text
src/runner/cancellation.service.ts
```

Suggested interface:

```ts
export interface CancellationService {
  readonly signal: AbortSignal;

  startPolling(testRunId: string): void;

  markCancelCommandReceived(commandId: string): void;

  isCancellationRequested(): boolean;

  throwIfCancellationRequested(): void;

  stop(): Promise<void>;
}
```

## Cancellation Event Behavior

If cancellation wins:

```text
Runner sends RUN_CANCELLED.
```

Payload:

```json
{
  "reason": "Cancellation requested"
}
```

If run already completed:

```text
Runner sends RUN_COMPLETED.
```

Do not send both final events for the same run.

Final event types:

```text
RUN_COMPLETED
RUN_FAILED
RUN_CANCELLED
```

There must be exactly one logical final event per command execution.

## AbortSignal Usage

Use `AbortController` internally.

Pass the signal to:

```text
1. Backend HTTP calls where supported.
2. Target HTTP calls where supported.
3. LLM judge calls where supported.
4. Promptfoo execution wrapper if supported.
```

If a third-party call cannot be aborted, use timeout wrappers and check cancellation after the call returns.

## Timeout Policy

Recommended default values from PRD:

```text
targetRequestTimeoutSeconds = 60
llmJudgeTimeoutSeconds = 120
testcaseTimeoutSeconds = 180
runTimeoutHours = 6
```

In runner config, store as milliseconds:

```text
VFQC_TARGET_REQUEST_TIMEOUT_MS=60000
VFQC_LLM_JUDGE_TIMEOUT_MS=120000
VFQC_TESTCASE_TIMEOUT_MS=180000
VFQC_RUN_TIMEOUT_MS=21600000
```

Rules:

```text
1. Do not hardcode timeout values in business logic.
2. Validate timeout config at startup.
3. Timeout for one target/testcase should not always fail the whole run.
4. Whole-run timeout should fail the run with RUN_FAILED.
5. Timeout results must be emitted as TESTCASE_ERROR or TESTCASE_COMPLETED with status TIMEOUT, depending on backend behavior verified in code.
```

Recommended mapping:

```text
Target request timeout:
  TESTCASE_ERROR with status TIMEOUT

LLM judge timeout:
  TESTCASE_ERROR with status TIMEOUT or JUDGE_ERROR

Testcase timeout:
  TESTCASE_ERROR with status TIMEOUT

Whole run timeout:
  RUN_FAILED with errorCategory RUN_TIMEOUT
```

## Retry Principles

Retry only transient/system errors.

Retry examples:

```text
1. Backend HTTP temporary unavailable.
2. Redis temporary issue.
3. Event ingestion temporary network failure.
4. Runner command reclaim after crash.
5. Artifact write temporary filesystem error if retryable.
```

Do not retry business failures:

```text
1. Chatbot answer is wrong.
2. Rule-based evaluator fails.
3. LLM judge grades fail.
4. Assertion result is FAILED.
```

A business failure is a valid testcase result, not a system retry condition.

## Retry Budgets

Recommended defaults:

```text
VFQC_COMMAND_MAX_ATTEMPTS=3
VFQC_BACKEND_HTTP_MAX_ATTEMPTS=3
VFQC_EVENT_SEND_MAX_ATTEMPTS=3
VFQC_REDIS_MAX_ATTEMPTS=3
```

Recommended backoff:

```text
exponential backoff with jitter
```

Example:

```text
baseDelayMs = 500
maxDelayMs = 10000
jitter = +/- 20%
```

Do not retry forever.

## Event Idempotency

Backend event ingestion uses `eventId` for idempotency.

Rules:

```text
1. Generate one eventId per logical event.
2. If retrying the same logical event, reuse the same eventId.
3. Do not generate a new eventId for each HTTP retry.
4. Do not reuse an eventId with different metadata/payload.
5. Treat a successful idempotent replay as success when backend returns the existing sequence, even if status is APPLIED rather than DUPLICATE.
6. Treat backend status INVALID_STATE as non-retryable unless backend contract says otherwise.
```

Suggested event id handling:

```ts
const event = eventFactory.testcaseCompleted({
  testRunId,
  testRunTargetId,
  datasetRowId,
  result,
});

await eventSink.sendWithRetry(event); // same event.eventId on every retry
```

## Testcase Result Idempotency

Backend upserts testcase results by:

```text
(testRunId, testRunTargetId, datasetRowId)
```

Runner must always send:

```text
testRunId
testRunTargetId
datasetRowId
```

Do not use target display name as identity.

Use:

```text
TargetSnapshot.id
DatasetRowSnapshot.id
```

from locked config.

## Command Idempotency

Command message identity:

```text
commandId + testRunId
```

Rules:

```text
1. START_RUN should be executed at most once logically.
2. Duplicate delivery is possible with Redis Streams.
3. Runner should check backend run status before executing.
4. If run is terminal, skip duplicate START_RUN safely.
5. If current runner already executes same command, ignore duplicate in same process.
6. If another runner reclaimed command, backend/event idempotency must prevent duplicate results.
```

Terminal statuses:

```text
COMPLETED
FAILED
CANCELLED
```

## Redis Command ACK

Redis-level `XACK` should happen only after safe finalization.

Recommended START_RUN ACK condition:

```text
1. Final event sent successfully, or duplicate final event confirmed.
2. Local cleanup attempted.
3. Runner heartbeat can return to IDLE.
```

Current backend gap:

```text
No explicit backend command acknowledgement endpoint is currently implemented.
```

Therefore distinguish:

```text
Redis XACK:
  acknowledges stream delivery.

Backend command status ACKNOWLEDGED:
  not currently exposed through a dedicated endpoint.
```

Do not invent a backend ACK endpoint.

## Final Event Ordering

Recommended finalization order:

```text
1. Stop accepting new testcase work.
2. Flush pending testcase events.
3. Write optional sanitized artifacts.
4. Send exactly one final event:
   - RUN_COMPLETED, or
   - RUN_FAILED, or
   - RUN_CANCELLED.
5. XACK Redis command if using Redis command consumer.
6. Heartbeat IDLE.
```

If final event send fails temporarily:

```text
Retry with same eventId.
```

If final event cannot be sent after max attempts:

```text
Do not XACK command.
Leave message pending for recovery, or move to DLQ according to command consumer policy.
```

## Error Classification

Suggested categories:

```ts
export type RunnerErrorCategory =
  | 'CONFIG_ERROR'
  | 'BACKEND_UNAVAILABLE'
  | 'BACKEND_CONTRACT_ERROR'
  | 'REDIS_UNAVAILABLE'
  | 'PROMPTFOO_EXECUTION_ERROR'
  | 'TARGET_REQUEST_ERROR'
  | 'JUDGE_ERROR'
  | 'ARTIFACT_WRITE_ERROR'
  | 'CANCELLED'
  | 'RUN_TIMEOUT'
  | 'UNKNOWN';
```

Mapping:

```text
CONFIG_ERROR:
  RUN_FAILED

BACKEND_UNAVAILABLE before run starts:
  retry, then RUN_FAILED or leave command pending depending on stage

TARGET_REQUEST_ERROR for one row/target:
  TESTCASE_ERROR

JUDGE_ERROR for one row/target:
  TESTCASE_ERROR with status JUDGE_ERROR

Business assertion failed:
  TESTCASE_COMPLETED with status FAILED

RUN_TIMEOUT:
  RUN_FAILED
```

## Backend HTTP Retry

Backend client should retry only safe calls where idempotency is clear.

Usually retryable:

```text
GET /internal/runner/test-runs/{testRunId}/config
GET /internal/runner/test-runs/{testRunId}/control-state
POST /internal/runner/instances/heartbeat
POST /internal/runner/events with same eventId
```

Be careful with:

```text
POST /internal/runner/test-runs/{testRunId}/runtime-secrets
```

It can be retried for transient failure, but never log the response or request payload beyond secret id count.

## Retryable HTTP Statuses

Recommended retryable statuses:

```text
408
409 only if backend contract says retryable
425
429
500
502
503
504
network timeout
connection reset
```

Recommended non-retryable statuses:

```text
400
401
403
404
422
```

For non-retryable backend contract errors, fail fast with sanitized error details.

## RUN_PROGRESS Emission

Do not emit progress too often.

Recommended policy:

```text
1. Emit after every N testcase results, or
2. Emit at a time interval, or
3. Emit on percentage change.
```

Example config:

```text
VFQC_PROGRESS_EVENT_INTERVAL_MS=1000
VFQC_PROGRESS_EVENT_EVERY_N_CASES=10
```

Avoid sending one extra `RUN_PROGRESS` for every tiny internal step.

## Implementation Checklist

```text
1. Add CancellationService.
2. Add timeout config to env schema.
3. Add retry helper with jitter.
4. Ensure event retry reuses eventId.
5. Ensure final event is sent exactly once logically.
6. Ensure Redis XACK happens after final event success.
7. Add tests for cancellation before run, during testcase, and after Promptfoo completion.
8. Add tests for timeout mapping.
9. Add tests for HTTP retry and non-retryable status handling.
10. Add tests for duplicate event handling.
```
