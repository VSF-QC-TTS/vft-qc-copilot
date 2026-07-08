# Backend API Implementation Context

Last checked: 2026-07-08 14:15 Asia/Bangkok.

Purpose: this is the short handoff file for the next agent/session. Read this first before re-reading all backend docs. It summarizes `docs/backend/README.md`, `docs/backend/IMPLEMENTATION_ORDER.md`, and the current `apps/api` implementation state.

## Baseline Read

Primary docs used:

- `docs/backend/README.md`
- `docs/backend/IMPLEMENTATION_ORDER.md`
- Module docs `00..13` by heading/package expectations
- Current source under `apps/api`

The README says the real implementation order is `IMPLEMENTATION_ORDER.md`; numbered docs `00..13` are module references.

## Verification Snapshot

Command run from `apps/api`:

```bash
rtk bash mvnw test
```

Result: build success. Maven compiled the backend and ran 124 tests successfully.

Additional verification after Phase 9 runner work started:

```bash
rtk bash mvnw -Dtest=Phase9RunnerIntegrationTest test
```

Result: build success. Maven ran 5 Phase 9 runner integration tests successfully against PostgreSQL and Redis Testcontainers.

Additional verification after Phase 10 SSE work:

```bash
rtk bash mvnw -Dtest=Phase10SseIntegrationTest,Phase9RunnerIntegrationTest test
```

Result: build success. Maven ran 11 integration tests successfully against PostgreSQL and Redis Testcontainers.

Additional verification after Phase 11 export job work:

```bash
rtk bash mvnw -Dtest=Phase9RunnerIntegrationTest,Phase10SseIntegrationTest,Phase11ExportJobIntegrationTest test
```

Result: build success. Maven ran 13 integration tests successfully against PostgreSQL and Redis Testcontainers.

Coverage currently includes compile wiring, auth email-token service behavior, auth endpoint integration for register/verify/login/refresh/logout/forgot/reset, personal workspace onboarding, admin user invite service flow, admin API role boundary, Auth controller delegation, Secret/Provider controller delegation, request DTO validation, secret encryption, secret preview safety, secret rotate/delete safety, disabled/deleted-secret provider create/update/test rejection, secret scope lookup rejection, provider config response/persistence sanitization, provider create/update sanitization and scope lookup, provider soft-delete hidden 404 behavior, provider test history limit clamping, provider test log sanitization, provider status updates, workspace provider non-member hidden 404 behavior, workspace viewer mutation denial for secrets/providers, request-shape and error/preview contract tests for the three provider clients, `/api/**` vs `/internal/runner/**` security boundary checks, workspace/project permission role boundaries, hidden 404 behavior for non-members, workspace member response user summaries, dataset default-schema bootstrap, dataset row hash add/patch behavior, draft discard lifecycle, duplicate-draft rejection, row-count recalculation/copy behavior, required/unknown cell validation, publish/archive and ACTIVE immutability, Excel dataset import preview/confirm with schema extension, import permission boundary, import invalid-state and required-column validation, cURL sanitization/parsing, target SSRF URL validation, target inspect endpoint behavior, target secret-binding persistence masking, response extraction, target config validation, evaluator create/version/update/validation/setup-status behavior, evaluator viewer boundary, evaluator custom JavaScript rejection, evaluator duplicate-name/soft-delete/version-read/LLM provider invalid-state/MIXED validation hardening, TestRun preflight/create snapshot/state/review/matrix behavior, Phase 9 runner command/config/secret/event/cancel behavior, Phase 10 SSE auth/permission/replay/masking/heartbeat behavior, Phase 11 dataset/TestRun XLSX export job/download/formula-sanitization behavior, and PostgreSQL Testcontainers + `@SpringBootTest` + MockMvc integration coverage for Phase 2/3/4/5/6/7/8/9/10/11 endpoint/security/persistence behavior.

## Current Source Shape

Implemented Java packages under `apps/api/src/main/java/vn/vinfast/vfqc/api`:

- `auth`
- `common`
- `dataset`
- `evaluator`
- `exportjob`
- `provider`
- `project`
- `secret`
- `target`
- `testrun`
- `runner`
- `user`
- `workspace`

Current migrations:

- `apps/api/src/main/resources/db/migration/V1__setup_data.sql`
  - Creates tables through users/auth/workspace/project/dataset.
  - Also creates `secrets`, `llm_providers`, and `llm_provider_test_logs`; source packages now map these existing tables.
- `apps/api/src/main/resources/db/migration/V2__auth_email_tokens.sql`
  - Creates `auth_email_tokens` for email verification, password reset, and admin invite flows.
  - Stores token hashes only and tracks purpose/status/expiry/audit metadata.
- `apps/api/src/main/resources/db/migration/V3__chatbot_targets.sql`
  - Creates `chatbot_targets` and `chatbot_target_examples` for Phase 6 target configuration and test examples.
  - Source entities/repositories map these tables; Phase 6 backend now uses the existing JSONB fields without an additional migration.
- `apps/api/src/main/resources/db/migration/V4__personal_workspace_unique_index.sql`
  - Adds a partial unique index that enforces one active PERSONAL workspace per creator.
- `apps/api/src/main/resources/db/migration/V5__dataset_import_jobs.sql`
  - Creates `dataset_import_jobs` for Excel import preview/confirm state.
  - Adds `dataset_versions.import_job_id` for imported dataset version traceability.
- `apps/api/src/main/resources/db/migration/V6__evaluators.sql`
  - Creates `evaluators`, `evaluator_versions`, and `evaluator_rules`.
  - Adds active evaluator name uniqueness and evaluator-version indexes.
- `apps/api/src/main/resources/db/migration/V7__test_runs.sql`
  - Creates `test_runs`, `test_run_targets`, `test_run_evaluator_versions`, `test_case_results`, and `test_case_result_reviews`.
  - Adds Phase 8 read/query indexes for project run lists, run targets, evaluator snapshots, testcase result lookup, and reviewer lookup.
- `apps/api/src/main/resources/db/migration/V8__runner_execution.sql`
  - Creates `runner_instances`, `runner_commands`, and `runner_events` for Phase 9 runner execution plumbing.
  - Adds indexes for runner heartbeat/status lookup, command status/run lookup, command stream id lookup, event run sequence lookup, event status lookup, and event stream id lookup.
- `apps/api/src/main/resources/db/migration/V9__export_jobs.sql`
  - Creates `export_jobs` for Phase 11 shared dataset version and TestRun result export jobs.
  - Adds indexes for created-by, dataset-version, and TestRun export job lookup.

## Phase Status Against IMPLEMENTATION_ORDER.md

### Phase 0 - POM and Boot 4 Baseline

Status: mostly done.

Evidence:

- Java version is `21` in `apps/api/pom.xml`.
- Spring Boot parent is `4.0.7`.
- `springdoc-openapi-starter-webmvc-ui` is `3.0.3`.
- Uses `spring-boot-starter-webmvc`.
- No unresolved `${testcontainers.version}` reference found in `apps/api/pom.xml`.
- Source compiles.

Notes:

- `SecurityChainsConfig` imports `javax.crypto.spec.SecretKeySpec`; this is JDK crypto, not old Java EE persistence/validation. Do not treat it as a `javax.persistence` or `javax.validation` migration issue.

### Phase 1 - Common Foundation

Status: partial.

Implemented:

- `common/config`: `SecurityProperties`, `AdminSeedProperties`, `ClockConfig`, `HttpClientConfig`, `JacksonConfig`, `AuthProperties`, `ClientProperties`, `MailProperties`
- `common/error`: `ProblemDetail`-based global handler and project error codes
- `common/naming`: `ResourceNameNormalizer`
- `common/masking`: `DefaultVfqcSensitiveDataMasker`
- `common/security`: current user model, JWT converter, security chains, runner token filter
- `common/permission`: workspace/project permission services
- `common/observability`: request trace filter with MDC trace id
- `common/api`: collection/page response records
- `common/mail`: JavaMail-backed `MailService` with strategy classes for email verification, password reset, and admin invite mail. `vfqc.mail.enabled=false` disables delivery for tests/local dry runs.

Still missing or not obvious:

- `common/time`, `common/json`, `common/storage`, `common/redis` packages from the phase checklist.
- Naming unit tests required by docs.
- Storage path traversal protection.
- Redis stream names/constants.

### Phase 2 - User, Auth and Permission Base

Status: implemented enough for local email/password JWT auth plus internal-safe registration, email verification, password reset, and admin invite flows.

Implemented:

- `user` entity/repository/enums.
- `auth` controller/service/repository/entity/DTO/mapper.
- Local email/password login with Spring Security `PasswordEncoder`.
- Refresh token persistence with hashed token values.
- Email-token persistence with hashed tokens only for `EMAIL_VERIFICATION`, `PASSWORD_RESET`, and `ADMIN_INVITE`.
- Configurable registration mode via `vfqc.auth.registration.mode`; default is `ADMIN_ONLY`, with `SELF_VERIFY` enabling self-registration and verification mail.
- Forgot-password and admin-invite flows send reset links, not plaintext passwords.
- Reset password consumes email tokens, activates pending users when appropriate, and revokes active refresh tokens.
- Email verification and admin-invite password reset now ensure an idempotent PERSONAL workspace owned by the activated user.
- OAuth2 Resource Server JWT validation for `/api/**`.
- Separate runner service-token chain for `/internal/runner/**`.
- `@AuthenticationPrincipal CurrentUser` mapping.
- Workspace/project permission service skeletons with real checks for current modules.
- Focused auth unit/controller tests cover token issuing/consumption, pending-login rejection, register mail, generic forgot-password response, password reset, and admin invite.
- `UserSummaryResponse` is available as the small frontend-safe user read model: `id`, `email`, `name`, `avatarUrl`.

Still missing or not obvious:

- OAuth/OIDC login flow despite OAuth client dependencies/config being present.
- Rate limiting on login.
- Rate limiting on forgot/reset token issuance.
- End-to-end SMTP integration tests; current tests disable mail delivery and mock `MailService`.

### Phase 3 - Workspace and Project Core

Status: mostly implemented.

Implemented:

- Workspace entity/repository/service/controller.
- Workspace member entity/repository/service/controller.
- Project entity/repository/service/controller.
- Name normalization and duplicate checks.
- Workspace/project soft delete behavior.
- Project archive behavior.
- Project permission integration.
- Project setup-status endpoint.
- Create project now calls `DatasetSchemaBootstrapService.createDefaultProjectSchema(...)` in the same transaction.
- Workspace, workspace member, and project responses include user summary fields beside UUID fields for frontend display.
- Workspace/member/project read models batch-load user summaries through `UserSummaryService` to avoid N+1 queries.
- Personal workspace onboarding is idempotent and backed by the V4 partial unique index.

Still missing or partial:

- Broader tests for CRUD, membership permission, hidden 404 behavior, archive/delete, and default dataset schema creation.
- Setup status now counts datasets, active dataset versions, active chatbot targets, targets with extraction, active evaluators, and workspace ACTIVE providers. Broader setup-status completeness tests are still partial.

### Phase 4 - Dataset Schema, Dataset Versioning and Source Data

Status: MVP manual dataset flow plus Excel import preview/confirm implemented.

Implemented:

- `DatasetSchema`, `DatasetSchemaColumn`, `Dataset`, `DatasetVersion`, `DatasetRow`.
- Dataset schema bootstrap for default project schema with `question` and `expected` columns.
- Project dataset schema and variable APIs.
- Dataset CRUD for project scope.
- Initial draft version creation on dataset create.
- Draft creation from current active version.
- Row add/patch for draft versions.
- Deterministic `dataset_rows.row_hash` population on add/patch and when copying rows into a new draft.
- Publish draft to active and archive old active.
- Discard draft.
- `DatasetImportJob` and synchronous Excel import MVP.
- `POST /api/v1/projects/{projectId}/dataset-import-jobs` for `.xlsx` preview.
- `GET /api/v1/dataset-import-jobs/{importJobId}` for preview/job state.
- `POST /api/v1/dataset-import-jobs/{importJobId}/confirm` to create a PROJECT dataset with ACTIVE version and rows.
- Import confirm supports mapping to existing columns, ignoring columns, and adding new TEXT columns through a new project schema version.
- Required/unknown column validation.
- Integration tests cover create-project default schema bootstrap, row hash add/patch, publish/archive, and ACTIVE immutability.
- Import integration tests cover preview/confirm, schema extension, viewer denial, invalid confirm state, and required-column rollback.
- Dataset schema, dataset, and dataset version responses include `createdByUser` where a creator UUID is exposed.

Still missing from Phase 4 docs:

- `DatasetVariableService` as a separate service; variables are currently served by `DatasetSchemaService`.
- Project/workspace/personal dataset clone import source APIs.
- Async/large-file import handling and persisted uploaded-file storage; current Excel import is synchronous and capped for MVP.
- Dataset export source-data flow.
- Tests for row count concurrency, clone import sources, export, and project setup-status completeness.

### Phase 5 - Secrets and LLM Providers

Status: implemented as an MVP slice, with focused unit tests and critical production-hardening integration coverage.

Implemented:

- `secret` entity/repository/mapper/controller/service/service impl.
- Workspace and personal secret APIs.
- Spring Security Crypto `SpringCryptoSecretEncryptionService` using `Encryptors.stronger(...)` / AES-256-GCM through a small `SecretEncryptionService` boundary.
- Encrypted payloads are Base64 encoded and version-prefixed with `spring-crypto:aes-gcm:v1:` for format validation during decrypt.
- Encryption is configured by `SpringCryptoSecretEncryptionConfig` from `vfqc.security.encryption-key` and `vfqc.security.encryption-salt`; production reads `ENCRYPTION_KEY` and `ENCRYPTION_SALT`.
- `RuntimeSecretResolver` and active-secret lookup boundary.
- Secret responses expose fixed masked preview only, never plaintext or encrypted value.
- `provider` entity/repository/mapper/controller/service/client/validator packages.
- Workspace and personal LLM provider APIs.
- Provider types: `OPENAI_COMPATIBLE`, `GOOGLE_GENAI`, `CUSTOM_HTTP`.
- Provider config validation/sanitization before persistence.
- Provider test flow with real client boundary and sanitized `llm_provider_test_logs`, using `LlmProviderTestLogPersister` to avoid `@Transactional` self-invocation proxies.
- Request-shape tests cover `OPENAI_COMPATIBLE`, `GOOGLE_GENAI`, and `CUSTOM_HTTP`; Google GenAI sends the API key via `x-goog-api-key` header instead of query string.
- Provider response assembler batch-loads secret summaries for list responses to avoid N+1 lookups.
- Secret/provider responses include owner user summaries where the resource is personal; workspace-scoped resources keep `ownerUser` null.
- Secret/provider response assemblers batch-load user summaries and secret summaries for list/detail responses to avoid N+1 lookups.
- Controller unit tests cover Secret/Provider delegation and collection wrapping.
- Request validation tests cover required Secret/Provider fields.
- Secret scope tests cover workspace/personal mismatch and hidden personal secrets.
- Provider test history clamps `limit` to `1..100` and checks provider visibility before querying logs.
- `Phase5SecretProviderIntegrationTest` runs with PostgreSQL Testcontainers, Flyway, JPA validation, Spring Security JWT, MockMvc, real services/repositories, and a fake provider client boundary.
- Integration coverage checks unauthenticated 401, encrypted secret persistence without plaintext leaks, fixed masked preview, secret rotate/delete safety, disabled/deleted-secret provider create/update/test rejection without unsafe side effects, workspace non-member hidden 404 behavior across provider read/test/history, workspace viewer mutation denial, cross-workspace and personal/workspace secret/provider scope rejection, provider update scope preservation on rejection, provider config response/persistence sanitization, provider soft-delete hidden 404 behavior, provider test success/failure status updates, and sanitized provider test history/log storage.
- `HttpClientConfig` provides a shared `RestClient.Builder` bean for provider clients.
- `JacksonConfig` provides a shared `ObjectMapper` bean; tests inject it via Lombok `@RequiredArgsConstructor` and Spring `@TestConstructor`.
- `spring.ai.model.chat` and `spring.ai.model.embedding.text` default to `none` in `application.yaml` so the app does not require a global Gemini key at startup; runtime provider keys are resolved through `Secret`.

Still missing or partial:

- Provider clients are minimal MVP RestClient calls; mocked request-shape/error/preview contract tests exist, but no real provider fixture suite is included.
- Existing module hardening tests for auth/project/dataset remain incomplete.

### Phase 6 - Chatbot Target

Status: backend MVP implemented.

Implemented:

- `target` entity/repository/controller/service/validator/parser/client/assembler foundation.
- `ChatbotTarget` and `ChatbotTargetExample` JPA mappings.
- `POST /api/v1/projects/{projectId}/chatbot-targets/curl/inspect`.
- Target CRUD: list/create/get/update/delete.
- Target runtime/config endpoints: test connection, example history, response extraction update, readiness.
- Service contracts are interfaces under `target/service`; implementations live under `target/service/impl`; service interface methods include Javadoc.
- Structured cURL parser for HTTP basics: URL, method, headers, query params, JSON/text body. Unsupported shell syntax, pipes, multipart/form upload, and file references are rejected.
- cURL inspection does not execute or persist raw input.
- cURL inspect returns sanitized cURL, sanitized parsed request preview, and secret binding candidates.
- Shared masking facade now supports `sanitizeCurl(...)`.
- cURL sanitizer masks Authorization Bearer values, cookies, API key query params, token-like values, and sensitive JSON fields before preview/persistence use.
- Target create/update validates secret bindings with `SecretLookupService.requireActiveForUse(...)`, validates dataset mappings against the latest project schema, and persists only sanitized config.
- `TargetUrlValidator` rejects non-HTTP(S), localhost, private IPv4, link-local/metadata, IPv6 loopback, and user-info URLs.
- Target HTTP test client uses `RestClient`, validates outbound URL, enforces a 30-second connect/read timeout, limits stored response text to 256 KiB, and disables automatic redirect following.
- Test connection resolves secrets in memory through `RuntimeSecretResolver`, applies sample dataset mappings, persists sanitized request/response/error examples, and updates `latestExampleId`.
- Response extraction supports body text and simple JSON paths such as `$.answer`, `answer`, and `items[0].name`.
- Project setup status now counts active chatbot targets and active targets with extraction using `ChatbotTargetRepository`.
- Target responses include `createdByUser` beside `createdBy`, loaded in batch for list responses.
- Integration/unit coverage checks inspect endpoint sanitization, no target persistence during inspect, unsupported shell pipe rejection, V3 Flyway migration/JPA validation, parser behavior, target config validation, extraction behavior, and target create persistence masking.

Still missing or partial:

- No frontend/UI integration yet.
- No Promptfoo runner conversion yet; target config is stored in backend domain shape for later phases.
- Redirects are not followed automatically in the MVP target client; if following redirects becomes required, each redirect target must be revalidated before execution.

### Phase 7 - Evaluator

Status: backend MVP implemented.

Implemented:

- `evaluator` entity/repository/controller/service/validator/assembler foundation.
- `Evaluator`, `EvaluatorVersion`, and `EvaluatorRule` JPA mappings backed by V6 migration.
- Evaluator CRUD: list/create/get/update metadata/delete.
- Immutable evaluator version flow: create version 1 with evaluator, create later versions through `/api/v1/evaluators/{evaluatorId}/versions`.
- Rule-based evaluator validation with safe declarative operators, required expected-value checks, order-index uniqueness, positive weights, safe field-source parsing, and custom JavaScript/script payload rejection.
- LLM Judge config validation for required rubric/template/schema/pass-condition/threshold and optional default provider visibility/ACTIVE checks.
- Validation-only endpoint for builder UI.
- Field-source discovery from latest project dataset schema and optional target response extraction config.
- Operator metadata endpoint.
- Project setup-status now reports `hasEvaluator` and `activeEvaluators`.
- Integration tests cover create + version immutability, viewer mutation denial, custom JavaScript rejection, target field-source validation, setup-status evaluator counts, metadata update type preservation, duplicate evaluator names, soft-delete visibility, version list/detail read models, disabled LLM judge provider rejection, and incomplete MIXED config rejection.

Still missing or partial:

- Richer LLM Judge provider/model compatibility checks against explicit model catalog shape.
- Evaluator readiness detail endpoint separate from setup-status.

### Phase 8 - TestRun Core Without Runner Execution

Status: backend MVP implemented.

Implemented:

- `testrun` entity/repository/controller/service/assembler/state foundation.
- `TestRun`, `TestRunTarget`, `TestRunEvaluatorVersion`, `TestCaseResult`, and `TestCaseResultReview` JPA mappings backed by V7 migration.
- TestRun preflight endpoint that checks project access, active dataset version/rows, active target readiness, evaluator versions, field-source compatibility, and provider state blockers without mutating state.
- Create-run flow that locks sanitized domain and Promptfoo snapshots, hashes them, stores target/evaluator version snapshots, and returns run detail.
- Create-run now delegates to the Phase 9 `TestRunCommandService` implementation, which persists and publishes runner commands after the run snapshot is locked.
- TestRun list/detail/cancel APIs with documented state-machine enforcement.
- Result list/detail APIs with FINAL vs MACHINE read models, sanitized response fields, and immutable machine result behavior.
- QC review upsert API that stores review override separately from machine results.
- Comparison matrix API grouped by dataset row and run target, without raw request/response bodies.
- Request DTOs include Jakarta validation messages and Swagger/OpenAPI schema descriptions.
- Service interfaces include Javadoc for permission, transaction, and side-effect expectations.
- Integration tests cover missing active dataset preflight blocker, snapshot locking and masking, viewer create denial, invalid CREATED cancel transition, review override final result behavior, immutable machine result preservation, and comparison matrix final-result projection.

Still missing or partial:

- Fine-grained `RUN_START`, `RUN_CANCEL`, and `RESULT_REVIEW` permission actions are documented but not yet implemented in `PermissionAction`; Phase 8 uses existing `VIEW`/`EDIT`.
- No artifact metadata/download API yet.
- Result machine rows are now produced by Phase 9 internal runner event ingestion; frontend still cannot create machine results.

### Phase 9 - Runner Internal APIs and Redis Event Consumer

Status: runner core mostly implemented and hardened; artifact metadata is deferred to the dedicated artifact/export scope.

Implemented:

- `runner` entity/repository/controller/service/service impl foundation.
- `RunnerInstance`, `RunnerCommand`, and `RunnerEvent` JPA mappings backed by V8 migration.
- Runner instance register and heartbeat endpoints under `/internal/runner/instances`.
- Locked TestRun config fetch endpoint under `/internal/runner/test-runs/{testRunId}/config`.
- Runtime secret resolution endpoint under `/internal/runner/test-runs/{testRunId}/runtime-secrets`.
- Control-state endpoint under `/internal/runner/test-runs/{testRunId}/control-state`.
- Runner event ingestion endpoint under `/internal/runner/events`.
- `START_RUN` command persistence and compact Redis stream publish after TestRun creation.
- Running-run cancellation now publishes durable `CANCEL_RUN` commands; queued-run cancellation stays local.
- Locked target/evaluator snapshots include runtime-only secret/provider references while keeping plaintext secrets out of persisted snapshots and Redis payloads.
- Runtime secret endpoint resolves only secrets referenced by the locked TestRun snapshot.
- Runtime secret resolution bulk-loads requested active secrets to avoid per-secret N+1 lookups.
- Runner event ingestion is idempotent by event id, rejects mismatched duplicate metadata, and re-checks after locking the TestRun to close concurrent duplicate races.
- Redis runner event consumer now uses a consumer group, retries pending messages, ACKs only after successful DB ingestion, and sends sanitized poison messages to DLQ after retry exhaustion.
- `RUN_STARTED`, `TESTCASE_COMPLETED`, `RUN_COMPLETED`, `RUN_FAILED`, duplicate-event rejection, Redis poison-message DLQ, runner token API boundary, and cancellation flow are covered by Phase 9 integration tests.
- Testcase result ingestion sanitizes request/response/machine summary before persistence.

Still missing or partial:

- Runner artifact metadata ingest/download APIs are not implemented.
- Runner instance assignment/claiming is minimal; current command publish is compact and does not include full execution config by design.
- Richer progress/artifact events can be added when the runner contract needs UI-level progress beyond current run/result state.

### Phase 10 - SSE Realtime Projection

Status: implemented for MVP single API instance.

Implemented:

- `GET /api/v1/test-runs/{testRunId}/events` using Spring MVC `SseEmitter`.
- Project `PermissionAction.VIEW` check via `test_runs.project_id -> projects.workspace_id`.
- `Last-Event-ID` header has priority over `afterSequenceNo`; no cursor replays from sequence `0` with a 500-event cap.
- Replay reads persisted `runner_events` ordered by `(testRunId, sequenceNo)` with `PageRequest`, not Redis.
- SSE event `id` equals `runner_events.sequence_no`; event name equals `RunnerEventType`.
- Payloads are projected through a dedicated SSE projector that re-sanitizes values and strips unsafe large/raw fields such as request, response, headers, body, raw curl, runtime secrets, promptfoo config, and artifact content.
- In-memory emitter registry handles completion, timeout, send failure, client disconnect cleanup, and heartbeat fanout.
- `RunnerEventIngestionServiceImpl` publishes `RunnerEventCommittedEvent`; `TestRunSseEventListener` broadcasts with `@TransactionalEventListener(AFTER_COMMIT)` and `@Async`, so frontend broadcasts do not happen before DB commit.
- Scheduling is enabled for periodic SSE heartbeat comments; heartbeats are not persisted and do not allocate sequence numbers.

Still missing or partial:

- Multi-instance realtime fanout is not implemented; replay from DB works, but live emitter registry is in-memory per API instance.
- Connection limit enforcement is not implemented yet.

### Phase 11 - Export Jobs

Status: implemented for MVP local-storage XLSX exports.

Implemented:

- `exportjob` controller/dto/entity/enums/repository/service/service impl foundation.
- `ExportJob` JPA mapping backed by V9 migration.
- Shared `ExportJobService`, `ExportJobWorker`, `ExportDownloadService`, `ExportFileWriter`, and `ExportFileNamePolicy`.
- Local storage abstraction under `common/storage` with path traversal protection and typed `vfqc.export` configuration defaults.
- `POST /api/v1/dataset-versions/{datasetVersionId}/export-jobs` creates a dataset version XLSX export job.
- `POST /api/v1/test-runs/{testRunId}/export-jobs` creates a TestRun result XLSX export job; `resultMode` defaults to `FINAL`.
- `GET /api/v1/export-jobs/{jobId}` returns job status without exposing internal `filePath`.
- `GET /api/v1/export-jobs/{jobId}/download` re-checks source permission, requires `COMPLETED`, hides inaccessible/expired jobs as 404, and returns an attachment `Resource`.
- Export creation publishes `ExportJobCreatedEvent`; `ExportJobCreatedEventListener` runs the worker with `@TransactionalEventListener(AFTER_COMMIT)` and `@Async`, so worker reads only committed source/job state.
- Worker status transitions are isolated in `ExportJobStatusService` instead of wrapping XLSX generation/storage IO in one long transaction.
- Dataset export loads schema columns once and dataset rows in pages.
- TestRun export loads result pages and batch-loads reviews/dataset rows/targets per page to avoid per-result N+1 queries.
- XLSX writer uses Apache POI `SXSSFWorkbook`, re-masks token-like string values through `VfqcSensitiveDataMasker`, and applies `FormulaInjectionSanitizer` to every string cell.
- Failed export jobs store safe sanitized error code/message, not stack traces.
- Phase 11 integration tests cover dataset export XLSX content, formula injection sanitization, token masking, hidden internal file path, TestRun FINAL result default, review override export, download permission re-check, and clean-DB Flyway V9 validation.

Still missing or partial:

- Storage is local filesystem MVP; production multi-instance deployments need shared object storage or sticky worker/download routing.
- No export cleanup/expiry scheduled job yet; jobs get `expiresAt`, but file deletion/EXPIRED transition is deferred.
- CSV format is not implemented; MVP supports XLSX only.
- Very large exports still materialize row models before writing; pagination avoids N+1 but a streaming row cursor/writer would be better for high-volume runs.

## API Surface Currently Present

Auth:

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `GET /api/v1/auth/me`
- `POST /api/v1/auth/verify-email`
- `POST /api/v1/auth/password/forgot`
- `POST /api/v1/auth/password/reset`

Admin:

- `POST /api/v1/admin/users`

Workspace:

- `GET /api/v1/workspaces`
- `POST /api/v1/workspaces`
- `GET /api/v1/workspaces/{workspaceId}`
- `PATCH /api/v1/workspaces/{workspaceId}`
- `DELETE /api/v1/workspaces/{workspaceId}`
- `GET /api/v1/workspaces/{workspaceId}/members`
- `POST /api/v1/workspaces/{workspaceId}/members`
- `PATCH /api/v1/workspaces/{workspaceId}/members/{memberId}/role`

Project:

- `GET /api/v1/workspaces/{workspaceId}/projects`
- `POST /api/v1/workspaces/{workspaceId}/projects`
- `GET /api/v1/projects/{projectId}`
- `PATCH /api/v1/projects/{projectId}`
- `POST /api/v1/projects/{projectId}/archive`
- `DELETE /api/v1/projects/{projectId}`
- `GET /api/v1/projects/{projectId}/setup-status`

Dataset:

- `GET /api/v1/projects/{projectId}/dataset-schema`
- `GET /api/v1/projects/{projectId}/dataset-variables`
- `GET /api/v1/projects/{projectId}/datasets`
- `POST /api/v1/projects/{projectId}/datasets`
- `GET /api/v1/datasets/{datasetId}`
- `PATCH /api/v1/datasets/{datasetId}`
- `DELETE /api/v1/datasets/{datasetId}`
- `GET /api/v1/datasets/{datasetId}/versions`
- `POST /api/v1/datasets/{datasetId}/drafts`
- `GET /api/v1/dataset-versions/{versionId}/rows`
- `POST /api/v1/dataset-versions/{versionId}/rows`
- `PATCH /api/v1/dataset-rows/{rowId}`
- `POST /api/v1/dataset-versions/{versionId}/publish`
- `POST /api/v1/dataset-versions/{versionId}/discard`
- `POST /api/v1/projects/{projectId}/dataset-import-jobs`
- `GET /api/v1/dataset-import-jobs/{importJobId}`
- `POST /api/v1/dataset-import-jobs/{importJobId}/confirm`
- `POST /api/v1/dataset-versions/{datasetVersionId}/export-jobs`

Secrets:

- `GET /api/v1/workspaces/{workspaceId}/secrets`
- `POST /api/v1/workspaces/{workspaceId}/secrets`
- `GET /api/v1/me/secrets`
- `POST /api/v1/me/secrets`
- `GET /api/v1/secrets/{secretId}`
- `PATCH /api/v1/secrets/{secretId}`
- `POST /api/v1/secrets/{secretId}/rotate`
- `POST /api/v1/secrets/{secretId}/disable`
- `DELETE /api/v1/secrets/{secretId}`

LLM Providers:

- `GET /api/v1/workspaces/{workspaceId}/llm-providers`
- `POST /api/v1/workspaces/{workspaceId}/llm-providers`
- `GET /api/v1/me/llm-providers`
- `POST /api/v1/me/llm-providers`
- `GET /api/v1/llm-providers/{providerId}`
- `PATCH /api/v1/llm-providers/{providerId}`
- `DELETE /api/v1/llm-providers/{providerId}`
- `POST /api/v1/llm-providers/{providerId}/test`
- `GET /api/v1/llm-providers/{providerId}/test-logs`

Chatbot Targets:

- `POST /api/v1/projects/{projectId}/chatbot-targets/curl/inspect`
- `GET /api/v1/projects/{projectId}/chatbot-targets`
- `POST /api/v1/projects/{projectId}/chatbot-targets`
- `GET /api/v1/chatbot-targets/{targetId}`
- `PATCH /api/v1/chatbot-targets/{targetId}`
- `DELETE /api/v1/chatbot-targets/{targetId}`
- `POST /api/v1/chatbot-targets/{targetId}/test`
- `GET /api/v1/chatbot-targets/{targetId}/examples`
- `PUT /api/v1/chatbot-targets/{targetId}/response-extraction`
- `GET /api/v1/chatbot-targets/{targetId}/readiness`

Evaluators:

- `GET /api/v1/projects/{projectId}/evaluators`
- `POST /api/v1/projects/{projectId}/evaluators`
- `GET /api/v1/evaluators/{evaluatorId}`
- `PATCH /api/v1/evaluators/{evaluatorId}`
- `DELETE /api/v1/evaluators/{evaluatorId}`
- `GET /api/v1/evaluators/{evaluatorId}/versions`
- `POST /api/v1/evaluators/{evaluatorId}/versions`
- `GET /api/v1/evaluator-versions/{evaluatorVersionId}`
- `POST /api/v1/projects/{projectId}/evaluators/validate`
- `GET /api/v1/projects/{projectId}/evaluator-field-sources`
- `GET /api/v1/evaluator-operators`

Test Runs:

- `POST /api/v1/projects/{projectId}/test-runs/preflight`
- `POST /api/v1/projects/{projectId}/test-runs`
- `GET /api/v1/projects/{projectId}/test-runs`
- `GET /api/v1/test-runs/{testRunId}`
- `POST /api/v1/test-runs/{testRunId}/cancel`
- `GET /api/v1/test-runs/{testRunId}/results`
- `GET /api/v1/test-case-results/{resultId}`
- `PUT /api/v1/test-case-results/{resultId}/review`
- `GET /api/v1/test-runs/{testRunId}/comparison-matrix`
- `POST /api/v1/test-runs/{testRunId}/export-jobs`

Export Jobs:

- `GET /api/v1/export-jobs/{jobId}`
- `GET /api/v1/export-jobs/{jobId}/download`

Internal Runner:

- `POST /internal/runner/instances/register`
- `POST /internal/runner/instances/heartbeat`
- `GET /internal/runner/test-runs/{testRunId}/config`
- `POST /internal/runner/test-runs/{testRunId}/runtime-secrets`
- `GET /internal/runner/test-runs/{testRunId}/control-state`
- `POST /internal/runner/events`

## Coding Style And Conventions

Follow the current project style when adding new backend code:

- Package by feature: `feature/controller`, `feature/dto`, `feature/entity`, `feature/enums`, `feature/mapper`, `feature/repository`, `feature/service`, `feature/service/impl`.
- Service contracts should be interfaces in `feature/service`; implementations go under `feature/service/impl` and should be named `XxxServiceImpl`.
- Add Javadoc for service interface methods so callers can understand intent, permissions, transaction expectations, and important side effects without opening the implementation.
- DTOs should be Java records where practical. Request DTO fields should use Jakarta Bean Validation and Swagger/OpenAPI schema messages/descriptions so API docs are understandable.
- Controllers should include Swagger annotations such as `@Tag`, `@Operation`, and useful `@ApiResponse` entries.
- Use Lombok for boilerplate already accepted in the codebase: `@RequiredArgsConstructor`, `@Slf4j`, `@Getter`, `@Setter`, `@Builder`, `@AllArgsConstructor`, `@NoArgsConstructor` for entities. Entities use standard non-chaining setters (no `@Accessors(chain = true)`).
- Use MapStruct for simple entity-to-DTO mapping. Use assemblers only when a response is an aggregate/read model and MapStruct alone would hide orchestration.
- Keep UUID reference fields in responses for compatibility. Add compact summary objects beside them when the frontend needs display data, and batch-load those summaries in an assembler/service rather than inside mappers or loops.
- Keep controllers thin: validation, authentication principal, path/body binding, and delegation only. Business rules belong in services/domain validators.
- Keep code clean and OOP-oriented: focused classes, intention-revealing names, constructor injection, small methods, guard clauses, no god services, no hardcoded magic values.
- Do not use inline fully-qualified class names (FQN) in method bodies or signatures (e.g. `java.util.Optional`, `java.util.stream.Stream`, `vn.vinfast.vfqc.api.user.dto.UserSummaryResponse`). Always use explicit `import` statements and reference types by their simple name. IDEs may auto-generate inline FQNs; review and clean them before committing.
- Service implementations (`service/impl`) should use `@Slf4j` and include structured `log.info(...)` for state-changing operations (create, update, delete, status transitions) and `log.warn(...)` for handled failures. Use key-value pairs in log messages (e.g. `"Entity created: entityId={}, actorId={}"`) for log aggregation. Read-only query methods do not require logging unless they serve debugging purposes.
- Use `@ConfigurationProperties` records for typed configuration binding (e.g. `AuthProperties`, `UserProperties`, `SecurityProperties`). Avoid `@Value` field injection.
- Use `AuthLinkBuilder` for email URL construction and `AuthLinkBuilder.normalizeEmail()` for email normalisation across auth/user modules.
- Use `ApplicationEventPublisher` + `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` for mail delivery. Do not call `MailService.send()` directly inside `@Transactional` methods.
- Use `DatasetCellValidator` for dataset row schema validation. Dataset operations are split into `DatasetService` (CRUD), `DatasetDraftService` (draft lifecycle), and `DatasetRowService` (row mutations).
- Use `@Modifying @Query` bulk UPDATEs for token revocation instead of find-then-loop patterns.
- Integration test configuration lives in `src/test/resources/application-test.yaml`, activated via `spring.profiles.active=test`. Do not hardcode properties in `@SpringBootTest` annotations.
- Prefer immutable DTO/value objects, final injected dependencies, and coding to interfaces (`List`, `Map`) rather than concrete implementations.
- Avoid N+1 queries. Use repository projections, `@EntityGraph`, join fetches, batch summary services, or focused queries for read models before adding loops over lazy relationships.
- Keep transactions in the service layer. Use `@Transactional(readOnly = true)` for reads and normal `@Transactional` for writes.
- Do not expose JPA entities directly through API responses; controllers return DTO records/response models only.
- Validate and sanitize all external input. Never log passwords, plaintext secrets, raw external payloads, or unsanitized dataset/business-sensitive content.
- Follow Spring Boot built-ins first: `ProblemDetail`, `@ConfigurationProperties`, Spring Security, Spring Data JPA, `RestClient`, `SseEmitter` where specified by docs.

## Recommended Next Work

1. Add focused tests for what already exists before frontend/deploy work:
   - naming unit tests
   - dataset import/export edge cases for larger workbooks, empty sheets, duplicated headers, and expired exports

2. Close Phase 1 gaps if they are still required:
   - Redis stream names/constants
   - common JSON/time helpers only if code actually needs them

3. Finish Phase 4 non-MVP source-data scope:
   - project/workspace/personal dataset clone import source APIs
   - export source-data flow
   - async/large-file import storage only if product requirements exceed current synchronous capped MVP

4. Continue Phase 6 chatbot target:
   - add frontend/UI integration for target CRUD, cURL inspect, test connection, examples, extraction, and readiness
   - add richer outbound target fixtures if product needs redirect-following or provider-like target simulations
   - wire stored target config into later Promptfoo/TestRun runner conversion

5. Finish Phase 9 runner/internal execution plumbing:
   - add explicit DLQ/retry/pending recovery tests for Redis event consumption
   - complete `RUN_FAILED` and richer progress-event ingestion behavior
   - add runner artifact metadata ingestion if Phase 9 scope still requires it before Export
   - harden runner instance assignment/claiming and command acknowledgement semantics
   - keep Redis command/event payloads compact and reference-only

6. Harden Phase 8 TestRun after runner integration:
   - add fine-grained `RUN_START`, `RUN_CANCEL`, and `RESULT_REVIEW` permission actions if the product authorization model requires them
   - add pagination/filter edge-case tests for run list and result list endpoints
   - add broader comparison-matrix fixtures once multiple targets/evaluators are populated by runner ingestion

7. Harden Phase 11 export jobs for production deployment:
   - replace local filesystem storage with shared object storage if API instances are horizontally scaled
   - add cleanup job that marks expired exports and deletes stored files
   - add CSV only if the API contract/product requires it
   - stream very large exports instead of materializing all row models in memory

8. Phase 10 can be hardened later if deployment needs multiple API instances or connection limits:
   - add distributed fanout or sticky-session policy
   - add per-user/per-run SSE connection limits
   - add explicit replay-unavailable error policy if runner event retention is introduced

9. Phase 5 is ready for later phases; add real provider fixtures only if the product needs provider-specific response parsing beyond raw preview behavior.

10. Harden auth mail flows before production use:
   - add rate limiting for login, forgot password, and token verification attempts
   - add SMTP configuration documentation and delivery failure policy
   - decide final product policy for `ADMIN_ONLY` vs `SELF_VERIFY` registration mode
   - add cleanup job for expired/revoked/used email tokens if token volume grows

## Caveats For Next Agent

- Worktree state should be checked with `rtk git status --short`; this file was last updated after Phase 11 export job work.
- `apps/api/target` may exist because `mvnw test` was run.
- Tests passing now cover 124 backend tests: compile, auth mail/register/forgot/reset service and controller slices, auth endpoint integration, email-token hashing/expiry/consume behavior, personal workspace onboarding, admin invite service flow, admin API role boundary, controller delegation, DTO validation, Spring Crypto encryption round-trip with `spring-crypto:aes-gcm:v1:` payloads, secret create/rotate/delete safety, disabled-secret provider-test rejection, fixed masked preview safety, secret scope rejection, provider config sanitization, provider create/update sanitization, provider client request/error/preview contracts, provider test history clamping, provider test log sanitization, security-chain separation, workspace/project role boundaries, hidden 404 behavior, workspace member response user summaries, dataset row hash/publish/default-schema/discard/row-count/schema-validation/import behavior, cURL sanitization/parsing, target URL SSRF validation, target inspect endpoint behavior, target config validation, response extraction, target create persistence masking, evaluator create/version/update/validation/setup-status/duplicate-name/soft-delete/version-read/invalid-provider/MIXED behavior, TestRun preflight/create locked-snapshot/state/review/comparison-matrix behavior, Phase 9 runner command/config/secret/event/cancel/DLQ behavior, Phase 10 SSE auth/permission/replay/invalid-cursor/masking/heartbeat behavior, Phase 11 dataset/TestRun export job/download/formula-sanitization behavior, and Phase 2/3/4/5/6/7/8/9/10/11 Spring MVC/security/PostgreSQL/Redis integration coverage.
- Additional targeted Phase 9/10/11 verification passed with 13 integration tests covering compact `START_RUN` publish, locked config fetch, runtime secret resolution, idempotent event ingest, sanitized testcase result persistence, run completion, `RUN_FAILED`, duplicate-event metadata rejection, Redis retry/DLQ sanitization, runner token API isolation, running-run cancel command publishing, SSE subscription auth/permission, replay after cursor, no-cursor replay, invalid cursor validation, stream payload stripping, heartbeat non-persistence, broken emitter cleanup, dataset XLSX export, TestRun FINAL result export, download permission re-check, hidden internal file path, formula injection neutralization, and export token masking.
- JavaMail usage follows Spring Boot 4.0.7/Spring Framework 7 docs: rely on `JavaMailSender` auto-configuration when `spring.mail.host` is configured; use `MimeMessage`/`MimeMessageHelper`; set SMTP timeouts through `spring.mail.properties[...]`.
- Mail is now delivered asynchronously via `MailSendEvent` + `MailSendEventListener` after transaction commit. `@EnableAsync` is on the main application class.
