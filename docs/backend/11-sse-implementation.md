# VinFast QC Copilot - SSE Implementation

Version: `v0.1`  
Status: Backend Implementation Spec Draft  
Applies to: `apps/api` Spring Boot backend  
Aligned with: PRD v0.7, ERD v0.5, API Contract `09-sse-events-contract.md`

---

## 1. Purpose

This document explains how to implement Server-Sent Events in the Spring Boot backend.

It is written for AI coding agents and backend developers so generated code follows the same realtime event, replay, authorization, masking and disconnect-handling rules.

This file covers:

```text
1. TestRun SSE endpoint implementation.
2. Authorization and Project VIEW permission.
3. SseEmitter lifecycle and registry.
4. Replay using runner_events.sequence_no.
5. Heartbeat comments.
6. Broadcast after RunnerEvent persistence.
7. Reconnect and Last-Event-ID behavior.
8. Snapshot fallback boundary.
9. Logging, transaction and error handling rules.
10. Testing checklist.
```

This file does not define runner event ingestion. That belongs to `docs/backend/10-runner-event-consumer.md`.

This file does not define frontend SSE client implementation. Frontend must follow `docs/api/09-sse-events-contract.md` and use a fetch-based SSE client that can send the Authorization header.

---

## 2. Design Principles

SSE backend code must follow these principles:

```text
1. SSE is a read-only realtime projection of persisted RunnerEvent records.
2. PostgreSQL remains the source of truth.
3. Redis is never read by frontend and never directly streamed to frontend.
4. RunnerEvent must be persisted before SSE broadcast.
5. SSE event id must equal runner_events.sequence_no.
6. Replay must read persisted runner_events, not in-memory buffers only.
7. Heartbeats keep the connection alive but are not persisted and do not consume sequence numbers.
8. SSE payloads must be compact and UI-oriented.
9. SSE payloads must not contain plaintext secrets, raw request/response bodies or large artifacts.
10. Dropped SSE connection must not lose data because frontend can replay and/or fetch REST snapshots.
```

Security and localization rules:

```text
1. Require Authorization: Bearer <accessToken>.
2. Do not accept access tokens in query strings.
3. Check Project VIEW permission before opening the stream.
4. Return 404 when the current user cannot access the TestRun/project.
5. Logs and error codes are English.
6. Vietnamese user-facing copy belongs in frontend i18n or a message catalog.
```

---

## 2.1. Deployment and Scaling Assumption

MVP SSE implementation may use an in-memory `SseEmitterRegistry` only when the backend runs as a single `apps/api` instance.

Rules:

```text
1. In-memory SseEmitterRegistry is not a distributed registry.
2. If apps/api is horizontally scaled, a runner event may be consumed by instance A while the browser SSE connection is attached to instance B.
3. In that topology, instance B will not receive the live in-memory broadcast unless a cross-instance fanout mechanism exists.
4. Reconnect/replay from runner_events still protects correctness, but realtime UX may be delayed or degraded.
5. Before horizontal scaling, add one of:
   - Redis Pub/Sub or another broker fanout for persisted RunnerEvent ids.
   - Database polling per subscribed TestRun with last sequence cursor.
   - Sticky routing plus a design that guarantees the event consumer and SSE clients share the same node, although this is less robust.
6. PostgreSQL runner_events remains the source of truth in every topology.
```

Backend docs should not claim multi-instance realtime SSE support until this fanout is implemented.

---

## 3. Package Layout

Recommended package layout:

```text
vn.vinfast.vfqc.api
  sse
    controller
      TestRunSseController.java
    dto
      TestRunSseEventEnvelope.java
      TestRunSsePayload.java
      TestRunSseReplayRequest.java
    enums
      SseEventType.java
    service
      TestRunSseService.java
      SseReplayService.java
      SseEmitterRegistry.java
      SseHeartbeatService.java
      SseEventSerializer.java
      impl
        TestRunSseServiceImpl.java
        SseReplayServiceImpl.java
        SseEmitterRegistryImpl.java
        SseHeartbeatServiceImpl.java
        SseEventSerializerImpl.java
    model
      RegisteredSseClient.java
      SseClientKey.java
      SseSendResult.java
      ReplayCursor.java
```

Related packages:

```text
runner/service/RunnerEventIngestionService
runner/repository/RunnerEventRepository
testrun/service/TestRunQueryService
common/permission/ProjectAccessResolver
common/security/CurrentUser
common/error
common/time
```

Dependency rules:

```text
1. sse module may query TestRun ownership/access through testrun/project services.
2. sse module may read runner_events for replay.
3. sse module must not mutate TestRun status or TestCaseResult.
4. sse module must not call Redis directly.
5. runner event consumer calls sse service after DB commit.
```

---

## 4. Endpoint Implementation

API contract endpoint:

```http
GET /api/v1/test-runs/{testRunId}/events
```

Controller responsibilities:

```text
1. Validate access token through normal Spring Security chain.
2. Resolve CurrentUser.
3. Resolve and authorize TestRun through Project VIEW permission.
4. Parse Last-Event-ID header or afterSequenceNo query parameter.
5. Delegate stream creation to TestRunSseService.
6. Return SseEmitter or a ResponseEntity<SseEmitter> with correct headers.
```

Recommended controller shape:

```java
@RestController
@RequestMapping("/api/v1/test-runs")
@RequiredArgsConstructor
@Tag(name = "Test Run SSE", description = "Realtime test run progress events")
public class TestRunSseController {

  private final TestRunSseService testRunSseService;

  @Operation(
      summary = "Subscribe to realtime test run events",
      description = "Streams persisted runner events for one TestRun using Server-Sent Events.")
  @GetMapping(value = "/{testRunId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter subscribe(
      @PathVariable UUID testRunId,
      @RequestHeader(value = "Last-Event-ID", required = false) Long lastEventId,
      @RequestParam(value = "afterSequenceNo", required = false) Long afterSequenceNo,
      @AuthenticationPrincipal CurrentUser currentUser) {
    return testRunSseService.subscribe(testRunId, lastEventId, afterSequenceNo, currentUser);
  }
}
```

Rules:

```text
1. Do not put business logic in controller.
2. Do not open the stream before permission is checked.
3. Do not use native EventSource-specific assumptions in backend.
4. Do not support accessToken query parameter.
```

---

## 5. Service Interfaces

Service interfaces must live in `sse/service` and implementations in `sse/service/impl`.

Every interface method must have Javadoc.

Example:

```java
public interface TestRunSseService {

  /**
   * Opens a Server-Sent Events stream for a TestRun visible to the current user.
   *
   * <p>The method validates project access, registers an emitter, replays missed persisted events
   * after the requested sequence number and starts heartbeat delivery. The returned emitter must
   * not expose secrets, full raw responses or large artifact content.
   *
   * @param testRunId test run identifier
   * @param lastEventId value from Last-Event-ID header; has priority when present
   * @param afterSequenceNo fallback replay cursor query parameter
   * @param currentUser authenticated user opening the stream
   * @return registered SSE emitter
   */
  SseEmitter subscribe(
      UUID testRunId, Long lastEventId, Long afterSequenceNo, CurrentUser currentUser);

  /**
   * Broadcasts one already-persisted runner event to all active SSE clients of the TestRun.
   *
   * @param event persisted runner event
   */
  void broadcastPersistedEvent(RunnerEvent event);
}
```

Replay service example:

```java
public interface SseReplayService {

  /**
   * Loads persisted runner events after the given sequence number for reconnect replay.
   *
   * @param testRunId test run identifier
   * @param afterSequenceNo exclusive lower bound sequence number; null means no replay cursor
   * @param limit maximum number of events to replay in one subscription setup
   * @return persisted runner events ordered by sequence number ascending
   */
  List<RunnerEvent> loadReplayEvents(UUID testRunId, Long afterSequenceNo, int limit);
}
```

---

## 6. SseEmitter Registry

`SseEmitterRegistry` tracks active clients per TestRun.

Recommended behavior:

```text
1. Use a thread-safe structure such as ConcurrentHashMap.
2. Registry key should include testRunId and a generated clientId.
3. Store createdAt and lastSendAt for diagnostics.
4. Remove emitter on completion, timeout and error.
5. Avoid memory leaks by always registering lifecycle callbacks.
```

Recommended model:

```java
public record RegisteredSseClient(
    UUID clientId,
    UUID testRunId,
    UUID userId,
    SseEmitter emitter,
    Instant createdAt,
    AtomicLong lastSentSequenceNo) {}
```

Rules:

```text
1. Never store access token in registry.
2. Never store runtime secret material in registry.
3. Do not keep unbounded per-client replay buffers.
4. Do not rely on registry for durable replay; use runner_events.
```

---

## 7. Replay Cursor Resolution

Cursor priority:

```text
1. Last-Event-ID header has priority.
2. afterSequenceNo query parameter is fallback.
3. If neither is provided, no replay is required unless product wants initial recent event replay.
```

Implementation rule:

```java
Long replayAfter = lastEventId != null ? lastEventId : afterSequenceNo;
```

Validation rules:

```text
1. replayAfter must be >= 0 when present.
2. If replayAfter is too old and events were archived, return available replay plus frontend REST snapshot fallback guidance through normal behavior.
3. Do not treat replay cursor as secret.
4. Do not log user token or full request headers.
```

Replay query rules:

```text
1. Filter by test_run_id.
2. Filter sequence_no > replayAfter.
3. Order by sequence_no ascending.
4. Apply a safety limit to avoid huge replay bursts.
5. If replay result reaches the safety limit, frontend can reconnect again using the latest id or fetch REST snapshot.
```

---

## 8. Event Envelope and Serialization

All business events must use the API contract envelope:

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

Serialization rules:

```text
1. SSE id = sequenceNo as decimal string.
2. SSE event name = eventType.
3. SSE data = one-line JSON string.
4. Do not pretty-print JSON in SSE data.
5. Escape line breaks inside string values.
6. Do not include raw request, raw response or artifact content.
7. Payload must already be sanitized before persistence and broadcast.
```

Recommended serializer:

```java
public interface SseEventSerializer {

  /**
   * Converts one persisted RunnerEvent into an SseEmitter event builder.
   *
   * @param runnerEvent persisted runner event
   * @return serialized SSE event builder
   */
  SseEmitter.SseEventBuilder toSseEvent(RunnerEvent runnerEvent);
}
```

---

## 9. Broadcast Boundary

Runner event consumer flow:

```text
1. Consume Redis runner event.
2. Validate and normalize event.
3. Persist RunnerEvent and related TestRun/TestCaseResult state in DB transaction.
4. Commit transaction.
5. Broadcast persisted RunnerEvent to active SSE clients.
6. ACK Redis message only after DB persistence succeeds.
```

SSE module rule:

```text
The SSE service must only broadcast persisted RunnerEvent objects, never raw Redis messages.
```

Recommended implementation technique:

```text
Use @TransactionalEventListener(phase = AFTER_COMMIT) or explicitly call TestRunSseService after the event ingestion transaction commits.
```

Rules:

```text
1. Do not broadcast before DB commit.
2. Do not let SSE send failure roll back runner event persistence.
3. If one client send fails, remove that client and continue sending to other clients.
4. Keep send operation bounded and log only identifiers.
```

---

## 10. Heartbeat Implementation

Heartbeat keeps long-lived HTTP connections alive.

Recommended default:

```text
heartbeatIntervalSeconds = 15
```

Heartbeat format:

```text
: ping

```

Rules:

```text
1. Heartbeat is an SSE comment, not a business event.
2. Heartbeat must not increment runner_events.sequence_no.
3. Heartbeat must not be persisted.
4. Heartbeat must not update TestRun progress.
5. Frontend should ignore heartbeat comments.
```

Implementation options:

```text
1. Dedicated scheduled task scans active emitters and sends heartbeat.
2. Per-emitter heartbeat task if registry size is small.
```

Recommended for MVP:

```text
Use one scheduled heartbeat service with bounded error handling.
```

Example:

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class SseHeartbeatServiceImpl implements SseHeartbeatService {

  private final SseEmitterRegistry registry;

  @Override
  @Scheduled(fixedDelayString = "${vfqc.sse.heartbeat-interval-ms:15000}")
  public void sendHeartbeat() {
    registry.forEachClient(client -> {
      try {
        client.emitter().send(SseEmitter.event().comment("ping"));
      } catch (IOException ex) {
        log.debug("SSE heartbeat failed, closing client. testRunId={}, clientId={}",
            client.testRunId(), client.clientId());
        registry.remove(client.testRunId(), client.clientId());
      }
    });
  }
}
```

---

## 11. Transaction Rules

SSE stream lifecycle must not hold long database transactions.

Rules:

```text
1. Permission check may use short read-only transaction.
2. Replay load may use short read-only transaction.
3. Do not keep @Transactional open while the SseEmitter is alive.
4. Broadcast does not require a transaction if it receives a persisted RunnerEvent.
5. Runner event ingestion transaction belongs to runner module, not SSE module.
```

Recommended annotations:

```java
@Transactional(readOnly = true)
public List<RunnerEvent> loadReplayEvents(...) { ... }
```

Do not annotate `subscribe` with a long-running transaction.

---

## 12. Logging Rules

Use Lombok `@Slf4j` in implementations.

Log useful lifecycle points:

```text
1. Subscription opened: testRunId, userId, replayAfter.
2. Subscription closed: testRunId, clientId, reason.
3. Replay count: testRunId, replayAfter, replayCount.
4. Broadcast failures: testRunId, clientId, sequenceNo.
```

Do not log:

```text
1. Access token.
2. Full request headers.
3. Raw runner event payload if it may contain model output.
4. Raw response/request/artifact content.
5. Runtime secrets.
```

Use debug for noisy lifecycle logs and info only for important transitions.

---

## 13. Response Headers and Proxy Notes

SSE response should include:

```http
Content-Type: text/event-stream;charset=UTF-8
Cache-Control: no-cache, no-transform
X-Accel-Buffering: no
```

Implementation notes:

```text
1. Spring may set Content-Type automatically from produces.
2. Add X-Accel-Buffering: no if using ResponseEntity<SseEmitter> or response filter.
3. Nginx/proxy read timeout must be longer than heartbeat interval.
4. Avoid gzip for SSE unless flushing is tested.
5. Do not cache SSE responses.
```

---

## 14. Error Handling

Before stream is opened:

```text
1. Authentication failure -> 401 Problem Detail.
2. Permission failure or hidden resource -> 404 Problem Detail.
3. Invalid replay cursor -> 400 VALIDATION_ERROR.
```

After stream is opened:

```text
1. Send errors should close and unregister the emitter.
2. Do not try to send ProblemDetail through an already broken SSE connection.
3. Frontend should reconnect and/or use REST snapshot fallback.
```

Recommended error codes:

```text
TEST_RUN_NOT_FOUND
TEST_RUN_ACCESS_DENIED
SSE_REPLAY_CURSOR_INVALID
SSE_SUBSCRIPTION_FAILED
```

---

## 15. Snapshot Fallback Boundary

SSE does not replace REST result APIs.

Frontend can recover with:

```http
GET /api/v1/test-runs/{testRunId}
GET /api/v1/test-runs/{testRunId}/results
GET /api/v1/test-runs/{testRunId}/comparison-matrix
GET /api/v1/test-runs/{testRunId}/review-summary
```

Backend rule:

```text
SSE only pushes compact progress and result events. Full detail remains available through REST APIs.
```

---

## 16. Async Rules

SSE may use async behavior, but it must be explicit.

Allowed:

```text
1. Scheduled heartbeat service.
2. Async broadcasting if current request thread must not block.
3. Application event listener after transaction commit.
```

Rules:

```text
1. Use @Async only when the method truly runs in a separate executor.
2. Name the executor if multiple executors exist.
3. Async broadcast failures must be logged and must not affect persisted event state.
4. Do not make controller subscribe method @Async.
```

---

## 17. Testing Checklist

Unit tests:

```text
1. Replay cursor priority: Last-Event-ID wins over afterSequenceNo.
2. Invalid negative cursor returns validation error.
3. Serializer uses sequenceNo as SSE id.
4. Serializer uses eventType as SSE event name.
5. Serializer emits one-line JSON data.
6. Registry removes clients on completion, timeout and error.
7. Broadcast removes only failed clients and continues to others.
```

Integration tests:

```text
1. User with Project VIEW can subscribe.
2. User without access receives 404.
3. Access token in query string is not supported.
4. Replay returns runner_events where sequence_no > cursor.
5. Heartbeat does not create runner_events.
6. Persisted runner event is broadcast after commit.
7. SSE reconnect with Last-Event-ID does not duplicate already received events.
```

Security tests:

```text
1. SSE payload never contains Authorization, API key, cookie or token values.
2. SSE endpoint rejects unauthenticated requests.
3. SSE endpoint does not expose TestRun existence across workspaces.
```

Performance tests:

```text
1. Many active clients for one TestRun do not leak memory after disconnect.
2. Replay limit prevents unbounded response on stale cursor.
3. Broadcast path does not perform N+1 database queries.
```

---

## 18. AI Coding Rules

When generating SSE backend code:

```text
1. Implement service interface first, then service/impl implementation.
2. Add Javadoc above every service interface method.
3. Use @Slf4j in implementations.
4. Do not log payloads or tokens.
5. Use short read-only transactions for replay queries only.
6. Do not hold transactions while SseEmitter is open.
7. Use persisted RunnerEvent as broadcast input.
8. Do not read Redis from SSE code.
9. Do not store per-client unbounded buffers.
10. Follow API contract 09 for wire format exactly.
```
