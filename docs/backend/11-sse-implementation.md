# VinFast QC Copilot - SSE Implementation

Version: `v0.2`  
Status: Spring Boot 4.0.7-first Implementation Spec  
Applies to: `apps/api`

---

## 1. Purpose

This document explains Server-Sent Events for TestRun progress using Spring Web MVC `SseEmitter`.

---

## 2. Design Principles

1. SSE is read-only realtime projection of persisted RunnerEvent records.
2. PostgreSQL remains source of truth.
3. Redis is never streamed directly to frontend.
4. RunnerEvent is persisted before SSE broadcast.
5. SSE event id equals `runner_events.sequence_no`.
6. Replay reads persisted runner_events.
7. Heartbeats are not persisted and do not consume sequence numbers.
8. Payloads are compact and UI-oriented.
9. Payloads never contain plaintext secrets, raw request/response bodies or large artifacts.
10. Dropped connections do not lose data because frontend can replay or fetch REST snapshot.

---

## 3. Web Stack

The backend uses `spring-boot-starter-webmvc`, so MVP SSE uses:

```java
org.springframework.web.servlet.mvc.method.annotation.SseEmitter
```

Do not switch this module to WebFlux unless:

1. WebFlux dependency is added.
2. Security config is redesigned.
3. API contract is updated.
4. Runner/SSE fanout behavior is retested.

---

## 4. Package Layout

```text
sse
  controller/TestRunSseController.java
  dto/TestRunSseEventEnvelope.java
  dto/TestRunSsePayload.java
  enums/SseEventType.java
  service/TestRunSseService.java
  service/SseReplayService.java
  service/SseEmitterRegistry.java
  service/SseHeartbeatService.java
  service/SseEventSerializer.java
  service/impl/TestRunSseServiceImpl.java
  service/impl/SseReplayServiceImpl.java
  service/impl/SseEmitterRegistryImpl.java
  service/impl/SseHeartbeatServiceImpl.java
  service/impl/SseEventSerializerImpl.java
  model/RegisteredSseClient.java
  model/SseClientKey.java
  model/ReplayCursor.java
```

---

## 5. Endpoint

```http
GET /api/v1/test-runs/{testRunId}/events
```

Controller responsibilities:

1. Require normal user authentication.
2. Resolve `CurrentUser`.
3. Check TestRun/project VIEW permission before opening stream.
4. Parse `Last-Event-ID` header or `afterSequenceNo` query param.
5. Delegate to service.
6. Return `SseEmitter`.

Recommended shape:

```java
@GetMapping(value = "/{testRunId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter subscribe(
    @PathVariable UUID testRunId,
    @RequestHeader(value = "Last-Event-ID", required = false) Long lastEventId,
    @RequestParam(value = "afterSequenceNo", required = false) Long afterSequenceNo,
    @AuthenticationPrincipal CurrentUser currentUser) {
  return testRunSseService.subscribe(testRunId, lastEventId, afterSequenceNo, currentUser);
}
```

Rules:

1. Do not accept access token in query string.
2. Do not open stream before permission check.
3. Do not perform business mutation in SSE service.

---

## 6. Replay

Replay cursor priority:

1. `Last-Event-ID` header.
2. `afterSequenceNo` query param.
3. No cursor means start from current policy: either replay from 0 or only future events depending API contract.

Rules:

1. Replay query reads `runner_events` ordered by `sequenceNo`.
2. Replay only events visible to current user.
3. Replay payload is same sanitized envelope as live event.
4. Large replay should be capped; frontend can use REST snapshot if too far behind.

---

## 7. Emitter Registry

MVP may use in-memory registry only for single `apps/api` instance.

Rules:

1. Registry key includes `testRunId` and client id.
2. Remove emitter on completion, timeout and error.
3. Send heartbeat comments periodically.
4. Do not store event history in memory as source of truth.

Horizontal scaling note:

If `apps/api` is scaled to multiple instances, in-memory broadcast is not enough. Add one of:

1. Redis Pub/Sub fanout of persisted event ids.
2. DB polling per subscribed run.
3. Sticky routing with clear limitations.

Do not claim multi-instance realtime support until fanout is implemented.

---

## 8. Broadcast

Runner event consumer calls SSE service after DB commit.

Live event send:

1. Serialize compact envelope.
2. Use `SseEmitter.event().id(sequenceNo).name(eventType).data(payload)`.
3. Remove broken emitters.
4. Never block runner event ingestion on slow clients.

---

## 9. Payload Rules

Payload may include:

1. Run status.
2. Progress counts.
3. Testcase result summary.
4. Target id/display name.
5. Dataset row id/index.
6. Score/pass/error category.
7. Artifact metadata id/name/type.

Payload must not include:

1. Plaintext secrets.
2. Runtime secret bundle.
3. Raw request JSON.
4. Raw response JSON.
5. Full Promptfoo config.
6. Large logs or artifacts.

---

## 10. Tests

Required tests:

1. Unauthorized user cannot subscribe.
2. Inaccessible run returns 404/403 according to API contract.
3. Last-Event-ID replay returns missed events.
4. Heartbeat does not allocate sequence number.
5. Broken emitter is removed.
6. SSE payload does not contain raw response/secret fields.
