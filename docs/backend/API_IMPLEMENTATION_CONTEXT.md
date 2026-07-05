# Backend API Implementation Context

Last checked: 2026-07-05 21:03 Asia/Bangkok.

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

Result: build success. Maven compiled the backend and ran 42 tests successfully.

Coverage currently includes compile wiring, auth email-token service behavior, registration/verification/password-reset service flows, admin user invite service flow, Auth controller delegation, Secret/Provider controller delegation, request DTO validation, secret encryption, secret preview safety, secret scope lookup rejection, provider config sanitization, provider create sanitization/scope lookup, provider test history limit clamping, provider test log sanitization, provider status updates, request-shape tests for the three provider clients, and a PostgreSQL Testcontainers + `@SpringBootTest` + MockMvc integration proof for Phase 5 endpoint/security/persistence behavior.

## Current Source Shape

Implemented Java packages under `apps/api/src/main/java/vn/vinfast/vfqc/api`:

- `auth`
- `common`
- `dataset`
- `provider`
- `project`
- `secret`
- `user`
- `workspace`

Not found as implemented source packages yet:

- `target` / `chatbot target`
- `evaluator`
- `testrun`
- `runner`
- `sse`
- `export`

Current migrations:

- `apps/api/src/main/resources/db/migration/V1__setup_data.sql`
  - Creates tables through users/auth/workspace/project/dataset.
  - Also creates `secrets`, `llm_providers`, and `llm_provider_test_logs`; source packages now map these existing tables.
- `apps/api/src/main/resources/db/migration/V2__auth_email_tokens.sql`
  - Creates `auth_email_tokens` for email verification, password reset, and admin invite flows.
  - Stores token hashes only and tracks purpose/status/expiry/audit metadata.

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
- Unit tests required by docs for masking and naming.
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
- OAuth2 Resource Server JWT validation for `/api/**`.
- Separate runner service-token chain for `/internal/runner/**`.
- `@AuthenticationPrincipal CurrentUser` mapping.
- Workspace/project permission service skeletons with real checks for current modules.
- Focused auth unit/controller tests cover token issuing/consumption, pending-login rejection, register mail, generic forgot-password response, password reset, and admin invite.

Still missing or not obvious:

- Security tests for `/api/**` vs `/internal/runner/**` boundary.
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

Still missing or partial:

- Tests for CRUD, membership permission, hidden 404 behavior, archive/delete, and default dataset schema creation.
- Setup status is intentionally partial: it counts datasets and active dataset versions only; target/evaluator/provider counts are hardcoded zero.

### Phase 4 - Dataset Schema, Dataset Versioning and Source Data

Status: partial MVP manual dataset flow implemented.

Implemented:

- `DatasetSchema`, `DatasetSchemaColumn`, `Dataset`, `DatasetVersion`, `DatasetRow`.
- Dataset schema bootstrap for default project schema with `question` and `expected` columns.
- Project dataset schema and variable APIs.
- Dataset CRUD for project scope.
- Initial draft version creation on dataset create.
- Draft creation from current active version.
- Row add/patch for draft versions.
- Publish draft to active and archive old active.
- Discard draft.
- Required/unknown column validation.

Still missing from Phase 4 docs:

- `DatasetImportJob`.
- `DatasetImportService`.
- `DatasetDraftService` as a separate service.
- `DatasetRowService` as a separate service.
- `DatasetVariableService` as a separate service.
- Excel import preview -> mapping -> confirm.
- Dataset export source-data flow.
- Tests for active immutability, row count correctness, publish/discard, schema validation, and project creation default schema.

Potential follow-up check:

- `dataset_rows.row_hash` exists in DB but current row service code does not populate it.

### Phase 5 - Secrets and LLM Providers

Status: implemented as an MVP slice, with focused unit tests and one full integration proof.

Implemented:

- `secret` entity/repository/mapper/controller/service/service impl.
- Workspace and personal secret APIs.
- AES-GCM `SecretEncryptionService` with versioned `v1:<iv>:<ciphertext>` payloads.
- `RuntimeSecretResolver` and active-secret lookup boundary.
- Secret responses expose fixed masked preview only, never plaintext or encrypted value.
- `provider` entity/repository/mapper/controller/service/client/validator packages.
- Workspace and personal LLM provider APIs.
- Provider types: `OPENAI_COMPATIBLE`, `GOOGLE_GENAI`, `CUSTOM_HTTP`.
- Provider config validation/sanitization before persistence.
- Provider test flow with real client boundary and sanitized `llm_provider_test_logs`.
- Request-shape tests cover `OPENAI_COMPATIBLE`, `GOOGLE_GENAI`, and `CUSTOM_HTTP`; Google GenAI sends the API key via `x-goog-api-key` header instead of query string.
- Provider response assembler batch-loads secret summaries for list responses to avoid N+1 lookups.
- Controller unit tests cover Secret/Provider delegation and collection wrapping.
- Request validation tests cover required Secret/Provider fields.
- Secret scope tests cover workspace/personal mismatch and hidden personal secrets.
- Provider test history clamps `limit` to `1..100` and checks provider visibility before querying logs.
- `Phase5SecretProviderIntegrationTest` runs with PostgreSQL Testcontainers, Flyway, JPA validation, Spring Security JWT, MockMvc, real services/repositories, and a fake provider client boundary.
- Integration coverage checks unauthenticated 401, encrypted secret persistence without plaintext leaks, fixed masked preview, cross-workspace secret/provider scope rejection, provider test success/failure status updates, and sanitized provider test history/log storage.
- `HttpClientConfig` provides a shared `RestClient.Builder` bean for provider clients.
- `JacksonConfig` provides a shared `ObjectMapper` bean; tests inject it via Lombok `@RequiredArgsConstructor` and Spring `@TestConstructor`.
- `spring.ai.model.chat` and `spring.ai.model.embedding.text` default to `none` in `application.yaml` so the app does not require a global Gemini key at startup; runtime provider keys are resolved through `Secret`.

Still missing or partial:

- Provider clients are minimal MVP RestClient calls; request-shape/preview tests exist, but no full contract tests against real provider fixtures.
- Existing module hardening tests for auth/project/dataset remain incomplete.

### Phase 6+ - Target, Evaluator, TestRun, Runner, SSE, Export

Status: not implemented in source.

No source packages/controllers/services found for:

- Chatbot target
- Evaluator
- TestRun core/results/review
- Runner command/event ingestion
- SSE realtime projection
- Export jobs/downloads

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

## Coding Style And Conventions

Follow the current project style when adding new backend code:

- Package by feature: `feature/controller`, `feature/dto`, `feature/entity`, `feature/enums`, `feature/mapper`, `feature/repository`, `feature/service`, `feature/service/impl`.
- Service contracts should be interfaces in `feature/service`; implementations go under `feature/service/impl` and should be named `XxxServiceImpl`.
- Add Javadoc for service interface methods so callers can understand intent, permissions, transaction expectations, and important side effects without opening the implementation.
- DTOs should be Java records where practical. Request DTO fields should use Jakarta Bean Validation and Swagger/OpenAPI schema messages/descriptions so API docs are understandable.
- Controllers should include Swagger annotations such as `@Tag`, `@Operation`, and useful `@ApiResponse` entries.
- Use Lombok for boilerplate already accepted in the codebase: `@RequiredArgsConstructor`, `@Slf4j`, entity getters/setters where consistent with existing entities.
- Use MapStruct for simple entity-to-DTO mapping. Use assemblers only when a response is an aggregate/read model and MapStruct alone would hide orchestration.
- Keep controllers thin: validation, authentication principal, path/body binding, and delegation only. Business rules belong in services/domain validators.
- Keep code clean and OOP-oriented: focused classes, intention-revealing names, constructor injection, small methods, guard clauses, no god services, no hardcoded magic values.
- Prefer immutable DTO/value objects, final injected dependencies, and coding to interfaces (`List`, `Map`) rather than concrete implementations.
- Avoid N+1 queries. Use repository projections, `@EntityGraph`, join fetches, or focused queries for read models before adding loops over lazy relationships.
- Keep transactions in the service layer. Use `@Transactional(readOnly = true)` for reads and normal `@Transactional` for writes.
- Do not expose JPA entities directly through API responses; controllers return DTO records/response models only.
- Validate and sanitize all external input. Never log passwords, plaintext secrets, raw external payloads, or unsanitized dataset/business-sensitive content.
- Follow Spring Boot built-ins first: `ProblemDetail`, `@ConfigurationProperties`, Spring Security, Spring Data JPA, `RestClient`, `SseEmitter` where specified by docs.

## Recommended Next Work

1. Add focused tests for what already exists before building later modules:
   - masking and naming unit tests
   - auth login/refresh/logout and cookie behavior
   - auth registration/forgot/reset endpoint integration tests through MockMvc and PostgreSQL
   - `/api/**` vs `/internal/runner/**` security boundary
   - admin API role boundary for `/api/v1/admin/**`
   - workspace/project permission and hidden 404 behavior
   - create project creates default dataset schema transactionally
   - dataset draft/publish/discard and ACTIVE immutability

2. Close Phase 1 gaps if they are still required:
   - storage abstraction with path traversal protection
   - Redis stream names/constants
   - common JSON/time helpers only if code actually needs them

3. Finish Phase 4 MVP scope:
   - decide whether Excel import preview -> mapping -> confirm is in MVP now
   - populate or remove/justify `dataset_rows.row_hash`
   - add import job/service if keeping the docs as source of truth

4. Harden Phase 5:
   - add broader provider client contract fixtures for response/error parsing
   - add more service tests for provider update cross-workspace/personal scope rejection
   - expand Testcontainers coverage for update/rotate/delete and workspace permission boundaries

5. Start Phase 6 chatbot target only after Phase 5 endpoint behavior is verified, because target config depends on secret references.

6. Harden auth mail flows before production use:
   - add rate limiting for login, forgot password, and token verification attempts
   - add SMTP configuration documentation and delivery failure policy
   - decide final product policy for `ADMIN_ONLY` vs `SELF_VERIFY` registration mode
   - add cleanup job for expired/revoked/used email tokens if token volume grows

## Caveats For Next Agent

- Worktree had many `apps/api` files already modified/untracked when this context was written. Treat them as existing user/session work; do not revert.
- `apps/api/target` may exist because `mvnw test` was run.
- Tests passing now cover compile, auth mail/register/forgot/reset service and controller slices, email-token hashing/expiry/consume behavior, admin invite service flow, controller delegation, DTO validation, encryption round-trip, secret create safety, fixed masked preview safety, secret scope rejection, provider config sanitization, provider create sanitization, provider client request shapes, provider test history clamping, provider test log sanitization, and Phase 5 Spring MVC/security/PostgreSQL integration coverage.
- JavaMail usage follows Spring Boot 4.0.7/Spring Framework 7 docs: rely on `JavaMailSender` auto-configuration when `spring.mail.host` is configured; use `MimeMessage`/`MimeMessageHelper`; set SMTP timeouts through `spring.mail.properties[...]`.
