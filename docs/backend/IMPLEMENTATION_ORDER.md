# Backend Implementation Order - Spring Boot 4.0.7

Version: `v0.3`  
Purpose: define the real coding order for `apps/api`.

This file is the execution roadmap. The numbered backend docs `00..13` are module references, not the exact coding order.

---

## 0. Rule of Thumb

Build from framework foundation -> access model -> domain configuration -> execution -> realtime/export.

Do not start a later module only because its doc number is lower or higher. Follow dependency order.

Most important ordering rules:

1. Build common Spring Boot foundation before feature modules.
2. Build security/current-user/permission before workspace resources become sensitive.
3. Build masking before any module that persists external config, HTTP samples, runner events, artifacts or exports.
4. Build workspace/project core before project-scoped resources.
5. Build dataset before target/evaluator/run preflight because target/evaluator field sources depend on dataset schema.
6. Build secret/provider before LLM Judge and baseline providers.
7. Build target and evaluator before TestRun creation.
8. Build TestRun state/snapshots before runner command publishing.
9. Build runner event persistence before SSE live streaming.
10. Build export after dataset/result read models are stable.

---

## 1. Phase 0 - POM and Boot 4 Baseline

Read:

```text
00-backend-overview.md
01-package-architecture.md
02-domain-model-mapping.md
```

Do:

1. Confirm Java 21.
2. Confirm Spring Boot parent `4.0.7`.
3. Bump springdoc to `3.0.3` if you keep Springdoc.
4. Fix or remove unresolved `${testcontainers.version}` BOM import.
5. Keep `spring-boot-starter-webmvc`, not WebFlux.
6. Keep `RestClient` for synchronous outbound HTTP.
7. Confirm all code uses `jakarta.*`, not `javax.*`.

Done when:

```text
mvn validate
mvn test -DskipTests=false
```

can at least resolve dependencies and compile an empty/skeleton backend.

---

## 2. Phase 1 - Common Foundation

Read:

```text
00-backend-overview.md
01-package-architecture.md
02-domain-model-mapping.md
13-security-masking-implementation.md
```

Implement first:

```text
common/config
common/error
common/time
common/json
common/naming
common/masking
common/storage
common/redis
common/security skeleton
common/permission skeleton
```

Use Spring built-ins:

1. `ProblemDetail` + `@RestControllerAdvice` for errors.
2. `@ConfigurationProperties` for app settings.
3. `Clock` bean for timestamps.
4. Jakarta Bean Validation for request validation.
5. Spring Data `Pageable` for list endpoints.
6. `@Transactional` on service methods.
7. `PasswordEncoder`, not `PasswordHasher`.
8. `RestClient`, not a custom HTTP stack.

Do not overbuild:

```text
Do not create all feature-specific services yet.
Do not implement runner/event/export yet.
Do not add custom JWT parsing filter for user APIs.
```

Done when:

1. Global error shape works.
2. Validation error shape works.
3. Masking service has unit tests.
4. Naming normalizer has unit tests.
5. Redis stream names/constants exist, even if no runner is implemented yet.
6. Storage abstraction prevents path traversal.

---

## 3. Phase 2 - User, Auth and Permission Base

Read:

```text
03-auth-permission-implementation.md
01-package-architecture.md sections common/security and common/permission
```

Implement:

```text
user entity/repository/service
PasswordEncoder bean
AuthController/AuthService for email-password login if MVP uses local login
JWT issuing/validation strategy
OAuth2 Resource Server security chain for /api/**
Runner service-token security chain for /internal/runner/**
CurrentUser mapping
WorkspacePermissionService skeleton
ProjectPermissionService skeleton
```

Important:

1. Local password auth uses Spring Security `PasswordEncoder`.
2. User API JWT validation should use OAuth2 Resource Server where possible.
3. Runner internal token is a separate `SecurityFilterChain`.
4. Do not let runner token authenticate frontend API.
5. Do not let user JWT authenticate runner internal API.

Done when:

1. Login works if local login is in MVP.
2. Authenticated endpoint can receive `@AuthenticationPrincipal CurrentUser`.
3. Protected endpoint returns 401/403 consistently.
4. Password hashes are never exposed.
5. Security tests cover both `/api/**` and `/internal/runner/**` boundaries.

---

## 4. Phase 3 - Workspace and Project Core

Read:

```text
04-workspace-project-implementation.md
02-domain-model-mapping.md workspace/project sections
```

Implement:

```text
Workspace entity/repository/service/controller
WorkspaceMember entity/repository/service/controller
Project entity/repository/lookup service
Project permission integration
Name normalization and duplicate checks
Soft delete/archive behavior
```

Two-pass note:

`CreateProject` eventually must create the default PROJECT dataset schema in the same transaction. But DatasetSchemaService is not implemented yet at this point.

Recommended approach:

1. Implement workspace/project core first.
2. Define a small interface such as `ProjectDatasetSchemaBootstrapper` or `DatasetSchemaBootstrapService`.
3. Temporarily wire a no-op or defer the full create-project use case until Phase 4.
4. After dataset schema is implemented, return here and complete project creation transaction.

Done when:

1. Workspace CRUD works.
2. Workspace membership permission works.
3. Project CRUD/archive/delete works.
4. Permission hiding returns 404 where required.
5. Project setup-status may be partial, but must not lie about dataset readiness.

---

## 5. Phase 4 - Dataset Schema, Dataset Versioning and Import/Export Source Data

Read:

```text
06-dataset-implementation.md
04-workspace-project-implementation.md create-project default schema rule
02-domain-model-mapping.md dataset sections
```

Implement:

```text
DatasetSchema
DatasetSchemaColumn
Dataset
DatasetVersion
DatasetRow
DatasetImportJob
DatasetSchemaService
DatasetService
DatasetDraftService
DatasetRowService
DatasetImportService
DatasetVariableService
```

Then revisit Phase 3:

1. Complete create-project -> create default PROJECT dataset schema in the same transaction.
2. Complete project setup-status dataset checks.

Important:

1. ACTIVE DatasetVersion is immutable.
2. DRAFT DatasetVersion is editable.
3. Publish DRAFT -> archive old ACTIVE -> set new currentVersionId.
4. Dataset variables use `$dataset.<columnName>` at API/domain layer.
5. Promptfoo syntax conversion does not belong in dataset module.

Done when:

1. Can create schema/dataset/draft rows.
2. Can publish/discard draft.
3. Row count stays correct.
4. Excel import preview -> mapping -> confirm works if in MVP.
5. Project creation now creates default schema correctly.

---

## 6. Phase 5 - Secrets and LLM Providers

Read:

```text
05-secret-provider-implementation.md
13-security-masking-implementation.md
03-auth-permission-implementation.md
```

Implement:

```text
Secret entity/repository/service/controller
SecretEncryptionService
RuntimeSecretResolver
LlmProvider entity/repository/service/controller
Provider config validation
Provider test flow
Provider test logs
Spring AI adapter boundary if using Google GenAI
```

Important:

1. API never returns plaintext secret after create/rotate.
2. Provider stores `secretId`, never API key directly.
3. Provider config JSON is sanitized before save.
4. Provider tests decrypt only in memory.
5. Provider test logs are sanitized.
6. Spring AI classes stay inside adapter/client layer.

Done when:

1. Secret CRUD/rotate works.
2. Provider CRUD works.
3. Provider cannot reference secret from another user/workspace.
4. Provider test creates sanitized log for success and failure.
5. RuntimeSecretResolver has tests proving no values are logged/persisted.

---

## 7. Phase 6 - Chatbot Target

Read:

```text
07-chatbot-target-implementation.md
13-security-masking-implementation.md
06-dataset-implementation.md variable rules
05-secret-provider-implementation.md secret binding rules
```

Implement:

```text
ChatbotTarget
ChatbotTargetExample
CurlInspectionService
ChatbotTargetService
ChatbotTargetTestService
ResponseExtractionService
Target readiness service
RestClientHttpTargetClient
SSRF protection
```

Important:

1. Never shell-execute cURL.
2. Persist only sanitized cURL.
3. Secrets are references to Secret entities.
4. Dataset mappings validate against DatasetSchema columns.
5. Test request/response examples are sanitized before save.
6. ACTIVE target requires request config and response extraction config.

Done when:

1. User can inspect cURL safely.
2. User can create/update target config.
3. User can run target test connection.
4. User can configure response extraction from masked example.
5. Target readiness is accurate.

---

## 8. Phase 7 - Evaluator

Read:

```text
08-evaluator-implementation.md
06-dataset-implementation.md
07-chatbot-target-implementation.md
05-secret-provider-implementation.md
```

Implement:

```text
Evaluator
EvaluatorVersion
EvaluatorRule
EvaluatorService
EvaluatorVersionService
EvaluatorValidationService
EvaluatorFieldSourceService
EvaluatorReadinessService
EvaluatorOperatorService
```

Important:

1. Evaluator type is immutable.
2. EvaluatorVersion is immutable.
3. Editing config creates a new version.
4. Field sources come from dataset schema + target response extraction.
5. No arbitrary JavaScript in MVP.
6. Judge provider/model validates against provider config.

Done when:

1. Can create rule-based evaluator.
2. Can create LLM judge evaluator.
3. Can create new evaluator version.
4. Validation API catches bad fields/operators/provider/model.
5. Evaluator readiness is accurate.

---

## 9. Phase 8 - TestRun Core Without Runner Execution

Read:

```text
09-test-run-implementation.md
06-dataset-implementation.md
07-chatbot-target-implementation.md
08-evaluator-implementation.md
05-secret-provider-implementation.md
```

Implement first:

```text
TestRun
TestRunTarget
TestRunEvaluatorVersion
TestCaseResult
TestCaseResultReview
TestRunStateMachine
TestRunPreflightService
TestRunSnapshotService
TestRunHashService
TestRunCommandService skeleton
TestRunQueryService
TestRunResultService
TestRunReviewService
ComparisonMatrixService
```

Important:

1. Preflight validates dataset/target/evaluator/provider readiness.
2. Create run locks sanitized snapshots.
3. Rerun means creating a new TestRun.
4. Machine result is immutable.
5. QC review is a separate override layer.
6. Do not publish Redis START_RUN until DB state exists.

Done when:

1. Preflight works.
2. Create run stores locked config snapshots.
3. Run state machine transitions are tested.
4. Result/review read models work with seeded/fake results.
5. Comparison matrix works with seeded/fake results.

---

## 10. Phase 9 - Runner Internal APIs and Redis Event Consumer

Read:

```text
10-runner-event-consumer.md
09-test-run-implementation.md
13-security-masking-implementation.md
```

Implement:

```text
RunnerInstance
RunnerCommand
RunnerEvent
RunnerInstanceService
RunnerConfigService
RuntimeSecretService
RunnerCommandPublisher
RunnerEventIngestionService
RunnerEventConsumerService
RunnerEventSequenceService
RunnerDeadLetterService
RunnerArtifactIngestService
Redis stream publisher/consumer integration
```

Important:

1. Redis payloads contain references only.
2. Runner config fetch returns locked TestRun config.
3. Runtime secret bundle is returned only to authenticated runner.
4. Runtime secret bundle is never persisted/logged.
5. Persist RunnerEvent before SSE broadcast.
6. ACK Redis event only after DB persistence succeeds.
7. Event ingestion must be idempotent.

Done when:

1. Backend can publish START_RUN command after TestRun creation commit.
2. Runner can fetch locked config.
3. Runner can resolve runtime secrets.
4. Backend can ingest TESTCASE_COMPLETED/RUN_COMPLETED/RUN_FAILED events.
5. RunnerEvent sequence numbers are stable per TestRun.
6. DLQ behavior is tested.

---

## 11. Phase 10 - SSE Realtime Projection

Read:

```text
11-sse-implementation.md
10-runner-event-consumer.md
09-test-run-implementation.md
```

Implement:

```text
TestRunSseController
TestRunSseService
SseReplayService
SseEmitterRegistry
SseHeartbeatService
SseEventSerializer
```

Important:

1. Use Spring MVC `SseEmitter`.
2. SSE is read-only.
3. Permission check happens before opening stream.
4. Replay reads `runner_events`, not Redis.
5. SSE event id equals `runner_events.sequence_no`.
6. Heartbeats are not persisted and do not consume sequence numbers.
7. Runner consumer broadcasts after DB commit.

Done when:

1. User can subscribe to TestRun events.
2. Reconnect with Last-Event-ID replays missed events.
3. Unauthorized users get 404/403 according to policy.
4. Disconnect cleanup works.
5. Large/raw/sensitive payloads are not sent through SSE.

---

## 12. Phase 11 - Export Jobs

Read:

```text
12-export-job-implementation.md
06-dataset-implementation.md
09-test-run-implementation.md
13-security-masking-implementation.md
```

Implement:

```text
ExportJob
ExportJobService
ExportJobWorker
ExportDownloadService
XlsxExportFileWriter
DatasetExportService
TestRunExportService
```

Important:

1. Dataset export and TestRun result export share ExportJob.
2. Export files are generated from persisted authorized data.
3. Download checks permission again.
4. Internal file path is never exposed.
5. Excel output uses formula injection sanitizer.
6. TestRun result export defaults to FINAL result.

Done when:

1. DatasetVersion export works.
2. TestRun result export works.
3. Export status transitions are tested.
4. Download permission is tested.
5. Export file contains no plaintext secrets or unsanitized raw data.

---

## 13. Phase 12 - Integration Hardening

Read all changed module docs again.

Do:

1. Run full test suite.
2. Run Flyway from empty DB.
3. Test security boundaries manually.
4. Test masking with deliberately secret-bearing payloads.
5. Test a full flow:
   - login
   - create workspace/project
   - create dataset
   - create secret/provider
   - create chatbot target
   - create evaluator
   - preflight run
   - create run
   - runner consumes command
   - runner posts events
   - SSE receives events
   - result review
   - export results
6. Check OpenAPI output.
7. Check logs for secret leaks.

Done when:

1. App starts from clean database.
2. All migrations run.
3. All core flows pass.
4. Logs are safe.
5. API contract and backend implementation docs still match.

---

## 14. AI Coding Agent Prompt Order

Use this exact order for code generation tasks:

```text
1. Implement Spring Boot 4 foundation from 00/01/02/13.
2. Implement auth/security/permission from 03.
3. Implement workspace/project core from 04, but defer default dataset schema integration until dataset schema exists.
4. Implement dataset schema/dataset/version/row/import from 06.
5. Revisit project creation and setup-status from 04 to integrate default dataset schema.
6. Implement secret/provider from 05.
7. Implement chatbot target from 07.
8. Implement evaluator from 08.
9. Implement TestRun preflight/snapshot/state/result/review from 09 without Redis execution first.
10. Implement runner Redis/internal APIs/event ingestion from 10.
11. Implement SSE from 11.
12. Implement export jobs from 12.
13. Run integration/security/masking pass across all modules.
```

For each step, ask the coding agent to produce:

```text
1. Flyway migration if schema changes.
2. Entity/repository.
3. Request/response DTO records.
4. Service interface.
5. Service implementation.
6. Mapper/assembler if needed.
7. Controller.
8. Tests.
9. Short checklist of security/masking/permission decisions.
```
