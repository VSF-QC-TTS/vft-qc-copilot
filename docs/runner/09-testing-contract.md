# Runner Testing Contract

This document defines the minimum test contract for the standalone runner.

The runner must not be implemented as an untested Promptfoo wrapper. Its most important responsibilities are boundary correctness, idempotency, sanitization, and predictable failure behavior.

## Source Basis

This document is based on:

```text
AI_RUNNER_DTO_HANDOFF.md
API_IMPLEMENTATION_CONTEXT.md
PRD v0.7
```

The backend handoff says backend tests already cover runner command/config/secret/event/cancel/DLQ behavior, SSE replay/masking/heartbeat behavior, and export safety. Runner tests should complement that coverage instead of duplicating backend internals.

## Test Layers

Use these layers:

```text
1. Unit tests.
2. Contract tests.
3. Integration tests with mocked backend.
4. Redis integration tests.
5. Promptfoo adapter fixture tests.
6. End-to-end smoke tests against local backend only when practical.
```

Do not require real external LLM or chatbot credentials for normal test runs.

## Commands

From `apps/runner`:

```bash
npm run typecheck
npm run lint
npm run test
npm run check
```

CI should run:

```bash
npm ci
npm run check
npm run build
```



## Slice-Based Test Rule

Every implementation slice must include tests in the same change.

Do not implement several runner layers first and "add tests later".

Minimum rule per slice:

```text
Slice 0 scaffold:
  npm run typecheck, npm run lint, npm run test, npm run build.
  Minimal tests for env parsing and DTO enum exports.

Slice 1 backend contract/client:
  Contract tests for DTO validators and mocked BackendClient requests/responses.

Slice 2 Redis command intake:
  Unit tests for command parsing and Redis consumer behavior with mocked Redis.
  Optional Redis integration tests if local Redis/Testcontainers is available.

Slice 3 event sink:
  Redis event sink tests, event idempotency retry tests, and HTTP fallback sink tests.

Slice 4 masking/artifacts:
  Mandatory leak tests using known fake secret strings.
  Artifact hash tests must hash sanitized bytes.

Slice 5 Promptfoo adapter:
  Deterministic adapter fixtures for single target, comparison, rule-based, LLM judge, mixed, timeout, and judge parse error.

Slice 6 runner lifecycle:
  Orchestration tests with mocked BackendClient, CommandConsumer, EventSink, PromptfooExecutor, and CancellationService.

Slice 7 cancel/retry/timeout:
  Tests for cancellation wins, completion after cancellation request, retry budget, same eventId on retry, and run timeout mapping.

Slice 8 smoke plan:
  Document local smoke steps in docs/runner/12-local-smoke-test.md.
  Automate only if local backend/Redis fixtures are stable.
```

After each slice, run:

```bash
npm run check
npm run build
```

A slice is not complete if tests are skipped because they are hard to write. If a test cannot be automated yet, document the gap and add a smaller unit/contract test around the nearest boundary.

## Unit Test Scope

Unit tests should cover:

```text
config/env-schema.ts
backend DTO validators
Redis command parser
event factory
event idempotency helper
retry helper
cancellation service
sensitive data masker
artifact writer sanitization
hash utility
Promptfoo result normalizer
error classifier
```

## Backend Contract Tests

The runner should encode the current backend DTOs in TypeScript and test them with representative fixtures.

Fixtures should cover:

```text
RegisterRunnerInstanceRequest
RunnerHeartbeatRequest
RunnerInstanceResponse
RunnerTestRunConfigResponse
RunnerRuntimeSecretsRequest
RunnerRuntimeSecretsResponse
RunnerControlStateResponse
RunnerEventRequest
RunnerEventIngestionResponse
```

Test goals:

```text
1. Required fields are enforced.
2. Optional nullable fields are accepted.
3. Unknown backend enum values fail loudly or are handled intentionally.
4. TestRunTargetType values are accepted: CHATBOT_TARGET, LLM_BASELINE.
5. TestRunType values are accepted: SINGLE_TARGET, COMPARISON.
6. TestRunStatus values match backend: CREATED, QUEUED, RUNNING, CANCEL_REQUESTED, COMPLETED, FAILED, CANCELLED.
```

If backend code changes, update fixtures and docs together.

## Event Payload Contract Tests

Test all event payloads currently understood by backend:

```text
RUN_STARTED
RUN_PROGRESS
TESTCASE_COMPLETED
TESTCASE_ERROR
RUN_COMPLETED
RUN_FAILED
RUN_CANCELLED
RUN_LOG
ARTIFACT_CREATED
```

Required tests:

```text
1. TESTCASE_COMPLETED includes testRunTargetId and datasetRowId.
2. TESTCASE_ERROR includes testRunTargetId and datasetRowId.
3. RUN_COMPLETED includes summary when available.
4. RUN_FAILED includes sanitized error/message/category.
5. RUN_CANCELLED includes reason.
6. RUN_LOG does not include raw secrets or raw payloads.
7. ARTIFACT_CREATED is metadata-only.
```

## Event Idempotency Tests

Test that event retry reuses the same event id.

Cases:

```text
1. First send fails with network error, retry sends same eventId.
2. Backend returns the existing sequence for an idempotent replay, runner treats it as success.
3. Backend returns INVALID_STATE, runner does not blindly retry forever.
4. Same logical testcase event is not regenerated with a new eventId during retry.
5. Final event retry uses same eventId.
```

## Sensitive Data Masking Tests

Masking tests are mandatory.

Input cases:

```text
Authorization header
Cookie header
Set-Cookie header
X-API-Key header
api_key field
access_token field
refresh_token field
nested token fields
Bearer token in text
Basic token in text
JWT-like value in text
private key block in text
raw cURL with Authorization
error object containing request headers
Promptfoo config containing secret reference and accidental secret value
```

Expected behavior:

```text
1. Secret values are masked.
2. Field presence may be preserved.
3. Non-sensitive fields remain useful.
4. Sanitizer is recursive.
5. Sanitizer does not throw on null, arrays, numbers, booleans, or unknown objects.
6. Sanitized output never contains known test secret values.
```

Use test secret strings with obvious names:

```text
TEST_SECRET_BEARER_TOKEN_SHOULD_NOT_LEAK
TEST_API_KEY_SHOULD_NOT_LEAK
```

Then assert they do not appear in:

```text
logs
runner events
artifact files
sanitized errors
```

## Artifact Tests

Artifact tests should cover:

```text
1. Run directory creation.
2. metadata.json creation.
3. promptfooconfig.json sanitization.
4. results.json sanitization.
5. results.jsonl sanitization.
6. Hashing final sanitized bytes.
7. ARTIFACT_CREATED payload contains metadata only.
8. Local path is not presented as public download URL.
```

Do not snapshot-test unsanitized artifacts.

Do not assert absolute local paths unless needed for internal filesystem behavior.

## Promptfoo Adapter Fixture Tests

Promptfoo adapter contract tests should be deterministic.

Minimum fixtures:

```text
1. Single CHATBOT_TARGET with HTTP target config and transformResponse.
2. RULE_BASED evaluator only.
3. LLM_JUDGE evaluator only.
4. MIXED evaluator.
5. COMPARISON run with multiple targets.
6. Target timeout result.
7. Target request error result.
8. Judge parse error.
9. Sanitized generated config artifact.
10. Dataset variable mapping: $dataset.question -> {{question}}.
```

Test the adapter boundary:

```text
PromptfooAdapterInput -> Promptfoo executable config / normalized result
```

Do not couple tests to raw Promptfoo internal result shape more than necessary.

If Promptfoo raw output changes after upgrade, update PromptfooAdapter while keeping VFQC normalized result stable where possible.

## Cancellation Tests

Required cases:

```text
1. Cancellation requested before run starts -> RUN_CANCELLED.
2. Cancellation requested during testcase loop -> stop before next testcase and RUN_CANCELLED.
3. Cancellation requested after Promptfoo already finished -> RUN_COMPLETED is allowed.
4. CANCEL_RUN command received but control-state says no cancellation -> poll again / best effort.
5. Control-state cancellation works even if CANCEL_RUN command is missed.
```

Do not assert that cancellation always wins.

Backend allows completion after cancellation request if execution already finished.

## Timeout Tests

Required cases:

```text
1. Target request timeout -> testcase TIMEOUT/TESTCASE_ERROR.
2. LLM judge timeout -> JUDGE_ERROR or TIMEOUT based on mapper.
3. Testcase timeout -> testcase TIMEOUT.
4. Whole run timeout -> RUN_FAILED with errorCategory RUN_TIMEOUT.
5. Timeout config is read from env and validated.
```

Use fake timers where practical.

Do not make tests sleep for real timeout durations.

## Retry Tests

Required cases:

```text
1. Backend config GET transient failure then success.
2. Event POST transient failure then success with same eventId.
3. Event POST non-retryable 401/403 fails fast.
4. Runtime secrets transient failure retry does not log secret response.
5. Retry budget exhaustion leaves command unacked or emits RUN_FAILED depending on stage.
```

Retry tests should verify:

```text
1. max attempts.
2. backoff is invoked.
3. same eventId is reused.
4. sanitized errors are logged.
```

## Redis Command Tests

Use local Redis or Testcontainers-compatible setup if available.

Required cases:

```text
1. Create consumer group if missing.
2. Read START_RUN with XREADGROUP.
3. Parse command fields into RunnerCommandMessage.
4. Reject invalid commandType.
5. XACK only after final event success.
6. Do not XACK if final event cannot be sent.
7. Handle duplicate START_RUN delivery safely.
8. Handle CANCEL_RUN best-effort.
```

Pending recovery tests:

```text
1. Claim idle pending command with XAUTOCLAIM when Redis version supports it.
2. Do not claim fresh pending messages.
3. Move command to DLQ or surface failure after max attempts according to policy.
```

If Redis version in local/test environment does not support a command required by docs, skip with a clear reason or adjust implementation to the supported baseline.

## Backend Client Tests

Mock backend responses for:

```text
register
heartbeat
get config
runtime secrets
control state
send event
```

Required behavior:

```text
1. Adds runner auth token.
2. Parses JSON responses.
3. Validates DTOs.
4. Sanitizes errors before logging.
5. Does not log runtime secret response bodies.
6. Retries only retryable requests/statuses.
```

## No Real Secret Test Rule

Tests must not require:

```text
real OpenAI key
real Gemini key
real Claude key
real internal chatbot token
real VinFast internal endpoint
```

Use fixtures and mock providers.

## Local End-To-End Smoke Test

Optional but useful after unit/integration tests pass.

Smoke flow:

```text
1. Start backend locally.
2. Start Redis locally.
3. Create or seed a minimal TestRun through backend-supported test utilities or API.
4. Backend publishes START_RUN.
5. Runner consumes command.
6. Runner fetches config.
7. Runner sends RUN_STARTED.
8. Runner sends one TESTCASE_COMPLETED or TESTCASE_ERROR.
9. Runner sends RUN_COMPLETED.
10. Backend SSE can replay events.
```

Do not make smoke tests depend on external network LLM calls.

## Coverage Expectations

Do not chase arbitrary coverage percentages at the expense of contract tests.

Minimum meaningful coverage:

```text
1. All boundary DTO validators covered.
2. All event payload factories covered.
3. All masking policies covered.
4. All cancellation branches covered.
5. Retry/idempotency helpers covered.
6. Promptfoo adapter fixtures covered.
```

## Upgrade Tests

Promptfoo upgrade requires:

```text
1. npm package version pinned exactly.
2. package-lock updated.
3. Promptfoo adapter fixture suite passing.
4. Normalized VFQC output shape compared against expected fixtures.
5. Sanitized config artifact fixture still safe.
```

Do not approve Promptfoo upgrades based only on `npm test` if adapter fixtures are missing.

## CI Checklist

Runner CI should verify:

```text
1. npm ci succeeds.
2. npm run typecheck succeeds.
3. npm run lint succeeds.
4. npm run test succeeds.
5. npm run build succeeds.
6. No test fixture secret string appears in generated artifacts/log snapshots.
```

Repository-level CI can later include backend + runner integration, but runner unit/contract tests should remain runnable from `apps/runner` alone.

## Test Fixture Directory

Suggested layout:

```text
test/
  fixtures/
    backend/
      runner-test-run-config.single.json
      runner-test-run-config.comparison.json
      runner-runtime-secrets.json
      runner-event-ingestion.applied.json
    promptfoo/
      rule-based-only.json
      llm-judge-only.json
      mixed-evaluator.json
      comparison-run.json
    masking/
      sensitive-request.json
      sensitive-response.json
      sensitive-promptfoo-config.json
  unit/
  integration/
```

Keep fixtures sanitized.

Do not commit real responses if they contain internal business-sensitive content.

## Definition Of Done For Runner MVP Tests

Runner implementation is not MVP-ready until:

```text
1. npm run check passes.
2. Backend contract fixtures pass.
3. Promptfoo adapter fixtures pass.
4. Masking tests prove known secret strings do not leak.
5. Event idempotency tests pass.
6. Cancellation tests pass.
7. Timeout tests pass.
8. Redis command consume tests pass.
9. Redis event sink tests pass.
10. Local smoke test plan is documented, even if not automated yet.
```
