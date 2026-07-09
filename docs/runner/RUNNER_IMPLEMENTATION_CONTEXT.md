# Runner Implementation Handoff Context

Last checked: 2026-07-09 21:02 Asia/Bangkok.

Read this first in a new runner session. It is intentionally short; detailed specs remain in `docs/runner/README.md`, `docs/runner/00..11-*.md`, and `docs/runner/AI_RUNNER_DTO_HANDOFF.md`.

## TL;DR

- Runner lives in `apps/runner`.
- Runtime: Node.js 24.
- Language: TypeScript strict mode, ESM, named exports.
- Package manager: npm with exact pinned versions in `package.json` and `package-lock.json`.
- Main libraries already installed: `zod`, `redis`, `promptfoo`, `pino`, `undici`, `uuid`, `vitest`, `oxlint`.
- Important convention: run runner commands from `apps/runner` and prefix verbose commands with `rtk`.
- Default event transport is Redis: `Runner -> vfqc:runs:events -> Backend RunnerRedisEventWorker -> DB/SSE`.
- HTTP event ingestion remains fallback/dev through `POST /internal/runner/events`.

## Quick Verify

Run from `apps/runner`:

```bash
rtk npm run check
rtk npm run build
```

Current verified baseline:

```text
rtk npm run check: 30 test files / 143 tests passed.
rtk npm run build: TypeScript build passed.
```

No `npm install` is needed for the next planned slices unless dependencies change.

## Source Map

Main runner source:

```text
apps/runner/src
```

Implemented module areas:

- `backend`
  - runner internal API DTOs
  - Zod runtime validators
  - `HttpBackendClient`
- `config`
  - env schema and `RunnerConfig`
  - `VFQC_EVENT_TRANSPORT` defaults to `redis`
- `errors`
  - `RunnerError`
  - retry classification helper
- `redis`
  - command message parser
  - Redis command stream wrapper
  - `RedisCommandConsumer`
  - `RedisEventStreamPublisher` for `XADD` to `vfqc:runs:events`
  - explicit XACK boundary
  - no-op pending recovery boundary
- `events`
  - event payload schemas
  - `DefaultRunnerEventFactory`
  - `RedisRunnerEventSink`
  - `HttpRunnerEventSink`
  - event retry helper preserving `eventId`
- `masking`
  - recursive sensitive data masker
  - masking policy for sensitive fields, known secrets, and token-like values
- `artifacts`
  - sanitized JSON artifact writer
  - SHA-256 hash utility
  - local artifact path builder
  - metadata builder without public URLs
- `promptfoo`
  - Promptfoo adapter input/generated-config/case-result contracts
  - deterministic config builder for locked backend snapshots
  - dataset variable, target, evaluator, and secret-reference mapping
  - result normalizer for fixture/raw result shapes
  - result normalizer also accepts Promptfoo `EvaluateSummary` results
  - library executor wraps `promptfoo.evaluate()` lazily and returns `toEvaluateSummary()` when available
- `runner`
  - `DefaultRunnerService` boot registration and IDLE/BUSY/IDLE heartbeat flow
  - one-active-run guard for local runner instance
  - START_RUN orchestration across backend config/secrets, Promptfoo config/executor, normalized events, and explicit command XACK after final event success
  - cancellation polling before execution and between testcase events
  - retry budget for backend/event operations, preserving event IDs on retry
  - run timeout mapping to `RUN_FAILED`
- `main.ts`
  - runtime dependency composition for backend client, Redis command consumer, Redis/HTTP event sink, Promptfoo executor, and `DefaultRunnerService`
  - long-running loop with SIGINT/SIGTERM shutdown handling
- Other folders are still mostly scaffold/boundaries for later slices.

## Current Slice Status

| Slice | Status | Notes |
| --- | --- | --- |
| 0 Docs + Scaffold | Done | Runner package, config, logging, DTO stubs, initial tests. |
| 1 Backend DTO Validators And BackendClient | Done | Zod DTO validators, HTTP backend client, sanitized errors, retry classification. |
| 2 Redis Command Intake | Done | START_RUN/CANCEL_RUN parser, Redis wrapper, XGROUP CREATE, XREADGROUP, explicit XACK. |
| 3 Event Sink And Event Factories | Done | Event payload schemas, factories, Redis/HTTP sinks, retry helper. |
| 4 Sensitive Data Masking And Artifact Utilities | Done | Recursive masking, sanitized JSON writer, sanitized-byte hash, metadata builder. |
| 5 Promptfoo Adapter Fixtures | Done | Config builder, deterministic fixtures, evaluator mapping, result normalizer, executor boundary. |
| 6 Runner Lifecycle Orchestration | Done | Register/heartbeat, config+secret resolution, Promptfoo fake executor boundary, ordered events, XACK-after-final-event tests. |
| 7 Cancellation, Retry, Timeout | Done | Control polling, best-effort CANCEL_RUN flag, event/backend retry, safe retry exhaustion, non-retryable auth fail-fast, run timeout -> RUN_FAILED. |
| 8 Local Smoke Test | Partial | Manual smoke plan documented; runtime loop, Redis publisher, and backend Testcontainers seed fixture are implemented. Full manual local smoke was not run because backend/Redis local services were not running and no non-test seed command exists yet. |

## Current Coverage Highlights

The runner suite currently covers:

- Env schema validation and test-safe `main.ts` import.
- Backend API DTO enum exports and Zod response validators.
- `HttpBackendClient` auth header, config pagination query, validation failures, sanitized runtime secret errors, and retry classification.
- Redis command message parsing for `START_RUN` and `CANCEL_RUN`.
- Redis command consumer group creation, `XREADGROUP`, no-message return, and explicit XACK.
- Event factory payloads for `RUN_STARTED`, testcase events, `RUN_COMPLETED`, `RUN_FAILED`, `RUN_CANCELLED`, and `ARTIFACT_CREATED`.
- Redis event sink publish acknowledgement.
- Redis event stream publisher `XADD` field mapping, optional field omission, sanitized payload handling, and publish failure propagation.
- HTTP event sink retry behavior, idempotent replay success, and non-looping `INVALID_STATE` behavior.
- Recursive masking for sensitive fields, known fake secrets, token-like values, raw cURL authorization, and accidental Promptfoo secrets.
- Sanitized JSON artifact writing, sanitized-byte hashing, metadata-only artifact records, and metadata-only `ARTIFACT_CREATED` behavior.
- Promptfoo config generation for chatbot, baseline comparison, rule-based evaluator, LLM judge evaluator, mixed evaluator, secret masking, and sanitized config artifacts.
- Promptfoo result normalization for success, target timeout, judge parse error, and stable VFQC testcase result schema.
- Promptfoo `EvaluateSummary` normalization for library executor output.
- Runner lifecycle orchestration for one START_RUN, including register, heartbeat IDLE/BUSY/IDLE, locked config fetch, runtime secret resolution, ordered RUN_STARTED/testcase/RUN_COMPLETED events, terminal duplicate skip, and XACK only after final event success.
- Runner cancellation/retry/timeout hardening, including pre-execution cancellation, during-loop cancellation before the next testcase, completion after finished work, same-eventId retry, safe retry exhaustion, non-retryable 401 fail-fast, run timeout -> RUN_FAILED, and target timeout -> testcase-level error.

Smoke documentation now covers:

- Local backend/Redis prerequisites.
- Minimal no-real-secret TestRun fixture requirements.
- Expected Redis command/event stream shapes.
- Backend Redis worker persistence and SSE replay checks.
- Secret leak pass/fail criteria.

Backend smoke seed coverage now includes:

- `RunnerSmokeSeedFixtureIntegrationTest`, which creates one queued TestRun, one dataset row, a fake local chatbot target, a rule-based evaluator, and verifies backend publishes a compact `START_RUN` command without leaking the fake secret or target URL.

## Important Implementation Conventions

- Follow `docs/runner/11-sliced-implementation-plan.md`; do not jump ahead or merge slices.
- Use TDD for new behavior: add failing tests first, then production code.
- Keep changes scoped to the slice allowed components.
- Keep TypeScript strict: no `any`, no runtime `enum`, no default exports, avoid non-null assertions.
- Validate all external boundaries with Zod.
- Do not log or persist plaintext runtime secrets, auth tokens, raw cURL, cookies, raw external payloads, or unsanitized result bodies.
- Prefer constructor injection for clients/sinks/adapters so tests can use fakes.
- Redis command intake and event output are transport boundaries only; they must not run Promptfoo or call backend result logic directly.
- `RedisRunnerEventSink` is asynchronous from the backend perspective. It returns a Redis `streamMessageId`, not a backend `sequenceNo`.
- `HttpRunnerEventSink` returns `RunnerEventIngestionResponse` and is fallback/dev.
- Retry must preserve the original `eventId`.
- Treat backend idempotent replay with existing `sequenceNo` as success, even if status is `APPLIED`.
- Treat `INVALID_STATE` as non-retryable unless backend contract changes.
- Commit after each completed slice using Conventional Commit format.

## Backend Contract Summary

Internal runner HTTP APIs:

- `POST /internal/runner/instances/register`
- `POST /internal/runner/instances/heartbeat`
- `GET /internal/runner/test-runs/{testRunId}/config`
- `POST /internal/runner/test-runs/{testRunId}/runtime-secrets`
- `GET /internal/runner/test-runs/{testRunId}/control-state`
- `POST /internal/runner/events`

Redis streams:

- Commands: `vfqc:runs:commands`
- Events: `vfqc:runs:events`
- Commands DLQ: `vfqc:runs:commands:dlq`
- Events DLQ: `vfqc:runs:events:dlq`

Backend Redis event consumer:

```text
RunnerRedisEventWorker consumes vfqc:runs:events with group vfqc-api and consumer api-runner-events.
```

Backend currently has Redis event worker enabled by default outside tests. Prefer Redis event output for MVP and keep HTTP event output as fallback/dev.

## Recommended Next Work

1. Add a local non-test seed command or documented dev-only seed flow for manual smoke.

2. Run full manual smoke with local backend/PostgreSQL/Redis:
   - Verify `START_RUN` in Redis.
   - Verify runner emits `RUN_STARTED -> TESTCASE_* -> RUN_COMPLETED`.
   - Verify backend worker persists events and SSE replay returns the sequence.

3. Only then add `npm run test:smoke`.

## Caveats

- `apps/runner/dist` may exist after `npm run build`; it is ignored.
- `apps/runner/node_modules` may exist locally; it is ignored.
- Pending command recovery is currently a no-op boundary; stale pending claim handling is still not done.
- `docs/runner/12-local-smoke-test.md` is a manual smoke plan only; local smoke was not completed in this session because `localhost:8080` was not running and `redis-cli` was unavailable.
- Backend/event retry currently uses immediate retries unless `retryDelayMs` is configured.
- Artifact events are metadata-only; there is no complete backend artifact upload/download contract yet.
- Always check worktree state with `rtk git status --short` before editing or committing.
