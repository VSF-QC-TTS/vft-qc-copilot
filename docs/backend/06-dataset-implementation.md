# VinFast QC Copilot - Dataset Implementation

Version: `v0.2`  
Status: Spring Boot 4.0.7-first Implementation Spec  
Applies to: `apps/api`

---

## 1. Purpose

This document explains Dataset Schema, Dataset, Dataset Version, Dataset Row, import and export implementation.

---

## 2. Design Principles

1. Dataset schema is versioned.
2. Dataset data is versioned.
3. Rows belong to `DatasetVersion`, not directly to `Dataset`.
4. ACTIVE DatasetVersion is immutable.
5. Editing happens only in DRAFT DatasetVersion.
6. Publishing a DRAFT promotes it to ACTIVE and archives previous ACTIVE.
7. TestRun locks exact dataset/schema/version IDs.
8. Import uses preview -> mapping -> confirm.
9. Excel import/export is MVP core workflow.
10. Dataset variable syntax exposed to UI is `$dataset.<columnName>`.
11. Runner converts dataset variables to Promptfoo syntax later.
12. Dataset content may be business-sensitive; do not log row data.

---

## 3. Package Layout

```text
dataset
  controller/DatasetSchemaController.java
  controller/DatasetController.java
  controller/DatasetVersionController.java
  controller/DatasetRowController.java
  controller/DatasetImportJobController.java
  controller/DatasetExportController.java
  dto/request/CreateDatasetSchemaVersionRequest.java
  dto/request/CreateDatasetRequest.java
  dto/request/UpdateDatasetRequest.java
  dto/request/CreateDatasetDraftRequest.java
  dto/request/CreateDatasetRowRequest.java
  dto/request/PatchDatasetRowCellsRequest.java
  dto/request/BulkUpsertDatasetRowsRequest.java
  dto/request/PublishDatasetDraftRequest.java
  dto/request/DiscardDatasetDraftRequest.java
  dto/request/CreateDatasetImportJobRequest.java
  dto/request/ConfirmDatasetImportJobRequest.java
  dto/response/DatasetSchemaResponse.java
  dto/response/DatasetSchemaColumnResponse.java
  dto/response/DatasetResponse.java
  dto/response/DatasetVersionResponse.java
  dto/response/DatasetRowResponse.java
  dto/response/DatasetVariableResponse.java
  dto/response/DatasetImportJobResponse.java
  entity/DatasetSchema.java
  entity/DatasetSchemaColumn.java
  entity/Dataset.java
  entity/DatasetVersion.java
  entity/DatasetRow.java
  entity/DatasetImportJob.java
  enums/DatasetSchemaScope.java
  enums/DatasetScope.java
  enums/DatasetVersionStatus.java
  enums/DatasetImportStatus.java
  enums/DatasetColumnDataType.java
  repository/*Repository.java
  mapper/*Mapper.java
  service/DatasetSchemaService.java
  service/DatasetService.java
  service/DatasetVersionService.java
  service/DatasetRowService.java
  service/DatasetDraftService.java
  service/DatasetImportService.java
  service/DatasetExportService.java
  service/DatasetVariableService.java
  service/DatasetLookupService.java
  validator/DatasetSchemaValidator.java
  validator/DatasetRowValidator.java
  validator/DatasetImportMappingValidator.java
  assembler/DatasetSchemaAssembler.java
  assembler/DatasetVersionAssembler.java
  excel/DatasetExcelReader.java
  excel/DatasetExcelWriter.java
  excel/DatasetExcelTemplate.java
```

---

## 4. Entity Rules

### DatasetSchema

Fields:

```text
id
scope
projectId
workspaceId
ownerUserId
version
name
createdBy
createdAt
```

Rules:

1. PROJECT schema requires `projectId`.
2. WORKSPACE schema requires `workspaceId`.
3. PERSONAL schema requires `ownerUserId`.
4. Schema version is immutable after creation.
5. New schema version creates new schema + columns.

### DatasetSchemaColumn

Fields:

```text
id
schemaId
name
displayName
dataType
required
description
orderIndex
createdAt
updatedAt
```

Rules:

1. Column `name` is machine-readable and stable.
2. `displayName` is UI-friendly.
3. Duplicate column names are not allowed within one schema.
4. Column names must be safe variable names.

### Dataset

Fields:

```text
id
scope
workspaceId
projectId
ownerUserId
currentVersionId
name
nameLookup
description
sourceDatasetId
sourceProjectId
sourceWorkspaceId
sourceType
createdBy
createdAt
updatedAt
deletedAt
```

Rules:

1. Metadata can change without changing rows.
2. Deleting dataset is soft delete.
3. Historical TestRuns remain readable.

### DatasetVersion

Fields:

```text
id
datasetId
version
schemaId
schemaVersion
sourceDatasetVersionId
baseVersionId
importJobId
importMappingJson
rowCount
status
createdBy
createdAt
publishedAt
discardedAt
```

Rules:

1. ACTIVE/ARCHIVED/DISCARDED versions are read-only.
2. DRAFT is editable.
3. `rowCount` must stay consistent.
4. Publishing must update `Dataset.currentVersionId` in same transaction.

### DatasetRow

Fields:

```text
id
datasetVersionId
rowIndex
cellsJson
rowHash
createdAt
updatedAt
```

Rules:

1. `cellsJson` uses JSONB.
2. Validate cells against schema columns.
3. Do not log full cells.
4. Row order is stable through `rowIndex`.

---

## 5. Draft Workflow

Create draft:

1. Check dataset edit permission.
2. Ensure no existing editable DRAFT or follow API policy.
3. Copy ACTIVE rows to DRAFT if requested.
4. Set `baseVersionId`.

Patch row:

1. Check version is DRAFT.
2. Validate cells against schema.
3. Save row.
4. Update `rowCount` if insert/delete.

Publish draft:

1. Check version is DRAFT.
2. Validate required columns.
3. Archive old ACTIVE.
4. Promote draft to ACTIVE.
5. Update dataset current version.
6. Set `publishedAt`.

Discard draft:

1. Check version is DRAFT.
2. Set status DISCARDED.
3. Set `discardedAt`.
4. Do not physically delete rows unless ERD explicitly allows cleanup.

---

## 6. Excel Import

Use Apache POI `poi-ooxml`.

Recommended:

1. `XSSFWorkbook` for small import preview.
2. Row limit for preview.
3. File size limit.
4. Sheet count limit.
5. Cell text length limit.
6. No row-data logs.

Flow:

1. Upload/import request creates `DatasetImportJob`.
2. Reader extracts headers and sample rows.
3. Backend returns preview and mapping candidates.
4. User confirms mapping.
5. Backend validates mapping.
6. Backend creates DRAFT DatasetVersion and rows.
7. Job status becomes COMPLETED or FAILED with safe error.

Rules:

1. Do not trust Excel headers blindly.
2. Normalize header names for suggested mapping only.
3. Validate final mapping against dataset schema.
4. Sanitize error messages.

---

## 7. Excel Export

Dataset export delegates to `exportjob` module.

Rules:

1. Use shared ExportJob entity.
2. Use `SXSSFWorkbook` for large exports.
3. Pass every cell through `FormulaInjectionSanitizer`.
4. Do not include secrets or target/provider config.
5. Download endpoint must re-check permission.

---

## 8. Spring Usage

Use:

1. Spring MVC multipart support for file upload if API contract includes upload.
2. `@Validated` / `@Valid` DTOs.
3. Spring Data `Pageable` for row listing.
4. `@Transactional` for draft/publish/import-confirm.
5. `@Transactional(readOnly = true)` for list/detail/preview reads.
6. `TaskExecutor`/ExportJob worker for long export jobs.

Do not:

1. Create raw threads in controllers.
2. Load huge XLSX exports fully into memory.
3. Mutate ACTIVE dataset version.
4. Log dataset rows.

---

## 9. Tests

Required tests:

1. ACTIVE version cannot be edited.
2. DRAFT row validation catches missing required cells.
3. Publish archives old active and updates current version.
4. Import preview does not persist rows.
5. Confirm import creates draft rows.
6. Export sanitizes formula injection cells.
7. Deleted dataset cannot be selected for new run.
