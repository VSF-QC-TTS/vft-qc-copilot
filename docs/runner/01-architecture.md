# Runner Architecture

## Purpose

The VFQC Runner is a standalone Node.js worker service that executes TestRuns created by the Java backend.

The runner wraps Promptfoo and converts locked backend run snapshots into executable Promptfoo configs.

The backend remains the source of truth.

## High-Level Architecture

```text
Backend Java
  ├── Creates TestRun
  ├── Locks dataset/target/evaluator/provider snapshots
  ├── Publishes START_RUN / CANCEL_RUN commands
  ├── Serves internal runner APIs
  ├── Persists runner events/results
  └── Pushes SSE events to frontend

Redis Streams
  └── vfqc:runs:commands

Runner Node.js
  ├── Consumes commands
  ├── Fetches locked run config
  ├── Resolves runtime secrets
  ├── Builds Promptfoo config
  ├── Executes Promptfoo Node API
  ├── Normalizes results
  ├── Sends runner events to backend
  ├── Polls cancellation state
  └── Writes sanitized local artifacts

Promptfoo
  └── Execution engine only

Frontend
  └── Subscribes to backend SSE, never to runner directly
```

## Source Of Truth

Source of truth:

```text
Backend database and locked TestRun config
```

Generated artifacts:

```text
Promptfoo config
Promptfoo results
Runner logs
HTML/JSON/JSONL reports
```

The runner must not read mutable project state from public APIs after a run starts.

For every run, the runner should use:

```text
GET /internal/runner/test-runs/{testRunId}/config
```

This config is locked for the run and includes:

```text
testRunId
runType
status
dataset/schema ids and versions
domainSnapshot
promptfooSnapshot
targets
evaluators
rows
```

## MVP Transport

### Command Transport

Current command transport:

```text
Backend -> Redis Stream vfqc:runs:commands -> Runner
```

Command payloads contain references only:

```text
commandId
testRunId
commandType
createdAt
```

The command does not contain:

```text
dataset rows
target config
evaluator config
secret values
Promptfoo config
raw responses
artifacts
```

### Event Transport

Default runner v1 event transport:

```text
Runner -> POST /internal/runner/events -> Backend
```

Reason:

```text
1. The backend currently exposes a clear HTTP event ingestion contract.
2. HTTP ingestion returns backend-assigned sequenceNo.
3. Backend persists RunnerEvent before SSE broadcast.
4. Full Redis event output has to be verified against actual backend event consumer code first.
```

### Redis Event Transport Readiness

The PRD target architecture includes:

```text
Runner -> Redis Stream vfqc:runs:events -> Backend event consumer
```

Do not assume this is fully supported for the standalone runner.

Implementation note checked against backend code on 2026-07-09:

```text
RunnerRedisEventConsumerImpl exists for vfqc:runs:events using consumer group
vfqc-api and consumer api-runner-events. It maps Redis fields into
RunnerEventRequest and has retry/DLQ behavior. RunnerRedisEventConsumerScheduler
can poll it in production when vfqc.runner.redis-events.enabled=true. This is
disabled by default, so Redis event output should remain opt-in for runner v1.
```

Before enabling Redis event output, inspect backend code for:

```text
1. A Redis event consumer reading vfqc:runs:events.
2. Mapping from Redis event fields to RunnerEventRequest-equivalent payload.
3. Idempotency behavior by eventId.
4. State transition behavior for RUN_STARTED/RUN_COMPLETED/RUN_FAILED/RUN_CANCELLED.
5. TestCaseResult upsert behavior for TESTCASE_COMPLETED/TESTCASE_ERROR.
6. ACK after DB persistence.
7. Pending recovery with XPENDING/XAUTOCLAIM.
8. DLQ behavior for event messages.
9. Tests covering the Redis event path.
```

If all are supported, implement:

```text
RedisRunnerEventSink
```

If not, keep:

```text
HttpRunnerEventSink
```

The rest of the runner should not care which transport is used.

## Core Components

Recommended components:

```text
RunnerService
  Owns command execution lifecycle.

RunnerInstanceService
  Registers runner and stores runner instance identity.

HeartbeatService
  Sends IDLE/BUSY heartbeat.

CommandConsumer
  Reads START_RUN/CANCEL_RUN from Redis.

PendingRecovery
  Reclaims stale pending Redis commands if enabled.

BackendClient
  Calls internal backend APIs.

RunnerEventSink
  Sends events through HTTP or Redis.

PromptfooAdapter
  Converts locked backend config to Promptfoo input and normalizes output.

PromptfooConfigBuilder
  Builds executable Promptfoo TestSuiteConfiguration.

PromptfooExecutor
  Executes Promptfoo Node API.

PromptfooResultNormalizer
  Converts raw Promptfoo results to VFQC normalized case results.

SensitiveDataMasker
  Sanitizes request/response/config/log/artifact data.

ArtifactWriter
  Writes sanitized files and metadata.

CancellationService
  Polls backend control state and exposes AbortSignal-like cancellation.
```

## Runner Lifecycle

Recommended MVP lifecycle:

```text
1. Boot process.
2. Load and validate env config.
3. Create logger.
4. Connect to Redis.
5. Register runner instance with backend.
6. Start heartbeat loop as IDLE.
7. Start command consumer.
8. Receive START_RUN.
9. Mark heartbeat BUSY with currentCommandId/currentTestRunId.
10. Fetch locked run config from backend.
11. Extract referenced secret ids from locked snapshots.
12. Resolve runtime secrets through backend.
13. Create run working directory.
14. Build Promptfoo config.
15. Write sanitized promptfoo config artifact if artifact writing is enabled.
16. Send RUN_STARTED.
17. Execute Promptfoo.
18. For each testcase/target result:
    - check cancellation
    - normalize result
    - sanitize payload
    - send TESTCASE_COMPLETED or TESTCASE_ERROR
    - send RUN_PROGRESS periodically
19. On successful completion, send RUN_COMPLETED.
20. On graceful cancellation, send RUN_CANCELLED.
21. On unrecoverable system failure, send RUN_FAILED.
22. Clear runtime secrets from references as much as practical.
23. Mark heartbeat IDLE.
24. Wait for next command.
```

## One Runner, One Active Run

MVP rule:

```text
1 runner instance = 1 active TestRun
```

Reasons:

```text
1. Easier memory control.
2. Easier cancellation.
3. Easier artifact isolation.
4. Easier debugging.
5. Horizontal scaling is simple: run more runner instances.
```

Do not implement multiple active TestRuns in one runner process for MVP.

## Locked Config Flow

```text
START_RUN command
  -> getTestRunConfig(testRunId)
  -> validate status is executable
  -> extract target/evaluator/row snapshots
  -> resolve secrets referenced by snapshots
  -> build Promptfoo config
  -> run Promptfoo
```

The runner must not call:

```text
/api/v1/projects/**
/api/v1/targets/**
/api/v1/evaluators/**
/api/v1/datasets/**
```

during execution.

## Secret Flow

Runtime secret flow:

```text
1. Locked config contains secret references only.
2. Runner extracts referenced secret ids.
3. Runner calls POST /internal/runner/test-runs/{testRunId}/runtime-secrets.
4. Backend returns plaintext secret values only for authorized secret ids.
5. Runner uses secret values in memory.
6. Runner does not log/persist/emit returned values.
7. Runner sanitizes any request/config/result before logs/events/artifacts.
```

Runtime secrets are memory-only.

## Cancellation Flow

Cancellation has two signals:

```text
1. CANCEL_RUN command from Redis.
2. Backend control-state polling.
```

Runner should poll:

```text
GET /internal/runner/test-runs/{testRunId}/control-state
```

during long runs.

Recommended check points:

```text
before run starts
before each testcase
after each testcase
before each target call
before each LLM judge call
before writing final artifacts
before final event
```

If cancellation is requested and the runner can stop safely:

```text
send RUN_CANCELLED
```

If the work already completed before cancellation was observed:

```text
send RUN_COMPLETED
```

The backend allows completion after cancellation request if Promptfoo already finished.

## Result Flow

For each testcase/target result:

```text
Promptfoo raw output
  -> PromptfooResultNormalizer
  -> PromptfooCaseResult
  -> SensitiveDataMasker
  -> RunnerEventRequest(TESTCASE_COMPLETED or TESTCASE_ERROR)
  -> RunnerEventSink
  -> Backend persistence
  -> Backend SSE projection
```

Required identifiers for result events:

```text
testRunId
testRunTargetId
datasetRowId
eventId
eventType
```

`testRunTargetId` comes from:

```text
TargetSnapshot.id
```

`datasetRowId` comes from:

```text
DatasetRowSnapshot.id
```

## Event Idempotency

Every emitted event must have a stable idempotency id.

Use a new event id for each logical event.

Do not reuse the same event id for a different event.

If retrying the exact same event after a transient failure, reuse the same event id.

Recommended event id strategy:

```text
RUN_STARTED:
  random UUID generated once per command execution

TESTCASE_COMPLETED:
  deterministic or stored id based on testRunId + testRunTargetId + datasetRowId + attempt
  OR generated UUID retained for retry

RUN_COMPLETED/RUN_FAILED/RUN_CANCELLED:
  random UUID generated once per final transition
```

Avoid generating a new event id on every HTTP retry for the same logical event.

## Artifact Flow

Artifact output is useful but backend support is currently incomplete.

Current backend gap:

```text
No dedicated artifact upload/metadata endpoint is currently implemented.
```

Therefore:

```text
1. Runner may write sanitized local artifacts.
2. Runner may emit ARTIFACT_CREATED as metadata-only.
3. Runner must not assume artifacts are downloadable from backend.
4. If downloadable Promptfoo reports are required, design backend artifact ingestion first.
```

Default local path:

```text
./storage/artifacts/test-runs/{testRunId}/
```

Expected files:

```text
metadata.json
promptfooconfig.json
results.json
results.jsonl
report.html
runner.log
```

All artifact files must be sanitized before writing or exposing.

## Failure Categories

Runner should classify failures into at least:

```text
CONFIG_ERROR
BACKEND_UNAVAILABLE
REDIS_UNAVAILABLE
PROMPTFOO_EXECUTION_ERROR
TARGET_REQUEST_ERROR
JUDGE_ERROR
ARTIFACT_WRITE_ERROR
CANCELLED
UNKNOWN
```

Mapping:

```text
Single testcase/target execution problem:
  TESTCASE_ERROR

Business/evaluator failure:
  TESTCASE_COMPLETED with status FAILED

Whole run unrecoverable system failure:
  RUN_FAILED

Graceful cancel:
  RUN_CANCELLED
```

A chatbot answer failing evaluation is not a system failure.

## Backend Gaps To Preserve

Do not hide these gaps in docs or implementation:

```text
1. No dedicated backend artifact upload/metadata endpoint is currently implemented.
2. No explicit command acknowledgement endpoint is currently implemented.
3. Redis command consumer for the standalone runner is not implemented in this repository yet.
4. Exact Promptfoo adapter schema is not finalized.
5. Large dataset row paging may need a more explicit runner row-page contract.
6. Redis event output should be verified before being used as the default event path.
```
