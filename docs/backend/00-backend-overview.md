# VinFast QC Copilot - Backend Overview

Version: `v0.2`  
Status: Spring Boot 4.0.7-first Implementation Spec  
Applies to: `apps/api`  
Aligned with: PRD v0.7, ERD v0.5, API Contracts `00` to `10`  
Replaces: Backend docs v0.1

---

## 1. Purpose

This document defines the backend implementation direction for VinFast QC Copilot.

It is written for:

1. AI coding agents generating backend code.
2. Backend developers implementing Spring Boot modules.
3. Reviewers checking whether generated code follows Spring Boot 4.0.7 and the product contracts.

This file is not an API contract. The API contract remains under `docs/api`.

---

## 2. Spring Boot 4.0.7 Baseline

The backend targets the dependencies configured in `pom.xml`:

| Area | Baseline |
| --- | --- |
| Java | Java 21 |
| Spring Boot | 4.0.7 |
| Spring Framework | 7.x |
| Jakarta | Jakarta EE 11 / Servlet 6.1 baseline |
| Web stack | Spring Web MVC |
| Security | Spring Security 7.x |
| Persistence | Spring Data JPA 4.x + Hibernate ORM 7.x |
| Database | PostgreSQL |
| Migration | Flyway 11.x |
| Redis | Spring Data Redis 4.x + Lettuce |
| API docs | springdoc-openapi 3.x |
| Excel | Apache POI 5.5.x |
| AI provider helper | Spring AI 2.0.x Google GenAI starter |
| Mapping | MapStruct 1.6.x |
| Boilerplate | Lombok |

Hard rules:

1. Use `jakarta.*` imports, never `javax.*`.
2. Do not generate Spring Boot 2.x or 3.x deprecated APIs.
3. Do not invent framework wrappers when Spring already provides the feature.
4. Use Spring Boot auto-configuration unless the project has a documented reason to override it.
5. Use Spring abstractions directly where they are already stable and expressive.
6. Put custom code only around VFQC domain rules, not around framework basics.

Examples:

| Need | Use | Do not invent |
| --- | --- | --- |
| Password hashing | `PasswordEncoder` | `PasswordHasher` |
| JWT resource server | `oauth2ResourceServer().jwt()` | Custom JWT parsing filter for user APIs |
| HTTP client | `RestClient` | Random Apache/OkHttp wrapper |
| Request validation | Jakarta Bean Validation | Manual null/blank checks everywhere |
| Error body | Spring `ProblemDetail` | Random error maps per controller |
| Transactions | `@Transactional` | Manual transaction manager calls |
| Pagination internals | Spring Data `Pageable` / `Page` | Hand-rolled pagination engine |
| Async jobs | `TaskExecutor`, `@Async`, scheduled worker | Raw thread creation in controllers |
| Config binding | `@ConfigurationProperties` | Static config constants reading env manually |
| Time | `Clock` bean | Direct `OffsetDateTime.now()` everywhere |
| Actuator/metrics | Spring Boot Actuator + Micrometer | Custom health endpoints unless domain-specific |

---

## 3. Application Boundary

The product has three services:

```text
apps/web      React frontend
apps/api      Spring Boot 4 backend
apps/runner   Node.js Promptfoo runner
```

The backend owns:

1. Authentication and authorization.
2. Workspace and project state.
3. Secret metadata and encrypted secret values.
4. Provider metadata and provider test logs.
5. Dataset schemas, versions, rows, imports and exports.
6. Chatbot target configuration and response extraction.
7. Evaluator configuration and immutable evaluator versions.
8. TestRun creation, immutable snapshots and state machine.
9. Redis Stream command publishing and runner event ingestion.
10. SSE event replay and live fanout.
11. Result persistence, QC review and comparison matrix.
12. Export jobs and artifact metadata.
13. Sensitive data masking before persistence/exposure.

The backend must not delegate domain decisions to the frontend or runner.

---

## 4. API Surfaces

### 4.1. Frontend-facing API

```text
Base path: /api/v1
Auth: user access token
Security chain: normal user chain
```

Rules:

1. Requires user authentication except explicitly public auth endpoints.
2. Must never return plaintext secrets.
3. Must check workspace/project permissions before reads and mutations.
4. Must return ProblemDetail-compatible errors.

### 4.2. Runner internal API

```text
Base path: /internal/runner
Auth: runner service token
Security chain: runner chain
```

Rules:

1. Never expose `/internal/runner/**` to normal frontend users.
2. Runner token is not a user token.
3. Runtime secrets may be returned only to authenticated runner internal APIs.
4. Runtime secret bundles must never be logged, persisted in Redis, persisted in RunnerEvent, or exposed through SSE.

---

## 5. Anti-Reinvention Rule

AI coding agents must not create custom infrastructure when Spring already provides it.

Allowed custom code:

1. VFQC domain state machines.
2. VFQC permission matrix.
3. VFQC sensitive-data masking policy.
4. VFQC target/evaluator configuration model.
5. VFQC run snapshot hashing.
6. VFQC comparison matrix assembly.
7. VFQC artifact storage path policy.

Not allowed unless justified:

1. Custom password hashing abstraction.
2. Custom JWT parsing filter for user APIs.
3. Custom validation framework.
4. Custom JSON serializer wrapper around Jackson for normal DTOs.
5. Custom transaction helper.
6. Custom pageable implementation for simple lists.
7. Custom HTTP client stack when `RestClient` is enough.
8. Custom health endpoint replacing Actuator health.

---

## 6. Backend Documentation Map

```text
docs/backend/
  00-backend-overview.md
  01-package-architecture.md
  02-domain-model-mapping.md
  03-auth-permission-implementation.md
  04-workspace-project-implementation.md
  05-secret-provider-implementation.md
  06-dataset-implementation.md
  07-chatbot-target-implementation.md
  08-evaluator-implementation.md
  09-test-run-implementation.md
  10-runner-event-consumer.md
  11-sse-implementation.md
  12-export-job-implementation.md
  13-security-masking-implementation.md
```

Recommended reading order for coding agents:

1. `docs/api/00-api-overview.md`
2. `docs/api/01-api-conventions.md`
3. `docs/backend/00-backend-overview.md`
4. `docs/backend/01-package-architecture.md`
5. `docs/backend/02-domain-model-mapping.md`
6. The module-specific backend file.
7. The matching API contract.
8. ERD v0.5.

If API docs, ERD and backend docs disagree, stop and update docs before coding.

---

## 7. Coding Style

Rules:

1. Controllers are thin HTTP adapters.
2. Services implement use cases.
3. Services may use Spring Data repositories, Spring services and domain validators.
4. Entity classes are persistence models, not API DTOs.
5. Request/response DTOs are Java records.
6. Use MapStruct for normal entity-to-response mapping.
7. Use assemblers only for aggregate/computed responses.
8. Use `@Transactional` at service implementation boundaries.
9. Use `@Transactional(readOnly = true)` for read-only service methods.
10. Use `Clock` bean for time.
11. Use `ProblemDetail` through a global exception handler.
12. Use Spring's `PasswordEncoder` for password hashing.
13. Use Spring Security filter chains, not ad-hoc authentication in controllers.
14. Use `RestClient` for synchronous outbound HTTP.
15. Use `StringRedisTemplate` or configured `RedisTemplate<String, String>` for Redis Streams.
16. Use Spring Boot Actuator for health/readiness/liveness.

---

## 8. Error Handling Baseline

Use Spring's `ProblemDetail` as the error body foundation.

Backend may add stable extension fields:

```json
{
  "type": "https://vfqc.vinfast.vn/problems/project-name-duplicate",
  "title": "Project name already exists",
  "status": 409,
  "detail": "A project with the same normalized name already exists in this workspace.",
  "instance": "/api/v1/workspaces/{workspaceId}/projects",
  "code": "PROJECT_NAME_DUPLICATE",
  "messageKey": "error.project.nameDuplicate",
  "traceId": "01J0Z7Y4Y4F9E1S2Y9W8Q7P6A5"
}
```

Rules:

1. Do not return raw exception messages.
2. Do not put secrets into `detail`.
3. Do not build different error shapes per module.
4. Keep `code` stable for frontend i18n.
5. Use `@RestControllerAdvice` for global exception handling.
6. Use `MethodArgumentNotValidException` handling for validation errors.

---

## 9. Security Baseline

Use two separate Spring Security chains:

1. Runner internal chain for `/internal/runner/**`.
2. User API chain for `/api/**`.

User authentication modes supported by dependencies:

1. Email/password login issuing project-owned JWT.
2. OAuth2/OIDC login if enabled later.
3. JWT resource server validation for API requests.

Password rules:

1. Use Spring Security `PasswordEncoder`.
2. Recommended bean: `PasswordEncoderFactories.createDelegatingPasswordEncoder()`.
3. Store encoded password only.
4. Never log plaintext password.
5. Never create `PasswordHasher` or custom hashing helper unless there is a concrete migration need.

JWT rules:

1. Use Spring Security OAuth2 Resource Server for bearer tokens.
2. Use `JwtDecoder` for validating incoming JWTs.
3. Use `JwtEncoder` if the backend issues its own JWTs.
4. Use `JwtAuthenticationConverter` to map JWT claims to `CurrentUser`.
5. Do not write a custom user JWT parsing filter.

---

## 10. Data and Transaction Baseline

Persistence rules:

1. Use Spring Data JPA repositories.
2. Use Flyway for schema migrations.
3. Use Hibernate ORM 7 JSON support consistently for JSONB fields.
4. Use `UUID` for public aggregate IDs.
5. Use `Long` for high-volume child rows.
6. Use soft delete where ERD has `deleted_at`.
7. Do not expose entities from controllers.
8. Do not mutate immutable versions after publication/creation.

Transaction rules:

1. One service method = one use case transaction.
2. Do not open transactions in controllers.
3. Do not perform slow external calls inside a DB transaction unless explicitly required.
4. Publish Redis commands after the DB state exists.
5. Persist runner events before SSE broadcast.

---

## 11. Observability Baseline

Use Spring Boot Actuator and Micrometer.

Recommended endpoints:

```properties
management.endpoints.web.exposure.include=health,info,metrics,prometheus
management.endpoint.health.probes.enabled=true
```

Rules:

1. Logs are English and technical.
2. Logs must include trace ID where available.
3. Do not log dataset row content, raw prompts, raw responses, secrets or Authorization headers.
4. Use metrics for job counts, run durations, queue lag and runner heartbeat status.

---

## 12. Dependency Notes

POM action items:

1. Keep Spring Boot parent at `4.0.7` unless intentionally upgrading.
2. Keep Java at `21`.
3. Prefer Boot-managed versions for Boot-managed dependencies.
4. Keep `mapstruct.version=1.6.3`.
5. Keep `spring-ai.version=2.0.0` unless compatibility is verified.
6. Upgrade `springdoc-openapi-starter-webmvc-ui` from `3.0.2` to `3.0.3`.
7. If importing `testcontainers-bom`, define `${testcontainers.version}` explicitly or remove the custom import and let Boot manage compatible test dependencies.

---

## 13. Definition of Done for Backend Codegen

A generated module is acceptable only if:

1. It compiles with Spring Boot 4.0.7 and Java 21.
2. It uses `jakarta.*` imports.
3. It uses Spring built-ins before custom infrastructure.
4. It follows package structure in `01`.
5. It maps entities according to `02`.
6. It checks permissions before sensitive actions.
7. It sanitizes data before persistence/exposure where required.
8. It has transactional service boundaries.
9. It does not log secrets.
10. It includes tests using Boot test slices where practical.
