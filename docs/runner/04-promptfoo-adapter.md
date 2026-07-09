# Promptfoo Adapter Design

This document describes the boundary between VFQC domain snapshots and Promptfoo.

Promptfoo is the execution engine. The backend remains the source of truth.

## Goals

The adapter should:

```text
1. Convert locked backend TestRun config into Promptfoo-compatible config.
2. Keep Promptfoo internals isolated from runner core logic.
3. Normalize Promptfoo output into backend event payloads.
4. Make Promptfoo upgrades testable with regression fixtures.
5. Avoid leaking plaintext secrets into config snapshots, logs, events, or artifacts.
```

## Non-Goals

The adapter should not:

```text
1. Read mutable project/target/evaluator state from frontend APIs.
2. Decide workspace permissions.
3. Mutate backend domain config.
4. Store runtime secrets.
5. Expose user-provided arbitrary JavaScript.
6. Depend on raw Promptfoo result shape outside this module.
```

## Package Usage

Use Promptfoo as a Node package, not as a CLI subprocess.

Reasons:

```text
1. Avoid shell spawning.
2. Avoid command escaping risks.
3. Keep config in typed objects.
4. Make result normalization easier to test.
5. Keep execution inside the runner process for cancellation/error handling.
```

Promptfoo installation must be exact-pinned in `package.json` and `package-lock.json`.

## Main Interfaces

Suggested runner-side interfaces:

```ts
export interface PromptfooAdapterInput {
  testRunId: string;
  targets: TargetSnapshot[];
  evaluators: EvaluatorSnapshot[];
  rows: DatasetRowSnapshot[];
  promptfooSnapshot: Record<string, unknown>;
  runtimeSecretsById: Record<string, string>;
}
```

```ts
export interface PromptfooGeneratedConfig {
  configObject: Record<string, unknown>;
  sanitizedPreview: Record<string, unknown>;
  referencedSecretIds: string[];
}
```

```ts
export interface PromptfooCaseResult {
  testRunTargetId: string;
  datasetRowId: number;
  status: "PASSED" | "FAILED" | "ERROR" | "JUDGE_ERROR" | "TIMEOUT";
  score?: number;
  passed?: boolean;
  request?: Record<string, unknown>;
  response?: Record<string, unknown>;
  extractedOutput?: Record<string, unknown>;
  gradingResult?: Record<string, unknown>;
  componentResults?: Record<string, unknown>;
  tokenUsage?: Record<string, unknown>;
  latencyMs?: number;
  machineSummary?: string;
}
```

```ts
export interface PromptfooRunSummary {
  total: number;
  passed: number;
  failed: number;
  errored: number;
  durationMs?: number;
}
```

## Suggested Class Boundary

```text
PromptfooAdapter
  buildConfig(input): PromptfooGeneratedConfig
  execute(config, context): AsyncIterable<PromptfooCaseResult> or PromptfooRunResult
  normalize(raw): PromptfooCaseResult[]
```

Prefer smaller classes internally:

```text
PromptfooConfigBuilder
PromptfooExecutor
PromptfooResultNormalizer
PromptfooSecretResolver
PromptfooAssertionMapper
PromptfooProviderMapper
```

Do not let `RunnerService` know Promptfoo raw internals.

## Mapping VFQC To Promptfoo

General mapping:

```text
Dataset row                -> Promptfoo tests[]
Dataset row cells          -> tests[].vars
$dataset.question          -> {{question}}
$dataset.expected          -> {{expected}}
Chatbot target HTTP config -> Promptfoo HTTP provider
Response extraction path   -> transformResponse or adapter-side extraction
Rule evaluator             -> Promptfoo assertions or adapter-generated safe checks
LLM judge evaluator        -> llm-rubric / model-graded assertion strategy
Comparison targets         -> multiple providers/targets
```

## Dataset Rows

Backend config rows:

```ts
export interface DatasetRowSnapshot {
  id: number;
  rowIndex: number;
  cells: Record<string, unknown>;
  revision: number;
  rowHash: string;
}
```

Runner must preserve:

```text
datasetRowId = DatasetRowSnapshot.id
rowIndex
cells
rowHash if needed for debug metadata
```

Each dataset row should become a test case.

## Targets

Target snapshot:

```ts
export interface TargetSnapshot {
  id: string;
  targetType: "CHATBOT_TARGET" | "LLM_BASELINE";
  chatbotTargetId?: string | null;
  providerId?: string | null;
  model?: string | null;
  name: string;
  orderIndex: number;
  config: Record<string, unknown>;
}
```

`TargetSnapshot.id` is the `testRunTargetId` required in testcase result events.

For comparison runs, every target must produce its own result for each dataset row.

## Secret Handling In Promptfoo Config

Runtime secrets are returned by:

```text
POST /internal/runner/test-runs/{testRunId}/runtime-secrets
```

Rules:

```text
1. Keep plaintext secret values in memory only.
2. Do not write plaintext secret values into generated config artifacts.
3. Prefer env-var placeholders or runtime provider injection.
4. SanitizedPreview must contain masked values or references only.
5. Final artifacts must be sanitized before writing.
```

If Promptfoo requires secrets through environment variables, generate per-run env mapping in memory only.

Example sanitized config preview:

```json
{
  "headers": {
    "Authorization": "Bearer ****MASKED****"
  }
}
```

## Evaluator Mapping

Evaluator snapshot:

```ts
export interface EvaluatorSnapshot {
  id: number;
  evaluatorId: string;
  evaluatorVersionId: string;
  evaluatorType: string;
  version: number;
  orderIndex: number;
  config: Record<string, unknown>;
  rules: Array<Record<string, unknown>>;
}
```

Supported MVP evaluator types:

```text
RULE_BASED
LLM_JUDGE
MIXED
```

Actual enum strings must be verified against backend code.

### Rule-Based Evaluator

Rule-based evaluator should map to Promptfoo assertions when possible.

Safe examples:

```text
equals
contains
not_contains
regex
exists
not_exists
is_json
contains_json
greater_than
greater_than_or_equal
less_than
less_than_or_equal
```

If Promptfoo does not natively support a rule, the runner may use adapter-generated safe logic.

Do not expose arbitrary user JavaScript in MVP.

### LLM Judge Evaluator

LLM judge maps to Promptfoo model-graded assertions such as rubric-based grading, if available in the pinned Promptfoo version.

The adapter must normalize judge output into:

```json
{
  "score": 0.86,
  "passed": true,
  "reason": "Answer matches expected facts.",
  "errors": [],
  "criteria": []
}
```

If judge output is not parseable:

```text
status = JUDGE_ERROR or ERROR
```

Do not convert judge parse errors into business `FAILED`.

## Cache Policy

Default:

```text
Promptfoo cache disabled for internal chatbot target runs.
```

Reason:

```text
QC needs to evaluate what the target returns at run time, not a cached previous response.
```

If cache is enabled for controlled baseline/debug mode, the run snapshot/artifact metadata must record:

```text
cache_mode
cache_namespace
```

Do not silently enable cache.

## Cancellation

Promptfoo execution should be integrated with runner cancellation.

The runner should check cancellation:

```text
1. Before building config.
2. Before execution.
3. Between testcases if execution is controlled by runner loop.
4. Between target/judge calls if possible.
5. Before final artifact write.
6. Before final event.
```

If Promptfoo Node API supports callbacks/hooks such as per-test completion, use them to emit incremental results and check cancellation.

If Promptfoo only returns after full run for a specific path, document that as a limitation and rely on control polling before/after execution.

## Output Normalization

Normalize raw Promptfoo output into backend-supported event payloads.

For each case result:

```text
testRunTargetId
datasetRowId
status
score
passed
request
response
extractedOutput
gradingResult
componentResults
tokenUsage
latencyMs
machineSummary
```

Required fields:

```text
testRunTargetId
datasetRowId
status
```

The backend defaults missing `TESTCASE_COMPLETED.status` to `PASSED`, but runner should always send it explicitly.

## Artifact Generation

The adapter may generate:

```text
promptfooconfig.json
results.json
results.jsonl
report.html
```

All generated files must be sanitized before writing.

Current backend gap:

```text
No dedicated artifact upload/metadata endpoint exists.
```

Therefore `ARTIFACT_CREATED` is metadata-only unless backend is extended.

## Regression Fixtures

PromptfooAdapterContract tests must cover:

```text
1. HTTP target + response extraction.
2. Rule-based only.
3. LLM judge only.
4. Mixed evaluator.
5. Multi-target comparison.
6. Target timeout/error.
7. Judge parse error.
8. Secret masking in config preview/artifacts.
9. Cache disabled by default.
10. Promptfoo raw output shape changes.
```

Fixtures should live under:

```text
src/promptfoo/fixtures
test/fixtures/promptfoo
```

Do not use real external provider keys in fixtures.

## Upgrade Checklist

Before upgrading Promptfoo:

```text
1. Check npm latest version.
2. Pin exact version in package.json.
3. Run npm ci.
4. Run adapter contract tests.
5. Run at least one local fake-target integration test.
6. Compare normalized output schema.
7. Check artifact sanitization.
8. Record upgrade note.
```

Suggested note path:

```text
docs/runner/promptfoo-upgrades.md
```
