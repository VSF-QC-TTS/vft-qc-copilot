# VFQC Runner Documentation

This directory documents the standalone **VFQC Runner** app.

The runner is a long-running Node.js service that consumes backend-created TestRun commands, wraps Promptfoo, normalizes execution results, and reports sanitized events back to the Java backend.

## Read This First

The backend already owns the domain model, permissions, snapshots, result persistence, review/export semantics, and most MVP orchestration.

Before implementing or changing runner code, read:

```text
AI_RUNNER_DTO_HANDOFF.md
docs/backend/API_IMPLEMENTATION_CONTEXT.md
docs/backend/09-test-run-implementation.md
docs/backend/10-runner-event-consumer.md
docs/backend/13-security-masking-implementation.md
```

The most important source for current runner API contracts is:

```text
AI_RUNNER_DTO_HANDOFF.md
```

If this documentation conflicts with actual backend code, prefer actual backend code and update these docs with an `Implementation note`.

## Source Of Truth Rule

Backend Java is the source of truth.

Promptfoo config is a generated runtime artifact.

The runner must not reimplement backend product rules such as workspace permission, dataset versioning, evaluator versioning, run creation, review semantics, or export/report authority.

## Runner Purpose

The runner should:

```text
1. Register itself with the backend.
2. Send heartbeat.
3. Consume START_RUN / CANCEL_RUN commands.
4. Fetch locked TestRun config from the backend.
5. Resolve runtime secrets only for the current run.
6. Build a Promptfoo-compatible config.
7. Execute Promptfoo through the Node API.
8. Normalize Promptfoo output into backend event payloads.
9. Send sanitized events/results back to the backend.
10. Write sanitized local artifacts when supported.
```

The runner should not:

```text
1. Call frontend /api/** endpoints with a user JWT.
2. Read or write the backend database directly.
3. Fetch mutable project/target/evaluator state from normal frontend APIs.
4. Store plaintext runtime secrets.
5. Log raw cURL, bearer tokens, cookies, runtime secret bundles, or unsanitized external payloads.
6. Put full configs, dataset rows, raw responses, artifacts, or secrets into Redis messages.
7. Add new backend API contracts without documenting the backend gap first.
```

## Current Recommended MVP Transport

Use this transport split for runner v1 unless actual backend code proves otherwise:

```text
Command intake:
  Backend -> Redis Stream vfqc:runs:commands -> Runner

Event/result output:
  Runner -> Redis Stream vfqc:runs:events -> Backend worker -> DB/SSE
```

Reason:

```text
1. Redis command stream is explicitly listed as the backend-published command channel.
2. Redis event stream gives backend-side buffering and backpressure for high-volume testcase events.
3. RunnerRedisEventWorker consumes vfqc:runs:events and persists through the same ingestion service as HTTP.
4. POST /internal/runner/events remains useful as a fallback/dev path.
```

The runner code must keep event output swappable:

```text
RunnerEventSink
  ├── RedisRunnerEventSink       # default MVP path
  └── HttpRunnerEventSink        # fallback/dev path
```

## Current Implementation Status

As of the current runner code:

```text
Done:
1. Slice 0 scaffold is in apps/runner.
2. Slice 1 backend DTO validators and HttpBackendClient are implemented.
3. BackendClient injects the runner bearer token and validates backend responses with Zod.
4. Backend HTTP failures are sanitized and classified for retry behavior.
5. Slice 2 Redis command parser, Redis client wrapper, consumer group read, and explicit XACK boundary are implemented.
6. Slice 3 event factories, Redis/HTTP event sinks, and retry helper are implemented.
7. Slice 4 recursive masking, sanitized JSON artifacts, sanitized-byte hashing, and artifact metadata are implemented.
8. Slice 5 Promptfoo config builder fixtures, evaluator mapping, result normalizer, and executor boundary are implemented.
9. Slice 6 RunnerService lifecycle orchestration is implemented for one START_RUN with register, heartbeat, config/secrets, ordered events, and XACK-after-final-event behavior.
10. Slice 7 cancellation polling, event/backend retry, safe retry exhaustion, non-retryable auth fail-fast, and run timeout mapping are implemented.

Not done yet:
1. Long-running runner loop wiring in main.ts.
2. Full Redis event stream publisher implementation behind RedisRunnerEventSink.
3. Full Redis pending recovery/claim loop beyond the no-op boundary.
4. Slice 8 smoke test plan.
```

## Documentation Files

Batch 1 contains:

```text
README.md
00-project-standard.md
01-architecture.md
02-backend-contract.md
```

Recommended later docs:

```text
03-redis-streams.md
04-promptfoo-adapter.md
05-events-and-results.md
06-artifacts-and-masking.md
07-cancel-retry-timeout.md
08-local-development.md
09-testing-contract.md
10-scaffold-package-versions.md
11-sliced-implementation-plan.md
10-scaffold-package-versions.md
```

## AI/Codex Guardrails

When asking an AI agent to work on the runner, use strict instructions:

```text
Do not modify backend API contracts.
Do not modify backend logic.
Do not invent DTO fields or endpoints.
Do not implement product scope outside the runner docs.
Do not use promptfoo@latest in package.json.
Do not log plaintext secrets or raw external payloads.
Use Redis event output by default; use HTTP event output only as an explicit fallback.
Prefer actual backend code over this doc if they conflict.
```

## Backend Paths To Inspect

Before coding runner implementation, inspect:

```text
apps/api/src/main/java/vn/vinfast/vfqc/api/runner
apps/api/src/main/java/vn/vinfast/vfqc/api/testrun
apps/api/src/main/java/vn/vinfast/vfqc/api/common
apps/api/src/main/resources/db/migration
```

## Current Known Backend Gaps

Preserve these as gaps, not as implemented behavior:

```text
1. No dedicated backend artifact upload/metadata endpoint is currently implemented.
2. No explicit command acknowledgement endpoint is currently implemented.
3. Full Redis pending recovery/claim loop is not implemented beyond the no-op boundary.
4. Exact Promptfoo adapter schema is not finalized.
5. Large dataset row paging may need a more explicit runner row-page contract.
6. HTTP event output is fallback/dev; Redis event output is the runner v1 default.
```

Implementation note checked against backend code on 2026-07-09:

```text
RunnerRedisEventConsumerImpl exists for vfqc:runs:events with consumer group
vfqc-api and consumer api-runner-events. RunnerRedisEventWorker runs as a
long-running backend worker when VFQC_RUNNER_REDIS_EVENTS_ENABLED=true, which is
the default outside tests. POST /internal/runner/events remains available as a
fallback/dev event transport.
```
