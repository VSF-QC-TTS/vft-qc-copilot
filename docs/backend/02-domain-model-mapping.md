# VinFast QC Copilot - Domain Model Mapping

Version: `v0.2`  
Status: Spring Boot 4.0.7-first Implementation Spec  
Applies to: `apps/api`

---

## 1. Purpose

This document maps the ERD to Spring Boot 4 / Spring Data JPA / Hibernate ORM 7 entities, repositories and service invariants.

---

## 2. Persistence Baseline

Use:

1. Spring Data JPA 4.x.
2. Hibernate ORM 7.x.
3. PostgreSQL.
4. Flyway migrations.
5. Jakarta Persistence imports.

Required import style:

```java
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
```

Forbidden:

```java
import javax.persistence.*;
import javax.validation.*;
```

---

## 3. Entity Is Persistence Model

Rules:

1. Do not expose entities directly from controllers.
2. Do not use entities as request/response DTOs.
3. Use Java records for DTOs.
4. Use MapStruct for simple mapping.
5. Use assemblers for aggregate responses.
6. Keep entity fields close to ERD column names and semantics.
7. Keep business invariants in service/domain validators, not in controllers.

---

## 4. ID Strategy

### 4.1. UUID aggregates

Use UUID for public/root resources:

```text
User
AuthRefreshToken
Workspace
Project
Secret
LlmProvider
DatasetSchema
Dataset
DatasetVersion
DatasetImportJob
ChatbotTarget
Evaluator
EvaluatorVersion
TestRun
TestRunTarget
ExportJob
RunnerInstance
```

Recommended entity field:

```java
@Id
@GeneratedValue(strategy = GenerationType.UUID)
@Column(name = "id", nullable = false, updatable = false)
private UUID id;
```

If Flyway migration uses database default `gen_random_uuid()`, pick either database-generated or Hibernate-generated UUID consistently. Do not mix randomly.

### 4.2. Long child/detail rows

Use `Long` for high-volume child rows:

```text
WorkspaceMember
DatasetSchemaColumn
DatasetRow
ChatbotTargetExample
EvaluatorRule
TestRunEvaluatorVersion
TestCaseResult
TestCaseResultReview
RunnerCommand
RunnerEvent
PromptfooRunArtifact
LlmProviderTestLog
```

Recommended entity field:

```java
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
@Column(name = "id", nullable = false, updatable = false)
private Long id;
```

---

## 5. Column and Enum Mapping

Database columns use `snake_case`. Java fields use `camelCase`.

Example:

```java
@Column(name = "workspace_id", nullable = false)
private UUID workspaceId;
```

Enum mapping:

```java
@Enumerated(EnumType.STRING)
@Column(name = "status", nullable = false)
private ProjectStatus status;
```

Rules:

1. Store enums as strings.
2. Enum names must match DB enum/check values.
3. Do not ordinal-map enums.
4. Add Flyway migrations before code relies on new enum values.

---

## 6. JSONB Mapping with Hibernate 7

Use one JSONB strategy consistently.

Recommended:

```java
import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "config_json", columnDefinition = "jsonb")
private JsonNode configJson;
```

Rules:

1. Prefer `JsonNode` for flexible snapshots/config.
2. Prefer typed internal records for heavily validated config.
3. Do not mix `String`, `Map<String,Object>`, `JsonNode` and converters randomly.
4. Do not store unsanitized secrets in JSONB.
5. Add tests for sensitive JSON paths.

Common JSONB fields:

```text
config_json
available_models_json
headers_json
query_params_json
body_json
auth_config_json
dataset_mapping_json
response_extraction_config_json
metadata_json
summary_metrics_json
domain_config_snapshot_json
promptfoo_config_snapshot_json
raw_response_json
extracted_output_json
grading_result_json
component_results_json
token_usage_json
```

---

## 7. Time and Audit Fields

Use `OffsetDateTime` for API-facing timestamps unless the project explicitly standardizes on `Instant`.

Use a Spring `Clock` bean in services:

```java
OffsetDateTime now = OffsetDateTime.now(clock);
```

Rules:

1. `createdAt` is set once.
2. `updatedAt` changes on metadata/content updates.
3. `deletedAt` means soft delete.
4. `archivedAt` means archived, not deleted.
5. `startedAt`/`finishedAt` track execution.
6. `publishedAt`/`discardedAt` track dataset draft lifecycle.

JPA auditing may be used if configured consistently:

```text
@EnableJpaAuditing
@CreatedDate
@LastModifiedDate
```

If JPA auditing is not configured, set timestamps explicitly in services.

---

## 8. Soft Delete Rule

Tables with `deleted_at` use soft delete.

Affected aggregates:

```text
Workspace
Project
Secret
LlmProvider
Dataset
ChatbotTarget
Evaluator
```

Repository methods should exclude deleted rows:

```java
Optional<Project> findByIdAndDeletedAtIsNull(UUID id);
List<Project> findByWorkspaceIdAndDeletedAtIsNull(UUID workspaceId);
```

Rules:

1. Do not call `repository.delete()` for soft-deleted resources.
2. Set `deletedAt` in service methods.
3. Deleted resources cannot be selected for new runs.
4. Historical TestRuns remain readable through locked snapshots.

---

## 9. Relationship Mapping Rule

Prefer ID fields for simple ownership checks and lightweight queries.

Use `@ManyToOne(fetch = FetchType.LAZY)` only where navigation improves mapping and consistency.

Allowed patterns:

```java
@Column(name = "project_id", nullable = false)
private UUID projectId;
```

or:

```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "project_id", nullable = false)
private Project project;
```

Rules:

1. Do not eager-load large graphs.
2. Avoid bidirectional collections for high-volume child rows.
3. Use repository queries/EntityGraph/DTO projections for read models.
4. Do not rely on cascade delete for historical data.

---

## 10. Entity Ownership Map

| Table | Entity | Module | ID |
| --- | --- | --- | --- |
| `users` | `User` | `user` | UUID |
| `auth_refresh_tokens` | `AuthRefreshToken` | `auth` | UUID |
| `workspaces` | `Workspace` | `workspace` | UUID |
| `workspace_members` | `WorkspaceMember` | `workspace` | Long |
| `projects` | `Project` | `project` | UUID |
| `secrets` | `Secret` | `secret` | UUID |
| `llm_providers` | `LlmProvider` | `provider` | UUID |
| `llm_provider_test_logs` | `LlmProviderTestLog` | `provider` | Long |
| `dataset_schemas` | `DatasetSchema` | `dataset` | UUID |
| `dataset_schema_columns` | `DatasetSchemaColumn` | `dataset` | Long |
| `datasets` | `Dataset` | `dataset` | UUID |
| `dataset_versions` | `DatasetVersion` | `dataset` | UUID |
| `dataset_rows` | `DatasetRow` | `dataset` | Long |
| `dataset_import_jobs` | `DatasetImportJob` | `dataset` | UUID |
| `chatbot_targets` | `ChatbotTarget` | `target` | UUID |
| `chatbot_target_examples` | `ChatbotTargetExample` | `target` | Long |
| `evaluators` | `Evaluator` | `evaluator` | UUID |
| `evaluator_versions` | `EvaluatorVersion` | `evaluator` | UUID |
| `evaluator_rules` | `EvaluatorRule` | `evaluator` | Long |
| `test_runs` | `TestRun` | `testrun` | UUID |
| `test_run_targets` | `TestRunTarget` | `testrun` | UUID |
| `test_run_evaluator_versions` | `TestRunEvaluatorVersion` | `testrun` | Long |
| `test_case_results` | `TestCaseResult` | `testrun` | Long |
| `test_case_result_reviews` | `TestCaseResultReview` | `testrun` | Long |
| `export_jobs` | `ExportJob` | `exportjob` | UUID |
| `runner_instances` | `RunnerInstance` | `runner` | UUID |
| `runner_commands` | `RunnerCommand` | `runner` | Long |
| `runner_events` | `RunnerEvent` | `runner` | Long |
| `promptfoo_run_artifacts` | `PromptfooRunArtifact` | `artifact` | Long |

---

## 11. Core Invariants

### Workspace and Project

1. User-created workspace is normally `TEAM`.
2. Onboarding may create one `PERSONAL` workspace.
3. Workspace creator becomes `OWNER`.
4. Project belongs to one workspace.
5. Project creation creates a default project dataset schema in the same transaction.
6. Project delete is soft delete.

### Secret and Provider

1. Plaintext secret is never persisted.
2. Provider references `secretId`.
3. Provider config JSON is sanitized before persistence.
4. Runtime secret decryption is allowed only for provider test and runner execution paths.
5. Provider test logs store sanitized data only.

### Dataset

1. Dataset schema is immutable by version.
2. Dataset rows belong to `DatasetVersion`.
3. ACTIVE DatasetVersion is immutable.
4. Editing happens only in DRAFT.
5. Publishing DRAFT updates `Dataset.currentVersionId`.
6. TestRun locks exact dataset/schema/version IDs.

### Target

1. Raw cURL is never shell-executed.
2. Only sanitized cURL may be persisted.
3. Secret values are split into Secret entities.
4. Target runtime config uses structured fields, not raw cURL.
5. ACTIVE target requires request config and response extraction config.

### Evaluator

1. Evaluator type is immutable after creation.
2. EvaluatorVersion is immutable.
3. Editing evaluator config creates a new version.
4. No arbitrary JavaScript in MVP.

### TestRun

1. TestRun is immutable execution record.
2. Rerun creates a new TestRun.
3. Backend locks domain and Promptfoo snapshots.
4. Testcase failure does not make TestRun failed.
5. Machine result is immutable.
6. QC review is a separate override layer.

### Runner and SSE

1. Redis carries references and compact events only.
2. Runner events are persisted before SSE broadcast.
3. SSE event id equals `runner_events.sequence_no`.
4. Replay reads PostgreSQL, not Redis or in-memory buffers.

---

## 12. Repository Rules

Use Spring Data repositories.

Examples:

```java
public interface ProjectRepository extends JpaRepository<Project, UUID> {
  Optional<Project> findByIdAndDeletedAtIsNull(UUID id);
  Page<Project> findByWorkspaceIdAndDeletedAtIsNull(UUID workspaceId, Pageable pageable);
}
```

Rules:

1. Use `Pageable` internally for paginated queries.
2. Use database unique indexes for real uniqueness guarantees.
3. Use repository method names for simple queries.
4. Use `@Query` for complex queries.
5. Use pessimistic/optimistic locking only where concurrent mutation matters.

---

## 13. Migration Rules

Flyway owns schema changes.

Rules:

1. Every entity field that is persisted must exist in a migration.
2. Every enum change must have a migration/check update if DB enforces values.
3. Add indexes for lookup columns.
4. Add unique constraints for idempotency and uniqueness.
5. Add foreign keys where historical retention policy allows it.
6. Do not rely only on service-level duplicate checks.
