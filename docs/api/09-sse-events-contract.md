# VinFast QC Copilot - SSE Events Contract

Version: `v0.2`  
Status: API Contract Draft  
Base path: `/api/v1`  
Related docs:

- `docs/api/00-api-overview.md`
- `docs/api/01-api-conventions.md`
- `docs/api/02-auth-permission-contract.md`
- `docs/api/08-test-run-api.md`
- `docs/api/10-runner-internal-api.md`
- `prd_v07.md`
- `erd_v05.md`

---

## 1. Purpose

This document defines the Server-Sent Events contract for realtime TestRun progress in VinFast QC Copilot.

SSE is used by the frontend to display run progress, testcase completion, terminal state and lightweight status updates while the Node Runner executes Promptfoo through Redis Streams.

This contract covers:

```text
1. SSE endpoint.
2. Authentication and headers.
3. Browser client rule.
4. Event wire format.
5. Event names and payload schemas.
6. Reconnect and Last-Event-ID replay behavior.
7. Snapshot fallback behavior.
8. Security and masking rules.
9. Backend persistence and ACK expectations.
10. Frontend state update rules.
```

This document does not define:

```text
1. TestRun creation and result REST APIs.
2. Redis Streams command/event consumer implementation.
3. Runner internal APIs.
4. Promptfoo result normalization internals.
5. Dashboard UI layout.
```

Those details belong in `08-test-run-api.md`, `10-runner-internal-api.md`, backend specs and runner specs.

---

## 2. Design Principles

```text
1. SSE is read-only from backend to frontend.
2. Client commands such as cancel run must use REST APIs, not SSE.
3. SSE payloads must be compact and UI-oriented.
4. SSE payloads must not contain plaintext secrets.
5. SSE payloads must not contain full raw request, raw response or large artifacts.
6. Backend must persist runner events before broadcasting to SSE clients.
7. SSE id must support reconnect/replay.
8. Frontend must be able to recover from dropped SSE connection using snapshot REST APIs.
9. SSE is a convenience realtime layer; PostgreSQL remains the source of truth.
10. Redis Streams remain backend/runner transport; frontend never reads Redis directly.
```

---

## 3. Endpoint Summary

| Method | Endpoint | Purpose |
| ------ | -------- | ------- |
| `GET` | `/api/v1/test-runs/{testRunId}/events` | Subscribe to realtime events for one TestRun |

The endpoint belongs to Test Run APIs but has its own contract because SSE has different wire-format, reconnect and buffering requirements from normal JSON REST APIs.

---

## 4. Authentication Rule

### 4.1. Access token

The SSE endpoint requires the same access token as other protected frontend-facing APIs.

Required request header:

```http
Authorization: Bearer <accessToken>
```

### 4.2. Native EventSource rule

Native browser `EventSource` does not support custom request headers in a reliable standard way.

Therefore MVP frontend must use a fetch-based SSE client that can send headers.

Recommended frontend behavior:

```text
1. Do not put access token in query string.
2. Do not put access token in localStorage just for SSE.
3. Use a fetch-based SSE client that supports Authorization header.
4. Reuse the same short-lived access token used by normal REST APIs.
```

Forbidden example:

```http
GET /api/v1/test-runs/{testRunId}/events?accessToken=eyJ...
```

Allowed example:

```http
GET /api/v1/test-runs/1b95f0b0-7795-4c0f-a745-d60bfa8e4cd37/events
Authorization: Bearer eyJ...
Accept: text/event-stream
```

### 4.3. Refresh token

Refresh token is not used directly by the SSE endpoint.

Refresh token remains stored in HttpOnly Secure cookie through `Set-Cookie`, as defined in `01-api-conventions.md` and `02-auth-permission-contract.md`.

If the access token expires while SSE is open:

```text
1. Backend may close the SSE connection.
2. Frontend refreshes access token using POST /api/v1/auth/refresh.
3. Frontend reconnects SSE with the new access token.
4. Frontend sends Last-Event-ID or last known sequence number to avoid missing events.
```

---

## 5. Permission Rule

The user must have Project VIEW permission for the project that owns the TestRun.

Allowed roles:

```text
PLATFORM_ADMIN
OWNER
ADMIN
MEMBER
VIEWER
```

Rules:

```text
1. VIEWER can subscribe to run progress and read results.
2. VIEWER cannot start or cancel a run.
3. OWNER / ADMIN / MEMBER can start and cancel runs.
4. If user cannot access the project, return 404 to avoid leaking TestRun existence.
```

Permission is resolved through:

```text
testRunId -> test_runs.project_id -> projects.workspace_id -> workspace_members
```

---

## 6. Request Contract

### 6.1. Request

```http
GET /api/v1/test-runs/{testRunId}/events HTTP/1.1
Accept: text/event-stream
Authorization: Bearer <accessToken>
Cache-Control: no-cache
Last-Event-ID: 42
```

Path parameters:

| Name | Type | Required | Description |
| ---- | ---- | -------- | ----------- |
| `testRunId` | UUID | yes | TestRun id |

Headers:

| Header | Required | Description |
| ------ | -------- | ----------- |
| `Accept: text/event-stream` | yes | Indicates SSE response |
| `Authorization: Bearer <accessToken>` | yes | Authenticates current user |
| `Last-Event-ID` | no | Last SSE id received by client; used for replay |
| `Cache-Control: no-cache` | recommended | Avoid client/proxy caching |

### 6.2. Alternative replay query parameter

Some clients or reverse proxies may not preserve `Last-Event-ID` reliably for fetch-based streaming.

MVP may support this optional query parameter as fallback:

```http
GET /api/v1/test-runs/{testRunId}/events?afterSequenceNo=42
```

Rules:

```text
1. Last-Event-ID header has priority over afterSequenceNo query parameter.
2. afterSequenceNo must be numeric.
3. afterSequenceNo is not a secret and is safe to appear in logs.
4. access token must still be sent through Authorization header, not query string.
```

---

## 7. Successful Response Contract

### 7.1. Response headers

```http
HTTP/1.1 200 OK
Content-Type: text/event-stream;charset=UTF-8
Cache-Control: no-cache, no-transform
X-Accel-Buffering: no
```

Recommended headers:

```text
Content-Type: text/event-stream;charset=UTF-8
Cache-Control: no-cache, no-transform
X-Accel-Buffering: no
```

Notes:

```text
1. X-Accel-Buffering: no is useful behind Nginx to reduce buffering.
2. Do not gzip SSE response unless the deployment is known to flush compressed chunks correctly.
3. Keep server/proxy read timeout longer than expected heartbeat interval.
```

### 7.2. Event wire format

Each event must follow standard SSE wire format:

```text
id: <sequenceNo>
event: <eventType>
data: <single-line JSON string>

```

Example:

```text
id: 15
event: TESTCASE_COMPLETED
data: {"eventId":"3d601f75-f7f2-43fd-911c-b6a7e447e2d4","sequenceNo":15,"testRunId":"1b95f0b0-7795-4c0f-a745-d60bfa8e4cd37","eventType":"TESTCASE_COMPLETED","createdAt":"2026-07-03T10:20:30+07:00","payload":{"testRunTargetId":"e5f65eda-7302-4738-99d1-1c5e9d63c8f2","datasetRowId":101,"rowIndex":1,"status":"PASSED","passed":true,"score":0.86,"latencyMs":1200,"completed":1,"total":100}}

```

### 7.3. Multiline data rule

MVP should send `data` as a single-line JSON string.

Rules:

```text
1. Do not pretty-print JSON in SSE data.
2. Do not send multiline JSON data in MVP.
3. Escape line breaks inside string values.
4. Keep payload compact.
```

---

## 8. Heartbeat

Backend should send heartbeat comments to keep the connection alive.

Heartbeat format:

```text
: ping

```

Recommended default:

```text
heartbeatIntervalSeconds = 15
```

Rules:

```text
1. Heartbeat is not a business event.
2. Heartbeat must not increment runner_events.sequence_no.
3. Heartbeat must not be persisted in runner_events.
4. Frontend should ignore heartbeat comments.
```

---

## 9. Event Identity and Replay Key

SSE id must be based on database event sequence.

Primary replay key:

```text
runner_events.sequence_no
```

Database invariant:

```text
unique(test_run_id, sequence_no)
```

Rules:

```text
1. SSE id must equal runner_events.sequence_no as a decimal string.
2. sequence_no starts at 1 for each TestRun.
3. sequence_no must increase monotonically within one TestRun.
4. Backend must persist runner_events before broadcasting SSE event.
5. Frontend must store last received SSE id per TestRun.
```

---

## 10. Event Envelope

All business SSE events must use this envelope:

```json
{
  "eventId": "3d601f75-f7f2-43fd-911c-b6a7e447e2d4",
  "sequenceNo": 15,
  "testRunId": "1b95f0b0-7795-4c0f-a745-d60bfa8e4cd37",
  "eventType": "TESTCASE_COMPLETED",
  "createdAt": "2026-07-03T10:20:30+07:00",
  "payload": {}
}
```

Fields:

| Field | Type | Required | Description |
| ----- | ---- | -------- | ----------- |
| `eventId` | UUID | yes | Stable event id from runner/backend |
| `sequenceNo` | integer | yes | Replay sequence for this TestRun |
| `testRunId` | UUID | yes | Parent TestRun id |
| `eventType` | string | yes | Event type, same as SSE `event` name |
| `createdAt` | datetime | yes | Event creation time |
| `payload` | object | yes | Event-specific payload |

Rules:

```text
1. eventType inside JSON must match the SSE event name.
2. sequenceNo inside JSON must match the SSE id.
3. eventId must be unique globally.
4. payload schema depends on eventType.
```

---

## 11. Supported Event Types

MVP supports these event types:

```text
RUN_STARTED
TESTCASE_STARTED
TESTCASE_COMPLETED
RUN_PROGRESS
RUN_COMPLETED
RUN_FAILED
RUN_CANCELLED
```

Out of scope for MVP:

```text
RUN_PAUSED
RUN_RESUMED
TESTCASE_RETRIED
ARTIFACT_READY
```

---

## 12. Event Payload Schemas

### 12.1. RUN_STARTED

Emitted when backend has accepted Runner start and TestRun enters RUNNING state.

SSE frame:

```text
id: 1
event: RUN_STARTED
data: {"eventId":"...","sequenceNo":1,"testRunId":"...","eventType":"RUN_STARTED","createdAt":"2026-07-03T10:00:00+07:00","payload":{"status":"RUNNING","runnerId":"runner-01","runType":"SINGLE","total":100,"completed":0,"startedAt":"2026-07-03T10:00:00+07:00"}}

```

Payload:

```json
{
  "status": "RUNNING",
  "runnerId": "runner-01",
  "runType": "SINGLE",
  "total": 100,
  "completed": 0,
  "startedAt": "2026-07-03T10:00:00+07:00"
}
```

Fields:

| Field | Type | Required | Description |
| ----- | ---- | -------- | ----------- |
| `status` | string | yes | `RUNNING` |
| `runnerId` | string | no | Runner instance id if known |
| `runType` | string | yes | `SINGLE` or `COMPARISON` |
| `total` | integer | yes | Total expected testcase-target executions |
| `completed` | integer | yes | Completed count, usually 0 |
| `startedAt` | datetime | yes | Actual run start time |

---

### 12.2. TESTCASE_STARTED

Emitted when Runner starts executing one dataset row against one target.

Payload:

```json
{
  "testRunTargetId": "e5f65eda-7302-4738-99d1-1c5e9d63c8f2",
  "targetDisplayName": "Internal Chatbot",
  "datasetRowId": 101,
  "rowIndex": 1,
  "completed": 0,
  "total": 100,
  "startedAt": "2026-07-03T10:00:05+07:00"
}
```

Fields:

| Field | Type | Required | Description |
| ----- | ---- | -------- | ----------- |
| `testRunTargetId` | UUID | yes | Locked target id under this TestRun |
| `targetDisplayName` | string | yes | UI label for target |
| `datasetRowId` | integer | yes | Dataset row id |
| `rowIndex` | integer | yes | Dataset row index |
| `completed` | integer | yes | Completed count before this testcase finishes |
| `total` | integer | yes | Total expected testcase-target executions |
| `startedAt` | datetime | yes | Execution start time |

Security rule:

```text
TESTCASE_STARTED must not include rendered prompt, resolved request, dataset row content or secret references.
```

---

### 12.3. TESTCASE_COMPLETED

Emitted when one dataset row against one target finishes with PASSED, FAILED or execution-level status.

Payload:

```json
{
  "testCaseResultId": 987,
  "testRunTargetId": "e5f65eda-7302-4738-99d1-1c5e9d63c8f2",
  "targetDisplayName": "Internal Chatbot",
  "datasetRowId": 101,
  "rowIndex": 1,
  "status": "PASSED",
  "passed": true,
  "score": 0.86,
  "latencyMs": 1200,
  "errorCode": null,
  "errorMessage": null,
  "completed": 1,
  "total": 100,
  "finishedAt": "2026-07-03T10:00:08+07:00"
}
```

Fields:

| Field | Type | Required | Description |
| ----- | ---- | -------- | ----------- |
| `testCaseResultId` | integer | yes | Persisted result id |
| `testRunTargetId` | UUID | yes | Locked target id under this TestRun |
| `targetDisplayName` | string | yes | UI label for target |
| `datasetRowId` | integer | yes | Dataset row id |
| `rowIndex` | integer | yes | Dataset row index |
| `status` | string | yes | Testcase result status |
| `passed` | boolean/null | yes | Business pass flag if applicable |
| `score` | number/null | yes | Normalized score if available |
| `latencyMs` | integer/null | yes | Target/evaluator latency |
| `errorCode` | string/null | yes | Stable error code if execution error |
| `errorMessage` | string/null | yes | Masked, UI-safe error summary |
| `completed` | integer | yes | Completed count after this testcase finishes |
| `total` | integer | yes | Total expected testcase-target executions |
| `finishedAt` | datetime | yes | Execution finish time |

Supported `status` values:

```text
PASSED
FAILED
ERROR
JUDGE_ERROR
TIMEOUT
SKIPPED
CANCELLED
```

Rules:

```text
1. PASSED and FAILED are business evaluation results.
2. ERROR, JUDGE_ERROR and TIMEOUT are execution/evaluator/system statuses at testcase level.
3. TESTCASE_COMPLETED must not include full raw response.
4. UI must call GET /api/v1/test-runs/{testRunId}/results/{testCaseResultId} for detail/debug view.
```

---

### 12.4. RUN_PROGRESS

Emitted periodically or after batch persistence to update summary without sending all testcase details.

Payload:

```json
{
  "status": "RUNNING",
  "completed": 25,
  "total": 100,
  "passedCount": 18,
  "failedCount": 5,
  "errorCount": 2,
  "cancelledCount": 0,
  "passRate": 0.72,
  "averageScore": 0.81,
  "averageLatencyMs": 1350,
  "updatedAt": "2026-07-03T10:05:00+07:00"
}
```

Fields:

| Field | Type | Required | Description |
| ----- | ---- | -------- | ----------- |
| `status` | string | yes | Current TestRun status |
| `completed` | integer | yes | Completed testcase-target executions |
| `total` | integer | yes | Total testcase-target executions |
| `passedCount` | integer | yes | Passed business results |
| `failedCount` | integer | yes | Failed business results |
| `errorCount` | integer | yes | ERROR/JUDGE_ERROR/TIMEOUT count |
| `cancelledCount` | integer | yes | CANCELLED/SKIPPED count |
| `passRate` | number/null | yes | passedCount / completed, if completed > 0 |
| `averageScore` | number/null | yes | Average score for completed scored results |
| `averageLatencyMs` | number/null | yes | Average latency for completed results |
| `updatedAt` | datetime | yes | Summary update time |

Rules:

```text
1. RUN_PROGRESS may be emitted less frequently than TESTCASE_COMPLETED.
2. UI should treat RUN_PROGRESS as summary refresh.
3. RUN_PROGRESS must not be the only source of testcase table data.
```

---

### 12.5. RUN_COMPLETED

Emitted when TestRun reaches COMPLETED terminal state.

Payload:

```json
{
  "status": "COMPLETED",
  "completed": 100,
  "total": 100,
  "passedCount": 82,
  "failedCount": 15,
  "errorCount": 3,
  "cancelledCount": 0,
  "passRate": 0.82,
  "averageScore": 0.84,
  "averageLatencyMs": 1275,
  "startedAt": "2026-07-03T10:00:00+07:00",
  "finishedAt": "2026-07-03T10:12:30+07:00",
  "artifactsAvailable": true
}
```

Rules:

```text
1. RUN_COMPLETED is terminal.
2. After RUN_COMPLETED, frontend may close the SSE connection.
3. UI should refetch GET /api/v1/test-runs/{testRunId} for final summary if needed.
4. UI should fetch GET /api/v1/test-runs/{testRunId}/artifacts to display sanitized artifact metadata and content URLs.
```

---

### 12.6. RUN_FAILED

Emitted when TestRun reaches FAILED terminal state because of unrecoverable execution/system failure.

Payload:

```json
{
  "status": "FAILED",
  "completed": 23,
  "total": 100,
  "errorCode": "RUNNER_COMMAND_PUBLISH_FAILED",
  "errorMessage": "Run failed because the runner command could not be recovered after maximum attempts.",
  "startedAt": "2026-07-03T10:00:00+07:00",
  "finishedAt": "2026-07-03T10:08:10+07:00",
  "artifactsAvailable": true
}
```

Rules:

```text
1. RUN_FAILED is terminal.
2. RUN_FAILED must be reserved for system/execution failure, not normal testcase business failure.
3. errorMessage must be masked and safe for UI display.
4. Full runner logs, if available, are exposed only through artifact metadata and sanitized artifact retrieval.
```

---

### 12.7. RUN_CANCELLED

Emitted when TestRun reaches CANCELLED terminal state.

Payload:

```json
{
  "status": "CANCELLED",
  "completed": 25,
  "total": 100,
  "cancelledCount": 75,
  "cancelRequestedAt": "2026-07-03T10:04:00+07:00",
  "finishedAt": "2026-07-03T10:04:15+07:00",
  "artifactsAvailable": true
}
```

Rules:

```text
1. RUN_CANCELLED is terminal.
2. After RUN_CANCELLED, frontend may close the SSE connection.
3. Cancel command itself is created by POST /api/v1/test-runs/{testRunId}/cancel, not by SSE.
```

---

## 13. Reconnect and Replay Flow

### 13.1. Normal connection flow

```text
1. Frontend opens Run Detail page.
2. Frontend calls GET /api/v1/test-runs/{testRunId} for current snapshot.
3. Frontend calls GET /api/v1/test-runs/{testRunId}/results for current result table page.
4. Frontend opens SSE connection.
5. Frontend applies incoming events to UI state.
6. Frontend stores last received SSE id per TestRun.
```

### 13.2. Reconnect flow

```text
1. SSE connection drops.
2. Frontend keeps lastSequenceNo from the last received SSE id.
3. Frontend refreshes access token if needed.
4. Frontend reconnects with Last-Event-ID: lastSequenceNo.
5. Backend replays persisted runner_events where sequence_no > lastSequenceNo.
6. Backend switches to live streaming after replay finishes.
```

### 13.3. Replay unavailable flow

Replay may be unavailable if:

```text
1. lastSequenceNo is too old for retention.
2. runner_events history was archived or cleaned.
3. Last-Event-ID is invalid.
4. Backend cannot guarantee event gap recovery.
```

Backend behavior:

```text
1. Return 409 with code SSE_REPLAY_UNAVAILABLE before starting stream; or
2. Start stream with a RUN_PROGRESS event and require frontend snapshot fallback.
```

Preferred MVP behavior:

```text
Return 409 SSE_REPLAY_UNAVAILABLE if client requested replay but backend cannot replay safely.
```

Example problem response:

```json
{
  "type": "https://vfqc.vinfast.vn/problems/sse-replay-unavailable",
  "title": "SSE replay unavailable",
  "status": 409,
  "detail": "Events after the requested sequence number are no longer available. Refresh the run snapshot before reconnecting.",
  "instance": "/api/v1/test-runs/1b95f0b0-7795-4c0f-a745-d60bfa8e4cd37/events",
  "code": "SSE_REPLAY_UNAVAILABLE",
  "traceId": "01J0Z7Y4Y4F9E1S2Y9W8Q7P6A5"
}
```

Frontend fallback:

```text
1. Close failed stream.
2. Call GET /api/v1/test-runs/{testRunId}.
3. Call GET /api/v1/test-runs/{testRunId}/results.
4. Reconnect SSE without Last-Event-ID or with latest sequence from snapshot if provided.
```

---

## 14. Initial Snapshot Policy

SSE endpoint does not replace snapshot REST APIs.

Frontend must load snapshot before or immediately after opening SSE:

```http
GET /api/v1/test-runs/{testRunId}
GET /api/v1/test-runs/{testRunId}/results
```

Reason:

```text
1. User may open Run Detail after many events already happened.
2. SSE replay may be unavailable.
3. Result table pagination/filtering belongs to REST APIs.
4. SSE should remain lightweight.
```

Optional response field in `GET /api/v1/test-runs/{testRunId}`:

```json
{
  "id": "1b95f0b0-7795-4c0f-a745-d60bfa8e4cd37",
  "status": "RUNNING",
  "progress": {
    "completed": 25,
    "total": 100
  },
  "lastSequenceNo": 31,
  "eventsUrl": "/api/v1/test-runs/1b95f0b0-7795-4c0f-a745-d60bfa8e4cd37/events"
}
```

---

## 15. Frontend State Update Rules

Frontend should process events idempotently.

Rules:

```text
1. Ignore event if sequenceNo <= lastAppliedSequenceNo for the same TestRun.
2. Apply events in sequenceNo order.
3. If a gap is detected, trigger snapshot fallback.
4. Terminal event closes live progress state.
5. TESTCASE_COMPLETED updates result table row if currently visible.
6. RUN_PROGRESS updates summary cards.
7. RUN_COMPLETED/RUN_FAILED/RUN_CANCELLED triggers final snapshot refetch.
```

Gap detection example:

```text
lastAppliedSequenceNo = 15
incoming sequenceNo = 17
=> sequence 16 is missing
=> fallback to snapshot APIs
```

---

## 16. Backend Event Broadcasting Rules

Backend receives compact events from Redis Streams, persists them, then broadcasts to SSE subscribers.

Expected backend order:

```text
1. Consume event from vfqc:runs:events.
2. Validate event idempotency by event_id.
3. Persist runner_events row.
4. Persist side effect:
   - update test_runs progress/status
   - insert/update test_case_results
   - update promptfoo_run_artifacts metadata if applicable
5. ACK Redis event only after DB side effect succeeds.
6. Publish internal application event.
7. SSE controller broadcasts to active subscribers.
```

Rules:

```text
1. Do not broadcast event before DB persistence succeeds.
2. Do not ACK Redis event before DB persistence succeeds.
3. Broadcasted SSE data must match persisted runner_events payload_json or a UI-safe projection of it.
4. Duplicate Redis event must not create duplicate SSE sequence.
```

---

## 17. Security and Masking Rules

SSE payloads must not include:

```text
- plaintext API key
- plaintext refresh token
- plaintext access token
- Authorization header value
- Cookie header value
- raw_curl original value
- full resolved request
- full raw response
- full Promptfoo config
- full artifact content
- unmasked provider error that may contain secret
```

Allowed in SSE:

```text
- TestRun id
- target display name
- dataset row id
- row index
- status
- score
- pass/fail
- latency
- masked short error summary
- progress counts
- sanitized artifact availability boolean
```

Sensitive details belong in REST detail endpoints and artifacts only after masking:

```http
GET /api/v1/test-runs/{testRunId}/results/{testCaseResultId}
GET /api/v1/test-runs/{testRunId}/artifacts
GET /api/v1/test-runs/{testRunId}/artifacts/{artifactType}/content
```

---

## 18. Error Handling

Before the stream starts, errors use RFC 9457 `application/problem+json` as defined in `01-api-conventions.md`.

Common errors:

| HTTP Status | Code | Meaning |
| ----------- | ---- | ------- |
| `401` | `UNAUTHORIZED` | Missing/invalid access token |
| `403` | `FORBIDDEN` | User is authenticated but not allowed |
| `404` | `TEST_RUN_NOT_FOUND` | TestRun not found or hidden by permission rule |
| `409` | `SSE_REPLAY_UNAVAILABLE` | Requested replay cannot be served safely |
| `429` | `SSE_CONNECTION_LIMIT_EXCEEDED` | Too many concurrent event streams |
| `503` | `SSE_EVENT_STREAM_UNAVAILABLE` | SSE service temporarily unavailable |

After the stream starts:

```text
1. Backend cannot send normal JSON error response because headers are already committed.
2. Backend should close the connection for auth expiration or unrecoverable stream error.
3. Frontend should reconnect or fallback to snapshot APIs.
```

Optional stream error event:

```text
id: 51
event: STREAM_ERROR
data: {"eventId":"...","sequenceNo":51,"testRunId":"...","eventType":"STREAM_ERROR","createdAt":"2026-07-03T10:20:30+07:00","payload":{"code":"SSE_EVENT_STREAM_UNAVAILABLE","message":"Event stream closed unexpectedly. Reconnect with Last-Event-ID."}}

```

MVP recommendation:

```text
Do not persist STREAM_ERROR in runner_events unless it represents a real TestRun event.
Prefer closing the connection and relying on frontend reconnect logic.
```

---

## 19. Connection Limits

MVP should protect backend from too many open streams.

Recommended limits:

```text
maxSseConnectionsPerUser = 5
maxSseConnectionsPerTestRunPerUser = 2
```

If exceeded, return:

```http
HTTP/1.1 429 Too Many Requests
Content-Type: application/problem+json
```

Problem code:

```text
SSE_CONNECTION_LIMIT_EXCEEDED
```

---

## 20. CORS and Proxy Notes

If frontend and backend are on different origins:

```text
1. Backend must allow the frontend origin explicitly.
2. Access-Control-Allow-Credentials is needed for refresh flow, but SSE auth uses Authorization header.
3. Access-Control-Allow-Headers must include Authorization and Last-Event-ID if used.
4. Access-Control-Allow-Origin must not be "*" when credentials are enabled.
```

Nginx notes:

```nginx
proxy_buffering off;
proxy_cache off;
proxy_read_timeout 3600s;
```

Spring/backend notes:

```text
1. Use streaming response support appropriate for Spring MVC/WebFlux implementation.
2. Flush after each event.
3. Handle client disconnect without marking TestRun failed.
4. Client disconnect only means the user closed/reloaded UI; Runner should continue.
```

---

## 21. Example Full Stream

```text
: ping

id: 1
event: RUN_STARTED
data: {"eventId":"11111111-1111-1111-1111-111111111111","sequenceNo":1,"testRunId":"1b95f0b0-7795-4c0f-a745-d60bfa8e4cd37","eventType":"RUN_STARTED","createdAt":"2026-07-03T10:00:00+07:00","payload":{"status":"RUNNING","runnerId":"runner-01","runType":"SINGLE","total":2,"completed":0,"startedAt":"2026-07-03T10:00:00+07:00"}}

id: 2
event: TESTCASE_STARTED
data: {"eventId":"22222222-2222-2222-2222-222222222222","sequenceNo":2,"testRunId":"1b95f0b0-7795-4c0f-a745-d60bfa8e4cd37","eventType":"TESTCASE_STARTED","createdAt":"2026-07-03T10:00:01+07:00","payload":{"testRunTargetId":"e5f65eda-7302-4738-99d1-1c5e9d63c8f2","targetDisplayName":"Internal Chatbot","datasetRowId":101,"rowIndex":1,"completed":0,"total":2,"startedAt":"2026-07-03T10:00:01+07:00"}}

id: 3
event: TESTCASE_COMPLETED
data: {"eventId":"33333333-3333-3333-3333-333333333333","sequenceNo":3,"testRunId":"1b95f0b0-7795-4c0f-a745-d60bfa8e4cd37","eventType":"TESTCASE_COMPLETED","createdAt":"2026-07-03T10:00:03+07:00","payload":{"testCaseResultId":987,"testRunTargetId":"e5f65eda-7302-4738-99d1-1c5e9d63c8f2","targetDisplayName":"Internal Chatbot","datasetRowId":101,"rowIndex":1,"status":"PASSED","passed":true,"score":0.86,"latencyMs":1200,"errorCode":null,"errorMessage":null,"completed":1,"total":2,"finishedAt":"2026-07-03T10:00:03+07:00"}}

id: 4
event: RUN_COMPLETED
data: {"eventId":"44444444-4444-4444-4444-444444444444","sequenceNo":4,"testRunId":"1b95f0b0-7795-4c0f-a745-d60bfa8e4cd37","eventType":"RUN_COMPLETED","createdAt":"2026-07-03T10:00:05+07:00","payload":{"status":"COMPLETED","completed":2,"total":2,"passedCount":2,"failedCount":0,"errorCount":0,"cancelledCount":0,"passRate":1.0,"averageScore":0.91,"averageLatencyMs":1180,"startedAt":"2026-07-03T10:00:00+07:00","finishedAt":"2026-07-03T10:00:05+07:00","artifactsAvailable":true}}

```

---

## 22. Frontend Pseudocode

MVP frontend should use a fetch-based SSE client.

```ts
async function subscribeToRunEvents(params: {
  testRunId: string;
  accessToken: string;
  lastSequenceNo?: number;
}) {
  const headers: Record<string, string> = {
    Accept: "text/event-stream",
    Authorization: `Bearer ${params.accessToken}`,
  };

  if (params.lastSequenceNo != null) {
    headers["Last-Event-ID"] = String(params.lastSequenceNo);
  }

  // Use a fetch-based SSE client that supports custom headers.
  // Do not use native EventSource if Authorization header is required.
}
```

State handling:

```text
1. Store lastSequenceNo in component state or run-specific store.
2. On event, parse JSON envelope.
3. Validate eventType equals SSE event name.
4. Apply event if sequenceNo is next expected sequence.
5. If terminal event, close stream and refetch final snapshot.
```

---

## 23. Backend Acceptance Criteria

SSE implementation is acceptable if:

```text
1. GET /api/v1/test-runs/{testRunId}/events requires Authorization Bearer token.
2. Endpoint checks Project VIEW permission.
3. Endpoint returns text/event-stream on success.
4. SSE id equals runner_events.sequence_no.
5. Backend supports Last-Event-ID replay from runner_events.
6. Backend can fallback with SSE_REPLAY_UNAVAILABLE when replay cannot be served safely.
7. Backend sends heartbeat comments.
8. Backend does not broadcast before DB persistence.
9. SSE payloads do not contain plaintext secrets or large artifacts.
10. Terminal events close or allow frontend to close connection cleanly.
11. Client disconnect does not cancel or fail the TestRun.
12. Connection limits are enforced or explicitly configured for deployment.
```

---

## 24. Frontend Acceptance Criteria

Frontend integration is acceptable if:

```text
1. Frontend loads run snapshot before or alongside SSE subscription.
2. Frontend uses fetch-based SSE client with Authorization header.
3. Frontend never puts access token in query string.
4. Frontend stores last received sequenceNo per TestRun.
5. Frontend reconnects with Last-Event-ID after disconnect.
6. Frontend refreshes access token when SSE receives/encounters 401.
7. Frontend falls back to snapshot APIs when replay is unavailable or sequence gap is detected.
8. Frontend handles RUN_COMPLETED, RUN_FAILED and RUN_CANCELLED as terminal events.
9. Frontend calls REST result detail API for debugging instead of expecting full detail in SSE payload.
```
