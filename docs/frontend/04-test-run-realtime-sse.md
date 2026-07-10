# VinFast QC Copilot Frontend — TestRun Realtime SSE Contract

**Document:** `docs/frontend/04-test-run-realtime-sse.md`  
**Status:** Reviewed and implementation-ready  
**Applies to:** `apps/web`  
**Last reviewed:** 2026-07-11

---

## 1. Purpose

This document defines the frontend contract for TestRun realtime updates over Server-Sent Events.

It covers:

- endpoint and authentication;
- event envelope;
- event types;
- payload fields;
- sequence handling;
- replay;
- reconnect;
- heartbeat;
- terminal behavior;
- REST synchronization;
- error recovery;
- multi-instance limitations;
- frontend tests.

---

## 2. Endpoint

```http
GET /api/v1/test-runs/{testRunId}/events
Accept: text/event-stream
Authorization: Bearer <access-token>
```

Optional reconnect inputs:

```http
Last-Event-ID: <sequenceNo>
```

or:

```text
?afterSequenceNo=<sequenceNo>
```

Priority:

```text
1. Last-Event-ID
2. afterSequenceNo
3. 0
```

Invalid non-numeric cursor returns a validation error.

---

## 3. Why native EventSource is not used

Native `EventSource` cannot attach a Bearer Authorization header.

The frontend must use fetch-based streaming:

```ts
fetch(url, {
  method: 'GET',
  headers: {
    Accept: 'text/event-stream',
    Authorization: `Bearer ${accessToken}`,
    'Last-Event-ID': String(lastSequenceNo),
  },
  credentials: 'include',
  signal,
});
```

Use a streaming parser that supports:

```text
id
event
data
comment
blank-line dispatch
multi-line data
partial chunks
UTF-8 chunk boundaries
```

---

## 4. Response headers

Backend response includes:

```text
Content-Type: text/event-stream
Cache-Control: no-cache
Connection: keep-alive
X-Accel-Buffering: no
```

Reverse proxies must not buffer SSE responses.

---

## 5. Authentication and permission

The endpoint requires:

```text
authenticated user
VIEW permission on the containing project
```

Verified access:

```text
PLATFORM_ADMIN: allowed
OWNER: allowed
ADMIN: allowed
MEMBER: allowed
VIEWER: allowed
```

A missing or invalid token may return:

```text
HTTP 401
empty body
WWW-Authenticate header
```

---

## 6. SSE envelope

For a normal event:

```text
id: <sequenceNo>
event: <RunnerEventType>
data: <JSON TestRunSseEventResponse>
```

Type:

```ts
interface TestRunSseEventResponse {
  eventId: string;
  sequenceNo: number;
  testRunId: string;
  eventType: RunnerEventType;
  occurredAt: string | null;
  createdAt: string;
  payload: Record<string, unknown>;
}
```

The SSE `id` equals:

```text
sequenceNo
```

The SSE `event` equals:

```text
eventType
```

The frontend must verify those values are consistent when practical.

---

## 7. Event types

```ts
type RunnerEventType =
  | 'RUN_STARTED'
  | 'RUN_PROGRESS'
  | 'TESTCASE_COMPLETED'
  | 'TESTCASE_ERROR'
  | 'RUN_COMPLETED'
  | 'RUN_FAILED'
  | 'RUN_CANCELLED'
  | 'RUN_LOG'
  | 'ARTIFACT_CREATED';
```

---

## 8. Event payload contracts

Payloads are sanitized maps.

Unsafe keys such as raw request/response bodies, headers, runtime secrets, raw cURL, Promptfoo snapshots, domain snapshots, artifacts, and full logs are removed before browser delivery.

### 8.1 RUN_STARTED

Backend effect:

```text
TestRun -> RUNNING
startedAt set
```

Frontend action:

```text
update visible status to RUNNING
refetch TestRun detail
```

Payload is not required for state transition rendering.

### 8.2 RUN_PROGRESS

Recognized payload fields:

```ts
interface RunProgressPayload {
  completed?: number | string;
  total?: number | string;
}
```

Backend accepts numeric values or numeric strings.

Frontend should normalize to numbers.

Frontend action:

```text
update local progress display
schedule or perform TestRun detail refetch
```

Do not allow:

```text
completed > total
negative progress
NaN
```

to corrupt the UI.

Invalid values should be ignored and followed by REST refetch.

### 8.3 TESTCASE_COMPLETED

Required backend fields:

```ts
interface TestCaseCompletedPayload {
  testRunTargetId: string;
  datasetRowId: number | string;

  status?:
    | 'PASSED'
    | 'FAILED'
    | 'ERROR'
    | 'JUDGE_ERROR'
    | 'TIMEOUT';

  score?: number | string;
  passed?: boolean | string;
  errorCategory?: string;
  latencyMs?: number | string;
  machineSummary?: string;

  request?: Record<string, unknown>;
  response?: Record<string, unknown>;
  extractedOutput?: Record<string, unknown>;
  gradingResult?: Record<string, unknown>;
  componentResults?: Record<string, unknown>;
  tokenUsage?: Record<string, unknown>;
}
```

Important:

The SSE projector removes unsafe keys such as:

```text
request
response
headers
body
raw
```

Therefore the browser should not expect request/response details in the SSE payload even if they exist in the persisted TestCaseResult.

Frontend action:

```text
incrementally invalidate or refetch result queries
update lightweight completion counters
do not construct authoritative result detail solely from SSE
```

Default backend status when omitted:

```text
PASSED
```

### 8.4 TESTCASE_ERROR

Uses the same general payload fields as `TESTCASE_COMPLETED`.

Required:

```text
testRunTargetId
datasetRowId
```

Default backend status when omitted:

```text
ERROR
```

Frontend action:

```text
invalidate result list/detail
update error counters if available
```

A testcase error does not mean the TestRun itself is `FAILED`.

### 8.5 RUN_COMPLETED

Backend effect:

```text
TestRun -> COMPLETED
finishedAt set
summaryMetrics calculated
```

Summary metrics:

```ts
interface TestRunSummaryMetrics {
  total: number;
  passed: number;
  failed: number;
  errored: number;
  passRate: number;
}
```

Frontend action:

```text
close SSE stream
refetch TestRun detail
refetch results
refetch comparison matrix
refetch export-relevant metadata
```

### 8.6 RUN_FAILED

Backend effect:

```text
TestRun -> FAILED
finishedAt set
```

Frontend action:

```text
close SSE stream
refetch TestRun detail
show run-level failure
```

Do not interpret this as a testcase evaluation failure.

### 8.7 RUN_CANCELLED

Backend effect:

```text
TestRun -> CANCELLED
finishedAt set
```

Frontend action:

```text
close SSE stream
refetch TestRun detail
refetch results
```

Partial results may exist.

### 8.8 RUN_LOG

Backend effect:

```text
TestRun.updatedAt changed
```

Payload is sanitized.

The MVP frontend may:

```text
ignore RUN_LOG for primary UI
or append safe short log entries to an ephemeral panel
```

Do not treat SSE logs as complete audit logs.

### 8.9 ARTIFACT_CREATED

Backend effect:

```text
TestRun.updatedAt changed
```

The SSE payload may omit artifact content.

The current frontend MVP should not assume a public artifact-download workflow unless an API exists.

---

## 9. Sequence contract

Sequence numbers are per TestRun.

Backend assigns:

```text
max persisted sequenceNo + 1
```

Frontend state:

```ts
interface SequenceState {
  lastAppliedSequenceNo: number;
  seenEventIds: Set<string>;
}
```

Apply an event only when:

```text
event.testRunId matches current TestRun
event.sequenceNo > lastAppliedSequenceNo
```

Duplicate or older event:

```text
ignore
```

### 9.1 Gap detection

If:

```text
incoming.sequenceNo > lastAppliedSequenceNo + 1
```

then a gap exists.

Frontend response:

```text
do not apply speculative intermediate state
close current stream
reconnect using lastAppliedSequenceNo
refetch REST TestRun detail
```

The backend does not emit a dedicated gap event.

---

## 10. Replay behavior

On subscribe, backend:

```text
registers emitter
sends initial heartbeat comment
replays persisted events after cursor
then keeps connection open for live events
```

Replay query:

```text
sequenceNo > cursor
ordered ascending
```

Backend reads replay pages of:

```text
500 events
```

and continues until no more pages exist.

Therefore `500` is a replay page size, not a total replay limit.

---

## 11. Heartbeat

Backend sends comment heartbeat:

```text
: ping
```

Default interval:

```text
15 seconds
```

An initial heartbeat is sent immediately after subscription.

Heartbeat has:

```text
no id
no event name
no JSON data
```

Frontend parser must ignore comment lines for business state.

Track last received byte or heartbeat time for connection-health display.

---

## 12. Connection timeout

Backend emitter timeout:

```text
30 minutes
```

The frontend must expect reconnect even for healthy long-running TestRuns.

Do not interpret a clean stream close as TestRun completion.

After close:

```text
fetch current TestRun detail
if terminal:
  stop
else:
  reconnect
```

---

## 13. Terminal event set

```ts
const TERMINAL_EVENTS = new Set<RunnerEventType>([
  'RUN_COMPLETED',
  'RUN_FAILED',
  'RUN_CANCELLED',
]);
```

On terminal event:

```text
apply the event
store sequence
abort SSE request
mark connection closed
refetch authoritative REST state
stop reconnect loop
```

Backend does not automatically complete the emitter on terminal event.

The frontend must close it.

---

## 14. REST-first synchronization

Opening a TestRun page:

```text
1. GET TestRun detail.
2. Render current persisted state.
3. If terminal, do not open SSE.
4. If non-terminal, open SSE using last known cursor.
```

Do not wait for SSE before rendering.

On reconnect:

```text
refetch detail
reopen SSE
```

On terminal:

```text
refetch detail and result data
```

SSE events do not replace REST result/detail endpoints.

---

## 15. Cursor persistence

Minimum requirement:

```text
keep lastAppliedSequenceNo in component/hook memory
```

Optional same-tab persistence:

```text
query cache keyed by testRunId
```

Do not place the cursor in localStorage by default.

After full reload, opening from cursor `0` is allowed because backend replay uses persisted events, but REST-first rendering should happen before replay.

A later optimization may expose latest sequence in REST detail.

---

## 16. Reconnect policy

Reconnect when:

```text
network disconnects
emitter times out
stream ends without terminal event
access token expires
sequence gap detected
parser reports recoverable transport error
```

Do not reconnect when:

```text
user leaves page
AbortController was intentionally aborted
TestRun is terminal
403 or 404 is returned
refresh fails
```

Suggested backoff:

```text
1s
2s
4s
8s
15s maximum
```

Add small jitter.

Reset backoff after a stable open connection or successful event receipt.

---

## 17. Authentication during reconnect

If SSE fetch returns 401:

```text
run single-flight refresh
if refresh succeeds:
  reconnect with latest cursor
if refresh fails:
  clear session and route to login
```

Do not open parallel SSE connections while refresh is running.

---

## 18. Event application strategy

SSE reducers may update lightweight UI state:

```text
status
progress
connection state
last sequence
recent event indicator
```

Use query invalidation/refetch for authoritative resources:

```text
TestRun detail
results
comparison matrix
reviews
summary metrics
```

Recommended event-to-query behavior:

| Event | TestRun detail | Results | Comparison |
|---|---:|---:|---:|
| RUN_STARTED | Refetch | No | No |
| RUN_PROGRESS | Optional/debounced | No | No |
| TESTCASE_COMPLETED | Optional | Invalidate/debounce | Invalidate/debounce |
| TESTCASE_ERROR | Optional | Invalidate/debounce | Invalidate/debounce |
| RUN_COMPLETED | Refetch | Refetch | Refetch |
| RUN_FAILED | Refetch | Optional | Optional |
| RUN_CANCELLED | Refetch | Refetch | Refetch |
| RUN_LOG | No | No | No |
| ARTIFACT_CREATED | Optional | No | No |

Debounce repeated invalidations during high-volume testcase events.

---

## 19. Throughput protection

A TestRun may emit many testcase events.

Do not refetch the complete result page after every event.

Use one of:

```text
debounced invalidation every 500–1000 ms
incremental lightweight counters plus periodic refetch
refetch only current visible page
terminal full refetch
```

Do not hold unbounded raw event history in memory.

Recommended cap for optional visible event log:

```text
100–500 recent safe entries
```

---

## 20. Sanitization expectations

Backend sanitizes and strips unsafe payload fields before SSE delivery.

Frontend still must:

```text
treat payload as untrusted
never render HTML
never log complete payloads in production
truncate long strings
avoid copying payload to analytics
```

Do not assume masking guarantees every future field is safe.

---

## 21. Multi-instance API limitation

The active emitter registry is in memory inside each API instance.

Live broadcast is local to the instance that processed the committed event.

In a multi-instance deployment:

```text
client connected to API instance A
runner event processed by API instance B
instance A may not receive immediate live broadcast
```

Persisted event replay still works after reconnect.

Current operational options:

```text
single API instance
sticky routing plus operational awareness
distributed pub/sub fanout in a later phase
```

The frontend cannot fully solve this limitation.

Frontend mitigation:

```text
REST polling fallback while TestRun is active
reconnect on heartbeat loss
REST refetch on visibility regain
```

A light fallback poll, such as every 15–30 seconds while disconnected, is acceptable.

---

## 22. Cancellation behavior

Possible transitions include:

```text
CREATED -> QUEUED
QUEUED -> RUNNING
QUEUED -> CANCELLED
RUNNING -> CANCEL_REQUESTED
RUNNING -> COMPLETED
RUNNING -> FAILED
CANCEL_REQUESTED -> CANCELLED
CANCEL_REQUESTED -> COMPLETED
```

The frontend must not assume that requesting cancellation guarantees `CANCELLED`.

After cancellation request:

```text
continue SSE
show CANCEL_REQUESTED
wait for terminal backend state
```

---

## 23. Error handling

### 23.1 400

Invalid cursor:

```text
reset cursor to 0 only after recording the issue
refetch REST detail
reconnect once
```

### 23.2 401

```text
refresh once
reconnect
```

### 23.3 403

```text
stop reconnecting
show permission error
```

### 23.4 404

```text
stop reconnecting
show TestRun not found
```

### 23.5 5xx/network

```text
reconnect with backoff
preserve latest cursor
show degraded realtime indicator
```

---

## 24. Browser lifecycle

### Tab hidden

Do not immediately close SSE solely because the tab is hidden.

On visibility regain:

```text
refetch TestRun detail
verify connection health
reconnect when needed
```

### Route leave

Abort the stream immediately.

### User logout

Abort all active streams before clearing session.

### Project/workspace switch

Abort streams belonging to the previous context.

---

## 25. User-facing realtime feedback

Connection-state code values remain English:

```text
idle
connecting
open
reconnecting
closed
error
```

User-facing labels are Vietnamese:

```text
open:
  Trực tiếp

connecting:
  Đang kết nối

reconnecting:
  Đang kết nối lại

closed:
  Đã đóng kết nối

error:
  Mất kết nối realtime
```

Use a persistent connection indicator while the condition remains active.

Toast rules:

```text
show at most one warning when a live connection is lost
do not show a Toast for every reconnect attempt
show one information Toast when the connection is restored
deduplicate repeated disconnect/recovery notifications
```

Follow document `08`; Toast never replaces the persistent connection state.

---

## 26. Suggested hook interface

```ts
interface UseTestRunSseResult {
  connectionState: SseConnectionState;
  lastSequenceNo: number;
  lastEventAt: Date | null;
  reconnectAttempts: number;
  reconnect(): void;
  disconnect(): void;
}
```

Usage:

```ts
useTestRunSse({
  testRunId,
  enabled: !isTerminal(testRun.status),
});
```

The hook owns transport and sequence handling.

Feature queries own REST data.

---

## 27. Parser requirements

Automated tests must cover:

```text
event split across chunks
multiple events in one chunk
multi-line data
comment heartbeat
missing optional fields
duplicate sequence
out-of-order sequence
sequence gap
terminal event
invalid JSON data
stream close without terminal
401 refresh and reconnect
intentional abort
```

---

## 28. Required E2E scenario

```text
Create TestRun
Observe QUEUED from creation response
Open TestRun detail
Connect SSE
Receive RUN_STARTED
Receive RUN_PROGRESS
Receive TESTCASE_COMPLETED or TESTCASE_ERROR
Receive RUN_COMPLETED
Close stream
Refetch detail
Open final results
```

Important:

```text
QUEUED comes from the TestRun creation response/state.
It is not a separate SSE event.
```

---

## 29. Verified TestRun state-machine reference

The authoritative backend transition matrix is:

| Current state | Allowed next state |
|---|---|
| `CREATED` | `QUEUED` |
| `QUEUED` | `RUNNING`, `CANCELLED` |
| `RUNNING` | `COMPLETED`, `FAILED`, `CANCEL_REQUESTED` |
| `CANCEL_REQUESTED` | `CANCELLED`, `COMPLETED` |
| `COMPLETED` | none |
| `FAILED` | none |
| `CANCELLED` | none |

Do not add a frontend transition that is absent from this table. REST responses
and accepted backend events remain authoritative when displaying current state.
