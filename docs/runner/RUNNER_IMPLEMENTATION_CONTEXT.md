# Runner Implementation Handoff Context

Last checked: 2026-07-09 19:15 Asia/Bangkok.

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
rtk npm run check: 16 test files / 55 tests passed.
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
  - explicit XACK boundary
  - no-op pending recovery boundary
- `events`
  - event payload schemas
  - `DefaultRunnerEventFactory`
  - `RedisRunnerEventSink`
  - `HttpRunnerEventSink`
  - event retry helper preserving `eventId`
- Other folders are still mostly scaffold/boundaries for later slices.

## Current Slice Status

| Slice | Status | Notes |
| --- | --- | --- |
| 0 Docs + Scaffold | Done | Runner package, config, logging, DTO stubs, initial tests. |
| 1 Backend DTO Validators And BackendClient | Done | Zod DTO validators, HTTP backend client, sanitized errors, retry classification. |
| 2 Redis Command Intake | Done | START_RUN/CANCEL_RUN parser, Redis wrapper, XGROUP CREATE, XREADGROUP, explicit XACK. |
| 3 Event Sink And Event Factories | Done | Event payload schemas, factories, Redis/HTTP sinks, retry helper. |
| 4 Sensitive Data Masking And Artifact Utilities | Not started | Next recommended slice. |
| 5 Promptfoo Adapter Fixtures | Not started | Do after masking/artifact utilities. |
| 6 Runner Lifecycle Orchestration | Not started | Do only after Promptfoo boundary exists. |
| 7 Cancellation, Retry, Timeout | Not started | Needs orchestration first. |
| 8 Local Smoke Test | Not started | Final local integration/smoke slice. |

## Current Coverage Highlights

The runner suite currently covers:

- Env schema validation and test-safe `main.ts` import.
- Backend API DTO enum exports and Zod response validators.
- `HttpBackendClient` auth header, config pagination query, validation failures, sanitized runtime secret errors, and retry classification.
- Redis command message parsing for `START_RUN` and `CANCEL_RUN`.
- Redis command consumer group creation, `XREADGROUP`, no-message return, and explicit XACK.
- Event factory payloads for `RUN_STARTED`, testcase events, `RUN_COMPLETED`, `RUN_FAILED`, `RUN_CANCELLED`, and `ARTIFACT_CREATED`.
- Redis event sink publish acknowledgement.
- HTTP event sink retry behavior, idempotent replay success, and non-looping `INVALID_STATE` behavior.

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

1. Do Slice 4: Sensitive Data Masking And Artifact Utilities.
   - Implement recursive `SensitiveDataMasker`.
   - Implement `MaskingPolicy`.
   - Implement sanitized JSON writer.
   - Implement sanitized hash utility.
   - Implement artifact metadata builder.
   - Keep backend artifact upload out of scope.

2. Then do Slice 5: Promptfoo Adapter Fixtures.
   - Build Promptfoo boundary with deterministic fixtures.
   - Do not wire full runner orchestration yet.

3. Then do Slice 6: Runner Lifecycle Orchestration.
   - Use mocked Promptfoo executor first.
   - Wire command consume, backend config/secrets/control, event sink, and XACK carefully.

## Caveats

- `apps/runner/dist` may exist after `npm run build`; it is ignored.
- `apps/runner/node_modules` may exist locally; it is ignored.
- `RedisRunnerEventSink` currently depends on a `RunnerEventPublisher` boundary; full Redis event stream publisher implementation is still not done.
- Pending command recovery is currently a no-op boundary; stale pending claim handling is still not done.
- Masking in Slice 3 is only minimal run-level text masking. Full recursive masking belongs to Slice 4.
- Artifact events are metadata-only; there is no complete backend artifact upload/download contract yet.
- Always check worktree state with `rtk git status --short` before editing or committing.
