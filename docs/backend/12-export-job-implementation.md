# VinFast QC Copilot - Export Job Implementation

Version: `v0.2`  
Status: Spring Boot 4.0.7-first Implementation Spec  
Applies to: `apps/api`

---

## 1. Purpose

This document explains common export job infrastructure for dataset export and test run result export.

---

## 2. Design Principles

1. Dataset export and result export use the same ExportJob entity.
2. Export creation is authenticated API action.
3. Export execution is async/worker-based and observable through status.
4. Export files are generated from persisted authorized data.
5. Download endpoint checks permission again.
6. Internal file paths are never exposed.
7. Exported files must not contain secrets or unsanitized raw request/response.
8. TestRun result export defaults to FINAL reviewed result.
9. Failed jobs store safe error code/message, not stack traces.
10. Excel writer uses Apache POI 5.5.x.

---

## 3. Package Layout

```text
exportjob
  controller/ExportJobController.java
  dto/request/CreateDatasetExportRequest.java
  dto/request/CreateTestRunExportRequest.java
  dto/response/ExportJobResponse.java
  dto/response/ExportJobDownloadResponse.java
  entity/ExportJob.java
  enums/ExportJobType.java
  enums/ExportFormat.java
  enums/ExportJobStatus.java
  enums/ExportResultMode.java
  repository/ExportJobRepository.java
  mapper/ExportJobMapper.java
  service/ExportJobService.java
  service/ExportJobQueryService.java
  service/ExportJobWorker.java
  service/ExportFileWriter.java
  service/ExportFileNamePolicy.java
  service/ExportDownloadService.java
  service/impl/ExportJobServiceImpl.java
  service/impl/ExportJobQueryServiceImpl.java
  service/impl/ExportJobWorkerImpl.java
  service/impl/XlsxExportFileWriter.java
  service/impl/ExportFileNamePolicyImpl.java
  service/impl/ExportDownloadServiceImpl.java
  model/ExportJobCreateCommand.java
  model/ExportDatasetRowModel.java
  model/ExportResultRowModel.java
  model/ExportFileWriteResult.java
```

Module integration:

```text
dataset/service/DatasetExportService.java
testrun/service/TestRunExportService.java
common/storage/StorageService.java
common/masking/FormulaInjectionSanitizer.java
```

---

## 4. Entity Rules

Fields:

```text
id
jobType
format
status
datasetVersionId
testRunId
resultMode
filePath
fileName
fileSizeBytes
errorCode
errorMessage
createdBy
createdAt
startedAt
finishedAt
expiresAt
```

Rules:

1. DATASET_VERSION job requires `datasetVersionId`.
2. TEST_RUN_RESULTS job requires `testRunId`.
3. TEST_RUN_RESULTS default resultMode is FINAL.
4. MVP supports XLSX; CSV optional only if API contract requires.
5. `filePath` is internal only.
6. `errorMessage` is sanitized.

---

## 5. Status State Machine

Statuses:

```text
PENDING
RUNNING
COMPLETED
FAILED
CANCELLED
EXPIRED
```

Transitions:

```text
PENDING -> RUNNING
RUNNING -> COMPLETED
RUNNING -> FAILED
PENDING -> CANCELLED
COMPLETED -> EXPIRED
FAILED -> EXPIRED
```

Rules:

1. Terminal completed/failed/cancelled jobs are not rerun in place.
2. New export request creates a new job.
3. Cleanup may mark old jobs EXPIRED and delete file from storage.

---

## 6. Execution Model

Creation flow:

1. Module service validates source and permission.
2. ExportJobService creates PENDING job.
3. Dispatch worker with `TaskExecutor` or scheduled poller.
4. Return ExportJobResponse.

Worker flow:

1. Mark job RUNNING.
2. Load authorized source data through module exporter strategy.
3. Build export row models.
4. Write XLSX with POI.
5. Store file through StorageService.
6. Mark COMPLETED.
7. On error, sanitize and mark FAILED.

Do not create raw threads in controllers.

---

## 7. Excel Writer

Use Apache POI:

1. `XSSFWorkbook` for small files/templates.
2. `SXSSFWorkbook` for large result exports.
3. Auto-size columns only for small files.
4. Avoid loading all rows into memory for large exports.
5. Apply `FormulaInjectionSanitizer` to every string cell.

Formula injection sanitizer must neutralize values starting with:

```text
=
+
-
@
\t
\r
```

according to the security policy.

---

## 8. Dataset Export

Rules:

1. Requires dataset read/export permission.
2. Exports one DatasetVersion.
3. Includes schema columns and rows.
4. Does not include provider/target secrets.
5. Uses dataset column display names when available.

---

## 9. TestRun Result Export

Rules:

1. Requires project result/export permission.
2. Defaults to FINAL result mode.
3. MACHINE mode includes machine result only if requested.
4. Includes review columns when FINAL.
5. Does not include unsanitized raw request/response.
6. May include short sanitized extracted output/error summaries.

---

## 10. Download

Rules:

1. Check job owner or project/dataset permission again.
2. Only COMPLETED jobs can be downloaded.
3. Do not expose internal path.
4. Use safe filename policy.
5. Return 404 if job inaccessible or expired.

---

## 11. Spring Usage

Use:

1. `TaskExecutor` / `@Async` or scheduled worker.
2. `Resource` response for downloads.
3. `Content-Disposition` with safe filename.
4. `@Transactional` for status transitions.
5. `Clock` for timestamps.

Do not:

1. Run export generation in request thread for large exports.
2. Write files outside configured storage root.
3. Put stack traces in API responses.
4. Include secrets in Excel.

---

## 12. Tests

Required tests:

1. Export job status transitions.
2. Dataset export writes expected columns/rows.
3. Result export uses FINAL mode by default.
4. Formula injection is sanitized.
5. Download re-checks permission.
6. Internal file path is not exposed.
7. Failed job stores sanitized error.
