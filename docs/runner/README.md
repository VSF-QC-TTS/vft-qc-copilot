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
  Runner -> POST /internal/runner/events -> Backend -> DB/SSE
```

Reason:

```text
1. Redis command stream is explicitly listed as the backend-published command channel.
2. POST /internal/runner/events has a concrete DTO and idempotency contract.
3. A dedicated command acknowledgement endpoint is not currently implemented.
4. Redis event delivery is now backend opt-in and should not become the default runner output path without explicit operational enablement.
```

The runner code must keep event output swappable:

```text
RunnerEventSink
  ├── HttpRunnerEventSink        # default MVP path
  └── RedisRunnerEventSink       # opt-in only when backend Redis event polling is enabled
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
Do not use Redis event output unless backend Redis event polling is explicitly enabled.
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
3. Redis command consumer for the standalone runner is not implemented in this repository yet.
4. Exact Promptfoo adapter schema is not finalized.
5. Large dataset row paging may need a more explicit runner row-page contract.
6. Redis event output is opt-in and disabled by default; HTTP remains the runner v1 default.
```

Implementation note checked against backend code on 2026-07-09:

```text
RunnerRedisEventConsumerImpl exists for vfqc:runs:events with consumer group
vfqc-api and consumer api-runner-events. RunnerRedisEventConsumerScheduler can
poll it in production when VFQC_RUNNER_REDIS_EVENTS_ENABLED=true. Keep
POST /internal/runner/events as the default runner event transport unless Redis
event output is explicitly enabled and operated.
```
