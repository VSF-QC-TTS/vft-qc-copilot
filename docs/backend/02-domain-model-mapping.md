# VinFast QC Copilot - Backend Domain Model Mapping

Version: `v0.1`  
Status: Backend Implementation Spec Draft  
Applies to: `apps/api` Spring Boot backend  
Aligned with: PRD v0.7, ERD v0.5, API Contracts `00` to `10`

---

## 1. Purpose

This document maps the ERD v0.5 database contract to backend Java modules, entities, enums, repositories, mappers and service responsibilities.

It is written for AI coding agents so they can generate backend code without inventing a different domain model.

This document answers:

```text
1. Which Java module owns each database table.
2. Which Java entity maps to each table.
3. Which id type should be used.
4. Which relationships matter in code.
5. Which invariants must be enforced in services.
6. Which fields must be sanitized, immutable, soft-deleted or version-locked.
```

---

## 2. Mapping Principles

### 2.1. Entity is persistence model

JPA entities represent database rows.

Rules:

```text
1. Do not expose entities directly from controllers.
2. Do not use entities as request/response DTOs.
3. Map entities to response records with MapStruct or an assembler.
4. Request records are converted into entities or domain commands in service layer.
5. Entity fields should stay close to ERD column names and semantics.
```

---

### 2.2. Service owns invariants

Business invariants must be enforced in service implementations, validators and state machines.

Do not rely only on frontend validation.

Examples:

```text
1. Project creation must create default dataset schema in the same transaction.
2. ACTIVE DatasetVersion must not be mutated.
3. EvaluatorVersion must not be mutated after creation.
4. TestRun must lock exact dataset/schema/evaluator/target/provider snapshots.
5. Plaintext secrets must never be returned after creation.
6. TestRun FAILED is reserved for execution/system failure, not business failed testcases.
```

---

### 2.3. API contract remains source for DTOs

The API files under `docs/api` define request and response shapes.

This file defines backend persistence mapping.

If API DTO and entity shape differ, do not force the entity to match the DTO. Use mapper/assembler.

---

## 3. ID Strategy

Use UUID for main resources exposed to API/frontend/Redis.

Use BIGINT for high-volume/detail/child tables.

### 3.1. UUID entities

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

Java type:

```java
UUID
```

Recommended entity field:

```java
@Id
@GeneratedValue(strategy = GenerationType.UUID)
@Column(name = "id", nullable = false, updatable = false)
private UUID id;
```

If the project uses database default `gen_random_uuid()`, generation may be delegated to the database. Pick one strategy consistently.

---

### 3.2. BIGINT entities

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

Java type:

```java
Long
```

Recommended entity field:

```java
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
@Column(name = "id", nullable = false, updatable = false)
private Long id;
```

---

## 4. Naming and Column Mapping

Database columns use snake_case.

Java fields use camelCase.

Example:

```text
workspace_id -> workspaceId
created_at   -> createdAt
deleted_at   -> deletedAt
```

Rules:

```text
1. Use @Column(name = "...") when needed for clarity.
2. Enums are stored as strings matching PostgreSQL enum values.
3. Timestamps should use OffsetDateTime unless the project already standardized on Instant.
4. JSONB fields should use JsonNode, Map<String, Object> or a strongly typed embeddable/value object with converter.
5. Do not store raw plaintext secrets in entity fields.
```

Recommended enum mapping:

```java
@Enumerated(EnumType.STRING)
@Column(name = "status", nullable = false)
private ProjectStatus status;
```

For PostgreSQL enum columns, ensure the JPA/Hibernate mapping is compatible with the actual migration strategy.

---

## 5. JSONB Mapping Rule

Many ERD fields are JSONB:

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

Recommended Java field type:

```java
JsonNode
```

Alternative:

```java
Map<String, Object>
```

Rules:

```text
1. Prefer JsonNode for flexible external payload/config snapshots.
2. Prefer strongly typed records/value objects for config that backend must validate heavily.
3. Never store unsanitized secrets in JSONB.
4. Add tests for masking JSON paths that may contain sensitive keys.
```

Example:

```java
@Column(name = "config_json", columnDefinition = "jsonb")
private JsonNode configJson;
```

If using Hibernate 6 JSON support, the project may use a JSON type annotation consistently. Do not mix random JSON mapping approaches per entity.

---

## 6. Timestamp and Audit Fields

Common timestamp fields:

```text
created_at
updated_at
deleted_at
archived_at
started_at
finished_at
published_at
discarded_at
cancel_requested_at
reviewed_at
```

Rules:

```text
1. Use createdAt for creation time.
2. Use updatedAt for last metadata/content update where table supports it.
3. Use deletedAt for soft delete.
4. Use archivedAt for non-deletion archive state.
5. Use startedAt/finishedAt for execution time.
6. Use reviewedAt for QC review time.
7. Use publishedAt/discardedAt for dataset draft lifecycle.
```

Recommended lifecycle:

```text
Created entity:
  createdAt = now
  updatedAt = now if column exists

Updated entity:
  updatedAt = now

Soft deleted entity:
  deletedAt = now
```

AI rule:

```text
Do not physically delete rows for resources whose API contract says soft delete/archive.
```

---

## 7. Soft Delete Rule

Tables with `deleted_at` use soft delete.

Normal list/detail queries should exclude deleted rows unless contract explicitly says otherwise.

Affected entities include:

```text
Workspace
Project
Secret
LlmProvider
Dataset
ChatbotTarget
Evaluator
```

Repository method examples:

```java
Optional<Project> findByIdAndDeletedAtIsNull(UUID id);
List<Project> findByWorkspaceIdAndDeletedAtIsNull(UUID workspaceId);
```

Rules:

```text
1. Do not use repository.delete() for soft-deleted resources.
2. Set deletedAt in service implementation.
3. Existing historical TestRuns may still reference soft-deleted config via locked snapshots.
4. Deleted secrets/providers/targets/evaluators cannot be selected for new runs.
```

---

## 8. Entity Ownership Map

| ERD table | Java entity | Module | ID type |
| --------- | ----------- | ------ | ------- |
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

## 9. Access and Workspace Model

### 9.1. `User`

Table:

```text
users
```

Owns:

```text
1. Identity.
2. Email.
3. Display name.
4. System role.
5. User status.
```

Important fields:

```text
id UUID
email
name
avatar_url
password_hash if email/password auth is implemented
system_role
status
created_at
updated_at
deleted_at
```

Java enum mapping:

```text
SystemRole: PLATFORM_ADMIN, USER
UserStatus: ACTIVE, DISABLED, DELETED
```

Rules:

```text
1. Disabled/deleted user cannot authenticate.
2. Password hash is never returned.
3. CurrentUser should be created from security context, not from arbitrary request body.
```

---

### 9.2. `AuthRefreshToken`

Table:

```text
auth_refresh_tokens
```

Module:

```text
auth
```

Purpose:

```text
Supports refresh token rotation/revoke when implemented.
```

Rules:

```text
1. Store token hash only.
2. Plaintext refresh token exists only in HttpOnly cookie and request processing memory.
3. Logout revokes current session/token family if persistence exists.
4. Reuse detection uses family id when implemented.
```

---

### 9.3. `Workspace`

Table:

```text
workspaces
```

Java enum:

```text
WorkspaceType: PERSONAL, TEAM
```

Rules:

```text
1. User registration should create PERSONAL workspace automatically if onboarding is implemented.
2. User-created workspace uses TEAM type.
3. Delete is soft delete through deletedAt.
4. Workspace name uniqueness is scoped by product policy, not necessarily global DB uniqueness.
```

---

### 9.4. `WorkspaceMember`

Table:

```text
workspace_members
```

Java enums:

```text
WorkspaceRole: OWNER, ADMIN, MEMBER, VIEWER
WorkspaceMemberStatus: INVITED, ACTIVE, DISABLED
```

Rules:

```text
1. unique(workspace_id, user_id).
2. Creator of workspace becomes OWNER.
3. Permission checks use only ACTIVE members.
4. Platform admin may bypass membership except plaintext secret read.
```

Service invariant:

```text
WorkspaceMemberService must prevent removing/downgrading the last OWNER unless replacement owner exists.
```

---

## 10. Project Model

### 10.1. `Project`

Table:

```text
projects
```

Module:

```text
project
```

Relationships:

```text
Workspace 1 -> N Project
Project 1 -> N DatasetSchema where scope PROJECT
Project 1 -> N Dataset where scope PROJECT
Project 1 -> N ChatbotTarget
Project 1 -> N Evaluator
Project 1 -> N TestRun
```

Important fields:

```text
id
workspace_id
name
description
created_by
created_at
updated_at
archived_at
deleted_at
```

Recommended implementation fields if migration supports them:

```text
normalized_name
```

Rules:

```text
1. Project belongs to exactly one workspace.
2. Project create must also create default PROJECT dataset schema with columns question and expected.
3. Project delete is soft delete.
4. Archived/deleted project cannot start new test runs.
5. Setup status is computed from related resources and does not mutate Project.
```

Transaction rule:

```text
ProjectServiceImpl.createProject must be @Transactional because it creates Project and DatasetSchema in one unit.
```

---

## 11. Secret and Provider Model

### 11.1. `Secret`

Table:

```text
secrets
```

Module:

```text
secret
```

Java enums:

```text
SecretScope: PERSONAL, WORKSPACE
SecretStatus: ACTIVE, DISABLED, DELETED
```

Scope fields:

```text
PERSONAL:
  owner_user_id required
  workspace_id null

WORKSPACE:
  workspace_id required
  owner_user_id null
```

Important fields:

```text
id
scope
owner_user_id
workspace_id
name
encrypted_value
status
created_by
created_at
updated_at
deleted_at
```

Rules:

```text
1. encryptedValue is never exposed through API responses.
2. Plaintext secret is accepted only in create/rotate/runtime-only flows.
3. Secret value is encrypted before persistence.
4. Disabled/deleted secrets cannot be used by new provider/test run setup.
5. Platform admin still cannot read plaintext secret.
```

Mapping rule:

```text
Secret -> SecretResponse uses SecretMapper and exposes hasValue/maskedPreview only.
```

---

### 11.2. `LlmProvider`

Table:

```text
llm_providers
```

Module:

```text
provider
```

Java enums:

```text
ProviderScope: PERSONAL_PROVIDER, WORKSPACE_PROVIDER
ProviderType: OPENAI_COMPATIBLE, GEMINI, CLAUDE, CUSTOM_HTTP
ProviderStatus: ACTIVE, DISABLED, ERROR
```

Scope fields:

```text
PERSONAL_PROVIDER:
  owner_user_id required
  workspace_id null
  secret must be PERSONAL and owned by same user

WORKSPACE_PROVIDER:
  workspace_id required
  owner_user_id null
  secret must be WORKSPACE and from same workspace
```

Important fields:

```text
id
scope
owner_user_id
workspace_id
name
provider_type
secret_id
base_url
default_model
available_models_json
config_json
status
created_by
created_at
updated_at
deleted_at
```

Rules:

```text
1. Provider references Secret by id, never stores raw API key.
2. configJson must be sanitized.
3. Provider can be used for LLM Judge and LLM baseline.
4. Active workspace provider and active personal provider are visible in run setup when permissions allow.
5. Deleted/disabled/error provider cannot be selected for new run unless contract explicitly allows debug mode.
```

---

### 11.3. `LlmProviderTestLog`

Table:

```text
llm_provider_test_logs
```

Module:

```text
provider
```

Purpose:

```text
Stores masked history of provider connection tests for QC/admin debugging.
```

Suggested fields:

```text
id BIGINT
llm_provider_id UUID
status SUCCESS | FAILED
request_json JSONB
response_status_code INT
response_headers_json JSONB
response_body_json JSONB
latency_ms INT
error_message TEXT
tested_by UUID
created_at TIMESTAMPTZ
```

Rules:

```text
1. requestJson must be sanitized.
2. response headers/body must be sanitized.
3. errorMessage must not contain secret/token.
4. Provider test history endpoint returns masked log only.
5. Do not store plaintext secret or runtime secret bundle.
```

---

## 12. Dataset Model

### 12.1. `DatasetSchema`

Table:

```text
dataset_schemas
```

Module:

```text
dataset
```

Java enum:

```text
DatasetSchemaScope: PROJECT, WORKSPACE, PERSONAL
```

Scope fields:

```text
PROJECT:
  project_id required
  workspace_id null
  owner_user_id null

WORKSPACE:
  workspace_id required
  project_id null
  owner_user_id null

PERSONAL:
  owner_user_id required
  workspace_id null
  project_id null
```

Rules:

```text
1. Schema is versioned.
2. Project creation creates default PROJECT schema.
3. Schema changes create new schema version.
4. TestRun locks schema id and schema version.
```

---

### 12.2. `DatasetSchemaColumn`

Table:

```text
dataset_schema_columns
```

Important fields:

```text
id
schema_id
name
display_name
data_type
required
description
order_index
created_at
updated_at
```

Rules:

```text
1. name is stable machine-readable column key.
2. displayName is human-friendly.
3. dataType supports TEXT, NUMBER, BOOLEAN, JSON, DATE, DATETIME in MVP.
4. UI variable is generated as $dataset.<name>.
5. Runner later converts dataset variable to Promptfoo variable syntax.
```

---

### 12.3. `Dataset`

Table:

```text
datasets
```

Java enums:

```text
DatasetScope: PROJECT, WORKSPACE, PERSONAL
DatasetSourceType: EXCEL, PROJECT_CLONE, WORKSPACE_DATASET, PERSONAL_DATASET, MANUAL
```

Important fields:

```text
id
scope
workspace_id
project_id
owner_user_id
current_version_id
name
description
source_dataset_id
source_project_id
source_workspace_id
source_type
created_by
created_at
updated_at
deleted_at
```

Rules:

```text
1. Dataset is logical container.
2. Rows belong to DatasetVersion, not directly Dataset.
3. currentVersion points to current ACTIVE version.
4. Delete is soft delete.
5. Dataset may be imported/cloned from project/workspace/personal source.
```

---

### 12.4. `DatasetVersion`

Table:

```text
dataset_versions
```

Java enum:

```text
DatasetVersionStatus: DRAFT, ACTIVE, ARCHIVED
```

Important fields:

```text
id
dataset_id
version
schema_id
schema_version
source_dataset_version_id
base_version_id
import_job_id
import_mapping_json
row_count
status
created_by
created_at
published_at
discarded_at
```

Rules:

```text
1. ACTIVE and ARCHIVED versions are read-only.
2. Editing starts by creating/reusing DRAFT from base version.
3. Publishing DRAFT creates ACTIVE current version.
4. Discarding DRAFT marks discardedAt/status and prevents further edits.
5. TestRun always references datasetVersionId directly.
6. Comparison run uses the same datasetVersionId for all targets.
```

Service invariants:

```text
DatasetVersionService must reject row/cell mutation when version status is not DRAFT.
DatasetVersionService.publishDraft must update Dataset.currentVersionId atomically.
```

---

### 12.5. `DatasetRow`

Table:

```text
dataset_rows
```

Important fields:

```text
id
dataset_version_id
row_index
data_json
metadata_json
revision
created_at
updated_at
```

Rules:

```text
1. dataJson stores dynamic column values.
2. revision supports optimistic concurrency for inline editing.
3. rowIndex should be stable inside a version.
4. Each DatasetRow becomes one Promptfoo test case.
5. Each dataJson key becomes one vars field for Runner-generated config.
```

---

### 12.6. `DatasetImportJob`

Table:

```text
dataset_import_jobs
```

Java enums:

```text
DatasetImportSourceType: EXCEL, PROJECT_CLONE, WORKSPACE_DATASET, PERSONAL_DATASET
DatasetImportStatus: PENDING, PREVIEWED, IMPORTING, COMPLETED, FAILED, CANCELLED
```

Rules:

```text
1. Import flow is preview -> mapping -> confirm.
2. Preview does not create ACTIVE rows.
3. Confirm creates DatasetVersion and rows.
4. Mapping is stored for audit.
5. Source metadata is preserved.
```

---

## 13. Chatbot Target Model

### 13.1. `ChatbotTarget`

Table:

```text
chatbot_targets
```

Module:

```text
target
```

Java enum:

```text
ChatbotTargetStatus: DRAFT, ACTIVE, INVALID, DISABLED
```

Important fields:

```text
id
project_id
name
description
raw_curl_sanitized
raw_curl_hash
method
url
headers_json
query_params_json
body_json
auth_config_json
dataset_mapping_json
latest_example_id
response_extraction_config_json
status
created_by
created_at
updated_at
deleted_at
```

Rules:

```text
1. Backend never shell-executes raw cURL.
2. Persist only rawCurlSanitized.
3. Store rawCurlHash for audit.
4. Secret values are represented by secret binding references.
5. Dataset mapping uses $dataset.<columnName> source syntax.
6. Runner converts mapping to Promptfoo config at runtime.
7. ACTIVE requires valid request config and response extraction config.
```

---

### 13.2. `ChatbotTargetExample`

Table:

```text
chatbot_target_examples
```

Purpose:

```text
Stores one masked test connection attempt.
```

Important fields:

```text
id
chatbot_target_id
request_json
response_status_code
response_headers_json
response_body_json
extracted_output_json
latency_ms
error_message
tested_by
created_at
```

Rules:

```text
1. Every test connection attempt creates an example row.
2. requestJson is sanitized.
3. response headers/body are sanitized.
4. extracted output is sanitized.
5. latestExampleId on ChatbotTarget points to latest attempt.
```

---

## 14. Evaluator Model

### 14.1. `Evaluator`

Table:

```text
evaluators
```

Java enum:

```text
EvaluatorType: RULE_BASED, LLM_JUDGE, MIXED
```

Important fields:

```text
id
project_id
name
type
created_by
created_at
updated_at
deleted_at
```

Rules:

```text
1. Evaluator belongs to one project.
2. Evaluator type is immutable after creation.
3. Evaluator name may be updated.
4. Delete is soft delete.
5. Soft-deleted evaluator cannot be selected for new TestRun.
```

---

### 14.2. `EvaluatorVersion`

Table:

```text
evaluator_versions
```

Important fields:

```text
id
evaluator_id
version
config_json
created_by
created_at
```

Rules:

```text
1. EvaluatorVersion is immutable.
2. Creating/changing rules/rubric/threshold creates new version.
3. TestRun locks exact EvaluatorVersion ids.
4. Do not update configJson after creation.
```

---

### 14.3. `EvaluatorRule`

Table:

```text
evaluator_rules
```

Important fields:

```text
id
evaluator_version_id
field_source
operator
expected_value_json
weight
required
order_index
config_json
created_at
updated_at
```

Rules:

```text
1. Rules belong to EvaluatorVersion.
2. Rule order is stable through orderIndex.
3. RULE_BASED version must have at least one rule.
4. MIXED may contain rules and LLM Judge config.
5. User-provided custom JavaScript is not allowed in MVP.
```

---

## 15. Test Run Model

### 15.1. `TestRun`

Table:

```text
test_runs
```

Java enums:

```text
TestRunType: SINGLE, COMPARISON
TestRunStatus: PENDING, RUNNING, CANCELLING, CANCELLED, COMPLETED, FAILED
```

Important fields:

```text
id
project_id
run_type
dataset_id
dataset_version_id
dataset_schema_id
dataset_schema_version
judge_provider_id
judge_model
status
progress_completed
progress_total
summary_metrics_json
domain_config_snapshot_json
promptfoo_config_snapshot_json
domain_config_hash
promptfoo_config_hash
started_by
started_at
finished_at
cancel_requested_at
created_at
updated_at
```

Rules:

```text
1. TestRun is created once and never restarted in place.
2. Rerun creates a new TestRun.
3. TestRun creation locks dataset, schema, evaluator, target/provider selection and sanitized snapshots.
4. Promptfoo cache is disabled by default for internal chatbot target runs.
5. Full generated Promptfoo config belongs in artifact storage, not directly in test_runs.
6. TestRun FAILED is for unrecoverable execution/system failure.
7. Business failed testcase does not make TestRun FAILED.
```

State machine:

```text
PENDING -> RUNNING
PENDING -> FAILED
RUNNING -> CANCELLING
RUNNING -> COMPLETED
RUNNING -> FAILED
CANCELLING -> CANCELLED
CANCELLING -> FAILED
```

Terminal statuses:

```text
COMPLETED
FAILED
CANCELLED
```

---

### 15.2. `TestRunTarget`

Table:

```text
test_run_targets
```

Java enum:

```text
TestRunTargetType: CHATBOT_TARGET, LLM_BASELINE
```

Important fields:

```text
id
test_run_id
target_type
chatbot_target_id
llm_provider_id
model
display_name
provider_label
config_json
created_at
```

Rules:

```text
CHATBOT_TARGET:
  chatbot_target_id required
  llm_provider_id null
  model null unless future target requires it

LLM_BASELINE:
  chatbot_target_id null
  llm_provider_id required
  model required
```

Run composition:

```text
SINGLE:
  exactly 1 TestRunTarget

COMPARISON:
  at least 2 TestRunTargets
  may include multiple CHATBOT_TARGET entries
  may include multiple LLM_BASELINE entries
  may include a mix of both
```

---

### 15.3. `TestRunEvaluatorVersion`

Table:

```text
test_run_evaluator_versions
```

Important fields:

```text
id
test_run_id
evaluator_version_id
evaluator_type
order_index
created_at
```

Rules:

```text
1. Locks exact evaluator versions used by a TestRun.
2. Order is stable.
3. A run may use multiple evaluators.
4. EvaluatorVersion must not be mutated after it is locked.
```

---

### 15.4. `TestCaseResult`

Table:

```text
test_case_results
```

Java enum:

```text
TestCaseResultStatus: PASSED, FAILED, ERROR, JUDGE_ERROR, TIMEOUT, SKIPPED, CANCELLED
```

Important fields:

```text
id
test_run_id
test_run_target_id
dataset_row_id
status
input_vars_json
rendered_prompt
resolved_request_json
raw_response_json
extracted_output_json
grading_result_json
component_results_json
score
passed
latency_ms
token_usage_json
cost
error_message
started_at
finished_at
attempt_count
created_at
```

Rules:

```text
1. A comparison run has one result per dataset row per target.
2. unique(test_run_id, test_run_target_id, dataset_row_id).
3. resolvedRequestJson and rawResponseJson must be sanitized before persistence.
4. renderedPrompt must not contain secrets.
5. PASSED/FAILED are business results.
6. ERROR/JUDGE_ERROR/TIMEOUT are execution/evaluator/system result statuses.
```

---

### 15.5. `TestCaseResultReview`

Table:

```text
test_case_result_reviews
```

Java enum:

```text
ReviewStatus: UNREVIEWED, REVIEWED, NEEDS_DISCUSSION, NEEDS_RERUN
```

Important fields:

```text
id
test_case_result_id
review_status
override_status
override_passed
override_score
note
reviewed_by
reviewed_at
created_at
updated_at
```

Rules:

```text
1. Machine result is immutable.
2. QC review/manual override is stored separately.
3. Final result is computed from review when present, otherwise machine result.
4. unique(test_case_result_id).
5. Result export should default to final reviewed result when requested by API contract.
```

---

## 16. Comparison Matrix Mapping

The comparison matrix endpoint groups results by dataset row and target.

Source tables:

```text
test_runs
test_run_targets
test_case_results
dataset_rows
test_case_result_reviews
```

Backend read model:

```text
ComparisonMatrixResponse
  testRunId
  runType
  datasetVersionId
  targets[]
  rows[]
    datasetRowId
    rowIndex
    inputVars
    cells[]
      testRunTargetId
      targetDisplayName
      machineStatus
      machinePassed
      machineScore
      finalStatus
      finalPassed
      finalScore
      reviewStatus
      latencyMs
      errorCode/errorMessage if safe
```

Rules:

```text
1. Only valid for COMPARISON runs, unless API allows SINGLE as degenerate matrix.
2. Preserve target order from test_run_targets creation order.
3. Preserve row order from dataset_rows.row_index.
4. Use final reviewed result when review exists.
5. Do not include unsanitized raw response in matrix cells.
6. Detailed raw/sanitized payloads remain in testcase result detail endpoint.
```

Implementation:

```text
Use TestRunComparisonMatrixService and ComparisonMatrixAssembler.
Do not make frontend group huge result pages manually when backend can provide stable grouping.
```

---

## 17. Export Job Model

### 17.1. `ExportJob`

Table:

```text
export_jobs
```

Module:

```text
exportjob
```

Java enums:

```text
ExportJobType: DATASET_VERSION, TEST_RUN_RESULTS
ExportFormat: XLSX, CSV
ExportJobStatus: PENDING, RUNNING, COMPLETED, FAILED, CANCELLED, EXPIRED
```

Important fields:

```text
id
job_type
format
status
dataset_version_id
test_run_id
result_mode
file_path
file_name
file_size_bytes
error_message
created_by
created_at
started_at
finished_at
```

Rules:

```text
1. Dataset export and run result export use the same entity and response DTO.
2. Export execution may be async.
3. Export file path must be internal and safe.
4. Download endpoint checks permission again.
5. Result export can use MACHINE or FINAL result mode depending on request.
6. Default TestRun result export should use FINAL reviewed result.
```

---

## 18. Runner and Artifact Model

### 18.1. `RunnerInstance`

Table:

```text
runner_instances
```

Purpose:

```text
Tracks Node runner instance registration, heartbeat and status.
```

Java enum:

```text
RunnerInstanceStatus: IDLE, RUNNING, OFFLINE, UNHEALTHY
```

Rules:

```text
1. Runner identity is service identity, not user identity.
2. Heartbeat updates status/current run metadata.
3. Offline detection may be scheduled based on lastHeartbeatAt.
```

---

### 18.2. `RunnerCommand`

Table:

```text
runner_commands
```

Purpose:

```text
Stores commands produced by backend for runner execution/control.
```

Command examples:

```text
START_RUN
CANCEL_RUN
```

Rules:

```text
1. Command payload contains references only.
2. Do not include full dataset/config/secrets/artifacts.
3. TestRunCommandService creates DB command and publishes Redis Stream message consistently.
```

---

### 18.3. `RunnerEvent`

Table:

```text
runner_events
```

Purpose:

```text
Stores normalized runner events before SSE broadcast.
```

Important fields:

```text
id
test_run_id
event_id
sequence_no
event_type
payload_json
created_at
```

Rules:

```text
1. unique(event_id).
2. unique(test_run_id, sequence_no).
3. sequenceNo starts at 1 for each TestRun.
4. Backend persists event before broadcasting SSE.
5. SSE id equals sequenceNo.
6. Payload must be compact and sanitized.
```

---

### 18.4. `PromptfooRunArtifact`

Table:

```text
promptfoo_run_artifacts
```

Module:

```text
artifact
```

Important fields:

```text
id
test_run_id
config_file_path
result_json_path
result_jsonl_path
html_report_path
logs_path
config_sha256
result_sha256
artifact_schema_version
sanitized
created_at
```

Rules:

```text
1. DB stores metadata/path/hash only.
2. File content lives in local artifact storage.
3. API does not expose download/read links when sanitized=false.
4. Full artifacts must pass VfqcSensitiveDataMasker before writing file.
```

---

## 19. Enum Mapping List

Backend enums should match ERD enum names exactly.

Recommended enum classes:

```text
user/SystemRole.java
user/UserStatus.java
workspace/WorkspaceType.java
workspace/WorkspaceRole.java
workspace/WorkspaceMemberStatus.java
secret/SecretScope.java
secret/SecretStatus.java
provider/ProviderScope.java
provider/ProviderType.java
provider/ProviderStatus.java
provider/ProviderTestStatus.java
dataset/DatasetSchemaScope.java
dataset/DatasetScope.java
dataset/DatasetSourceType.java
dataset/DatasetVersionStatus.java
dataset/DatasetImportSourceType.java
dataset/DatasetImportStatus.java
target/ChatbotTargetStatus.java
evaluator/EvaluatorType.java
testrun/TestRunType.java
testrun/TestRunStatus.java
testrun/TestRunTargetType.java
testrun/TestCaseResultStatus.java
testrun/ReviewStatus.java
exportjob/ExportJobType.java
exportjob/ExportFormat.java
exportjob/ExportJobStatus.java
auth/AuthRefreshTokenStatus.java
runner/RunnerCommandType.java
runner/RunnerCommandStatus.java
runner/RunnerEventStatus.java
runner/RunnerInstanceStatus.java
```

Rules:

```text
1. Enum constant names should equal database enum values.
2. API responses should serialize enum names as strings.
3. Do not add enum values in code without migration and API contract update.
```

---

## 20. Relationship Loading Strategy

Default recommendation:

```text
1. Use LAZY relationships for JPA associations.
2. Avoid large eager graphs.
3. Use repository queries/EntityGraph/DTO projections for list pages.
4. Use assemblers for detail pages requiring multiple relations.
5. Avoid N+1 queries in dataset rows, results and comparison matrix.
```

High-volume areas:

```text
dataset_rows
test_case_results
runner_events
```

Rules:

```text
1. Paginate high-volume tables.
2. Do not load all rows/results for large runs.
3. Use streaming/batched export workers for large Excel export.
4. Use indexes from ERD/migration for run/result filtering.
```

---

## 21. Snapshot and Hash Mapping

TestRun stores sanitized snapshots and hashes.

Fields:

```text
domain_config_snapshot_json
promptfoo_config_snapshot_json
domain_config_hash
promptfoo_config_hash
```

Rules:

```text
1. Snapshot JSON must be canonicalized before hashing.
2. Snapshot must be sanitized.
3. Full generated Promptfoo config should be written as sanitized artifact file.
4. DB snapshot is compact and for quick debug/audit.
5. Hash lets backend verify old run config did not change meaning.
```

Recommended components:

```text
testrun/service/RunSnapshotService
testrun/service/RunConfigHashService
common/json/CanonicalJsonService
common/masking/VfqcSensitiveDataMasker
```

---

## 22. Module-Level Invariant Checklist

### Auth

```text
- Refresh token never in JSON response.
- Refresh token stored as HttpOnly cookie.
- Refresh token hash only if persisted.
```

### Workspace

```text
- Creator becomes OWNER.
- ACTIVE membership required for normal user permissions.
- Last OWNER protection.
```

### Project

```text
- Create project + default dataset schema in one transaction.
- Setup status is read-only aggregation.
```

### Secret

```text
- Plaintext accepted only create/rotate/runtime-only.
- Plaintext never returned.
- encryptedValue never mapped to response.
```

### Provider

```text
- Provider secret scope compatible with provider scope.
- Provider test logs are masked.
- Disabled/deleted provider cannot be selected for new run.
```

### Dataset

```text
- ACTIVE DatasetVersion immutable.
- DRAFT required for edits.
- Publish draft atomically updates current version.
- Row revision enforced for inline edit if provided.
```

### Target

```text
- Raw cURL sanitized.
- Secrets become bindings.
- Test connection example masked.
- ACTIVE requires response extraction config.
```

### Evaluator

```text
- EvaluatorVersion immutable.
- Custom JS not accepted from user.
- TestRun locks exact evaluator versions.
```

### TestRun

```text
- Create locks all config references/snapshots.
- Redis command has references only.
- FAILED run status means execution/system failure.
- Comparison supports multiple targets.
```

### Runner/SSE

```text
- Runner internal auth is separate.
- Runner events persisted before SSE.
- SSE id equals runner_events.sequence_no.
```

### Export

```text
- Common ExportJob entity for dataset/result exports.
- Download checks permission.
- Result export defaults to FINAL when requested.
```

---

## 23. AI Coding Agent Checklist

When generating an entity/service for a table:

```text
1. Find table in Entity Ownership Map.
2. Use correct module and id type.
3. Use Java enum matching ERD enum values.
4. Use JsonNode or project-standard JSON mapping for JSONB.
5. Add soft-delete fields when present.
6. Do not expose sensitive fields in mapper.
7. Put repository in owning module.
8. Put service interface in service/ with Javadoc.
9. Put implementation in service/impl/ with @Slf4j.
10. Put @Transactional on DB methods.
11. Use MapStruct for simple mapping.
12. Use assembler for aggregate/read-model mapping.
13. Enforce invariants in service/validator/state machine.
14. Keep logs English and non-sensitive.
15. Keep user-facing localization out of backend business code.
```
