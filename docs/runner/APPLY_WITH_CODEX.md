# Apply Runner Docs, Scaffold, And Implement In Slices With Codex

Use this prompt after copying these docs into the repository.

The goal is to keep Codex work small, reviewable, and testable. Do not ask Codex to scaffold, implement all runner logic, and fix tests in one giant pass.

## Global Rules

Codex must follow these rules for every step:

```text
Do not modify backend code unless explicitly asked.
Do not invent backend endpoints or DTO fields.
Do not use promptfoo@latest.
Do not re-resolve dependency versions unless the owner explicitly asks; use docs/runner/10-scaffold-package-versions.md.
Do not change the package manager.
Do not add NestJS/Express/Fastify.
Do not add direct DB access.
Do not log runtime secrets or raw external payloads.
Do not use Redis event output unless backend Redis event polling is explicitly enabled.
After each implementation slice, run the required checks and stop if they fail.
```

## Step 1 — Apply docs only

```text
Create/update docs/runner with the markdown files provided in this package.
Do not modify backend code while applying docs.
Do not rewrite the docs stylistically.
Do not scaffold apps/runner in this step.

After applying docs, show:
- git status --short
- git diff -- docs/runner
```

Stop after this step if requested.

## Step 2 — Verify backend contract before scaffold

```text
Inspect only these backend paths:
- apps/api/src/main/java/vn/vinfast/vfqc/api/runner
- apps/api/src/main/java/vn/vinfast/vfqc/api/testrun
- apps/api/src/main/java/vn/vinfast/vfqc/api/common

Update docs/runner only if endpoint names, DTO names, enum names, event payload side effects, or Redis stream behavior differ from actual backend code.

Pay special attention to:
- whether vfqc:runs:events is fully supported for standalone runner event output
- whether POST /internal/runner/events is the implemented event ingestion path
- whether command acknowledgement exists
- whether artifact upload/metadata APIs exist
- whether runner config row paging is fully specified

Do not invent new backend endpoints. If something is missing, document it as a backend gap.

After verification, show:
- backend contract mismatches found
- docs changed because of backend mismatch
- git diff -- docs/runner
```

Stop after this step if backend contract mismatches are significant.

## Step 3 — Scaffold apps/runner only

Codex may scaffold `apps/runner`, but must follow the project standard exactly.

```text
Create apps/runner as a Node.js 24 LTS + TypeScript worker service.
Use npm and package-lock.json.
Do not use pnpm/yarn/bun.
Do not add NestJS/Express/Fastify.
Do not modify backend code.
Do not implement full runner business logic in this scaffold step.
Only create skeleton files, DTO definitions, config parsing, logger, empty boundary interfaces/stubs, and minimal smoke tests.
```

Use these exact dependency versions from docs/runner/10-scaffold-package-versions.md. Do not replace them with latest versions during scaffold:

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

Required scaffold output:

```text
apps/runner/package.json
apps/runner/package-lock.json
apps/runner/tsconfig.json
apps/runner/.env.example
apps/runner/.nvmrc
apps/runner/.node-version
apps/runner/src/main.ts
apps/runner/src/config/env-schema.ts
apps/runner/src/config/runner-config.ts
apps/runner/src/logging/logger.ts
apps/runner/src/backend/dto/runner-api.dto.ts
empty boundary stubs/interfaces for backend, Redis, events, artifacts, masking, and Promptfoo adapter
minimal tests proving config/env schema and DTO enum constants compile
```

Required scripts:

```json
{
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
```

After scaffold, run if possible:

```bash
npm run check
npm run build
npm audit --audit-level=high
```

If install/build/check fails, stop and report the exact failure. Do not silently choose different dependency versions. If npm audit reports high/critical issues, report them and wait for approval before changing pinned versions.

Human review checkpoint:

```text
Stop after scaffold.
Do not implement command consumer, backend client, Promptfoo adapter, or runner orchestration until scaffold is reviewed.
```

## Step 4 — Implement by slices

After scaffold is reviewed, implement the runner using this slice order:

```text
Slice 1: Backend DTO validators and BackendClient.
Slice 2: Redis command message parsing and CommandConsumer boundary.
Slice 3: RunnerEventSink with HTTP default and Redis placeholder/future strategy.
Slice 4: SensitiveDataMasker and artifact-safe utilities.
Slice 5: PromptfooAdapter fixtures and config builder boundary.
Slice 6: Runner lifecycle orchestration with mocked Promptfoo executor.
Slice 7: Cancellation, retry, timeout, and idempotency behavior.
Slice 8: Local smoke test plan and optional smoke script.
```

For every slice:

```text
1. Implement only the files needed for that slice.
2. Add/update tests for that slice in the same change.
3. Run npm run check.
4. Run npm run build if implementation files changed.
5. Show git diff limited to apps/runner and docs/runner.
6. Stop if tests/checks fail.
7. Do not proceed to the next slice until the current slice is green.
```

Detailed slice acceptance criteria are in:

```text
docs/runner/11-sliced-implementation-plan.md
```

## Step 5 — Show result after each slice

After every slice, show:

```text
- slice completed
- files created/updated
- tests added/updated
- commands run
- pass/fail result
- git status --short
- known gaps or next slice
```

Do not hide failing tests.

Do not fix by weakening tests unless the docs or backend contract are wrong and the mismatch is explicitly documented.
