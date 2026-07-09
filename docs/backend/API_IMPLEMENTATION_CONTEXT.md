# Backend API Handoff Context

Last checked: 2026-07-09 00:08 Asia/Bangkok.

Read this first in a new backend session. It is intentionally short; detailed module specs remain in `docs/backend/README.md`, `docs/backend/IMPLEMENTATION_ORDER.md`, and `docs/backend/00..13-*.md`.

## TL;DR

- Backend lives in `apps/api`.
- Spring Boot: 4.0.7.
- Java: 21.
- Database: PostgreSQL via Flyway SQL migrations.
- Test infra: Maven, JUnit, MockMvc, PostgreSQL Testcontainers, Redis Testcontainers.
- Current full backend verification after migration squash: `rtk bash mvnw clean test` passed with 133 tests.
- Current migrations were squashed before any real deployed backend data exists: 9 phase-by-phase files became 4 domain files.
- Important convention: run backend commands from `apps/api` and prefix verbose commands with `rtk`.

## Quick Verify

Run from `apps/api`:

```bash
rtk bash mvnw test
```

Useful targeted suites:

```bash
rtk bash mvnw -Dtest=DatasetImportIntegrationTest,Phase11ExportJobIntegrationTest test
rtk bash mvnw -Dtest=Phase9RunnerIntegrationTest,Phase10SseIntegrationTest,Phase11ExportJobIntegrationTest test
rtk bash mvnw -Dtest=ResourceNameNormalizerTest,OpenApiIntegrationTest test
```

Expected current baseline: Flyway applies 4 migrations from an empty PostgreSQL Testcontainer schema and all 133 backend tests pass.

## Source Map

Main Java package:

`apps/api/src/main/java/vn/vinfast/vfqc/api`

Implemented feature packages:

- `auth`
- `common`
- `dataset`
- `evaluator`
- `exportjob`
- `provider`
- `project`
- `runner`
- `secret`
- `target`
- `testrun`
- `user`
- `workspace`

Current migration files:

- `V1__core_identity_workspace_dataset.sql`
  - `users`, auth refresh/email tokens, workspaces, workspace members, projects, dataset schemas, datasets, dataset import jobs, dataset versions, dataset rows.
- `V2__secrets_providers_targets_evaluators.sql`
  - secrets, LLM providers, provider test logs, chatbot targets/examples, evaluators/versions/rules.
- `V3__test_runs_runner_execution.sql`
  - test runs, run targets, evaluator snapshots, testcase results, reviews, runner instances/commands/events.
- `V4__export_jobs.sql`
  - shared export jobs for dataset version and TestRun result exports.

Migration note: the squash is acceptable only because no real backend deployment/data exists yet. After a shared environment exists, do not rewrite applied Flyway migrations; use additive migrations instead.

## Current Phase Status

| Phase | Status | Notes |
| --- | --- | --- |
| 0 Boot baseline | Mostly done | Java 21, Boot 4.0.7, Maven compile/test works. |
| 1 Common foundation | Partial | Error handling, security, masking, naming, mail, storage exist; JSON/time/Redis constants remain only if needed. |
| 2 User/Auth | MVP done | Email/password JWT, refresh tokens, email verification, password reset, admin invite, personal workspace onboarding. |
| 3 Workspace/Project | Mostly done | CRUD, membership, permissions, soft delete/archive, setup status basics. |
| 4 Dataset | MVP done | Schema bootstrap, versions, rows, publish/discard, synchronous capped Excel import. |
| 5 Secrets/Providers | MVP done | Encrypted secrets, masked previews, provider config sanitization, provider tests/logs. |
| 6 Chatbot Target | Backend MVP done | cURL inspect, CRUD, test connection, examples, extraction, readiness, SSRF checks. |
| 7 Evaluator | Backend MVP done | CRUD, immutable versions, rule validation, LLM judge validation, setup status. |
| 8 TestRun Core | Backend MVP done | Preflight, create snapshot, list/detail/cancel, results, review, matrix. |
| 9 Runner Internal APIs | Mostly done | Runner register/heartbeat/config/secrets/control/events, Redis command/event plumbing, DLQ, Redis event polling scheduler opt-in. |
| 10 SSE | MVP done | TestRun event stream with auth, replay, masking, heartbeat, in-memory fanout. |
| 11 Export Jobs | MVP done | Async local-storage XLSX export jobs and safe downloads. |
| 12 Hardening | Backend pass done | OpenAPI generation, naming tests, dataset import/export edge tests, full suite green after migration squash. |

## Current Coverage Highlights

The backend suite currently covers:

- OpenAPI generation and representative path/security metadata.
- Resource name normalization and invalid-name rejection.
- Auth register/verify/login/refresh/logout/forgot/reset and admin invite flows.
- Personal workspace onboarding.
- User/admin/workspace/project permission boundaries and hidden 404 behavior.
- Secret encryption, masked preview, rotate/delete safety, and secret/provider scope rejection.
- Provider config sanitization, request-shape tests, test log sanitization, and history limit clamping.
- Dataset schema bootstrap, row hash add/patch, draft lifecycle, publish/archive, row-count recalculation, required/unknown cell validation.
- Excel dataset import preview/confirm, schema extension, viewer denial, invalid confirm state, required-column rollback, empty-sheet rejection, duplicate-header rejection, larger workbook preview clamp and row persistence.
- cURL parsing/sanitization, target SSRF URL validation, target inspect/test/extraction behavior.
- Evaluator create/version/update/validation/setup-status, duplicate-name, soft-delete, invalid provider, and MIXED config validation.
- TestRun preflight/create snapshot/state/review/comparison matrix behavior.
- Runner command/config/secret/event/cancel/DLQ behavior.
- SSE auth/permission/replay/invalid cursor/masking/heartbeat behavior.
- Export job creation/status/download, formula injection neutralization, token masking, permission re-check, expired export 404, missing stored file 404.

## Important Implementation Conventions

- Package by feature: `feature/controller`, `dto`, `entity`, `enums`, `mapper`, `repository`, `service`, `service/impl`.
- Controllers stay thin; business rules live in services/validators.
- Controllers and request/response contracts should include Swagger/OpenAPI annotations such as `@Tag`, `@Operation`, useful `@ApiResponse`, and `@Schema` descriptions.
- Service contracts are interfaces in `feature/service`; implementations live under `feature/service/impl`, are named `XxxServiceImpl`, and interface methods should have Javadoc for intent, permissions, transaction expectations, and important side effects.
- Keep JPA entities out of API responses.
- Use DTO records where practical.
- Keep UUID reference fields in responses for compatibility; add compact summary objects beside them when the frontend needs display data.
- Batch-load response summaries in assemblers/services; do not load summaries inside mappers or loops.
- Use Jakarta validation on request DTO fields.
- Use Lombok for accepted boilerplate: `@RequiredArgsConstructor`, `@Slf4j`, `@Getter`, `@Setter`, `@Builder`, `@AllArgsConstructor`, `@NoArgsConstructor` for entities. Entities use standard non-chaining setters; do not use `@Accessors(chain = true)`.
- Use MapStruct for simple entity-to-DTO mapping. Use assemblers only when a response is an aggregate/read model and MapStruct would hide orchestration.
- Do not use inline fully-qualified class names in code; add imports and use simple type names.
- Prefer immutable DTO/value objects, final injected dependencies, constructor injection, focused classes, guard clauses, and code to interfaces such as `List`/`Map`.
- Avoid hardcoded magic values and god services.
- Use `ProblemDetail` through the global error handler.
- Use `@ConfigurationProperties`; avoid `@Value` field injection.
- Follow Spring Boot built-ins first: Spring Security, Spring Data JPA, `RestClient`, `SseEmitter`, `ProblemDetail`, and `@ConfigurationProperties`.
- Use service-layer transactions; `@Transactional(readOnly = true)` for reads.
- Use `@Modifying @Query` bulk updates for token revocation instead of find-then-loop patterns.
- Use `ApplicationEventPublisher` plus `@TransactionalEventListener(AFTER_COMMIT)` plus `@Async` for side effects such as mail/export workers.
- Service implementations should use `@Slf4j`; log state changes with structured `log.info(...)` and handled failures with `log.warn(...)`. Read-only queries do not need logging unless useful for debugging.
- Use `AuthLinkBuilder` for email URL construction and `AuthLinkBuilder.normalizeEmail()` for auth/user email normalization.
- Use `DatasetCellValidator` for dataset row schema validation; keep dataset operations split across `DatasetService`, `DatasetDraftService`, and `DatasetRowService`.
- Never log passwords, plaintext secrets, raw external payloads, unsanitized cURL, runtime secrets, or business-sensitive raw result bodies.
- Avoid N+1 queries in read models; use focused repository methods or batch summary services.
- Integration test config lives in `src/test/resources/application-test.yaml`; do not hardcode properties in `@SpringBootTest`.

## API Surface Summary

Public API groups:

- Auth: register, login, refresh, logout, me, verify email, forgot/reset password.
- Admin: create invited users.
- Workspace/project: CRUD, members, archive/delete, setup status.
- Dataset: schema/variables, datasets, versions, rows, publish/discard, Excel import jobs, dataset version export jobs.
- Secrets/providers: workspace and personal secrets/providers, rotate/disable/delete, provider test/logs.
- Chatbot targets: cURL inspect, CRUD, test, examples, response extraction, readiness.
- Evaluators: CRUD, versions, validate, field sources, operators.
- Test runs: preflight, create, list/detail, cancel, results, review, comparison matrix, export jobs.
- Export jobs: status and download.

Internal runner API:

- `POST /internal/runner/instances/register`
- `POST /internal/runner/instances/heartbeat`
- `GET /internal/runner/test-runs/{testRunId}/config`
- `POST /internal/runner/test-runs/{testRunId}/runtime-secrets`
- `GET /internal/runner/test-runs/{testRunId}/control-state`
- `POST /internal/runner/events`

Runner Redis event polling:

- Backend can consume `vfqc:runs:events` through `RunnerRedisEventConsumerScheduler`.
- It is disabled by default with `VFQC_RUNNER_REDIS_EVENTS_ENABLED=false`.
- Enable only when a runner is deliberately configured to publish events through Redis instead of the default HTTP event ingestion path.

## Recommended Next Work

1. Add remaining focused tests for existing backend behavior:
   - dataset draft/version row-count concurrency and duplicate-draft races
   - setup-status completeness once frontend-required checklist fields are final
   - TestRun and result list pagination/filter edge cases

2. Close Phase 1 only where real code needs it:
   - Redis stream names/constants
   - common JSON/time helpers

3. Finish non-MVP dataset scope if product still needs it:
   - project/workspace/personal dataset clone import source APIs
   - source-data export flow
   - async/large-file import storage beyond the current synchronous capped MVP

4. Continue frontend or integration work:
   - chatbot target CRUD/cURL inspect/test/extraction/readiness UI
   - dataset import/export UI
   - TestRun create/realtime/result review/export UI

5. Harden production deployment later:
   - shared object storage for exports if API instances scale horizontally
   - cleanup job for expired export files
   - distributed SSE fanout or sticky-session policy
   - auth rate limiting and SMTP delivery policy

## Caveats

- `apps/api/target` may exist because tests were run.
- Always check worktree state with `rtk git status --short` before editing or committing.
- Mail delivery is disabled in tests/local dry runs with `vfqc.mail.enabled=false`.
- Local export storage is MVP and not safe for multi-instance production unless storage/routing is handled.
- SSE live fanout is in-memory per API instance; DB replay works, multi-instance broadcast does not.
