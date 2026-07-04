# VinFast QC Copilot - Backend Package Architecture

Version: `v0.2`  
Status: Spring Boot 4.0.7-first Implementation Spec  
Applies to: `apps/api`

---

## 1. Purpose

This document defines the package architecture for the Spring Boot 4 backend.

The goal is to keep generated code consistent without wrapping Spring features unnecessarily.

---

## 2. Root Package

Use:

```text
vn.vinfast.vfqc.api
```

If the actual project already uses another root, keep the same subpackage structure under that root.

---

## 3. High-Level Package Tree

```text
vn.vinfast.vfqc.api
  VfqcApplication.java

  common
    config
    error
    security
    permission
    naming
    masking
    audit
    json
    time
    redis
    storage

  auth
  user
  workspace
  project
  secret
  provider
  dataset
  target
  evaluator
  testrun
  runner
  sse
  exportjob
  artifact
```

Rules:

1. Keep package names short and domain-oriented.
2. Use `testrun`, not `test_run`.
3. Use `provider`, not `llmproviderconfigmanagement`.
4. Do not create random utility packages for domain logic.
5. Do not create framework wrapper packages unless they add real VFQC behavior.

---

## 4. Common Package Responsibilities

### 4.1. `common/config`

Contains Spring configuration classes only.

Recommended classes:

```text
OpenApiConfig
SecurityConfig
JpaConfig
RedisConfig
AsyncConfig
ClockConfig
JacksonConfig
ActuatorConfig
```

Rules:

1. Prefer Spring Boot auto-configuration.
2. Add config classes only when the project needs explicit beans.
3. Do not put business logic in configuration classes.

---

### 4.2. `common/error`

Contains shared error handling.

Recommended classes:

```text
ApiException
ErrorCode
GlobalExceptionHandler
ProblemDetailFactory
FieldViolation
```

Spring baseline:

1. Use Spring `ProblemDetail`.
2. Use `@RestControllerAdvice`.
3. Handle Jakarta validation exceptions centrally.
4. Add stable extension fields: `code`, `messageKey`, `traceId`, `fieldErrors`.

Do not create different error response shapes per module.

---

### 4.3. `common/security`

Contains authentication infrastructure.

Recommended classes:

```text
CurrentUser
SecurityUserPrincipal
SecurityChainsConfig
JwtTokenService
JwtAuthenticationConverter
RunnerServiceAuthenticationFilter
CookieFactory
```

Spring built-ins to use:

1. `PasswordEncoder` for password hashing.
2. `SecurityFilterChain` for security config.
3. `JwtEncoder` / `JwtDecoder` for backend-issued JWT if used.
4. OAuth2 Resource Server for bearer token validation.
5. OAuth2 Client if OIDC login is implemented.

Do not create:

1. `PasswordHasher`.
2. Custom JWT parsing filter for normal user APIs.
3. Controller-level authentication logic.

---

### 4.4. `common/permission`

Contains VFQC authorization rules.

Recommended classes:

```text
PermissionAction
WorkspacePermissionService
ProjectPermissionService
ResourceAccessGuard
RequireWorkspacePermission
RequireProjectPermission
```

This package is custom because Spring Security does not know VFQC workspace/project membership rules.

Rules:

1. Centralize workspace/project permission matrix.
2. Return 404 when resource existence must be hidden.
3. Do not duplicate permission checks in controllers.
4. Method security annotations may be used, but complex checks should call domain permission services.

---

### 4.5. `common/naming`

Contains domain name normalization.

Recommended classes:

```text
ResourceNamePolicy
ResourceNameNormalizer
NormalizedName
```

This is custom because Spring does not provide VFQC-specific duplicate-name policy.

Rules:

1. Trim display names.
2. Collapse repeated whitespace for uniqueness checks.
3. Preserve display casing.
4. Reject control characters and path separators.
5. Prefer a persisted `name_lookup` column for uniqueness.

---

### 4.6. `common/masking`

Contains shared sensitive-data sanitization.

Recommended classes:

```text
VfqcSensitiveDataMasker
SensitiveFieldDetector
SensitiveValueDetector
MaskingPolicy
MaskingContext
MaskingTarget
SanitizationResult
SanitizationFinding
JsonSanitizer
HeaderSanitizer
CurlSanitizer
ArtifactSanitizer
FormulaInjectionSanitizer
```

This is custom because VFQC has product-specific masking rules for cURL, Promptfoo artifacts, runner events and exports.

Rules:

1. Never implement one-off masking in controllers/services.
2. Never return original secret values in findings.
3. Do not access DB from masking components.

---

### 4.7. `common/redis`

Contains Redis Stream helpers.

Recommended classes:

```text
RedisStreamNames
RedisStreamPublisher
RedisStreamConsumerSupport
RedisDeadLetterPublisher
```

Spring baseline:

1. Use Spring Data Redis.
2. Prefer `StringRedisTemplate` or configured `RedisTemplate<String, String>`.
3. Do not use JDK serialization for stream payloads.
4. Keep payloads compact JSON/hash fields.

---

### 4.8. `common/storage`

Contains local artifact/file storage abstraction.

Recommended classes:

```text
StorageService
LocalStorageService
StoragePathPolicy
StoredFileMetadata
```

This abstraction is allowed because the app may later switch from local disk to object storage.

Rules:

1. Prevent path traversal.
2. Do not expose internal file paths.
3. Store only sanitized artifacts.

---

## 5. Feature Module Standard Layout

Each module uses this layout where applicable:

```text
<module>/
  controller/
  dto/
    request/
    response/
  entity/
  enums/
  repository/
  mapper/
  service/
    impl/
  validator/
  assembler/
  model/
  event/
```

Guidelines:

| Folder | Responsibility |
| --- | --- |
| `controller` | HTTP adapter only |
| `dto` | API records only |
| `entity` | JPA entities only |
| `repository` | Spring Data repositories |
| `mapper` | MapStruct mappers |
| `service` | Use-case contracts |
| `service/impl` | Use-case implementation |
| `validator` | Domain validation not covered by Bean Validation |
| `assembler` | Aggregate/computed response builder |
| `model` | Internal domain/config models |
| `event` | Internal application events |

---

## 6. Module Responsibilities

### `auth`

Owns login, refresh, logout and current user.

Spring built-ins:

1. `PasswordEncoder`.
2. `AuthenticationManager` or explicit service-level verification.
3. `JwtEncoder` / `JwtDecoder` if backend issues JWT.
4. HttpOnly cookie support through servlet response/cookie APIs.

Do not create custom password hashing abstractions.

### `user`

Owns user persistence and lookup.

Do not put workspace permission logic here.

### `workspace`

Owns workspaces and members.

Use domain services for membership role/status transitions.

### `project`

Owns project metadata and setup status.

Project creation creates default project dataset schema in the same transaction.

### `secret`

Owns encrypted secrets and secret metadata.

Use JCA/JCE or a KMS-backed service. Keep the encryption boundary small.

### `provider`

Owns LLM provider metadata, provider clients and provider test logs.

Spring AI may be used inside client adapters only. Domain entities must not depend on Spring AI classes.

### `dataset`

Owns dataset schema, versions, rows, import/export integration.

Use Apache POI for Excel operations.

### `target`

Owns chatbot target HTTP config, cURL inspection, test connection and response extraction.

Use `RestClient` for outbound HTTP.

### `evaluator`

Owns evaluator config and immutable evaluator versions.

Do not accept arbitrary JavaScript in MVP.

### `testrun`

Owns run creation, preflight, snapshots, result read models, review and comparison matrix.

Custom state machine is allowed because this is domain logic.

### `runner`

Owns runner registration, internal config, runtime secret resolution, Redis command/event ingestion and artifact metadata ingest.

### `sse`

Owns `SseEmitter` subscription, replay and live fanout.

### `exportjob`

Owns shared async export lifecycle.

### `artifact`

Owns Promptfoo artifact metadata and sanitized artifact access.

---

## 7. Dependency Direction

Allowed dependency examples:

```text
testrun -> dataset lookup
testrun -> target lookup
testrun -> evaluator lookup
testrun -> provider lookup
testrun -> runner command publisher
runner -> testrun event update service
runner -> secret runtime resolver
sse -> runner event repository/query service
exportjob -> dataset/test-run exporter strategies
```

Forbidden:

```text
controller -> repository directly for business use cases
feature module -> other feature entity mutation directly
runner -> mutable target/evaluator/dataset definitions
sse -> Redis directly
provider entity -> Spring AI class
```

---

## 8. Testing Package Rule

Use Spring Boot test slices where practical:

1. `@WebMvcTest` for controllers.
2. `@DataJpaTest` for repositories.
3. `@JsonTest` for JSON config serialization if needed.
4. `@SpringBootTest` for integration paths.
5. Testcontainers for PostgreSQL/Redis integration tests.
6. Spring Security test support for auth/permission tests.

Do not write only mocked service tests for persistence-heavy behavior.
