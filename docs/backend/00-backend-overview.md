# VinFast QC Copilot - Backend Implementation Overview

Version: `v0.1`  
Status: Backend Implementation Spec Draft  
Applies to: `apps/api` Spring Boot backend  
Aligned with: PRD v0.7, ERD v0.5, API Contracts `00` to `10`

---

## 1. Purpose

This document defines the backend implementation direction for VinFast QC Copilot.

It is written for:

```text
1. AI coding agents that need enough context to generate backend code consistently.
2. Backend developers implementing Spring Boot modules.
3. Reviewers checking whether generated code follows the product/domain contracts.
```

This file is not an API contract. Public and internal API contracts are already defined in:

```text
docs/api/00-api-overview.md
docs/api/01-api-conventions.md
docs/api/02-auth-permission-contract.md
...
docs/api/10-runner-internal-api.md
```

This backend spec explains how those contracts should be implemented inside `apps/api`.

---

## 2. Backend Documentation Map

The backend implementation documentation is split by responsibility. Each file should be read together with the matching API contract and ERD section.

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

Recommended reading order for AI coding agents:

```text
1. docs/api/00-api-overview.md
2. docs/api/01-api-conventions.md
3. docs/backend/00-backend-overview.md
4. docs/backend/01-package-architecture.md
5. docs/backend/02-domain-model-mapping.md
6. The backend implementation file for the target module
7. The matching API contract file
8. ERD v0.5 section for affected tables
```

Rules:

```text
1. Do not treat backend docs as replacement for API contracts.
2. If API contract and backend implementation docs disagree, stop and update docs before coding.
3. Backend docs define code architecture, transaction boundaries, permission placement and implementation style.
4. API docs define external request/response contracts.
```

---

## 3. Backend Responsibility

The backend is the source of truth for VinFast QC Copilot.

The backend owns:

```text
1. Authentication and authorization.
2. Workspace and project state.
3. Secret and provider metadata.
4. Dataset schema, dataset versioning and row persistence.
5. Chatbot target configuration.
6. Evaluator configuration and versioning.
7. TestRun orchestration and state machine.
8. Redis Stream command production and event consumption.
9. SSE event publishing to frontend.
10. Result persistence, QC review and final metrics.
11. Export jobs and artifact metadata.
12. Sensitive data masking before persistence or exposure.
```

The backend must not delegate domain decisions to the frontend or runner.

Examples:

```text
Frontend may help parse cURL for UX.
Backend still validates and persists only sanitized structured target config.

Runner may execute Promptfoo.
Backend still owns locked run config, run status, result persistence and event ordering.

Frontend may render comparison matrix.
Backend still provides stable comparison result grouping for large runs.
```

---

## 4. Service Boundary

VinFast QC Copilot has three application services:

```text
apps/web
  React frontend used by QC/QA users.

apps/api
  Java Spring Boot backend.
  This document applies to this service.

apps/runner
  Node.js Promptfoo runner.
```

Backend exposes two API surfaces:

```text
1. Frontend-facing API
   Base path: /api/v1
   Auth: user access token

2. Runner internal API
   Base path: /internal/runner
   Auth: runner service token
```

Rules:

```text
1. Never expose /internal/runner/** as a normal frontend route.
2. Never return plaintext secrets from any frontend-facing endpoint.
3. Never store unsanitized raw cURL, Authorization headers, API keys, cookies or tokens in normal domain tables.
4. Store large generated Promptfoo artifacts in artifact storage, not directly in main domain tables.
5. Keep PostgreSQL as the source of truth. Redis is transport/cache/queue, not durable domain state.
```

---

## 5. Backend Technology Baseline

Use the versions already configured in the project build files. The implementation style assumes:

```text
1. Java 21.
2. Spring Boot.
3. Spring Web MVC.
4. Spring Security.
5. Spring Data JPA.
6. PostgreSQL.
7. Redis Streams.
8. Lombok.
9. MapStruct.
10. Jakarta Bean Validation.
11. Springdoc/OpenAPI or equivalent Swagger annotation support.
```

AI code generation rule:

```text
Do not invent framework versions in code comments or docs.
Read pom.xml / build.gradle when implementation starts and follow the actual project versions.
```

---

## 6. Language and Localization Policy

The platform serves Vietnamese QC users, but backend code should not hardcode Vietnamese user-facing text.

Backend should use English for:

```text
1. Java class names.
2. Package names.
3. Method names.
4. Logs.
5. Error codes.
6. ProblemDetail titles in default responses.
7. Validation message keys.
8. Internal comments and technical documentation.
```

Recommended API error shape:

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

Frontend can translate by using:

```text
code
messageKey
field-level validation errors
```

Rules:

```text
1. Do not hardcode Vietnamese business messages inside service/controller classes.
2. Do not return raw exception messages to users.
3. Logs should stay English and technical.
4. If Vietnamese copy is needed, put it in frontend i18n or a backend message catalog, not inline in business code.
```

---

## 7. Coding Style Principles

Backend code must optimize for correctness, traceability and AI-readability.

Core rules:

```text
1. Controllers are thin.
2. Services contain business use cases.
3. Service interfaces define use-case contracts.
4. Service implementations live under service/impl.
5. Entities are persistence models, not API DTOs.
6. Request/response DTOs are Java records.
7. MapStruct is used for entity/domain to response mapping.
8. Do not write manual toResponse(), toEntity() helper methods in services/controllers.
9. Transactions are declared at service implementation boundaries.
10. Logs are useful but not noisy.
11. Sensitive fields must never be logged.
12. Permission checks happen before data mutation or sensitive reads.
```

---

## 8. Service Interface Rule

Each feature module should expose service interfaces under:

```text
<module>/service/
```

Each implementation must live under:

```text
<module>/service/impl/
```

Example:

```text
project/
  service/
    ProjectService.java
    ProjectSetupStatusService.java
    impl/
      ProjectServiceImpl.java
      ProjectSetupStatusServiceImpl.java
```

Interface methods must have Javadoc.

Javadoc should explain:

```text
1. What the method does.
2. Main input meaning.
3. Return value meaning.
4. Important business rules.
5. Important exceptions or error conditions when useful.
```

Example:

```java
/**
 * Creates a new project inside a workspace and initializes its default PROJECT dataset schema.
 *
 * @param workspaceId workspace that will own the project
 * @param request project creation payload containing normalized name and optional description
 * @param currentUser authenticated user performing the operation
 * @return created project response with workspace id and timestamps
 * @throws ForbiddenException if the user cannot create projects in the workspace
 * @throws DuplicateResourceException if another active project has the same normalized name
 */
ProjectResponse createProject(UUID workspaceId, CreateProjectRequest request, CurrentUser currentUser);
```

Implementation classes should not duplicate obvious Javadoc from interfaces unless the implementation contains non-trivial algorithm details.

---

## 9. Logging Rule

All service implementations should use Lombok `@Slf4j`.

Example:

```java
@Slf4j
@Service
@RequiredArgsConstructor
class ProjectServiceImpl implements ProjectService {
  // ...
}
```

Logging guidelines:

```text
1. Log use-case start at info or debug depending on importance.
2. Log important state transitions.
3. Log external service calls at debug or info without sensitive payloads.
4. Log failures with enough identifiers to debug.
5. Do not log full request body if it may contain secrets, raw cURL, tokens or raw chatbot response.
6. Do not log every entity field.
7. Do not log inside tight loops for every row unless using sampled/debug logging.
```

Recommended examples:

```java
log.info("Creating project: workspaceId={}, requestedBy={}", workspaceId, currentUser.id());
log.info("Project created: projectId={}, workspaceId={}", project.getId(), workspaceId);
log.warn("Project setup blocked: projectId={}, blockers={}", projectId, blockerCodes);
log.error("Failed to consume runner event: testRunId={}, eventId={}, errorCode={}", testRunId, eventId, errorCode, ex);
```

Forbidden examples:

```java
log.info("Raw cURL: {}", rawCurl);
log.info("Authorization header: {}", authorization);
log.info("Secret value: {}", secretValue);
log.info("Promptfoo full config: {}", configJson);
```

---

## 10. Transaction Rule

Use `@Transactional` on service implementation methods that read or write database state.

Guidelines:

```text
1. Mutating use cases should use @Transactional.
2. Read-only use cases should use @Transactional(readOnly = true).
3. Do not put transaction boundaries in controllers.
4. Do not keep a database transaction open while doing slow external IO if avoidable.
5. Split DB state update and external execution when needed.
6. For async jobs, open transaction inside the async method or delegated service method, not in the caller only.
```

Examples:

```java
@Transactional
public ProjectResponse createProject(...) {
  // validate permission
  // normalize name
  // persist project
  // create default dataset schema
  // return mapped response
}

@Transactional(readOnly = true)
public ProjectSetupStatusResponse getSetupStatus(...) {
  // read project setup state
  // compute blockers
}
```

External IO caution:

```text
Do not call chatbot target, provider API, Promptfoo or large file export while holding a long database transaction unless the state change requires it.

Spring proxy caveats:

```text
1. Put @Transactional on public service implementation methods that are called through a Spring proxy.
2. Do not rely on @Transactional on private methods or same-class self-invocation.
3. If a method needs a separate transaction, move it to another Spring bean or call it through an injected proxied collaborator.
4. After-commit callbacks that update database state must delegate to a separate transactional service method, often with Propagation.REQUIRES_NEW.
5. Do not put transaction boundaries in controllers.
```
```

---

## 11. Mapper Rule

Use MapStruct for mapping between:

```text
1. Entity -> Response record.
2. Entity + related entities -> Detail response record.
3. Request record -> Entity only when the mapping is straightforward and safe.
4. Domain value object -> DTO.
```

Do not write ad-hoc mapping helpers in services such as:

```java
private ProjectResponse toResponse(Project project) { ... }
private Project toEntity(CreateProjectRequest request) { ... }
```

Allowed exceptions:

```text
1. Complex aggregation responses such as setup-status, dashboard summary or comparison matrix may be assembled in a dedicated assembler/component.
2. Mapping that requires permission-dependent visibility may use an assembler.
3. Sensitive masking should happen before mapping or inside dedicated masking component, not hidden inside random mapper code.
```

Recommended package:

```text
<module>/mapper/
```

Example:

```java
@Mapper(componentModel = "spring")
public interface ProjectMapper {
  ProjectResponse toResponse(Project project);
}
```

---

## 12. DTO Rule

Use Java records for request and response DTOs.

Recommended package:

```text
<module>/dto/request/
<module>/dto/response/
```

Request records must include validation annotations and OpenAPI schema examples.

Example:

```java
public record CreateProjectRequest(
    @NotBlank
    @Size(max = 120)
    @Schema(description = "Project display name", example = "VF Sales Chatbot")
    String name,

    @Size(max = 1000)
    @Schema(description = "Optional project description", example = "Evaluation project for sales chatbot")
    String description) {}
```

Rules:

```text
1. Do not use mutable DTO classes for normal API request/response.
2. Do not expose JPA entities from controllers.
3. Put examples on request fields where useful.
4. Use validation annotations on request records.
5. Keep response records stable and aligned with API contracts.
```

---

## 13. Controller Rule

Controllers should be thin HTTP adapters.

Controller responsibilities:

```text
1. Define route, method and content type.
2. Validate request body with @Valid.
3. Extract path/query parameters.
4. Resolve CurrentUser.
5. Call service interface.
6. Return ResponseEntity when status/header control is needed.
```

Controllers must not:

```text
1. Contain business workflows.
2. Access repositories directly.
3. Build entities manually.
4. Perform permission logic directly except simple annotations or guard integration.
5. Perform sensitive masking ad hoc.
```

Swagger/OpenAPI annotations should be used on controllers.

Example:

```java
@Tag(name = "Projects", description = "Workspace project management APIs")
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/projects")
@RequiredArgsConstructor
class ProjectController {

  private final ProjectService projectService;

  @Operation(summary = "Create project", description = "Creates a project and initializes the default dataset schema.")
  @ApiResponses({
      @ApiResponse(responseCode = "201", description = "Project created"),
      @ApiResponse(responseCode = "403", description = "User cannot create projects in this workspace"),
      @ApiResponse(responseCode = "409", description = "Project name already exists")
  })
  @PostMapping
  ResponseEntity<ProjectResponse> createProject(
      @PathVariable UUID workspaceId,
      @Valid @RequestBody CreateProjectRequest request,
      @AuthenticationPrincipal CurrentUser currentUser) {
    ProjectResponse response = projectService.createProject(workspaceId, request, currentUser);
    return ResponseEntity.created(URI.create("/api/v1/projects/" + response.id())).body(response);
  }
}
```

---

## 14. Lombok Rule

Use Lombok to avoid boilerplate in implementation classes and entities where appropriate.

Recommended annotations:

```text
@RequiredArgsConstructor
@Slf4j
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
```

Rules:

```text
1. Prefer @RequiredArgsConstructor for Spring dependency injection.
2. Do not write manual constructors only for dependency injection.
3. Do not use @Data blindly on JPA entities.
4. For JPA entities, prefer explicit @Getter/@Setter and controlled equals/hashCode strategy.
5. JPA entities must have a no-args constructor; prefer protected no-args constructors when possible.
6. Use @Builder on JPA entities carefully because it can bypass required invariants; prefer static factory methods or domain commands when creation rules are complex.
7. Avoid logging entities with generated toString if they may contain sensitive data or lazy associations.
8. Do not use @ToString on entities with lazy relationships or sensitive fields.
```

---

## 15. Async Rule

Use `@Async` only for use cases that are intentionally asynchronous.

Likely async areas:

```text
1. Export job execution.
2. Provider connection testing if implemented as background job.
3. Dataset import processing if large files are supported asynchronously.
4. Runner event fan-out if needed.
5. Non-critical audit side effects.
```

Rules:

```text
1. Async methods must not rely on caller transaction context.
2. Async method should call a transactional service method when database mutation is needed.
3. Async failures must be captured and persisted when the user needs status visibility.
4. Do not use @Async to hide slow code without status tracking.
5. Do not return null for async operations that need user-visible result; create a job resource instead.
```

Example:

```java
@Async
public void executeExportJob(UUID exportJobId) {
  exportJobWorker.runExportJob(exportJobId);
}
```

---

## 16. Redis Rule

Redis is relevant to backend modules that orchestrate runner execution.

Backend Redis responsibilities:

```text
1. Publish START_RUN and CANCEL_RUN commands to Redis Streams.
2. Consume runner events from Redis Streams.
3. Recover pending Redis Stream messages when consumers crash.
4. Move invalid or poison messages to DLQ when retry policy is exhausted.
5. Never put full secrets, full dataset rows, full Promptfoo config or large artifacts in Redis command payloads.
```

Redis is not required for basic CRUD modules such as workspace, project, dataset metadata or provider metadata unless caching is explicitly introduced later.

Rules:

```text
1. Keep Redis stream payloads compact and reference-based.
2. Store durable state in PostgreSQL.
3. Use Redis event id / database runner event sequence for traceability.
4. Do not build frontend-visible state directly from Redis.
5. Backend must persist runner events before broadcasting SSE.
```

---

## 17. Sensitive Data Policy

Sensitive data includes:

```text
1. Plaintext secret values.
2. Authorization headers.
3. API keys.
4. Cookies.
5. Bearer tokens.
6. Raw cURL containing secrets.
7. Runtime secret bundles.
8. Unsanitized Promptfoo config.
9. Unsanitized request/response artifacts.
```

Rules:

```text
1. Accept plaintext secret only in create/rotate/runtime-only flows.
2. Encrypt secret before persistence.
3. Never return plaintext secret after creation.
4. Mask sensitive request/response fields before persistence.
5. Mask before writing artifacts.
6. Mask before logging.
7. Store hashes for audit where required.
```

Recommended shared component:

```text
common/masking/VfqcSensitiveDataMasker.java
```

---

## 18. Backend Implementation Order

Recommended implementation order:

```text
1. Common error, validation, pagination, CurrentUser and permission foundation.
2. Auth and workspace/project modules.
3. Secret/provider modules.
4. Dataset schema/version/import/export modules.
5. Chatbot target module.
6. Evaluator module.
7. TestRun orchestration and runner command producer.
8. Runner event consumer and result persistence.
9. SSE streaming.
10. Export job worker.
11. Security, masking and artifact hardening using docs/backend/13-security-masking-implementation.md.
```

Reason:

```text
Later modules depend on earlier modules for permission, project context, provider visibility, dataset version locking and masking.
```

---

## 19. AI Coding Agent Instructions

When generating backend code, always follow this checklist:

```text
1. Read API contract for the module before generating controller/DTO code.
2. Read ERD table definition before generating entity/repository code.
3. Put service interface in service/ and implementation in service/impl/.
4. Add Javadoc to service interface methods.
5. Add @Slf4j to service implementations.
6. Add meaningful logs without sensitive data.
7. Add @Transactional or @Transactional(readOnly = true) at service implementation boundary.
8. Use MapStruct mapper; do not write manual toResponse/toEntity helpers.
9. Use record for request/response DTOs.
10. Add Bean Validation and OpenAPI @Schema examples to request records.
11. Add Swagger annotations to controllers.
12. Use Lombok for constructor/getter/setter/builder boilerplate.
13. Use @Async only for true async/job workflows.
14. Keep backend messages/logs in English and expose messageKey/code for localization.
15. Never log or return plaintext secrets.
```
