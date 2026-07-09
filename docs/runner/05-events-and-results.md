# Events And Result Normalization

This document defines how the runner should emit execution events and testcase results.

The current MVP default is Redis event ingestion:

```text
Runner -> vfqc:runs:events -> Backend RunnerRedisEventWorker
```

HTTP event output remains available behind `HttpRunnerEventSink` as a fallback/dev path.

## Event Sink Boundary

Runner core must depend on an interface:

```ts
export interface RunnerEventSink {
  send(event: RunnerEventRequest): Promise<RunnerEventSinkResult>;
}

export type RunnerEventSinkResult =
  | { transport: "http"; response: RunnerEventIngestionResponse }
  | { transport: "redis"; eventId: string; testRunId: string; streamMessageId: string };
```

Implementations:

```text
RedisRunnerEventSink
HttpRunnerEventSink
```

Default:

```text
RedisRunnerEventSink
```

Reason:

```text
Redis event transport gives backend-side buffering and uses the same backend ingestion service through RunnerRedisEventWorker.
```

Implementation note:

```text
RedisRunnerEventSink cannot know the backend-assigned sequenceNo synchronously.
It returns the Redis stream message id as publish acknowledgement. HttpRunnerEventSink
returns the backend RunnerEventIngestionResponse.
```

## RunnerEventRequest

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
  status: "APPLIED" | "DUPLICATE" | "INVALID_STATE" | "FAILED";
}
```

## Event Types

```ts
export type RunnerEventType =
  | "RUN_STARTED"
  | "RUN_PROGRESS"
  | "TESTCASE_COMPLETED"
  | "TESTCASE_ERROR"
  | "RUN_COMPLETED"
  | "RUN_FAILED"
  | "RUN_CANCELLED"
  | "RUN_LOG"
  | "ARTIFACT_CREATED";
```

## Emission Order

Happy path:

```text
RUN_STARTED
TESTCASE_COMPLETED / TESTCASE_ERROR repeated
RUN_PROGRESS periodically
ARTIFACT_CREATED optional metadata-only
RUN_COMPLETED
```

Cancel path:

```text
RUN_STARTED
TESTCASE_COMPLETED / TESTCASE_ERROR for already completed work
RUN_PROGRESS optional
RUN_CANCELLED
```

Failure path:

```text
RUN_STARTED optional if failure happens after start
RUN_LOG optional sanitized diagnostic
RUN_FAILED
```

If failure occurs before `RUN_STARTED`, send `RUN_FAILED` if backend state allows it.

## Idempotency

Rules:

```text
1. Each logical event has one eventId.
2. Retry of the same logical event reuses the same eventId.
3. A different event must never reuse an eventId.
4. Backend returns the existing sequence for replayed identical event ids.
5. Reusing eventId with different metadata is a conflict.
```

Implementation note checked against backend code on 2026-07-09:

```text
RunnerEventStatus includes DUPLICATE, but exact idempotent replays currently
return the existing stored status, usually APPLIED. Treat a successful replay
with the same eventId/testRunId/eventType and existing sequence as success; do
not require the response status to be DUPLICATE.
```

Suggested event id strategy:

```text
RUN_STARTED:
  Generate once per command execution.

TESTCASE_COMPLETED / TESTCASE_ERROR:
  Generate once per testRunTargetId + datasetRowId + attempt.
  Keep the id stable across transport retries.

RUN_PROGRESS:
  Generate per progress tick.

RUN_COMPLETED / RUN_FAILED / RUN_CANCELLED:
  Generate once per final transition.
```

If a process crashes before persisting local event retry state, backend testcase result upsert still protects testcase result duplication by:

```text
(testRunId, testRunTargetId, datasetRowId)
```

## Result Status Semantics

Allowed testcase statuses in current event payload:

```text
PASSED
FAILED
ERROR
JUDGE_ERROR
TIMEOUT
```

Meanings:

```text
PASSED:
  Machine evaluation passed.

FAILED:
  Business/evaluator failure. The run itself can still complete.

ERROR:
  Execution or adapter error for one testcase/target.

JUDGE_ERROR:
  LLM judge failed or returned unparseable verdict.

TIMEOUT:
  Target/judge/testcase timeout.
```

Do not use `RUN_FAILED` for normal testcase `FAILED` results.

## TESTCASE_COMPLETED Payload

```ts
export interface TestcaseCompletedPayload {
  testRunTargetId: string;
  datasetRowId: number;
  status?: "PASSED" | "FAILED" | "ERROR" | "JUDGE_ERROR" | "TIMEOUT";
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

Runner should always include:

```text
testRunTargetId
datasetRowId
status
```

Backend behavior:

```text
1. Upserts by (testRunId, testRunTargetId, datasetRowId).
2. Missing status defaults to PASSED for TESTCASE_COMPLETED.
3. request, response, machineSummary are sanitized before persistence.
```

Runner must still sanitize before sending.

## TESTCASE_ERROR Payload

Use the same shape as `TESTCASE_COMPLETED`.

Default backend status is `ERROR` if status is omitted, but runner should send status explicitly.

Use for:

```text
target request exception
target timeout
judge timeout
judge parse error
adapter extraction error
single row/target execution error
```

## Progress Events

Progress payload:

```ts
export interface RunProgressPayload {
  completed?: number;
  total?: number;
}
```

Recommended policy:

```text
1. Send progress after each testcase result for small/medium runs.
2. For very large runs, throttle progress to avoid excessive events.
3. Always send completed and total together when known.
4. Never include raw result details in RUN_PROGRESS.
```

Example:

```json
{
  "completed": 120,
  "total": 5000
}
```

## RUN_COMPLETED Payload

```ts
export interface RunCompletedPayload {
  summary?: Record<string, unknown>;
}
```

Recommended summary:

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

Backend computes authoritative summary metrics from persisted testcase results, so runner summary is advisory.

## RUN_FAILED Payload

```ts
export interface RunFailedPayload {
  error?: string;
  errorCategory?: string;
  message?: string;
}
```

Use for unrecoverable run-level failures:

```text
invalid locked config
backend runtime secret resolution cannot proceed
Promptfoo cannot start
runner fatal error
artifact failure that invalidates result integrity
```

Sanitize error messages before sending.

Do not include stack traces with secrets or raw payloads.

## RUN_CANCELLED Payload

```ts
export interface RunCancelledPayload {
  reason?: string;
}
```

Use when cancellation wins before completion.

If backend control state says cancellation was requested but Promptfoo already completed successfully, `RUN_COMPLETED` is valid.

## RUN_LOG Payload

```ts
export interface RunLogPayload {
  level?: "debug" | "info" | "warn" | "error";
  message: string;
  data?: Record<string, unknown>;
}
```

Rules:

```text
1. Use sparingly.
2. Keep messages sanitized.
3. Do not send full request/response bodies.
4. Do not send secrets.
5. Prefer structured data with safe ids/statuses.
```

## ARTIFACT_CREATED Payload

```ts
export interface ArtifactCreatedPayload {
  artifactType?: string;
  name?: string;
  uri?: string;
  sanitized?: boolean;
  contentHash?: string;
}
```

Current backend gap:

```text
No complete artifact ingestion API exists.
```

Therefore this event is metadata-only.

Do not assume UI can download the artifact from this event.

## Sanitization Before Events

Sanitize before sending event payloads.

Sensitive keys include:

```text
Authorization
Cookie
Set-Cookie
X-API-Key
api_key
apikey
access_token
refresh_token
id_token
client_secret
password
secret
token
bearer
credential
x-auth-token
proxy-authorization
```

Mask format:

```json
{
  "Authorization": "Bearer ****MASKED****",
  "api_key": "****MASKED****"
}
```

Backend also sanitizes, but runner-side sanitization is still required.

## Request/Response Payload Size

`TESTCASE_COMPLETED` may include request/response for result detail, but runner should avoid huge payloads.

Recommended policy:

```text
1. Include sanitized compact request/response preview in event payload.
2. Keep full raw/sanitized large artifacts in local artifact storage if needed.
3. Do not send artifact content in event payload.
4. Consider truncating very large fields with clear markers.
```

Example marker:

```json
{
  "bodyPreview": "...truncated...",
  "truncated": true,
  "originalSizeBytes": 182345
}
```

## Error Category Suggestions

Suggested values:

```text
CONFIG_ERROR
BACKEND_UNAVAILABLE
REDIS_UNAVAILABLE
SECRET_RESOLUTION_ERROR
PROMPTFOO_EXECUTION_ERROR
TARGET_REQUEST_ERROR
TARGET_TIMEOUT
JUDGE_ERROR
JUDGE_TIMEOUT
JUDGE_PARSE_ERROR
ARTIFACT_WRITE_ERROR
CANCELLED
UNKNOWN
```

These are runner-side categories unless backend has stricter enum values.

Verify actual backend expectations before hardcoding them as enums.

## SSE Projection

Runner does not call SSE.

Backend projects persisted runner events to:

```text
GET /api/v1/test-runs/{testRunId}/events
```

SSE behavior:

```text
1. SSE event id equals runner_events.sequence_no.
2. SSE event name equals RunnerEventType.
3. Last-Event-ID replays events after the given sequence.
4. SSE strips large/raw unsafe fields.
```

Runner responsibility is only to emit clean backend events.

## Testing Checklist

Event/result tests should cover:

```text
1. RUN_STARTED accepted.
2. Duplicate RUN_STARTED returns duplicate status or existing sequence.
3. TESTCASE_COMPLETED upserts by target+row.
4. TESTCASE_ERROR defaults or maps to ERROR.
5. Business FAILED does not produce RUN_FAILED.
6. RUN_COMPLETED after results computes backend summary.
7. RUN_CANCELLED after cancel request.
8. Sanitizer removes tokens from request/response/machineSummary.
9. Retry reuses eventId.
10. RedisEventSink is disabled unless backend Redis event polling is explicitly enabled.
```
