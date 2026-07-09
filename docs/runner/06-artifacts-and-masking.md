# Artifacts And Sensitive Data Masking

This document defines how the runner handles Promptfoo artifacts, local files, result payloads, logs, and sensitive data.

The main rule is simple:

```text
Never persist, log, emit, or upload plaintext secrets or unsanitized external payloads.
```

## Source Basis

This document is based on:

```text
AI_RUNNER_DTO_HANDOFF.md
API_IMPLEMENTATION_CONTEXT.md
PRD v0.7
```

Important current backend reality:

```text
There is no dedicated backend artifact upload/metadata endpoint implemented yet.
```

Therefore runner v1 may write sanitized local artifacts and may emit `ARTIFACT_CREATED` as metadata-only, but it must not assume downloadable Promptfoo artifacts are supported by the backend.

## Scope

This document covers:

```text
1. Local artifact directory layout.
2. Artifact metadata.
3. Sanitization before file writes.
4. Sanitization before event emission.
5. Runtime secret handling.
6. Logging restrictions.
7. Hashing and audit rules.
8. Known backend artifact gaps.
```

This document does not cover:

```text
1. Backend artifact upload implementation.
2. Object storage implementation.
3. ExportJob XLSX generation.
4. Frontend artifact download UX.
```

## Artifact Strategy For Runner v1

Runner v1 should support local sanitized artifacts only.

Recommended local path:

```text
./storage/artifacts/test-runs/{testRunId}/
```

Recommended files:

```text
metadata.json
promptfooconfig.json
results.json
results.jsonl
runner.log
report.html
```

Important:

```text
1. All files must be sanitized before write.
2. Hashes must be computed from final sanitized bytes.
3. Runtime secret values must never appear in artifact files.
4. Raw Promptfoo config with secrets must never be written.
5. Artifact paths are local implementation details and must not be exposed as public download URLs.
```

## Artifact Writer Boundary

Artifact writes should go through one component:

```text
src/artifacts/artifact-writer.ts
```

Suggested interface:

```ts
export interface ArtifactWriter {
  prepareRunDirectory(testRunId: string): Promise<RunArtifactDirectory>;

  writeJsonArtifact(input: WriteJsonArtifactInput): Promise<WrittenArtifact>;

  writeTextArtifact(input: WriteTextArtifactInput): Promise<WrittenArtifact>;

  writeMetadata(input: WriteMetadataInput): Promise<WrittenArtifact>;
}
```

Suggested DTOs:

```ts
export interface RunArtifactDirectory {
  testRunId: string;
  absolutePath: string;
  relativePath: string;
}

export interface WriteJsonArtifactInput {
  testRunId: string;
  artifactType: ArtifactType;
  fileName: string;
  value: unknown;
  sanitize: true;
}

export interface WriteTextArtifactInput {
  testRunId: string;
  artifactType: ArtifactType;
  fileName: string;
  content: string;
  sanitize: true;
}

export interface WrittenArtifact {
  testRunId: string;
  artifactType: ArtifactType;
  fileName: string;
  absolutePath: string;
  relativePath: string;
  sha256: string;
  sanitized: true;
  sizeBytes: number;
  createdAt: string;
}

export type ArtifactType =
  | 'PROMPTFOO_CONFIG'
  | 'RESULT_JSON'
  | 'RESULT_JSONL'
  | 'HTML_REPORT'
  | 'RUNNER_LOG'
  | 'METADATA';
```

Do not write artifacts directly from Promptfoo adapter, event sink, or backend client. Those components should return data to the runner service, then the runner service calls `ArtifactWriter`.

## Metadata File

Recommended `metadata.json`:

```json
{
  "testRunId": "run_123",
  "runnerKey": "runner-local-01",
  "runnerInstanceId": "4bf87e52-9b7e-4b26-bc4f-000000000000",
  "promptfooVersion": "<PINNED_PACKAGE_VERSION>",
  "promptfooAdapterSchemaVersion": 1,
  "createdAt": "2026-07-09T10:00:00+07:00",
  "domainConfigHash": "sha256:...",
  "promptfooConfigHash": "sha256:...",
  "artifactSchemaVersion": 1,
  "sanitized": true,
  "files": [
    {
      "artifactType": "PROMPTFOO_CONFIG",
      "fileName": "promptfooconfig.json",
      "sha256": "sha256:...",
      "sanitized": true,
      "sizeBytes": 12345
    }
  ]
}
```

Rules:

```text
1. `sanitized` must be true before exposing metadata outside the runner process.
2. Hash final sanitized bytes.
3. Do not include absolute host paths in event payloads.
4. If a local path is included, mark it as local-only and not downloadable.
5. Do not include plaintext secret ids together with values.
```

## ARTIFACT_CREATED Event

Current backend contract supports an event type:

```text
ARTIFACT_CREATED
```

Payload shape:

```ts
export interface ArtifactCreatedPayload {
  artifactType?: string;
  name?: string;
  uri?: string;
  sanitized?: boolean;
  contentHash?: string;
}
```

Current backend gap:

```text
No complete artifact ingestion API exists in the current backend implementation.
```

Therefore, treat `ARTIFACT_CREATED` as metadata-only.

Recommended payload:

```json
{
  "artifactType": "PROMPTFOO_CONFIG",
  "name": "promptfooconfig.json",
  "uri": "local://test-runs/{testRunId}/promptfooconfig.json",
  "sanitized": true,
  "contentHash": "sha256:..."
}
```

Do not rely on this event to make artifacts downloadable.

If downloadable reports/configs are required, design backend artifact ingestion first.

## Sanitization Layers

Sanitization should happen at multiple boundaries:

```text
1. Backend stores locked run config without plaintext secrets.
2. Runner resolves runtime secrets only for the current run.
3. Runner sanitizes Promptfoo config preview before writing artifact.
4. Runner sanitizes testcase request/response before event emission.
5. Runner sanitizes logs before writing/printing.
6. Backend sanitizes runner event payload before persistence.
7. SSE projection strips unsafe fields again for frontend streaming.
```

Runner must not rely only on backend sanitization. Backend sanitization is a safety net, not permission to emit unsafe payloads.

## Sensitive Fields

Default sensitive field names:

```text
Authorization
Proxy-Authorization
Cookie
Set-Cookie
X-API-Key
X-Auth-Token
api_key
apikey
access_token
refresh_token
id_token
client_secret
password
secret
token
bearer
credential
private_key
```

Matching should be case-insensitive.

Nested JSON keys should also be checked.

## Sensitive Value Patterns

The masker should also catch common value patterns, even if the key name is not obviously sensitive.

Examples:

```text
Bearer <token>
Basic <base64>
sk-...
xoxb-...
eyJ... JWT-like token
-----BEGIN PRIVATE KEY-----
```

Do not overfit the first implementation. Centralize patterns in:

```text
src/masking/masking-policy.ts
```

so patterns can be adjusted without changing runner business logic.

## Mask Format

Preferred mask format:

```json
{
  "Authorization": "Bearer ****MASKED****",
  "api_key": "****MASKED****"
}
```

Rules:

```text
1. Preserve field presence when useful for debugging.
2. Remove or mask secret value.
3. Keep value type where practical, but safety wins over type preservation.
4. Do not include partial secret suffix/prefix by default.
5. Do not hash secret values into public artifacts unless there is an explicit backend audit requirement.
```

## Sanitizer Interface

Suggested interface:

```ts
export interface SensitiveDataMasker {
  sanitizeJson<T = unknown>(input: T, context: MaskingContext): T;

  sanitizeText(input: string, context: MaskingContext): string;

  sanitizeHeaders(input: Record<string, unknown>, context: MaskingContext): Record<string, unknown>;

  sanitizeError(input: unknown, context: MaskingContext): SanitizedError;
}
```

Suggested context:

```ts
export interface MaskingContext {
  source:
    | 'BACKEND_CONFIG'
    | 'RUNTIME_SECRET'
    | 'PROMPTFOO_CONFIG'
    | 'TARGET_REQUEST'
    | 'TARGET_RESPONSE'
    | 'JUDGE_RESPONSE'
    | 'RUNNER_EVENT'
    | 'ARTIFACT'
    | 'LOG';

  testRunId?: string;
  commandId?: string;
  allowLargePayload?: boolean;
}
```

## Runtime Secrets

Runtime secret values come from:

```text
POST /internal/runner/test-runs/{testRunId}/runtime-secrets
```

Rules:

```text
1. Request only secret ids referenced by locked snapshots.
2. Keep returned plaintext secret values memory-only.
3. Do not write returned secret values to disk.
4. Do not log returned secret values.
5. Do not include returned secret values in events.
6. Do not include returned secret values in artifacts.
7. Do not include runtime secret map in thrown errors.
8. Clear references after run completion when practical.
```

JavaScript cannot guarantee immediate memory zeroization for normal strings, so docs should not claim perfect memory wiping. The practical requirement is to keep secret lifetime short and avoid persistence/logging/emission.

## Promptfoo Config Sanitization

The executable Promptfoo config may need runtime secret values to call private targets/providers.

Rules:

```text
1. Build executable config in memory or in an isolated temp directory.
2. Persist only sanitized config preview/artifact.
3. Replace secret values with references, placeholders, or env var names.
4. Never write plaintext headers/tokens to promptfooconfig.json artifact.
5. Never emit raw executable config to RUN_LOG.
```

Recommended sanitized config value:

```json
{
  "headers": {
    "Authorization": "Bearer {{runtimeSecret:secretId}}"
  }
}
```

or:

```json
{
  "headers": {
    "Authorization": "Bearer ****MASKED****"
  }
}
```

Do not expose actual runtime secret values.

## Request And Response Sanitization

`TESTCASE_COMPLETED` may include:

```text
request
response
extractedOutput
gradingResult
componentResults
machineSummary
```

Runner must sanitize these before sending events.

Rules:

```text
1. Strip or mask headers.
2. Strip or mask cookies.
3. Mask tokens in query params.
4. Mask secret-looking fields in JSON body.
5. Clamp large bodies if needed.
6. Do not send full HTML or binary payloads.
7. Prefer previews for large response bodies.
```

Suggested large payload policy:

```text
maxEventPayloadBytes = configurable
maxResponsePreviewChars = configurable
maxMachineSummaryChars = configurable
```

If payload is too large:

```json
{
  "response": {
    "truncated": true,
    "preview": "...",
    "omittedBytes": 12345
  }
}
```

## Logging

Use structured logs through the runner logger.

Allowed log fields:

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

Forbidden log fields:

```text
runtimeSecrets
Authorization
Cookie
apiKey
accessToken
refreshToken
rawCurl
rawRequestBody
rawResponseBody
rawPromptfooConfig
```

Error logging rule:

```text
Log sanitized error summary, not the raw error object blindly.
```

Some HTTP libraries include request headers/config in thrown errors. Always pass errors through `sanitizeError(...)` before logging.

## Hashing

Hash rules:

```text
1. Hash final sanitized bytes.
2. Use SHA-256.
3. Prefix stored hashes with `sha256:`.
4. Canonicalize JSON before hashing if the hash is used for semantic comparison.
5. Hash artifact bytes directly if the hash is used for file integrity.
```

Suggested helpers:

```text
src/artifacts/hash-util.ts
src/utils/json.ts
```

## Failure Behavior

If sanitizer fails:

```text
1. Do not write artifact.
2. Do not emit unsafe event payload.
3. Emit RUN_LOG with sanitized high-level error if possible.
4. If final artifacts cannot be safely produced, fail the run with RUN_FAILED.
```

If artifact write fails after the run itself completed:

```text
1. Prefer RUN_FAILED only if artifact integrity is required by product contract.
2. If artifacts are optional, emit RUN_LOG warning and still send RUN_COMPLETED.
3. Keep this behavior configurable and documented.
```

For MVP, because artifact upload is not fully supported, local artifact failure should not automatically turn every otherwise successful run into `FAILED` unless the product owner explicitly requires artifacts as mandatory.

## Backend Gaps

Current known gaps:

```text
1. No dedicated backend artifact upload endpoint.
2. No dedicated backend artifact metadata endpoint.
3. ARTIFACT_CREATED is metadata-only.
4. Local artifact path is not a download URL.
5. Object storage is not part of runner v1.
```

Document backend change requests separately before implementing downloadable artifacts.

## Implementation Checklist

```text
1. Create SensitiveDataMasker.
2. Create MaskingPolicy.
3. Add recursive JSON sanitization.
4. Add text sanitization for bearer/basic/JWT/private-key-like values.
5. Add sanitized error conversion.
6. Add ArtifactWriter.
7. Write metadata.json with sanitized=true.
8. Hash final sanitized bytes.
9. Emit ARTIFACT_CREATED metadata only.
10. Add tests for nested JSON, headers, query params, logs, and artifact files.
```
