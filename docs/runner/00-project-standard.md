# Runner Project Standard

This document defines the standard project shape for the standalone VFQC Runner.

The goal is to prevent AI/codegen tools from creating an unstructured Node.js worker from an empty folder.

## Responsibility Split

This section is intentionally explicit so initial project setup is not mixed with runner business logic.

The preferred workflow is now:

```text
1. Codex may scaffold apps/runner using the exact commands and pinned versions in this doc.
2. Human reviews package.json, package-lock.json, folder structure, scripts, and npm run check output.
3. Only after that review should Codex implement runner logic.
```

### Codex-owned scaffold, with strict constraints

Codex/AI may create the initial runner skeleton if it follows this document exactly.

Codex-owned scaffold work:

```text
1. Create apps/runner.
2. Use Node.js 24 LTS.
3. Use npm + package-lock.json.
4. Run npm config set save-exact true.
5. Install the exact pinned dependency versions listed in this document.
6. Commit/create package-lock.json as part of the scaffold diff.
7. Create tsconfig.json.
8. Create .env.example.
9. Create package scripts.
10. Create empty src/test folder structure.
11. Create minimal src/main.ts that boots config/logging only.
12. Create boundary stubs/interfaces only; do not implement full runner logic yet.
13. Run npm run check on the skeleton if the environment supports it.
```

### Human-owned checkpoint after scaffold

The repository owner should review the skeleton before implementation begins.

Human review checklist:

```text
1. package manager is npm.
2. package-lock.json exists.
3. dependency versions are exact, not caret/range/latest.
4. Node target is 24 LTS.
5. no web framework was added by default.
6. folder structure matches this doc.
7. package scripts are present.
8. npm run check passes or any failure is clearly explained.
9. no backend code was modified.
10. no runner business logic was implemented during scaffold unless explicitly requested.
```

### Codex-owned after scaffold approval

Codex/AI may do these after the skeleton exists and is accepted:

```text
1. Fill backend DTOs from the documented contract and actual Java DTOs.
2. Implement BackendClient.
3. Implement Redis CommandConsumer.
4. Implement RunnerEventSink variants.
5. Implement runner lifecycle.
6. Implement PromptfooAdapter.
7. Implement masking/artifact helpers.
8. Add unit and integration tests.
9. Update docs if actual backend code differs.
```

### Codex must not do during scaffold

```text
1. Do not choose a different package manager.
2. Do not introduce NestJS/Express/Fastify by default.
3. Do not change backend API contracts.
4. Do not invent artifact upload endpoints.
5. Do not use floating dependency versions.
6. Do not install promptfoo@latest.
7. Do not install caret dependency ranges.
8. Do not implement full runner execution logic before the skeleton is verified.
```

## App Location

The runner app should live at:

```text
apps/runner
```

Recommended root layout:

```text
apps/runner/
  package.json
  package-lock.json
  tsconfig.json
  .env.example
  Dockerfile
  README.md

  src/
    main.ts

    config/
      env-schema.ts
      runner-config.ts

    backend/
      backend-client.ts
      dto/
        runner-api.dto.ts

    redis/
      redis-client.ts
      command-consumer.ts
      command-message.ts
      event-stream-publisher.ts
      pending-recovery.ts
      stream-names.ts

    runner/
      runner-service.ts
      runner-instance.service.ts
      heartbeat.service.ts
      cancellation.service.ts
      execution-context.ts

    promptfoo/
      promptfoo-adapter.ts
      promptfoo-config-builder.ts
      promptfoo-executor.ts
      promptfoo-result-normalizer.ts

    events/
      runner-event-sink.ts
      http-runner-event-sink.ts
      redis-runner-event-sink.ts
      runner-event-factory.ts
      normalized-events.ts

    artifacts/
      artifact-writer.ts
      artifact-paths.ts
      artifact-metadata.ts
      hash-util.ts

    masking/
      sensitive-data-masker.ts
      masking-policy.ts

    errors/
      runner-error.ts
      error-classifier.ts

    logging/
      logger.ts

    utils/
      json.ts
      sleep.ts
      time.ts

  test/
    unit/
    integration/
    fixtures/
```

## Runtime Stack

Use:

```text
Node.js 24 LTS
TypeScript
npm
package-lock.json
Promptfoo Node API
Redis Streams
Backend internal HTTP APIs
```

Do not use a full web framework by default.

The runner is a worker service, not a public HTTP API. Avoid NestJS, Express, or Fastify until there is a real need for health/metrics endpoints.

## Node Version

Use Node.js 24 LTS.

Recommended files:

```text
.nvmrc
.node-version
```

Content:

```text
24
```

Recommended `package.json` engine:

```json
{
  "engines": {
    "node": ">=24 <25",
    "npm": ">=11"
  }
}
```

Docker image should be pinned by the repository owner. Prefer Debian slim for fewer native dependency surprises:

```dockerfile
FROM node:24-bookworm-slim
```

Avoid Alpine by default unless Promptfoo and all native/transitive dependencies have been verified on Alpine.

## Package Manager

Use npm and commit `package-lock.json`.

Required conventions:

```text
1. Use npm, not pnpm/yarn/bun, unless the repository standard changes.
2. Commit package-lock.json.
3. Use npm ci in CI/Docker.
4. Use exact dependency versions.
5. Do not use promptfoo@latest in package.json.
```

Set exact-save before installing:

```bash
npm config set save-exact true
```

## Dependency Version Policy

This scaffold uses exact pinned versions resolved at the start of runner work.

Last checked with `npm view <package> version`: 2026-07-09 Asia/Bangkok.

Pinned runtime dependencies:

```text
promptfoo  0.121.18
redis      6.1.0
zod        4.4.3
pino       10.3.1
undici     8.7.0
uuid       14.0.1
dotenv     17.4.2
```

Pinned dev dependencies:

```text
typescript  7.0.2
tsx         4.23.0
vitest      4.1.10
oxlint      1.73.0
prettier    3.9.4
@types/node 24.13.3
```

Important note about `@types/node`:

```text
npm latest for @types/node was 26.1.1, but the runner runtime target is Node 24 LTS.
Therefore this scaffold intentionally pins the newest observed Node 24 type package, @types/node@24.13.3, instead of @types/node@latest.
```

Codex should use these exact install commands for the first scaffold:

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

npm install -D \
  typescript@7.0.2 \
  tsx@4.23.0 \
  vitest@4.1.10 \
  oxlint@1.73.0 \
  prettier@3.9.4 \
  @types/node@24.13.3 \
  --save-exact
```

Rules:

```text
1. Do not use `latest` in package.json.
2. Do not use caret ranges such as ^0.121.18.
3. Do not use npm `next`, `beta`, `canary`, or nightly tags.
4. Commit package-lock.json.
5. Use npm ci in CI/Docker.
6. Run npm audit after scaffold.
7. If a pinned version fails build/test, document the failure before changing it.
8. If work starts much later, re-run npm view and update this section deliberately, not implicitly.
```

Optional verification commands before install:

```bash
npm view promptfoo version
npm view redis version
npm view zod version
npm view pino version
npm view undici version
npm view uuid version
npm view dotenv version
npm view typescript version
npm view tsx version
npm view vitest version
npm view oxlint version
npm view prettier version
npm view @types/node@24 version
```

Immediate validation after install:

```bash
npm run typecheck
npm run lint
npm run test
npm audit --audit-level=high
```

If `npm audit` reports high/critical issues, stop and report them. Do not run automatic fixes that change pinned versions unless the repository owner approves the dependency change.

If a pinned version is incompatible, do not silently downgrade. Record the issue in a dependency decision note and pin the newest working version.

Suggested decision note path:

```text
docs/runner/dependency-decisions.md
```

## Redis Client Choice

Use the `redis` package by default for new runner code.

Reason:

```text
1. It is the recommended modern Node Redis client for new Node.js projects.
2. It supports Redis Streams commands needed by the runner, including XADD, XREADGROUP, XACK, and XAUTOCLAIM.
3. It avoids adding ioredis-specific abstractions unless the runner needs Sentinel/Cluster behavior later.
```

Keep Redis access behind runner-owned wrappers such as `RedisCommandConsumer` and `RedisEventPublisher`. Business logic must not depend on node-redis-specific method names.

If deployment requires Redis Sentinel/Cluster features that are easier with ioredis, document that as a separate dependency decision before switching. The switch should affect only Redis adapter files, not runner lifecycle code.

## Promptfoo Version Pinning

Promptfoo must be pinned exactly.

For this scaffold, use the pinned version from `docs/runner/10-scaffold-package-versions.md`:

```bash
npm install promptfoo@0.121.18 --save-exact
```

Do not let Codex silently re-resolve Promptfoo. If work starts much later, update `10-scaffold-package-versions.md` deliberately first, then scaffold from that updated doc.

Do not write:

```json
{
  "dependencies": {
    "promptfoo": "latest"
  }
}
```

Do not write:

```json
{
  "dependencies": {
    "promptfoo": "^0.121.17"
  }
}
```

Promptfoo upgrade rule:

```text
1. Upgrade in a separate branch.
2. Run PromptfooAdapterContract regression fixtures.
3. Compare normalized VFQC result schema, not raw Promptfoo internals blindly.
4. If Promptfoo raw output changes, adapt PromptfooAdapter first.
5. Do not change backend domain model unless absolutely required.
```

## TypeScript Standard

Use TypeScript for runner source.

Recommended `tsconfig.json` direction:

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

Use ESM with `NodeNext`.

If a dependency causes ESM/CJS friction, solve it at the adapter boundary instead of changing the whole project style.

## Package Scripts

Recommended `package.json` scripts:

```json
{
  "scripts": {
    "dev": "tsx watch src/main.ts",
    "build": "tsc -p tsconfig.json",
    "start": "node dist/main.js",
    "typecheck": "tsc -p tsconfig.json --noEmit",
    "lint": "oxlint .",
    "lint:fix": "oxlint . --fix",
    "format": "prettier --write .",
    "test": "vitest run",
    "test:watch": "vitest",
    "check": "npm run typecheck && npm run lint && npm run test"
  }
}
```

Production should run:

```bash
npm ci
npm run build
npm run start
```

Do not run `tsx` or TypeScript source directly in production.

## Formatting And Spacing Style

Use Prettier as the formatting source of truth.

Recommended `.prettierrc.json`:

```json
{
  "printWidth": 100,
  "singleQuote": true,
  "semi": true,
  "trailingComma": "all"
}
```

Do not manually enforce arbitrary two-line gaps everywhere.

Spacing rule:

```text
1. One blank line between imports, type declarations, functions, and classes is enough.
2. Use an extra blank line only to separate distinct logical sections in long files.
3. Let Prettier format line wrapping and whitespace.
4. Keep files small enough that spacing does not become the main readability tool.
```

## Code Style: Clean Code, OOP, SOLID, DRY

The runner should follow clean code principles, but avoid cargo-cult architecture.

Required style:

```text
1. Keep functions focused.
2. Keep classes small and named by responsibility.
3. Prefer constructor injection for dependencies.
4. Depend on interfaces at module boundaries.
5. Keep DTOs immutable/read-only where practical.
6. Validate external inputs at boundaries.
7. Keep secrets/masking centralized.
8. Avoid god services.
9. Avoid N+1-like loops over backend calls.
10. Keep errors typed/classified.
```

SOLID usage should be practical:

```text
Single Responsibility:
  BackendClient calls backend only.
  CommandConsumer reads Redis only.
  PromptfooAdapter maps Promptfoo only.
  SensitiveDataMasker masks only.

Open/Closed:
  Add RedisRunnerEventSink without rewriting RunnerService.

Liskov:
  Event sink implementations must behave consistently from RunnerService's perspective.

Interface Segregation:
  Do not create one huge InfrastructureClient for backend, Redis, artifacts, and Promptfoo.

Dependency Inversion:
  RunnerService depends on BackendClient/EventSink/PromptfooAdapter abstractions, not raw fetch/redis/promptfoo calls.
```

DRY usage should be balanced:

```text
1. Centralize shared DTOs and event factories.
2. Centralize masking policy.
3. Centralize retry/backoff helpers.
4. Do not abstract two pieces of code just because they look similar once.
5. Prefer duplication over premature generic frameworks.
```

## Design Patterns Allowed

Use patterns only where they solve current runner boundaries.

Recommended patterns:

```text
Adapter:
  PromptfooAdapter maps VFQC locked config to Promptfoo config/results.

Strategy:
  RunnerEventSink chooses HTTP or Redis event output.

Factory:
  RunnerEventFactory creates valid RunnerEventRequest objects.

Repository-like wrapper:
  BackendClient wraps backend HTTP calls, but it is not a database repository.

Policy object:
  MaskingPolicy defines sensitive field matching rules.
```

Avoid:

```text
1. AbstractFactory/Builder layers unless needed.
2. Enterprise-style inheritance hierarchies.
3. A generic event bus inside the runner before there is a real use case.
4. Multiple implementations of every service just for “SOLID”.
```

## Configuration Standard

Environment variables should be parsed once at startup.

Use:

```text
src/config/env-schema.ts
src/config/runner-config.ts
```

Suggested env names:

```text
VFQC_RUNNER_KEY
VFQC_RUNNER_VERSION
VFQC_RUNNER_HOSTNAME

VFQC_BACKEND_BASE_URL
VFQC_RUNNER_TOKEN

VFQC_REDIS_URL
VFQC_REDIS_COMMAND_STREAM
VFQC_REDIS_COMMAND_GROUP
VFQC_REDIS_CONSUMER_NAME
VFQC_REDIS_EVENTS_STREAM

VFQC_EVENT_TRANSPORT
VFQC_WORK_DIR
VFQC_ARTIFACTS_DIR

VFQC_HEARTBEAT_INTERVAL_MS
VFQC_CONTROL_POLL_INTERVAL_MS
VFQC_COMMAND_BLOCK_MS
VFQC_COMMAND_MAX_ATTEMPTS
VFQC_PENDING_IDLE_THRESHOLD_MS
VFQC_RECOVERY_INTERVAL_MS

VFQC_LOG_LEVEL
```

`VFQC_EVENT_TRANSPORT` should support:

```text
http
redis
```

Default:

```text
http
```

Reason: the backend now has `RunnerRedisEventWorker` for `vfqc:runs:events`. HTTP event ingestion remains a fallback/dev path.

## Event Transport Standard

All runner code should publish events through an interface:

```ts
export interface RunnerEventSink {
  send(event: RunnerEventRequest): Promise<RunnerEventIngestionResult>;
}
```

Implementations:

```text
RedisRunnerEventSink
HttpRunnerEventSink
```

Default MVP:

```text
RedisRunnerEventSink
```

The runner service must not directly call `BackendClient.sendEvent(...)` or `redis.xAdd(...)` from business logic. Use the sink interface so the transport can change without rewriting execution logic.

## Logging Standard

Use structured logs through pino.

Logging rules:

```text
1. Log runner lifecycle state changes.
2. Log command id, testRunId, runnerKey, runner instance id when safe.
3. Log handled failures at warn.
4. Log fatal process failures at error.
5. Never log plaintext secrets.
6. Never log raw external request/response bodies unless sanitized and explicitly allowed.
7. Never log raw cURL.
8. Never log runtime secret maps.
```

Good log fields:

```text
runnerKey
runnerInstanceId
commandId
testRunId
eventId
eventType
status
attempt
durationMs
errorCategory
```

Bad log fields:

```text
Authorization
Cookie
apiKey
accessToken
refreshToken
runtimeSecrets
rawCurl
rawResponseBody
promptfooRawConfigWithSecrets
```

## Backend Boundary

The runner should talk to backend only through:

```text
src/backend/backend-client.ts
```

The backend client should expose methods such as:

```ts
registerInstance(...)
heartbeat(...)
getTestRunConfig(...)
resolveRuntimeSecrets(...)
getControlState(...)
sendEvent(...)
```

Do not make raw `fetch` calls from arbitrary runner components.

## Redis Boundary

Redis should be encapsulated in:

```text
src/redis/redis-client.ts
src/redis/command-consumer.ts
src/redis/pending-recovery.ts
src/redis/event-stream-publisher.ts
```

Business logic should consume typed command messages:

```ts
RunnerCommandMessage
```

not raw Redis field arrays.

## Artifact Boundary

Artifact writing should be encapsulated in:

```text
src/artifacts
```

Rules:

```text
1. Artifact content must be sanitized before writing or uploading.
2. Runtime secrets must never be included.
3. Hash final sanitized bytes, not pre-sanitized content.
4. Artifact metadata can be emitted as ARTIFACT_CREATED only as metadata until backend artifact ingestion is implemented.
```

## Testing Standard

Minimum test groups:

```text
unit:
  - env schema validation
  - backend DTO parsing
  - command message parsing
  - sensitive data masking
  - Promptfoo adapter fixtures
  - event payload factory

integration:
  - Redis command consume with fake Redis/Testcontainers
  - backend client against mocked backend
  - runner lifecycle happy path
  - cancel path
  - event idempotency retry path
```

Promptfoo adapter contract tests must use deterministic fixtures.

Do not require real external LLM/API keys in unit tests.

## Initial Scaffold Checklist

When creating `apps/runner`:

```text
1. Create package.json with scripts.
2. Configure npm exact save.
3. Install the exact pinned dependency versions from this document.
4. Create package-lock.json with those exact versions.
5. Commit package-lock.json.
6. Create tsconfig.json.
7. Create .env.example.
8. Create src/main.ts.
9. Create config/env-schema.ts.
10. Create logging/logger.ts.
11. Create backend/dto/runner-api.dto.ts from actual backend contract.
12. Create empty interfaces/stubs for RunnerEventSink, BackendClient, CommandConsumer.
13. Run npm run check.
14. Run npm audit --audit-level=high.
15. Human reviews the scaffold diff.
16. Only then allow Codex to implement runner logic.
```
