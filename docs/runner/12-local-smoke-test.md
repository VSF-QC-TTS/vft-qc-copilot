# Local Runner Smoke Test

This document defines the local end-to-end smoke path for the standalone runner.

It is intentionally a manual plan for now. Do not add it to `npm run check` until the local backend/Redis smoke environment and a non-test seed command are stable.

## Scope

The smoke path verifies:

```text
1. Backend can create and publish one START_RUN command.
2. Runner can consume the command from Redis.
3. Runner can fetch locked config and runtime secrets from backend internal APIs.
4. Runner can emit RUN_STARTED, one testcase event, and RUN_COMPLETED.
5. Backend Redis event worker can persist events.
6. Backend SSE replay can project persisted events.
```

The smoke path must not require:

```text
1. Real LLM keys.
2. VinFast internal network.
3. External chatbot endpoints.
4. Unsanitized artifact or raw payload persistence.
```

## Current Automation Status

Current status:

```text
Manual plan only. Runner runtime loop, Redis event publisher, and backend Testcontainers seed fixture are implemented, but this is not yet an automated local smoke command.
```

Known blockers before automation:

```text
1. A stable non-test seed command or dev-only seed flow for local backend is not implemented yet.
2. Local smoke still requires a running backend, PostgreSQL, Redis, and a Redis inspection tool.
3. Promptfoo execution is wired through the library executor; keep the smoke fixture deterministic and allow TESTCASE_ERROR for closed local targets.
```

Because of these blockers, do not add `npm run test:smoke` yet.

## Prerequisites

Run backend dependencies locally:

```text
PostgreSQL
Redis
Backend API
```

Runner local config should match:

```text
VFQC_BACKEND_BASE_URL=http://localhost:8080
VFQC_RUNNER_TOKEN=dev-runner-token
VFQC_REDIS_URL=redis://localhost:6379
VFQC_REDIS_COMMAND_STREAM=vfqc:runs:commands
VFQC_REDIS_EVENTS_STREAM=vfqc:runs:events
VFQC_EVENT_TRANSPORT=redis
```

Backend Redis event worker should be enabled:

```text
VFQC_RUNNER_REDIS_EVENTS_ENABLED=true
```

## Fixture Requirements

Use a fixture TestRun with:

```text
1. One dataset row.
2. One CHATBOT_TARGET or local fake target.
3. One deterministic rule-based evaluator.
4. No real external API key.
5. No real customer data.
6. Runtime secret value set to a fake string if the target config references a secret.
```

Acceptable fake secret:

```text
vfqc_secret_1234567890abcdef
```

The fake secret must not appear in:

```text
1. Redis command payload.
2. Redis event payload.
3. Backend runner_events payload.
4. SSE replay payload.
5. Runner logs.
```

## Manual Smoke Steps

1. Start Redis locally.

```bash
docker run --rm -p 6379:6379 redis:7-alpine
```

2. Start backend locally with PostgreSQL and Redis configured.

```bash
cd apps/api
rtk bash mvnw spring-boot:run
```

3. Confirm backend internal runner auth accepts the local runner token.

```bash
rtk curl -i \
  -X POST \
  -H "Authorization: Bearer dev-runner-token" \
  -H "Content-Type: application/json" \
  -d '{"runnerKey":"runner-local-smoke","hostname":"localhost","version":"smoke"}' \
  http://localhost:8080/internal/runner/instances/register
```

Expected:

```text
2xx response for runner token.
```

4. Create or seed one minimal TestRun through an existing backend-supported path.

Use the frontend/API flow available at the time of the smoke. The backend test fixture `RunnerSmokeSeedFixture` verifies the required shape under Testcontainers, but it is not a local HTTP seed endpoint. The result must publish one `START_RUN` command to `vfqc:runs:commands`.

5. Inspect Redis command stream.

```bash
redis-cli XRANGE vfqc:runs:commands - +
```

Expected command fields:

```text
commandId
testRunId
commandType=START_RUN
createdAt
```

The command payload must not contain config, dataset rows, URLs, raw cURL, or plaintext secrets.

6. Start the runner after the runtime loop and Redis event publisher are wired.

```bash
cd apps/runner
rtk npm run dev
```

7. Inspect Redis event stream.

```bash
redis-cli XRANGE vfqc:runs:events - +
```

Expected event order:

```text
RUN_STARTED
TESTCASE_COMPLETED or TESTCASE_ERROR
RUN_COMPLETED
```

8. Confirm backend persists projected runner events.

Use backend-supported query/API/SSE tooling for the test run.

Expected persisted sequence:

```text
1 RUN_STARTED
2 TESTCASE_COMPLETED or TESTCASE_ERROR
3 RUN_COMPLETED
```

9. Confirm SSE replay can return the persisted events.

```text
GET /api/v1/test-runs/{testRunId}/events
```

Expected:

```text
SSE event ids are backend runner_events.sequence_no values.
No fake secret or raw external payload appears in replay.
```

## Pass Criteria

The smoke passes only if:

```text
1. One START_RUN command is consumed.
2. Runner sends RUN_STARTED.
3. Runner sends exactly one testcase result event for the fixture row.
4. Runner sends RUN_COMPLETED or RUN_FAILED for a controlled negative fixture.
5. Redis events are consumed by backend worker.
6. Backend stores events in sequence order.
7. SSE replay returns the same persisted sequence.
8. No plaintext runtime secret appears in Redis, DB payload, SSE, or logs.
```

## Failure Triage

If the command remains pending:

```text
1. Check runner process is running.
2. Check Redis command stream and consumer group.
3. Check runner internal token and backend URL.
```

If events remain in Redis but do not appear in SSE:

```text
1. Check VFQC_RUNNER_REDIS_EVENTS_ENABLED=true.
2. Check backend RunnerRedisEventWorker logs.
3. Check vfqc:runs:events DLQ.
```

If a secret appears anywhere outside runtime secret response handling:

```text
Stop the smoke, delete local data, and fix masking before continuing.
```
