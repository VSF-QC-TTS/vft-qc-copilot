# VinFast QC Copilot - Dataset Implementation

Version: `v0.1`  
Status: Backend Implementation Spec Draft  
Applies to: `apps/api` Spring Boot backend  
Aligned with: PRD v0.7, ERD v0.5, API Contract `05-dataset-api.md`

---

## 1. Purpose

This document explains how to implement Dataset Schema, Dataset, Dataset Version, Dataset Row, Dataset Import and Dataset Export features in the Spring Boot backend.

It is written for AI coding agents and backend developers so generated code follows the same versioning model, transaction boundaries, validation rules and API behavior.

This file covers:

```text
1. Dataset schema and schema column implementation.
2. Dataset metadata implementation.
3. Dataset version implementation.
4. Dataset row implementation.
5. DRAFT edit workflow.
6. Publish/discard DRAFT workflow.
7. Excel import preview -> mapping -> confirm flow.
8. Clone/import from project, workspace and personal datasets.
9. Dataset variable metadata for target/evaluator mapping UI.
10. Dataset export through common ExportJob.
11. Permission checks, optimistic concurrency and error handling.
12. DTO, mapper, transaction, async and logging rules.
```

This file does not redefine API request/response contracts. API shapes are defined in `docs/api/05-dataset-api.md`.

---

## 2. Design Principles

Dataset code must follow these principles:

```text
1. Dataset schema is versioned.
2. Dataset data is versioned.
3. Dataset rows belong to DatasetVersion, not directly to Dataset.
4. ACTIVE DatasetVersion is immutable.
5. Editing happens only in DRAFT DatasetVersion.
6. Publishing a DRAFT creates or promotes a new ACTIVE current version.
7. TestRun must lock datasetId, datasetVersionId, datasetSchemaId and datasetSchemaVersion.
8. Import uses preview -> mapping -> confirm flow.
9. Backend validation is authoritative even if frontend validates first.
10. Dataset variables use UI syntax `$dataset.<columnName>` in API responses.
11. Runner converts dataset variables to Promptfoo syntax later; dataset module does not expose Promptfoo as source of truth.
12. Excel import/export is core MVP workflow, not optional debug functionality.
13. Export uses the shared ExportJob model so dataset export and result export behave consistently.
```

Security and localization rules:

```text
1. Dataset APIs must not expose plaintext secrets, target credentials or provider keys.
2. Imported Excel content may contain business-sensitive text; avoid logging row data.
3. Backend code/log/error codes should use English.
4. Vietnamese user-facing copy belongs in frontend i18n or a message catalog, not inline service logic.
```

---

## 3. Package Layout

Recommended package layout:

```text
vn.vinfast.vfqc.api
  dataset
    controller
      DatasetSchemaController.java
      DatasetController.java
      DatasetVersionController.java
      DatasetRowController.java
      DatasetImportSourceController.java
      DatasetImportJobController.java
      DatasetExportController.java
    dto
      request
        CreateDatasetSchemaVersionRequest.java
        UpdateDatasetRequest.java
        CreateDatasetDraftRequest.java
        CreateDatasetRowRequest.java
        PatchDatasetRowCellsRequest.java
        BulkUpsertDatasetRowsRequest.java
        PublishDatasetDraftRequest.java
        DiscardDatasetDraftRequest.java
        CreateDatasetImportJobRequest.java
        ConfirmDatasetImportJobRequest.java
        CreateDatasetExportRequest.java
      response
        DatasetSchemaResponse.java
        DatasetSchemaColumnResponse.java
        DatasetResponse.java
        DatasetVersionResponse.java
        DatasetRowResponse.java
        DatasetVariableResponse.java
        DatasetImportJobResponse.java
        DatasetImportSourceProjectResponse.java
        DatasetImportSourceDatasetResponse.java
    entity
      DatasetSchema.java
      DatasetSchemaColumn.java
      Dataset.java
      DatasetVersion.java
      DatasetRow.java
      DatasetImportJob.java
    enums
      DatasetSchemaScope.java
      DatasetScope.java
      DatasetSourceType.java
      DatasetVersionStatus.java
      DatasetImportSourceType.java
      DatasetImportStatus.java
      DatasetColumnDataType.java
    mapper
      DatasetSchemaMapper.java
      DatasetMapper.java
      DatasetVersionMapper.java
      DatasetRowMapper.java
      DatasetImportJobMapper.java
    repository
      DatasetSchemaRepository.java
      DatasetSchemaColumnRepository.java
      DatasetRepository.java
      DatasetVersionRepository.java
      DatasetRowRepository.java
      DatasetImportJobRepository.java
    service
      DatasetSchemaService.java
      DatasetService.java
      DatasetVersionService.java
      DatasetRowService.java
      DatasetDraftService.java
      DatasetImportSourceService.java
      DatasetImportService.java
      DatasetExportService.java
      DatasetVariableService.java
      DatasetLookupService.java
      impl
        DatasetSchemaServiceImpl.java
        DatasetServiceImpl.java
        DatasetVersionServiceImpl.java
        DatasetRowServiceImpl.java
        DatasetDraftServiceImpl.java
        DatasetImportSourceServiceImpl.java
        DatasetImportServiceImpl.java
        DatasetExportServiceImpl.java
        DatasetVariableServiceImpl.java
        DatasetLookupServiceImpl.java
    validator
      DatasetSchemaValidator.java
      DatasetRowValidator.java
      DatasetImportMappingValidator.java
    assembler
      DatasetSchemaAssembler.java
      DatasetVersionAssembler.java
      DatasetVariableAssembler.java
      DatasetImportPreviewAssembler.java
    excel
      DatasetExcelReader.java
      DatasetExcelWriter.java
      DatasetExcelTemplate.java

  exportjob
    service
      ExportJobService.java
      ExportJobWorker.java
```

Rules:

```text
1. Controllers stay thin and call services.
2. Services own business use cases.
3. Implementations live under `service/impl`.
4. MapStruct maps simple entity to response fields.
5. Assemblers may combine schema + columns + dataset + version + row counts.
6. Validators own schema/row/mapping validation rules.
7. Excel reader/writer should be isolated from service business rules.
8. DatasetExportService should delegate common job creation/execution to exportjob module.
```

---

## 4. Entity Implementation

### 4.1. DatasetSchema

ERD table:

```text
dataset_schemas
```

Recommended entity fields:

```java
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "dataset_schemas")
public class DatasetSchema {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "scope", nullable = false)
  private DatasetSchemaScope scope;

  @Column(name = "project_id")
  private UUID projectId;

  @Column(name = "workspace_id")
  private UUID workspaceId;

  @Column(name = "owner_user_id")
  private UUID ownerUserId;

  @Column(name = "version", nullable = false)
  private Integer version;

  @Column(name = "name", nullable = false, length = 120)
  private String name;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;
}
```

Rules:

```text
1. Scope determines which foreign key is required.
2. PROJECT schema requires projectId.
3. WORKSPACE schema requires workspaceId.
4. PERSONAL schema requires ownerUserId.
5. Schema version is immutable after creation.
6. Creating a new schema version creates new DatasetSchema + DatasetSchemaColumn rows.
```

### 4.2. DatasetSchemaColumn

ERD table:

```text
dataset_schema_columns
```

Recommended entity fields:

```java
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "dataset_schema_columns")
public class DatasetSchemaColumn {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false, updatable = false)
  private Long id;

  @Column(name = "schema_id", nullable = false)
  private UUID schemaId;

  @Column(name = "name", nullable = false, length = 120)
  private String name;

  @Column(name = "display_name", length = 200)
  private String displayName;

  @Enumerated(EnumType.STRING)
  @Column(name = "data_type", nullable = false)
  private DatasetColumnDataType dataType;

  @Column(name = "required", nullable = false)
  private boolean required;

  @Column(name = "description", columnDefinition = "text")
  private String description;

  @Column(name = "order_index", nullable = false)
  private Integer orderIndex;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
}
```

Rules:

```text
1. Column `name` is stable machine-readable key.
2. Column `displayName` is UI-friendly.
3. Column order is stable through orderIndex.
4. Duplicate column names are not allowed within one schema version.
5. Column names should be normalized according to API naming policy.
```

### 4.3. Dataset

ERD table:

```text
datasets
```

Recommended entity fields:

```java
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "datasets")
public class Dataset {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "scope", nullable = false)
  private DatasetScope scope;

  @Column(name = "workspace_id")
  private UUID workspaceId;

  @Column(name = "project_id")
  private UUID projectId;

  @Column(name = "owner_user_id")
  private UUID ownerUserId;

  @Column(name = "current_version_id")
  private UUID currentVersionId;

  @Column(name = "name", nullable = false, length = 120)
  private String name;

  @Column(name = "description", columnDefinition = "text")
  private String description;

  @Column(name = "source_dataset_id")
  private UUID sourceDatasetId;

  @Column(name = "source_project_id")
  private UUID sourceProjectId;

  @Column(name = "source_workspace_id")
  private UUID sourceWorkspaceId;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_type")
  private DatasetSourceType sourceType;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;
}
```

Rules:

```text
1. `currentVersionId` points to the current ACTIVE version.
2. Dataset metadata can be updated without changing rows.
3. Deleting a dataset is soft delete.
4. TestRuns that already reference a dataset version remain readable.
5. Do not cascade delete rows used by historical TestRuns.
```

### 4.4. DatasetVersion

ERD table:

```text
dataset_versions
```

Recommended entity fields:

```java
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "dataset_versions")
public class DatasetVersion {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "dataset_id", nullable = false)
  private UUID datasetId;

  @Column(name = "version", nullable = false)
  private Integer version;

  @Column(name = "schema_id", nullable = false)
  private UUID schemaId;

  @Column(name = "schema_version", nullable = false)
  private Integer schemaVersion;

  @Column(name = "source_dataset_version_id")
  private UUID sourceDatasetVersionId;

  @Column(name = "base_version_id")
  private UUID baseVersionId;

  @Column(name = "import_job_id")
  private UUID importJobId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "import_mapping_json", columnDefinition = "jsonb")
  private JsonNode importMappingJson;

  @Column(name = "row_count", nullable = false)
  private Integer rowCount;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private DatasetVersionStatus status;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "published_at")
  private OffsetDateTime publishedAt;

  @Column(name = "discarded_at")
  private OffsetDateTime discardedAt;
}
```

Rules:

```text
1. ACTIVE and ARCHIVED versions are read-only.
2. DRAFT versions are editable.
3. baseVersionId points to the version from which a DRAFT was created.
4. Publishing a DRAFT updates Dataset.currentVersionId.
5. Backend policy is one current ACTIVE DatasetVersion per Dataset. The old current version is set to ARCHIVED when a new version is published.
6. `rowCount` must be maintained when rows are inserted/deleted/published.
```

### 4.5. DatasetRow

ERD table:

```text
dataset_rows
```

Recommended entity fields:

```java
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "dataset_rows")
public class DatasetRow {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false, updatable = false)
  private Long id;

  @Column(name = "dataset_version_id", nullable = false)
  private UUID datasetVersionId;

  @Column(name = "row_index", nullable = false)
  private Integer rowIndex;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "data_json", nullable = false, columnDefinition = "jsonb")
  private JsonNode dataJson;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata_json", columnDefinition = "jsonb")
  private JsonNode metadataJson;

  @Column(name = "revision", nullable = false)
  private Integer revision;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
}
```

Rules:

```text
1. Each DatasetRow belongs to one DatasetVersion.
2. `dataJson` keys must match schema column names.
3. `revision` supports optimistic concurrency for inline editing.
4. Patch row cells must check expected revision when provided.
5. Do not log full dataJson.
```

### 4.6. DatasetImportJob

ERD table:

```text
dataset_import_jobs
```

Recommended entity fields:

```java
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "dataset_import_jobs")
public class DatasetImportJob {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "target_project_id", nullable = false)
  private UUID targetProjectId;

  @Column(name = "target_dataset_id")
  private UUID targetDatasetId;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_type", nullable = false)
  private DatasetImportSourceType sourceType;

  @Column(name = "source_file_id")
  private String sourceFileId;

  @Column(name = "source_dataset_id")
  private UUID sourceDatasetId;

  @Column(name = "source_dataset_version_id")
  private UUID sourceDatasetVersionId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private DatasetImportStatus status;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "mapping_json", columnDefinition = "jsonb")
  private JsonNode mappingJson;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "preview_json", columnDefinition = "jsonb")
  private JsonNode previewJson;

  @Column(name = "error_message", columnDefinition = "text")
  private String errorMessage;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "finished_at")
  private OffsetDateTime finishedAt;
}
```

Rules:

```text
1. Import job preview may store sample rows and detected columns.
2. Import job must not create ACTIVE dataset rows before confirm.
3. Error message must be safe and not include raw file content.
4. Confirmed import creates dataset/version/rows in a transaction.
```

---

## 5. Enum Implementation

Recommended enums:

```java
public enum DatasetSchemaScope {
  PROJECT,
  WORKSPACE,
  PERSONAL
}

public enum DatasetScope {
  PROJECT,
  WORKSPACE,
  PERSONAL
}

public enum DatasetSourceType {
  EXCEL,
  PROJECT_CLONE,
  WORKSPACE_DATASET,
  PERSONAL_DATASET,
  MANUAL
}

public enum DatasetVersionStatus {
  DRAFT,
  ACTIVE,
  ARCHIVED
}

public enum DatasetImportSourceType {
  EXCEL,
  PROJECT_CLONE,
  WORKSPACE_DATASET,
  PERSONAL_DATASET
}

public enum DatasetImportStatus {
  PENDING,
  PREVIEWED,
  IMPORTING,
  COMPLETED,
  FAILED,
  CANCELLED
}

public enum DatasetColumnDataType {
  TEXT,
  NUMBER,
  BOOLEAN,
  JSON,
  DATE,
  DATETIME
}
```

Rules:

```text
1. Store enums as strings with `EnumType.STRING`.
2. Use names exactly as API/ERD values.
3. Unknown enum input should produce VALIDATION_ERROR.
```

---

## 6. Repository Implementation

Recommended repositories:

```java
public interface DatasetSchemaRepository extends JpaRepository<DatasetSchema, UUID> {
  Optional<DatasetSchema> findFirstByProjectIdOrderByVersionDesc(UUID projectId);
  Optional<DatasetSchema> findById(UUID schemaId);
}

public interface DatasetSchemaColumnRepository extends JpaRepository<DatasetSchemaColumn, Long> {
  List<DatasetSchemaColumn> findBySchemaIdOrderByOrderIndexAsc(UUID schemaId);
  boolean existsBySchemaIdAndNameIgnoreCase(UUID schemaId, String name);
}

public interface DatasetRepository extends JpaRepository<Dataset, UUID> {
  Optional<Dataset> findByIdAndDeletedAtIsNull(UUID datasetId);
  Page<Dataset> findByProjectIdAndDeletedAtIsNull(UUID projectId, Pageable pageable);
  boolean existsByProjectIdAndNameIgnoreCaseAndDeletedAtIsNull(UUID projectId, String name);
}

public interface DatasetVersionRepository extends JpaRepository<DatasetVersion, UUID> {
  Optional<DatasetVersion> findByIdAndDatasetId(UUID versionId, UUID datasetId);
  Page<DatasetVersion> findByDatasetIdOrderByVersionDesc(UUID datasetId, Pageable pageable);
  Optional<DatasetVersion> findByDatasetIdAndStatus(UUID datasetId, DatasetVersionStatus status);
  Optional<DatasetVersion> findFirstByDatasetIdOrderByVersionDesc(UUID datasetId);
}

public interface DatasetRowRepository extends JpaRepository<DatasetRow, Long> {
  Page<DatasetRow> findByDatasetVersionIdOrderByRowIndexAsc(UUID datasetVersionId, Pageable pageable);
  Optional<DatasetRow> findByIdAndDatasetVersionId(Long rowId, UUID datasetVersionId);
  long countByDatasetVersionId(UUID datasetVersionId);
  void deleteByIdAndDatasetVersionId(Long rowId, UUID datasetVersionId);
}

public interface DatasetImportJobRepository extends JpaRepository<DatasetImportJob, UUID> {
  Optional<DatasetImportJob> findByIdAndCreatedBy(UUID importJobId, UUID createdBy);
}
```

Rules:

```text
1. Add custom queries for complex source import filters.
2. Avoid loading all rows for large datasets; always paginate rows.
3. Use batch insert/update where possible for bulk paste and import confirm.
4. Do not delete rows from historical ACTIVE versions.
5. Use explicit permission checks before repository mutations.
```

---

## 7. DTO Implementation

### 7.1. Request records

Use Java records with validation and Swagger examples.

Example schema column request:

```java
@Schema(name = "DatasetSchemaColumnRequest")
public record DatasetSchemaColumnRequest(
    @NotBlank
    @Size(max = 120)
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]*$")
    @Schema(example = "expectedIntent", description = "Stable machine-readable column name")
    String name,

    @Size(max = 200)
    @Schema(example = "Expected intent")
    String displayName,

    @NotNull
    @Schema(example = "TEXT")
    DatasetColumnDataType dataType,

    @Schema(example = "false")
    boolean required,

    @Size(max = 1000)
    @Schema(example = "Expected detected intent")
    String description,

    @NotNull
    @Min(0)
    @Schema(example = "3")
    Integer orderIndex) {}
```

Example row patch request:

```java
@Schema(name = "PatchDatasetRowCellsRequest")
public record PatchDatasetRowCellsRequest(
    @NotNull
    @Schema(example = "4", description = "Expected current row revision for optimistic concurrency")
    Integer expectedRevision,

    @NotNull
    @Schema(description = "Cell values keyed by dataset schema column name")
    JsonNode cells) {}
```

Example bulk upsert request:

```java
@Schema(name = "BulkUpsertDatasetRowsRequest")
public record BulkUpsertDatasetRowsRequest(
    @NotEmpty
    @Size(max = 1000)
    @Schema(description = "Rows copied from table/Excel-like UI")
    List<JsonNode> rows,

    @Schema(example = "true", description = "Append rows after existing rows when true")
    boolean append) {}
```

Rules:

```text
1. Request records use Jakarta validation.
2. All request fields should have Swagger `@Schema` descriptions/examples where practical.
3. Large JSON examples should be short.
4. Do not use entity classes as request/response bodies.
```

### 7.2. Response records

Example dataset variable response:

```java
@Schema(name = "DatasetVariableResponse")
public record DatasetVariableResponse(
    String name,
    String displayName,
    DatasetColumnDataType dataType,
    boolean required,
    String variable,
    String description) {}
```

Example row response:

```java
@Schema(name = "DatasetRowResponse")
public record DatasetRowResponse(
    Long id,
    UUID datasetVersionId,
    Integer rowIndex,
    JsonNode data,
    JsonNode metadata,
    Integer revision,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
```

Rules:

```text
1. API responses use camelCase field names.
2. Dataset variable field must use `$dataset.<columnName>` syntax.
3. Row data may be returned because dataset is user-managed test content, but logs should not print it.
4. ExportJobResponse comes from common exportjob DTO, not a dataset-specific duplicate.
```

---

## 8. Mapper and Assembler Implementation

### 8.1. MapStruct mappers

Use MapStruct for direct mappings:

```java
@Mapper(componentModel = "spring")
public interface DatasetRowMapper {

  @Mapping(target = "data", source = "dataJson")
  @Mapping(target = "metadata", source = "metadataJson")
  DatasetRowResponse toResponse(DatasetRow row);
}
```

```java
@Mapper(componentModel = "spring")
public interface DatasetVersionMapper {

  DatasetVersionResponse toResponse(DatasetVersion version);
}
```

Rules:

```text
1. Do not write manual toResponse/toEntity methods in services/controllers.
2. Use mappers for simple entity -> response transformations.
3. Use assemblers for aggregate responses that require columns, row counts, source metadata or permission-derived fields.
```

### 8.2. DatasetVariableAssembler

Dataset variables are derived from schema columns.

```java
@Component
@RequiredArgsConstructor
public class DatasetVariableAssembler {

  public List<DatasetVariableResponse> toVariables(List<DatasetSchemaColumn> columns) {
    return columns.stream()
        .sorted(Comparator.comparing(DatasetSchemaColumn::getOrderIndex))
        .map(column -> new DatasetVariableResponse(
            column.getName(),
            column.getDisplayName(),
            column.getDataType(),
            column.isRequired(),
            "$dataset." + column.getName(),
            column.getDescription()))
        .toList();
  }
}
```

Rules:

```text
1. `$dataset.<columnName>` is API/UI syntax.
2. Do not return Promptfoo `{{columnName}}` syntax from dataset APIs.
3. Preserve schema column order.
```

---

## 9. Dataset Schema Services

### 9.1. DatasetSchemaService interface

Example interface:

```java
public interface DatasetSchemaService {

  /**
   * Returns the current project dataset schema with ordered columns.
   *
   * @param projectId project identifier
   * @param currentUser authenticated user performing the read
   * @return current schema response containing ordered columns and UI variables
   */
  DatasetSchemaResponse getCurrentProjectSchema(UUID projectId, CurrentUser currentUser);

  /**
   * Creates a new PROJECT dataset schema version for a project.
   *
   * @param projectId project identifier
   * @param request schema version payload containing column definitions
   * @param currentUser authenticated user creating the schema version
   * @return created schema version with ordered columns
   */
  DatasetSchemaResponse createProjectSchemaVersion(
      UUID projectId,
      CreateDatasetSchemaVersionRequest request,
      CurrentUser currentUser);
}
```

### 9.2. Default project schema creation

Project creation must create default schema in the same transaction as project creation.

Recommended helper service:

```java
public interface DatasetSchemaInitializationService {

  /**
   * Creates the default PROJECT schema for a newly-created project.
   *
   * @param projectId new project id
   * @param createdBy user id that created the project
   * @return created dataset schema id
   */
  UUID createDefaultProjectSchema(UUID projectId, UUID createdBy);
}
```

Default columns:

```text
question TEXT required=true order=1
expected TEXT required=false order=2
```

Rules:

```text
1. Called by ProjectServiceImpl inside project create transaction.
2. Do not expose Promptfoo syntax.
3. Do not create duplicate default schema if project creation is retried after partial failure; transaction should prevent partial commit.
```

---

## 10. Dataset and Version Services

### 10.1. DatasetService interface

Example interface:

```java
public interface DatasetService {

  /**
   * Lists datasets in a project that the current user can view.
   *
   * @param projectId project identifier
   * @param query filtering and pagination parameters
   * @param currentUser authenticated user performing the read
   * @return paged dataset metadata responses
   */
  PageResponse<DatasetResponse> listProjectDatasets(UUID projectId, ListDatasetsQuery query, CurrentUser currentUser);

  /**
   * Updates dataset metadata such as name or description without changing rows.
   *
   * @param datasetId dataset identifier
   * @param request metadata update payload
   * @param currentUser authenticated user performing the update
   * @return updated dataset metadata response
   */
  DatasetResponse updateDataset(UUID datasetId, UpdateDatasetRequest request, CurrentUser currentUser);

  /**
   * Soft deletes a dataset from normal project views while preserving historical references.
   *
   * @param datasetId dataset identifier
   * @param currentUser authenticated user performing delete
   */
  void deleteDataset(UUID datasetId, CurrentUser currentUser);
}
```

### 10.2. DatasetVersionService interface

```java
public interface DatasetVersionService {

  /**
   * Lists versions of a dataset ordered by newest version first.
   *
   * @param datasetId dataset identifier
   * @param query pagination parameters
   * @param currentUser authenticated user performing the read
   * @return paged dataset version responses
   */
  PageResponse<DatasetVersionResponse> listVersions(UUID datasetId, PageQuery query, CurrentUser currentUser);

  /**
   * Returns a dataset version detail response.
   *
   * @param datasetVersionId dataset version identifier
   * @param currentUser authenticated user performing the read
   * @return dataset version detail response
   */
  DatasetVersionResponse getVersion(UUID datasetVersionId, CurrentUser currentUser);
}
```

Rules:

```text
1. Version listing must not load all rows.
2. Version detail may include rowCount and schema info but not full row list.
3. Row list has its own paginated endpoint.
```

---

## 11. Draft Editing Services

### 11.1. DatasetDraftService interface

```java
public interface DatasetDraftService {

  /**
   * Creates or reuses an editable DRAFT version from an existing dataset version.
   *
   * @param sourceVersionId source dataset version to edit from
   * @param request draft creation options
   * @param currentUser authenticated user creating or reusing the draft
   * @return editable DRAFT dataset version response
   */
  DatasetVersionResponse createOrReuseDraft(
      UUID sourceVersionId,
      CreateDatasetDraftRequest request,
      CurrentUser currentUser);

  /**
   * Publishes a DRAFT dataset version as the dataset current ACTIVE version.
   *
   * @param draftVersionId draft dataset version identifier
   * @param request publish options
   * @param currentUser authenticated user publishing the draft
   * @return published ACTIVE dataset version response
   */
  DatasetVersionResponse publishDraft(
      UUID draftVersionId,
      PublishDatasetDraftRequest request,
      CurrentUser currentUser);

  /**
   * Discards a DRAFT dataset version so it can no longer be edited or published.
   *
   * @param draftVersionId draft dataset version identifier
   * @param request discard options
   * @param currentUser authenticated user discarding the draft
   */
  void discardDraft(UUID draftVersionId, DiscardDatasetDraftRequest request, CurrentUser currentUser);
}
```

### 11.2. Create or reuse DRAFT rule

Recommended MVP rule:

```text
1. If the current user already has an editable DRAFT for the same dataset and baseVersionId, return that DRAFT.
2. Otherwise create a new DRAFT version.
3. Copy rows from source version to DRAFT rows.
4. Keep row_index stable.
5. Set revision = 1 for copied rows.
```

If the product chooses workspace-level single draft instead, update API/backend docs before implementation.

### 11.3. Publish DRAFT rule

Publishing a DRAFT should run inside one transaction:

```text
1. Load DRAFT version with lock if needed.
2. Verify current user can update dataset.
3. Verify status = DRAFT and discarded_at is null.
4. Validate all rows against schema.
5. Compute row_count.
6. Archive previous current ACTIVE version by setting status = ARCHIVED, without mutating its rows.
7. Set DRAFT status = ACTIVE.
8. Set published_at.
9. Update Dataset.currentVersionId = draftVersionId.
10. Return published version response.
```

Rules:

```text
1. Publishing must be idempotency-aware if clients retry.
2. Do not mutate previously ACTIVE/ARCHIVED rows.
3. TestRun created before publish still points to old datasetVersionId even after that version becomes ARCHIVED.
4. Normal UI should use Dataset.currentVersionId, not query for arbitrary ACTIVE versions.
```

---

## 12. Dataset Row Services

### 12.1. DatasetRowService interface

```java
public interface DatasetRowService {

  /**
   * Lists rows in a dataset version with stable row_index ordering.
   *
   * @param datasetVersionId dataset version identifier
   * @param query pagination parameters
   * @param currentUser authenticated user performing the read
   * @return paged dataset rows
   */
  PageResponse<DatasetRowResponse> listRows(UUID datasetVersionId, PageQuery query, CurrentUser currentUser);

  /**
   * Inserts one row into an editable DRAFT dataset version.
   *
   * @param draftVersionId draft dataset version identifier
   * @param request row creation payload
   * @param currentUser authenticated user performing the insert
   * @return created row response
   */
  DatasetRowResponse insertRow(UUID draftVersionId, CreateDatasetRowRequest request, CurrentUser currentUser);

  /**
   * Patches one or more cells in a row of an editable DRAFT version.
   *
   * @param draftVersionId draft dataset version identifier
   * @param rowId row identifier
   * @param request patch payload containing expected revision and changed cells
   * @param currentUser authenticated user performing the update
   * @return updated row response with incremented revision
   */
  DatasetRowResponse patchCells(
      UUID draftVersionId,
      Long rowId,
      PatchDatasetRowCellsRequest request,
      CurrentUser currentUser);

  /**
   * Bulk inserts or upserts rows into an editable DRAFT dataset version.
   *
   * @param draftVersionId draft dataset version identifier
   * @param request rows copied from Excel-like UI
   * @param currentUser authenticated user performing bulk update
   * @return bulk operation summary
   */
  BulkDatasetRowsResponse bulkUpsert(
      UUID draftVersionId,
      BulkUpsertDatasetRowsRequest request,
      CurrentUser currentUser);

  /**
   * Deletes one row from an editable DRAFT dataset version.
   *
   * @param draftVersionId draft dataset version identifier
   * @param rowId row identifier
   * @param currentUser authenticated user performing delete
   */
  void deleteRow(UUID draftVersionId, Long rowId, CurrentUser currentUser);
}
```

### 12.2. Row validation

DatasetRowValidator should enforce:

```text
1. All required columns are present and non-null unless allowed by type.
2. Unknown columns are rejected or stored only if schema policy explicitly allows it.
3. TEXT values are strings or coercible according to documented import rules.
4. NUMBER values are numeric.
5. BOOLEAN values are boolean.
6. JSON values are valid JSON.
7. DATE/DATETIME values are parseable according to accepted formats.
8. Row data size does not exceed configured limit.
```

Rules:

```text
1. Validation errors should identify rowIndex and column name.
2. Do not include full cell content in error detail when content may be sensitive.
3. Bulk validation should collect useful errors but avoid huge response bodies.
```

### 12.3. Optimistic concurrency

Patch cell flow:

```text
1. Load row by rowId and draftVersionId.
2. Verify parent DatasetVersion status = DRAFT.
3. If request.expectedRevision is present, compare with row.revision.
4. If mismatch, return DATASET_ROW_REVISION_CONFLICT.
5. Merge changed cells into dataJson.
6. Validate merged dataJson against schema.
7. Increment revision by 1.
8. Save row.
```

Rules:

```text
1. Only DRAFT rows can be patched.
2. ACTIVE/ARCHIVED rows must return DATASET_VERSION_IMMUTABLE.
3. Revision conflict should be 409 Conflict.
```

---

## 13. Import Source and Import Job Services

### 13.1. DatasetImportSourceService

This service lists importable source projects/datasets.

Rules:

```text
1. User must have VIEW permission on source dataset.
2. User must have IMPORT_DATASET permission on target project.
3. Do not reveal source dataset existence if user lacks permission.
4. Use pagination for source lists.
```

### 13.2. DatasetImportService interface

```java
public interface DatasetImportService {

  /**
   * Creates an import preview job for Excel or dataset clone/import source.
   *
   * @param projectId target project identifier
   * @param request import request containing source type and source metadata
   * @param currentUser authenticated user creating the import job
   * @return import job response with preview metadata when available
   */
  DatasetImportJobResponse createImportJob(
      UUID projectId,
      CreateDatasetImportJobRequest request,
      CurrentUser currentUser);

  /**
   * Returns import job status and preview details.
   *
   * @param importJobId import job identifier
   * @param currentUser authenticated user performing the read
   * @return import job response
   */
  DatasetImportJobResponse getImportJob(UUID importJobId, CurrentUser currentUser);

  /**
   * Confirms import mapping and persists the resulting dataset version and rows.
   *
   * @param importJobId import job identifier
   * @param request mapping confirmation payload
   * @param currentUser authenticated user confirming the import
   * @return completed import job response
   */
  DatasetImportJobResponse confirmImportJob(
      UUID importJobId,
      ConfirmDatasetImportJobRequest request,
      CurrentUser currentUser);

  /**
   * Cancels an import job that has not been confirmed yet.
   *
   * @param importJobId import job identifier
   * @param currentUser authenticated user cancelling the job
   */
  void cancelImportJob(UUID importJobId, CurrentUser currentUser);
}
```

### 13.3. Excel import preview flow

Recommended flow:

```text
1. Receive multipart Excel upload.
2. Validate file type and size.
3. Store uploaded file in controlled storage if needed.
4. Parse workbook using DatasetExcelReader.
5. Detect headers and sample rows.
6. Build preview_json with detected columns, sample rows and warnings.
7. Create DatasetImportJob with status PREVIEWED.
8. Return preview response.
```

Rules:

```text
1. Do not persist ACTIVE dataset rows during preview.
2. Do not log row content.
3. Only store a small preview sample in preview_json.
4. Large file parsing may be async if needed, but MVP API contract currently supports preview job status polling.
```

### 13.4. Confirm import flow

Recommended transaction:

```text
1. Load import job and lock row.
2. Verify status = PREVIEWED or PENDING according to source type.
3. Verify user still has target project import permission.
4. Validate source dataset permission if cloning from existing dataset.
5. Validate mapping against source preview/schema and target schema.
6. Create or update target Dataset metadata.
7. Create DatasetVersion with status ACTIVE because confirm import is an explicit publish action.
8. Insert DatasetRow rows in batches.
9. Archive previous current version if the target Dataset already had one.
10. Set Dataset.currentVersionId to new ACTIVE version.
11. Set import job status COMPLETED and finishedAt.
```

MVP recommendation:

```text
Confirmed import creates an ACTIVE DatasetVersion because import confirmation is an explicit publish action.
```

Rules:

```text
1. Use batch insert for rows.
2. Do not silently drop required columns.
3. Store import_mapping_json on DatasetVersion.
4. Preserve source_dataset_id/source_dataset_version_id where applicable.
5. Do not mutate rows of the previous current version; only set its version status to ARCHIVED.
6. If import fails after transaction begins, rollback domain changes and set job FAILED in a separate safe transaction if needed.
```

### 13.5. Project/workspace/personal clone import

Clone/import rules:

```text
1. If source schema is compatible with target schema, rows can be copied directly.
2. If schemas differ, mapping is required.
3. If user chooses to add columns during import, create a new target schema version.
4. Imported rows must be validated against the resulting target schema.
5. Source metadata should be stored for traceability.
```

---

## 14. Dataset Export Implementation

Dataset export uses common ExportJob.

Endpoint:

```text
POST /api/v1/dataset-versions/{datasetVersionId}/exports
```

Implementation flow:

```text
1. Verify user has EXPORT_DATASET or equivalent project permission.
2. Verify datasetVersionId is visible to user.
3. Create ExportJob with jobType = DATASET_VERSION and format = XLSX.
4. Store source.datasetVersionId and sanitized export options.
5. Return 202 Accepted with common ExportJobResponse.
6. Export worker generates XLSX using DatasetExcelWriter.
7. Worker stores file in controlled storage.
8. Worker updates ExportJob status, fileName, fileSizeBytes and expiry metadata.
```

Recommended service:

```java
public interface DatasetExportService {

  /**
   * Creates an asynchronous export job for a dataset version.
   *
   * @param datasetVersionId dataset version identifier
   * @param request export options
   * @param currentUser authenticated user creating the export
   * @return common export job response
   */
  ExportJobResponse createDatasetExport(
      UUID datasetVersionId,
      CreateDatasetExportRequest request,
      CurrentUser currentUser);
}
```

Async rules:

```text
1. Export file generation may use @Async through ExportJobWorker.
2. Export job creation itself should be transactional and fast.
3. Export worker opens its own transactions.
4. Do not keep transaction open while writing large XLSX files.
5. Do not expose server-local file_path to frontend.
6. Download endpoint must check permission again.
```

Excel writer rules:

```text
1. Export columns follow schema column order.
2. Include row index and metadata only if export options request them.
3. Escape cell values properly.
4. Do not include secrets or hidden backend columns.
5. For large exports, stream rows rather than loading all rows into memory if possible.
```

---

## 15. Controller Implementation

### 15.1. DatasetSchemaController

Example:

```java
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Dataset Schemas", description = "Manage dataset schemas and schema versions")
public class DatasetSchemaController {

  private final DatasetSchemaService datasetSchemaService;

  @Operation(summary = "Get current project dataset schema")
  @GetMapping("/projects/{projectId}/dataset-schemas/current")
  public DatasetSchemaResponse getCurrentProjectSchema(
      @PathVariable UUID projectId,
      @AuthenticationPrincipal CurrentUser currentUser) {
    return datasetSchemaService.getCurrentProjectSchema(projectId, currentUser);
  }
}
```

### 15.2. DatasetRowController

Example:

```java
@RestController
@RequestMapping("/api/v1/dataset-versions/{datasetVersionId}/rows")
@RequiredArgsConstructor
@Tag(name = "Dataset Rows", description = "View and edit dataset rows")
public class DatasetRowController {

  private final DatasetRowService datasetRowService;

  @Operation(summary = "Patch dataset row cells", description = "Patches cells in a DRAFT dataset version row using optimistic concurrency.")
  @PatchMapping("/{rowId}/cells")
  public DatasetRowResponse patchCells(
      @PathVariable UUID datasetVersionId,
      @PathVariable Long rowId,
      @Valid @RequestBody PatchDatasetRowCellsRequest request,
      @AuthenticationPrincipal CurrentUser currentUser) {
    return datasetRowService.patchCells(datasetVersionId, rowId, request, currentUser);
  }
}
```

Rules:

```text
1. Controllers do not contain business validation beyond annotation-based request validation.
2. Controllers do not access repositories directly.
3. Controllers should include Swagger annotations.
4. Controllers should not log row content or uploaded file content.
```

---

## 16. Permission Rules

Dataset APIs are scoped through workspace/project access.

Recommended actions:

```text
VIEW_DATASET
IMPORT_DATASET
UPDATE_DATASET
DELETE_DATASET
CREATE_SCHEMA_VERSION
EDIT_DATASET_DRAFT
PUBLISH_DATASET_DRAFT
EXPORT_DATASET
```

Rules:

```text
1. OWNER/ADMIN/MEMBER can import/edit/publish/export in MVP.
2. VIEWER can view dataset metadata/rows but cannot import/edit/publish/export.
3. PLATFORM_ADMIN may access operationally unless a stricter policy says otherwise.
4. Source dataset import requires VIEW permission on source and IMPORT_DATASET permission on target.
5. If user cannot view source dataset, return 404 instead of leaking existence.
```

---

## 17. Transaction and Async Rules

Transaction rules:

```text
1. Metadata update/delete use @Transactional.
2. List/detail endpoints use @Transactional(readOnly = true).
3. Create schema version uses @Transactional.
4. Create/reuse DRAFT uses @Transactional.
5. Row insert/patch/bulk/delete use @Transactional.
6. Publish/discard DRAFT uses @Transactional.
7. Confirm import uses @Transactional for domain persistence.
8. Export job creation uses @Transactional.
```

Async rules:

```text
1. Dataset export file generation should use @Async or a worker if file generation can be slow.
2. Large import parsing may use @Async if API contract is implemented as polling job.
3. Do not use @Async for normal row patch/insert operations.
4. Redis is not required for dataset CRUD in MVP.
5. Redis Streams are used by TestRun/Runner orchestration, not dataset editing.
```

Long transaction caution:

```text
Do not parse huge Excel files or write large XLSX files while holding a database transaction.
```

---

## 18. Logging Rules

Use `@Slf4j` on service implementations.

Recommended logs:

```java
log.info("Creating dataset import job: projectId={}, sourceType={}, requestedBy={}", projectId, sourceType, currentUser.id());
log.info("Dataset import completed: importJobId={}, datasetId={}, versionId={}, rowCount={}", importJobId, datasetId, versionId, rowCount);
log.info("Creating dataset draft: sourceVersionId={}, requestedBy={}", sourceVersionId, currentUser.id());
log.info("Dataset draft published: datasetId={}, versionId={}, rowCount={}, publishedBy={}", datasetId, versionId, rowCount, currentUser.id());
log.warn("Dataset row revision conflict: rowId={}, expectedRevision={}, actualRevision={}", rowId, expectedRevision, actualRevision);
```

Forbidden logs:

```java
log.info("Dataset row data: {}", row.getDataJson());
log.info("Excel file content: {}", rows);
log.info("Full import preview: {}", previewJson);
```

Rules:

```text
1. Do not log row/cell content by default.
2. Log row counts, dataset ids, version ids and status changes.
3. Avoid per-row info logs during bulk import/export.
4. Use debug logs sparingly for troubleshooting, still without sensitive content.
```

---

## 19. Error Codes

Use stable error codes. Recommended codes:

```text
DATASET_NOT_FOUND
DATASET_NAME_DUPLICATE
DATASET_ACCESS_DENIED
DATASET_VERSION_NOT_FOUND
DATASET_VERSION_IMMUTABLE
DATASET_DRAFT_NOT_FOUND
DATASET_DRAFT_ALREADY_DISCARDED
DATASET_DRAFT_ALREADY_PUBLISHED
DATASET_SCHEMA_NOT_FOUND
DATASET_SCHEMA_COLUMN_DUPLICATE
DATASET_SCHEMA_INVALID
DATASET_ROW_NOT_FOUND
DATASET_ROW_INVALID
DATASET_ROW_REVISION_CONFLICT
DATASET_IMPORT_JOB_NOT_FOUND
DATASET_IMPORT_SOURCE_NOT_FOUND
DATASET_IMPORT_MAPPING_INVALID
DATASET_IMPORT_FILE_INVALID
DATASET_IMPORT_FAILED
DATASET_EXPORT_NOT_ALLOWED
EXPORT_JOB_NOT_FOUND
VALIDATION_ERROR
AUTHENTICATION_REQUIRED
FORBIDDEN
INTERNAL_ERROR
```

Rules:

```text
1. Use 409 Conflict for revision conflicts and duplicate names.
2. Use 400 Validation Error for invalid schema/mapping/row data.
3. Use 404 when hiding inaccessible source dataset existence.
4. ProblemDetail should include traceId and messageKey.
5. Do not include full row/cell content in error details.
```

---

## 20. Relationship with Project Setup Status

Project setup-status depends on dataset module.

Dataset module should expose read-only lookup methods:

```java
public interface DatasetLookupService {

  /**
   * Checks whether a project has at least one non-deleted dataset with an ACTIVE current version.
   *
   * @param projectId project identifier
   * @return dataset readiness summary for project setup-status
   */
  DatasetReadinessSummary getProjectDatasetReadiness(UUID projectId);
}
```

Readiness should provide:

```text
1. hasDataset
2. hasActiveDatasetVersion
3. datasetCount
4. activeDatasetVersionCount
5. latestDatasetId if useful
6. blocker codes when not ready
```

Rules:

```text
1. Lookup service is read-only.
2. Do not return row data to setup-status.
3. Do not perform expensive full row scans.
```

---

## 21. Testing Checklist

Unit tests:

```text
1. Default project schema contains question and expected.
2. Schema column duplicate names are rejected case-insensitively.
3. ACTIVE DatasetVersion cannot be edited.
4. DRAFT DatasetVersion can insert/patch/delete rows.
5. Patch row increments revision.
6. Patch row with stale revision returns DATASET_ROW_REVISION_CONFLICT.
7. Required column validation fails when missing.
8. Publish DRAFT updates Dataset.currentVersionId.
9. Discarded DRAFT cannot be edited or published.
10. Dataset variables use `$dataset.<columnName>` syntax.
```

Integration tests:

```text
1. MEMBER can import dataset into project.
2. VIEWER can view dataset but cannot edit/import/export.
3. Excel import preview does not create ACTIVE rows before confirm.
4. Confirm import creates DatasetVersion and rows in one transaction.
5. Import from inaccessible source dataset returns 404.
6. Dataset export creates ExportJobResponse with jobType DATASET_VERSION.
7. Export download checks permission and does not expose local path.
8. Project setup-status sees dataset readiness after import/publish.
```

Performance/regression tests:

```text
1. List rows is paginated and does not load all rows.
2. Bulk upsert handles expected MVP row limit.
3. Import confirm uses batch insert.
4. Export worker can stream or page rows for large datasets.
5. Logs do not contain sample secret-like cell values from test fixture.
```

---

## 22. AI Coding Agent Guardrails

When generating code for this module:

```text
1. Use service interfaces with Javadoc.
2. Put implementations in `service/impl` and annotate with `@Slf4j`, `@Service`, `@RequiredArgsConstructor`.
3. Use MapStruct mappers; do not create manual toResponse/toEntity helpers inside services.
4. Use record DTOs with validation and Swagger examples.
5. Use `@Transactional` at service implementation boundaries.
6. Never mutate ACTIVE or ARCHIVED DatasetVersion rows.
7. Implement DRAFT editing with row revision checks.
8. Do not log dataset row/cell content, import preview content or uploaded file content.
9. Use common ExportJobResponse for dataset export.
10. Use @Async only for long-running import/export worker flows, not normal CRUD.
11. Keep backend messages/logs in English and expose messageKey for frontend localization.
12. Do not introduce Redis into dataset CRUD unless a future async import/export contract requires it.
```
