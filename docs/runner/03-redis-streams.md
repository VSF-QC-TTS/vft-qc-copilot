# Redis Streams For Runner

This document describes how the standalone runner should use Redis Streams.

The current MVP recommendation is:

```text
Commands: Backend -> Redis -> Runner
Events:   Runner -> Backend HTTP by default
```

Redis event output is a target architecture from the PRD, but it must be enabled only after verifying backend support end-to-end.

## Streams

Known stream names:

```text
vfqc:runs:commands
vfqc:runs:events
vfqc:runs:commands:dlq
vfqc:runs:events:dlq
```

Known consumer groups:

```text
commands stream group: vfqc-runners
events stream group:   vfqc-api
events consumer name:  api-runner-events
```

Runner v1 must consume:

```text
vfqc:runs:commands
```

Runner v1 should not publish to `vfqc:runs:events` unless backend Redis event polling is explicitly enabled.

## What XADD Means

`XADD` appends a message to a Redis Stream.

Example command published by backend:

```bash
XADD vfqc:runs:commands * \
  commandId "7b6d..." \
  testRunId "34bf..." \
  commandType "START_RUN" \
  createdAt "2026-07-09T10:00:00+07:00"
```

Meaning:

```text
XADD                  append message
vfqc:runs:commands    stream name
*                     let Redis generate the stream message id
commandId ...         field/value pairs
```

The generated Redis stream id looks like:

```text
1752000000000-0
```

This id is the transport message id. It is not the same as the domain `commandId`.


## Node Redis Client Boundary

Runner v1 uses the `redis` package by default. The package supports the Redis Streams operations needed by the runner.

The runner should wrap Redis calls behind internal methods instead of spreading client calls through business logic:

```ts
export interface RedisCommandClient {
  readNextCommand(): Promise<RunnerCommandDelivery | null>;
  ackCommand(messageId: string): Promise<void>;
  claimStaleCommands(): Promise<RunnerCommandDelivery[]>;
}
```

Required Redis operations behind this boundary:

```text
XGROUP CREATE  ensure consumer group exists
XREADGROUP     read new commands
XACK           acknowledge processed commands
XPENDING       inspect stale pending messages
XAUTOCLAIM     reclaim stale pending messages
XADD           publish to DLQ or future Redis event stream if enabled
```

If a typed helper is unavailable or changes between package versions, use `sendCommand` only inside the Redis adapter. Do not leak raw Redis command arrays into runner services.

## Command Message Shape

Runner should parse Redis fields into:

```ts
export interface RunnerCommandMessage {
  commandId: string; // UUID
  testRunId: string; // UUID
  commandType: "START_RUN" | "CANCEL_RUN";
  createdAt: string; // ISO offset date-time
}
```

Rules:

```text
1. Commands contain references only.
2. Commands do not contain full config.
3. Commands do not contain dataset rows.
4. Commands do not contain secrets.
5. Commands do not contain Promptfoo artifacts.
```

## Consumer Group Read Flow

Runner should read commands using a consumer group.

Conceptual command:

```bash
XREADGROUP GROUP vfqc-runners runner-01 \
  COUNT 1 \
  BLOCK 5000 \
  STREAMS vfqc:runs:commands >
```

Meaning:

```text
GROUP vfqc-runners runner-01  use consumer group and consumer name
COUNT 1                       claim one command at a time
BLOCK 5000                    wait up to 5s
STREAMS ... >                 read new messages never delivered to this group
```

MVP rule:

```text
1 runner instance = 1 active TestRun
```

Therefore `COUNT 1` is the safest default.

## Command Handling Flow

For `START_RUN`:

```text
1. Parse and validate message.
2. Check runner is IDLE.
3. Heartbeat BUSY with currentCommandId/currentTestRunId.
4. Fetch locked config from backend.
5. Resolve runtime secrets.
6. Send RUN_STARTED event.
7. Execute Promptfoo.
8. Send testcase events and progress events.
9. Send final RUN_COMPLETED/RUN_FAILED/RUN_CANCELLED event.
10. XACK Redis command only after final event succeeds.
11. Heartbeat IDLE.
```

For `CANCEL_RUN`:

```text
1. Parse and validate message.
2. If current run matches testRunId, request cancellation locally.
3. Poll backend control-state as the durable source.
4. If no matching active run exists, treat as idempotent/no-op.
5. XACK the cancel command after it is handled or skipped safely.
```

## XACK Rule

`XACK` tells Redis that a stream message was processed by the consumer group.

Runner should acknowledge `START_RUN` only after:

```text
1. The run reaches a terminal state from the runner perspective.
2. Final event was accepted by backend event ingestion.
3. Required sanitized artifacts were written or artifact failure was represented in RUN_FAILED.
```

Runner should not XACK a `START_RUN` immediately after receiving it.

Reason:

```text
If the runner crashes after early XACK, no other runner can reclaim the command.
```

## Backend Command Status Gap

Current handoff says there is no explicit command acknowledgement endpoint.

That means runner can acknowledge Redis transport with `XACK`, but may not be able to mark backend `runner_commands.status = ACKNOWLEDGED` through an HTTP API.

Document this as a backend gap, not a runner invention.

Do not create a fake endpoint like:

```text
POST /internal/runner/commands/{commandId}/ack
```

unless backend actually implements it.

## Pending Recovery

Redis Streams support pending entries.

A message becomes pending when it is delivered to a consumer group but not acknowledged.

Recovery flow:

```text
1. Recovery worker checks pending messages with XPENDING.
2. If a message is idle too long, another runner claims it with XAUTOCLAIM.
3. Claimed command is handled idempotently.
4. If attempts exceed maxAttempts, move to DLQ or report RUN_FAILED according to backend support.
```

Recommended defaults:

```text
pendingIdleThresholdMs = 60_000
recoveryIntervalMs    = 30_000
maxAttempts           = 3
```

## Idempotency Requirements

Redis delivery is at-least-once.

Runner must assume it can see duplicate commands.

Rules:

```text
1. commandId + testRunId identifies a logical command.
2. START_RUN must not create duplicate execution if the run is already terminal.
3. Before executing, fetch backend config/control state and check current status.
4. Event sending must use stable eventId per logical event.
5. Testcase results are backend-upserted by (testRunId, testRunTargetId, datasetRowId).
```

## DLQ Policy

DLQ streams:

```text
vfqc:runs:commands:dlq
vfqc:runs:events:dlq
```

Runner should move or request moving a command to DLQ only after:

```text
1. maxAttempts is exceeded.
2. The failure is not expected to recover by retry.
3. A clear errorCategory/lastError is recorded.
4. RUN_FAILED is sent if the START_RUN cannot be executed.
```

If backend already owns DLQ movement for commands, follow backend implementation instead of duplicating it.

## Event Stream Verification

Before using `vfqc:runs:events` as runner output, verify these backend capabilities:

```text
1. Backend has a consumer group for vfqc:runs:events.
2. Backend maps Redis event message to RunnerEventRequest-equivalent DTO.
3. Backend assigns sequenceNo.
4. Backend persists RunnerEvent before SSE broadcast.
5. Backend applies side effects for RUN_STARTED/RUN_PROGRESS/TESTCASE_COMPLETED/etc.
6. Backend ACKs only after DB persistence succeeds.
7. Backend has pending recovery for events.
8. Backend has DLQ behavior for failed events.
9. Tests cover this Redis event path.
```

Current backend verification note, 2026-07-09:

```text
RunnerRedisEventConsumerImpl exists and reads vfqc:runs:events using group vfqc-api
and consumer api-runner-events. It maps Redis fields into RunnerEventRequest and
ACKs after ingestion or DLQ after retry exhaustion. RunnerRedisEventConsumerScheduler
can poll it in production when vfqc.runner.redis-events.enabled=true. The scheduler
is disabled by default, so HTTP event ingestion remains the standalone runner
default unless Redis event output is explicitly enabled and operated.
```

If any item is missing, keep HTTP event ingestion as default:

```text
POST /internal/runner/events
```

## Safe Trimming

Streams must not grow forever, but trimming must not delete unacknowledged work.

Rules:

```text
1. Do not trim below the oldest pending message id.
2. Prefer approximate max length only when pending safety is understood.
3. If Redis supports ACKED trimming and backend uses it, prefer that for consumer-group streams.
4. If a pending message was trimmed/deleted, record reconcile/DLQ information.
```

For runner v1, do not implement aggressive trimming in the runner unless backend ownership is clear.

## Local Development Notes

A local runner needs:

```text
VFQC_REDIS_URL
VFQC_REDIS_COMMAND_STREAM=vfqc:runs:commands
VFQC_REDIS_COMMAND_GROUP=vfqc-runners
VFQC_REDIS_CONSUMER_NAME=<unique-runner-name>
```

Consumer names should be stable enough for debugging but unique per running process.

Recommended format:

```text
<runnerKey>-<hostname>-<pid>
```
