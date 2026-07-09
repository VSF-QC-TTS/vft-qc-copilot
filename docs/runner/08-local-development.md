# Local Development

This document defines how to scaffold, run, and verify the standalone runner locally.

It intentionally separates:

```text
1. Codex-owned scaffold under strict pinned-version rules.
2. Human review checkpoint after scaffold.
3. Codex/AI-owned implementation work after scaffold approval.
```

This separation prevents an AI agent from inventing project structure, dependency choices, or backend contracts.

## Source Basis

This document is based on:

```text
AI_RUNNER_DTO_HANDOFF.md
API_IMPLEMENTATION_CONTEXT.md
PRD v0.7
Official Promptfoo, Node.js, npm, and Redis docs checked during doc drafting
```

## Local App Location

Runner app path:

```text
apps/runner
```

Backend app path:

```text
apps/api
```

Run backend commands from:

```text
apps/api
```

Run runner commands from:

```text
apps/runner
```

## Codex-Owned Initial Scaffold

Codex may create the initial runner skeleton.

Reason:

```text
Runner does not have an official start.spring.io equivalent.
The skeleton controls package manager, Node version, dependency pinning, TypeScript strictness, folder layout, and scripts.
Because those choices are now fixed in docs, Codex can scaffold them safely.
```

Codex-owned scaffold steps:

```text
1. Create apps/runner directory.
2. Use Node 24 LTS.
3. Create .nvmrc and/or .node-version.
4. Run npm init.
5. Set npm save-exact.
6. Install the exact pinned package versions listed below.
7. Create package-lock.json.
8. Add tsconfig.json.
9. Add .env.example.
10. Add minimal src/main.ts.
11. Add config/env-schema.ts.
12. Add logging/logger.ts.
13. Add empty boundary interfaces for backend, Redis, events, artifacts, masking, and Promptfoo adapter.
14. Run npm run check if the environment supports dependency install.
```

Human review checkpoint after scaffold:

```text
1. Review package.json and package-lock.json.
2. Confirm exact versions are pinned.
3. Confirm no backend code changed.
4. Confirm no unapproved framework was added.
5. Confirm runner execution logic was not implemented during scaffold.
6. Confirm npm run check output or explanation.
```

Codex should not change these decisions unless explicitly asked.

## Codex-Owned Work After Scaffold

After scaffold, Codex may implement:

```text
1. BackendClient based on docs/runner/02-backend-contract.md.
2. Redis CommandConsumer based on docs/runner/03-redis-streams.md.
3. PromptfooAdapter based on docs/runner/04-promptfoo-adapter.md.
4. Event factories and event sink based on docs/runner/05-events-and-results.md.
5. ArtifactWriter and SensitiveDataMasker based on docs/runner/06-artifacts-and-masking.md.
6. Cancellation/retry/timeout behavior based on docs/runner/07-cancel-retry-timeout.md.
7. Tests based on docs/runner/09-testing-contract.md.
```

The local smoke path is documented separately in:

```text
docs/runner/12-local-smoke-test.md
```

Keep it manual until backend seeding and runner runtime wiring are stable.

Codex must not:

```text
1. Modify backend API contracts.
2. Invent new backend endpoints.
3. Change package manager.
4. Replace Node version.
5. Use promptfoo@latest dynamically.
6. Add NestJS/Express/Fastify without explicit approval.
7. Add direct DB access.
8. Log runtime secrets.
```

## Prerequisites

Required locally:

```text
Node.js 24 LTS
npm bundled with Node 24
Docker / Docker Compose if running Redis/backend dependencies locally
Java 21 for backend
Maven wrapper from apps/api
PostgreSQL and Redis through existing backend local setup or Docker Compose
```

Recommended checks:

```bash
node --version
npm --version
docker --version
```

Node should be v24.x.

## Version Pinning Workflow

Use the exact versions below for the initial scaffold.

Last checked with `npm view <package> version`: 2026-07-09 Asia/Bangkok.

Runtime dependencies:

```text
promptfoo  0.121.18
redis      6.1.0
zod        4.4.3
pino       10.3.1
undici     8.7.0
uuid       14.0.1
dotenv     17.4.2
```

Dev dependencies:

```text
typescript  7.0.2
tsx         4.23.0
vitest      4.1.10
oxlint      1.73.0
prettier    3.9.4
@types/node 24.13.3
```

`@types/node` is intentionally pinned to the Node 24 major line because the runner targets Node 24 LTS.

Do not install `promptfoo@latest`.

Do not re-run `npm view` and silently replace these versions during scaffold. If work starts much later, update `10-scaffold-package-versions.md` deliberately first.

Do not commit caret ranges for core dependencies.

## Suggested Scaffold Commands

From repository root:

```bash
mkdir -p apps/runner
cd apps/runner
npm init -y
npm config set save-exact true
```

Create Node version files:

```bash
printf "24\n" > .nvmrc
printf "24\n" > .node-version
```

Install pinned runtime dependencies:

```bash
npm install \
  promptfoo@0.121.18 \
  redis@6.1.0 \
  zod@4.4.3 \
  pino@10.3.1 \
  undici@8.7.0 \
  uuid@14.0.1 \
  dotenv@17.4.2 \
  --save-exact
```

Install pinned dev dependencies:

```bash
npm install -D \
  typescript@7.0.2 \
  tsx@4.23.0 \
  vitest@4.1.10 \
  oxlint@1.73.0 \
  prettier@3.9.4 \
  @types/node@24.13.3 \
  --save-exact
```

After install:

```bash
npm pkg set type=module
npm pkg set scripts.dev="tsx watch src/main.ts"
npm pkg set scripts.build="tsc -p tsconfig.json"
npm pkg set scripts.start="node dist/main.js"
npm pkg set scripts.typecheck="tsc -p tsconfig.json --noEmit"
npm pkg set scripts.lint="oxlint ."
npm pkg set scripts.lint:fix="oxlint . --fix"
npm pkg set scripts.format="prettier --write ."
npm pkg set scripts.test="vitest run"
npm pkg set scripts.test:watch="vitest"
npm pkg set scripts.check="npm run typecheck && npm run lint && npm run test"
```

Review `package.json` manually before commit.

Run `npm audit --audit-level=high` after install. If it reports high/critical issues, stop and report them instead of running automatic fixes that change pinned versions.

## Minimal tsconfig.json

Recommended starting point:

```json
{
  "compilerOptions": {
    "target": "ES2023",
    "lib": ["ES2023"],
    "module": "NodeNext",
    "moduleResolution": "NodeNext",
    "strict": true,
    "noUncheckedIndexedAccess": true,
    "exactOptionalPropertyTypes": true,
    "useUnknownInCatchVariables": true,
    "esModuleInterop": true,
    "forceConsistentCasingInFileNames": true,
    "skipLibCheck": true,
    "rootDir": "src",
    "outDir": "dist"
  },
  "include": ["src/**/*.ts", "test/**/*.ts"]
}
```

## Minimal .env.example

```dotenv
VFQC_RUNNER_KEY=runner-local-01
VFQC_RUNNER_VERSION=0.1.0-local
VFQC_RUNNER_HOSTNAME=local

VFQC_BACKEND_BASE_URL=http://localhost:8080
VFQC_RUNNER_TOKEN=replace-me

VFQC_REDIS_URL=redis://localhost:6379
VFQC_REDIS_COMMAND_STREAM=vfqc:runs:commands
VFQC_REDIS_COMMAND_GROUP=vfqc-runners
VFQC_REDIS_CONSUMER_NAME=runner-local-01
VFQC_REDIS_EVENTS_STREAM=vfqc:runs:events

# Use redis by default for runner-to-backend execution events.
VFQC_EVENT_TRANSPORT=redis

# Backend-side Redis event worker.
VFQC_RUNNER_REDIS_EVENTS_ENABLED=true
VFQC_RUNNER_REDIS_EVENTS_IDLE_SLEEP_MS=100
VFQC_RUNNER_REDIS_EVENTS_ERROR_SLEEP_MS=1000

VFQC_WORK_DIR=./.runner-work
VFQC_ARTIFACTS_DIR=./storage/artifacts/test-runs

VFQC_HEARTBEAT_INTERVAL_MS=10000
VFQC_CONTROL_POLL_INTERVAL_MS=1000
VFQC_COMMAND_BLOCK_MS=5000
VFQC_COMMAND_MAX_ATTEMPTS=3
VFQC_PENDING_IDLE_THRESHOLD_MS=60000
VFQC_RECOVERY_INTERVAL_MS=30000

VFQC_TARGET_REQUEST_TIMEOUT_MS=60000
VFQC_LLM_JUDGE_TIMEOUT_MS=120000
VFQC_TESTCASE_TIMEOUT_MS=180000
VFQC_RUN_TIMEOUT_MS=21600000

VFQC_BACKEND_HTTP_MAX_ATTEMPTS=3
VFQC_EVENT_SEND_MAX_ATTEMPTS=3
VFQC_PROGRESS_EVENT_INTERVAL_MS=1000
VFQC_PROGRESS_EVENT_EVERY_N_CASES=10

VFQC_LOG_LEVEL=info
```

Never commit real runner tokens or secrets.

## Minimal src/main.ts

Initial scaffold can be intentionally small:

```ts
import { loadRunnerConfig } from './config/runner-config.js';
import { createLogger } from './logging/logger.js';

async function main(): Promise<void> {
  const config = loadRunnerConfig(process.env);
  const logger = createLogger(config.logLevel);

  logger.info(
    {
      runnerKey: config.runnerKey,
      version: config.version,
      eventTransport: config.eventTransport,
    },
    'VFQC runner starting',
  );

  // Actual services are implemented after the scaffold is reviewed.
}

main().catch((error: unknown) => {
  console.error(error);
  process.exitCode = 1;
});
```

## Local Backend Verification

Before testing runner integration, verify backend baseline from:

```text
apps/api
```

Command:

```bash
rtk bash mvnw test
```

Useful backend targeted suite:

```bash
rtk bash mvnw -Dtest=Phase9RunnerIntegrationTest,Phase10SseIntegrationTest,Phase11ExportJobIntegrationTest test
```

Expected handoff baseline:

```text
Flyway applies 4 migrations from an empty PostgreSQL Testcontainer schema and all backend tests pass.
```

If backend tests fail, do not debug runner first.

## Local Redis Verification

Use Redis CLI to check stream existence and command delivery.

Example local command:

```bash
redis-cli XINFO STREAM vfqc:runs:commands
```

Manual test command example:

```bash
redis-cli XADD vfqc:runs:commands '*' \
  commandId '00000000-0000-0000-0000-000000000001' \
  testRunId '00000000-0000-0000-0000-000000000002' \
  commandType 'START_RUN' \
  createdAt '2026-07-09T10:00:00+07:00'
```

Do not use manual Redis commands against shared/staging environments unless explicitly approved.

## Event Transport Local Rule

Default:

```text
VFQC_EVENT_TRANSPORT=redis
```

Fallback:

```text
VFQC_EVENT_TRANSPORT=http
```

verify backend supports:

```text
1. Reading vfqc:runs:events.
2. Mapping stream fields to RunnerEventRequest-equivalent payload.
3. Persisting events idempotently.
4. Applying state transitions.
5. ACKing after DB persistence.
6. Recovering pending event messages.
7. DLQ behavior.
8. Tests for the Redis event path.
```

Use HTTP only when explicitly debugging or when Redis event transport is deliberately disabled.

## Development Commands

From `apps/runner`:

```bash
npm run dev
npm run typecheck
npm run lint
npm run test
npm run check
npm run build
npm run start
```

Production-like local run:

```bash
npm ci
npm run build
npm run start
```

## Dockerfile Direction

Use Node 24.

Recommended direction:

```dockerfile
FROM node:24-bookworm-slim AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

FROM deps AS build
COPY tsconfig.json ./
COPY src ./src
RUN npm run build

FROM node:24-bookworm-slim AS runtime
WORKDIR /app
ENV NODE_ENV=production
COPY package.json package-lock.json ./
RUN npm ci --omit=dev
COPY --from=build /app/dist ./dist
CMD ["node", "dist/main.js"]
```

Do not use Alpine by default unless Promptfoo and transitive dependencies are verified on Alpine.

## Local Data Safety

Local files that must not be committed:

```text
.env
.runner-work/
storage/artifacts/
dist/
coverage/
node_modules/
```

Add to `.gitignore` if not already covered.

## Troubleshooting

### Runner cannot register

Check:

```text
VFQC_BACKEND_BASE_URL
VFQC_RUNNER_TOKEN
backend is running
runner token has RUNNER role
```

### Runner sees command but cannot fetch config

Check:

```text
testRunId exists
run is in executable status
runner token is accepted by backend
GET /internal/runner/test-runs/{testRunId}/config works
```

### Runtime secrets fail

Check:

```text
secret ids are extracted from locked snapshots only
secret ids belong to the current run context
runner is not requesting unrelated secret ids
```

Never print secret values while debugging.

### Events do not appear in frontend SSE

Check:

```text
POST /internal/runner/events response status
RunnerEventIngestionResponse.sequenceNo
backend persisted runner event
frontend SSE Last-Event-ID
SSE projection strips unsafe fields by design
```

### Redis event path does not work

Fallback:

```text
VFQC_EVENT_TRANSPORT=http
VFQC_RUNNER_REDIS_EVENTS_ENABLED=false
```

Then retry Redis event transport with runner `VFQC_EVENT_TRANSPORT=redis`
and backend `VFQC_RUNNER_REDIS_EVENTS_ENABLED=true`.

## Pre-Implementation Checklist

Before Codex implements runner logic:

```text
1. apps/runner scaffold exists.
2. package-lock.json exists.
3. Node version file exists.
4. npm run check passes on empty scaffold.
5. Backend tests pass.
6. AI_RUNNER_DTO_HANDOFF.md has been checked against actual backend code.
7. docs/runner/02-backend-contract.md matches actual backend DTOs.
8. Event transport decision is explicit: http or redis.
9. No real secrets are stored in .env.example.
```


## Sliced Local Development Workflow

Do not implement the full runner in one pass.

Use this workflow after the scaffold exists:

```text
1. Pick exactly one slice from docs/runner/11-sliced-implementation-plan.md.
2. Read only the docs relevant to that slice.
3. Implement only the boundary/component for that slice.
4. Add tests for that slice immediately.
5. Run npm run check.
6. Run npm run build if source files changed.
7. Stop and review the diff before continuing.
```

Recommended command after each slice:

```bash
npm run check && npm run build
```

If the slice touches dependency installation or package scripts, also run:

```bash
npm audit --audit-level=high
```

If Redis integration tests are added, keep them separate from fast unit tests unless local Redis/Testcontainers is guaranteed.

Recommended script split:

```json
{
  "test": "vitest run",
  "test:unit": "vitest run test/unit",
  "test:integration": "vitest run test/integration",
  "test:contract": "vitest run test/contract"
}
```

Fast checks should not require real external LLM keys, real chatbot endpoints, or VinFast internal network access.

## Human Review Checkpoints

Human review is recommended after:

```text
1. Scaffold.
2. BackendClient + DTO validators.
3. Redis command consumer.
4. Promptfoo adapter fixtures.
5. Runner lifecycle orchestration.
6. Before enabling any real external chatbot/LLM call.
```

Review checklist:

```text
1. No backend code changed unexpectedly.
2. No new dependency added without reason.
3. Tests exist for the current slice.
4. npm run check passed.
5. No secret value appears in tests, logs, fixtures, or artifacts.
6. Event transport is still HTTP by default unless Redis support was verified.
```
