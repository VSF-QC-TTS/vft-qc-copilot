# Sliced Runner Implementation Plan

This plan exists to keep Codex work small, reviewable, and testable.

Do not implement the standalone runner in one large pass.

Each slice must have:

```text
1. Clear scope.
2. Files allowed to change.
3. Tests added in the same slice.
4. Commands to run.
5. Stop condition.
```

## Global Stop Conditions

Stop and report instead of continuing if:

```text
1. Backend contract differs from docs in a non-trivial way.
2. npm install cannot use the pinned versions.
3. npm run check fails.
4. npm run build fails after source changes.
5. A test requires real secrets or real internal endpoints.
6. Redis event output is requested but backend Redis event polling is not explicitly enabled.
7. Artifact upload/download is required but backend endpoint is missing.
```

Do not silently change dependency versions or weaken tests.

## Slice 0 — Docs + Scaffold Only

Purpose:

```text
Create the runner project skeleton without implementing business logic.
```

Allowed changes:

```text
docs/runner/**
apps/runner/package.json
apps/runner/package-lock.json
apps/runner/tsconfig.json
apps/runner/.env.example
apps/runner/.nvmrc
apps/runner/.node-version
apps/runner/src/main.ts
apps/runner/src/config/**
apps/runner/src/logging/**
apps/runner/src/backend/dto/**
empty interface/stub files under apps/runner/src/**
minimal tests under apps/runner/test/**
```

Not allowed:

```text
No backend code changes.
No real Redis consume loop.
No real Promptfoo execution.
No real backend HTTP calls.
No runtime secret resolution.
No runner lifecycle orchestration.
```

Tests:

```text
1. env-schema validates a minimal local config fixture.
2. runner-api DTO enum exports compile.
3. main.ts can import config/logger without side effects in test mode.
```

Commands:

```bash
npm run check
npm run build
npm audit --audit-level=high
```

Stop for human review after this slice.

## Slice 1 — Backend DTO Validators And BackendClient

Status:

```text
Completed in apps/runner:
1. Zod runtime validators now live beside the runner API DTO types.
2. HttpBackendClient implements the internal runner HTTP contract.
3. Runner bearer token injection is covered by tests.
4. Backend validation, HTTP, and network errors are sanitized and retry-classified.
5. Slice tests pass under npm run check and npm run build.
```

Purpose:

```text
Implement typed backend boundary without touching Redis or Promptfoo.
```

Allowed components:

```text
src/backend/dto/runner-api.dto.ts
src/backend/backend-client.ts
src/config/runner-config.ts
src/errors/runner-error.ts
src/utils/json.ts
```

Implement:

```text
1. DTO types and runtime validators for runner API responses.
2. BackendClient methods:
   - registerInstance
   - heartbeat
   - getTestRunConfig
   - resolveRuntimeSecrets
   - getControlState
   - sendEvent
3. Runner token header injection.
4. Sanitized error handling.
5. Retry classification helper only if needed by client tests.
```

Do not implement:

```text
Redis command consumer.
Promptfoo adapter.
Runner orchestration.
```

Tests:

```text
1. Valid DTO fixtures parse successfully.
2. Invalid enum/required field fixtures fail loudly.
3. BackendClient sends auth token.
4. BackendClient does not log runtime secret response bodies.
5. 401/403 are classified as non-retryable.
6. Transient 5xx/network errors are classified as retryable.
```

Commands:

```bash
npm run check
npm run build
```

Stop after slice and show diff.

## Slice 2 — Redis Command Intake

Status:

```text
Completed in apps/runner:
1. Runner command messages are validated with Zod and parse raw Redis field arrays.
2. Redis command stream wrapper exposes connect/disconnect, XGROUP CREATE, XREADGROUP, and XACK.
3. RedisCommandConsumer creates the consumer group with MKSTREAM and reads one new command with STREAMS >.
4. XACK remains explicit and is not called automatically after read.
5. Pending recovery is a no-op boundary; full stale pending claim handling is intentionally left for a later slice.
6. Slice tests pass under npm run check and npm run build.
```

Purpose:

```text
Read and parse START_RUN/CANCEL_RUN command messages safely.
```

Allowed components:

```text
src/redis/stream-names.ts
src/redis/redis-client.ts
src/redis/command-message.ts
src/redis/command-consumer.ts
src/redis/pending-recovery.ts
```

Implement:

```text
1. Redis connection wrapper.
2. Command message parser.
3. Consumer group creation if missing.
4. XREADGROUP-based command read.
5. XACK method, but do not call it from business logic yet.
6. Pending recovery boundary/stub if full recovery is not implemented yet.
```

Do not implement:

```text
Full runner execution.
Promptfoo execution.
Backend event sending from command consumer directly.
```

Tests:

```text
1. Valid START_RUN parses.
2. Valid CANCEL_RUN parses.
3. Invalid commandType fails.
4. Missing commandId/testRunId fails.
5. Raw Redis field arrays are converted into typed messages.
6. XACK is exposed but not automatically called before final event success.
```

Optional integration tests:

```text
Use local Redis/Testcontainers only if available.
If unavailable, keep unit tests with mocked Redis and document integration gap.
```

Commands:

```bash
npm run check
npm run build
```

Stop after slice and show diff.

## Slice 3 — Event Sink And Event Factories

Status:

```text
Completed in apps/runner:
1. Runner event payload schemas cover RUN_STARTED, RUN_PROGRESS, TESTCASE_COMPLETED, TESTCASE_ERROR, RUN_COMPLETED, RUN_FAILED, RUN_CANCELLED, RUN_LOG, and ARTIFACT_CREATED.
2. DefaultRunnerEventFactory creates typed RunnerEventRequest values and minimally sanitizes run-level error/log text.
3. RedisRunnerEventSink is the default async transport boundary and returns a Redis stream publish acknowledgement.
4. HttpRunnerEventSink remains the fallback/dev strategy and returns backend ingestion responses.
5. sendEventWithRetry preserves eventId across retries and does not retry INVALID_STATE responses.
6. Slice tests pass under npm run check and npm run build.
```

Purpose:

```text
Emit typed, sanitized runner events through a transport boundary.
```

Allowed components:

```text
src/events/runner-event-sink.ts
src/events/http-runner-event-sink.ts
src/events/redis-runner-event-sink.ts
src/events/runner-event-factory.ts
src/events/normalized-events.ts
```

Implement:

```text
1. RunnerEventSink interface.
2. RedisRunnerEventSink as default.
3. HttpRunnerEventSink as fallback/dev strategy behind config.
4. Event factory for all backend-supported event types.
5. Idempotency retry helper preserving eventId.
```

Default:

```text
VFQC_EVENT_TRANSPORT=redis
```

Use HTTP event output only as an explicit fallback/dev path.

Tests:

```text
1. RUN_STARTED payload is valid.
2. TESTCASE_COMPLETED requires testRunTargetId and datasetRowId.
3. TESTCASE_ERROR requires testRunTargetId and datasetRowId.
4. RUN_COMPLETED summary payload is valid.
5. RUN_FAILED error is sanitized.
6. RUN_CANCELLED reason is valid.
7. ARTIFACT_CREATED is metadata-only.
8. HTTP retry reuses same eventId.
9. Backend idempotent replay response with the existing sequence is treated as success.
10. Backend INVALID_STATE does not retry forever.
```

Commands:

```bash
npm run check
npm run build
```

Stop after slice and show diff.

## Slice 4 — Sensitive Data Masking And Artifact Utilities

Status:

```text
Completed in apps/runner:
1. RecursiveSensitiveDataMasker masks sensitive fields, known secrets, token-like text, and raw cURL Authorization headers.
2. DefaultMaskingPolicy includes backend-documented sensitive keys and known-secret support.
3. SanitizedJsonArtifactWriter writes only sanitized JSON bytes.
4. Sha256HashUtil hashes the sanitized bytes written to disk.
5. Artifact metadata is local metadata only and does not include public download URLs.
6. Slice tests pass under npm run check and npm run build.
```

Purpose:

```text
Prevent secret leakage before implementing real execution.
```

Allowed components:

```text
src/masking/**
src/artifacts/**
src/logging/logger.ts
```

Implement:

```text
1. Recursive SensitiveDataMasker.
2. MaskingPolicy with field names and token-like value detection.
3. Sanitized JSON writer.
4. Hash utility that hashes sanitized bytes.
5. Artifact metadata builder.
```

Do not implement:

```text
Backend artifact upload endpoint.
Public artifact download URLs.
```

Tests:

```text
1. Known fake secret strings never appear in sanitized output.
2. Authorization/Cookie/X-API-Key fields are masked.
3. Nested token/password/secret fields are masked.
4. Raw cURL with Authorization is masked.
5. Promptfoo config with accidental secret value is masked.
6. Artifact hash uses sanitized bytes.
7. ARTIFACT_CREATED contains metadata only.
```

Commands:

```bash
npm run check
npm run build
```

Stop after slice and show diff.

## Slice 5 — Promptfoo Adapter Fixtures

Purpose:

```text
Build the Promptfoo boundary with deterministic fixtures before real orchestration.
```

Allowed components:

```text
src/promptfoo/promptfoo-adapter.ts
src/promptfoo/promptfoo-config-builder.ts
src/promptfoo/promptfoo-result-normalizer.ts
src/promptfoo/promptfoo-executor.ts
src/promptfoo/fixtures/**
test/fixtures/promptfoo/**
```

Implement:

```text
1. PromptfooAdapterInput.
2. Promptfoo config builder for locked backend snapshots.
3. Dataset variable mapping: $dataset.question -> {{question}}.
4. Target mapping for CHATBOT_TARGET and LLM_BASELINE at config-boundary level.
5. Evaluator mapping fixtures for RULE_BASED, LLM_JUDGE, MIXED.
6. Result normalizer from fixture/raw shape into PromptfooCaseResult.
7. PromptfooExecutor interface; real execution can remain thin or mocked until lifecycle slice.
```

Do not call real external LLM/chatbot endpoints in tests.

Tests:

```text
1. Single CHATBOT_TARGET fixture builds expected sanitized config preview.
2. COMPARISON fixture creates multiple targets/providers.
3. RULE_BASED fixture maps assertions.
4. LLM_JUDGE fixture maps rubric/judge config.
5. MIXED fixture combines components.
6. Target timeout maps to TIMEOUT/TESTCASE_ERROR.
7. Judge parse error maps to JUDGE_ERROR.
8. Generated config artifact is sanitized.
9. Normalized result keeps VFQC schema stable.
```

Commands:

```bash
npm run check
npm run build
```

Stop after slice and show diff.

## Slice 6 — Runner Lifecycle Orchestration

Purpose:

```text
Connect backend client, command consumer, event sink, cancellation, and Promptfoo executor using mocks/fakes first.
```

Allowed components:

```text
src/runner/**
src/main.ts
src/backend/**
src/redis/**
src/events/**
src/promptfoo/**
```

Implement:

```text
1. RunnerService orchestration for one START_RUN.
2. One runner instance = one active run.
3. Register on boot.
4. Heartbeat IDLE/BUSY/IDLE.
5. Fetch locked config.
6. Resolve referenced runtime secrets.
7. Build Promptfoo config.
8. Send RUN_STARTED.
9. Send testcase events from mocked/fake Promptfoo results.
10. Send RUN_COMPLETED.
11. XACK command only after final event success.
```

Tests:

```text
1. Happy path sends register, heartbeat BUSY, config, secrets, RUN_STARTED, testcase event, RUN_COMPLETED, heartbeat IDLE.
2. Command is not acknowledged before final event success.
3. Runtime secrets are not logged or emitted.
4. Duplicate command delivery does not run the same terminal run twice when backend status is terminal.
5. Event order is stable for one run.
```

Commands:

```bash
npm run check
npm run build
```

Stop after slice and show diff.

## Slice 7 — Cancellation, Retry, Timeout, Idempotency

Purpose:

```text
Harden runner execution behavior.
```

Allowed components:

```text
src/runner/cancellation.service.ts
src/runner/heartbeat.service.ts
src/errors/error-classifier.ts
src/utils/sleep.ts
src/events/**
src/backend/backend-client.ts
```

Implement:

```text
1. Control-state polling.
2. CANCEL_RUN best-effort signal handling.
3. Retry policy for retryable backend/event failures.
4. Timeout wrappers for target/judge/testcase/run boundaries if runner controls them.
5. Same eventId on event retry.
6. RUN_FAILED mapping for run-level unrecoverable errors.
```

Tests:

```text
1. Cancellation before execution sends RUN_CANCELLED.
2. Cancellation during loop stops before next testcase.
3. Completion after cancellation request is allowed if work already finished.
4. Event POST retry uses same eventId.
5. Retry budget exhaustion is reported safely.
6. 401/403 fail fast.
7. Run timeout maps to RUN_FAILED.
8. Target timeout maps to testcase-level error, not run-level failure.
```

Commands:

```bash
npm run check
npm run build
```

Stop after slice and show diff.

## Slice 8 — Smoke Test Plan

Purpose:

```text
Document and optionally automate a local end-to-end smoke path.
```

Allowed components:

```text
docs/runner/**
apps/runner/scripts/**
apps/runner/test/smoke/**
```

Smoke path:

```text
1. Start backend locally.
2. Start Redis locally.
3. Seed or create minimal TestRun through existing backend-supported path.
4. Backend publishes START_RUN.
5. Runner consumes command.
6. Runner fetches config.
7. Runner sends RUN_STARTED.
8. Runner sends one TESTCASE_COMPLETED or TESTCASE_ERROR.
9. Runner sends RUN_COMPLETED.
10. Backend SSE can replay projected events.
```

Rules:

```text
1. Smoke test must not require real LLM keys.
2. Smoke test must not require VinFast internal network.
3. If backend seeding path is not stable, keep smoke as documented manual steps.
4. Do not block npm run check on flaky external smoke tests.
```

Commands:

```bash
npm run check
npm run build
```

Optional:

```bash
npm run test:smoke
```

## Final Runner MVP Definition Of Done

Runner MVP implementation is ready only when:

```text
1. npm ci succeeds.
2. npm run check succeeds.
3. npm run build succeeds.
4. Backend contract tests pass.
5. Redis command intake tests pass.
6. Redis event sink tests pass.
7. Masking leak tests pass.
8. Promptfoo adapter fixtures pass.
9. Runner lifecycle tests pass.
10. Cancellation/retry/timeout tests pass.
11. Local smoke plan exists.
12. No real secret appears in fixtures, logs, events, or artifacts.
13. Event transport remains Redis by default with HTTP fallback available.
```
