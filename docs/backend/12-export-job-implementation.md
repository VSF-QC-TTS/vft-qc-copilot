# VinFast QC Copilot - Export Job Implementation

Version: `v0.1`  
Status: Backend Implementation Spec Draft  
Applies to: `apps/api` Spring Boot backend  
Aligned with: PRD v0.7, ERD v0.5, API Contracts `01-api-conventions.md`, `05-dataset-api.md` and `08-test-run-api.md`

---

## 1. Purpose

This document explains how to implement the common export job infrastructure in the Spring Boot backend.

It is written for AI coding agents and backend developers so generated code implements dataset export and test run result export consistently.

This file covers:

```text
1. Common ExportJob entity/service/worker.
2. DatasetVersion export to Excel.
3. TestRun result export to Excel.
4. Shared ExportJobResponse DTO.
5. Async execution and status transitions.
6. File storage and safe download.
7. Permission checks and result-mode rules.
8. Transaction, logging, masking and error handling rules.
9. Testing checklist.
```

This file does not define dataset import. That belongs to `docs/backend/06-dataset-implementation.md`.

This file does not define Promptfoo artifact access. That belongs to `docs/backend/09-test-run-implementation.md`, `docs/backend/10-runner-event-consumer.md` and the artifact module.

---

## 2. Design Principles

Export code must follow these principles:

```text
1. Dataset export and run result export use the same ExportJob entity and ExportJobResponse DTO.
2. Export creation is a normal authenticated API action.
3. Export execution may be async and must be observable through ExportJob status.
4. Export files are generated from persisted, authorized data.
5. Download endpoint must check permission again.
6. Internal file paths must never be exposed as public API values.
7. Exported files must not contain plaintext secrets or unsanitized raw request/response data.
8. TestRun result export defaults to FINAL reviewed result unless request explicitly asks for MACHINE.
9. Failed export jobs must store a safe error code/message, not raw stack traces.
10. Export jobs must be reusable by frontend as one component for dataset and result exports.
```

Localization rule:

```text
Backend uses English codes/default messages. Vietnamese labels inside exported Excel may be handled by frontend/report templates later; do not hardcode Vietnamese business copy in service logic.
```

---

## 3. Package Layout

Recommended package layout:

```text
vn.vinfast.vfqc.api
  exportjob
    controller
      ExportJobController.java
    dto
      request
        CreateDatasetExportRequest.java
        CreateTestRunExportRequest.java
        ExportJobQueryRequest.java
      response
        ExportJobResponse.java
        ExportJobDownloadResponse.java
    entity
      ExportJob.java
    enums
      ExportJobType.java
      ExportFormat.java
      ExportJobStatus.java
      ExportResultMode.java
    mapper
      ExportJobMapper.java
    repository
      ExportJobRepository.java
    service
      ExportJobService.java
      ExportJobQueryService.java
      ExportJobWorker.java
      ExportFileWriter.java
      ExportFileNamePolicy.java
      ExportDownloadService.java
      impl
        ExportJobServiceImpl.java
        ExportJobQueryServiceImpl.java
        ExportJobWorkerImpl.java
        XlsxExportFileWriter.java
        ExportFileNamePolicyImpl.java
        ExportDownloadServiceImpl.java
    model
      ExportJobCreateCommand.java
      ExportJobWorkItem.java
      ExportDatasetRowModel.java
      ExportResultRowModel.java
      ExportFileWriteResult.java
```

Dataset module integration:

```text
dataset/service/DatasetExportService.java
dataset/service/impl/DatasetExportServiceImpl.java
```

TestRun module integration:

```text
testrun/service/TestRunExportService.java
testrun/service/impl/TestRunExportServiceImpl.java
```

Common package integration:

```text
common/storage/StorageService.java
common/masking/VfqcSensitiveDataMasker.java
common/permission/ProjectAccessResolver.java
common/error
common/time
```

Dependency rules:

```text
1. DatasetExportService creates DATASET_VERSION export jobs through ExportJobService.
2. TestRunExportService creates TEST_RUN_RESULTS export jobs through ExportJobService.
3. ExportJobWorker reads job metadata and calls the correct exporter strategy.
4. ExportJob module should not contain dataset/test-run business rules beyond shared job lifecycle.
5. Module-specific services own source permission and source validation.
```

---

## 4. ExportJob Domain Model

Entity:

```text
ExportJob
```

Table:

```text
export_jobs
```

Java enums:

```text
ExportJobType: DATASET_VERSION, TEST_RUN_RESULTS
ExportFormat: XLSX, CSV
ExportJobStatus: PENDING, RUNNING, COMPLETED, FAILED, CANCELLED, EXPIRED
ExportResultMode: MACHINE, FINAL
```

Important fields:

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
errorMessage
createdBy
createdAt
startedAt
finishedAt
```

Rules:

```text
1. DATASET_VERSION jobs must have datasetVersionId and must not require testRunId.
2. TEST_RUN_RESULTS jobs must have testRunId and may have resultMode.
3. TEST_RUN_RESULTS default resultMode is FINAL.
4. format MVP supports XLSX only.
5. filePath is internal and must not be returned directly in normal response.
6. errorMessage must be safe and sanitized.
```

---

## 5. Shared ExportJobResponse

`ExportJobResponse` must match the common API convention from `01-api-conventions.md`.

Recommended record:

```java
@Schema(description = "Export job metadata shared by dataset and test run result exports")
public record ExportJobResponse(
    @Schema(example = "74462c40-d4fb-40d5-8619-cbf78221ec3d")
    UUID id,

    @Schema(example = "DATASET_VERSION")
    ExportJobType jobType,

    @Schema(example = "XLSX")
    ExportFormat format,

    @Schema(example = "RUNNING")
    ExportJobStatus status,

    @Schema(example = "FINAL")
    ExportResultMode resultMode,

    @Schema(example = "dataset-v1-export.xlsx")
    String fileName,

    @Schema(example = "524288")
    Long fileSizeBytes,

    @Schema(example = "2026-07-03T10:00:00+07:00")
    OffsetDateTime createdAt,

    @Schema(example = "2026-07-03T10:00:02+07:00")
    OffsetDateTime startedAt,

    @Schema(example = "2026-07-03T10:00:05+07:00")
    OffsetDateTime finishedAt,

    @Schema(example = "null")
    String errorMessage,

    @Schema(example = "/api/v1/export-jobs/74462c40-d4fb-40d5-8619-cbf78221ec3d/download")
    String downloadUrl) {}
```

Rules:

```text
1. Use record for DTO.
2. Use @Schema examples for all fields.
3. downloadUrl is present only when status=COMPLETED and file is still available.
4. Do not expose internal filePath.
5. Do not expose stack traces.
```

MapStruct rule:

```text
Use ExportJobMapper for simple ExportJob -> ExportJobResponse mapping.
Use assembler only if downloadUrl requires permission-aware or request-context-aware construction.
```

---

## 6. Service Interfaces

Service interfaces must live outside `service/impl` and every method must have Javadoc.

Example:

```java
public interface ExportJobService {

  /**
   * Creates an export job in PENDING status and schedules or dispatches execution.
   *
   * @param command validated export job creation command
   * @param currentUser authenticated user creating the job
   * @return created export job response
   */
  ExportJobResponse createExportJob(ExportJobCreateCommand command, CurrentUser currentUser);

  /**
   * Returns export job metadata visible to the current user.
   *
   * @param exportJobId export job identifier
   * @param currentUser authenticated user
   * @return export job response
   */
  ExportJobResponse getExportJob(UUID exportJobId, CurrentUser currentUser);
}
```

Worker example:

```java
public interface ExportJobWorker {

  /**
   * Executes one pending export job and updates its final status.
   *
   * @param exportJobId export job identifier
   */
  void executeExportJob(UUID exportJobId);
}
```

Download service example:

```java
public interface ExportDownloadService {

  /**
   * Resolves a completed export job into a safe downloadable resource after permission checks.
   *
   * @param exportJobId export job identifier
   * @param currentUser authenticated user
   * @return file download response wrapper
   */
  ExportJobDownloadResponse prepareDownload(UUID exportJobId, CurrentUser currentUser);
}
```

---

## 7. Dataset Export Flow

Endpoint from dataset API:

```http
POST /api/v1/dataset-versions/{datasetVersionId}/exports
```

Implementation owner:

```text
dataset/service/DatasetExportService
```

Flow:

```text
1. Authenticate user.
2. Resolve DatasetVersion -> Dataset -> Project/Workspace scope.
3. Check EXPORT_DATASET or equivalent permission.
4. Validate dataset version is visible and not deleted.
5. Validate format = XLSX.
6. Create ExportJob with jobType = DATASET_VERSION, status = PENDING.
7. Store datasetVersionId, format and sanitized export options.
8. Dispatch async worker.
9. Return 202 Accepted with common ExportJobResponse.
```

Worker flow:

```text
1. Load ExportJob by id with lock or safe status transition.
2. Move PENDING -> RUNNING.
3. Query DatasetVersion rows and schema using optimized pagination/streaming.
4. Generate XLSX with columns ordered by DatasetSchemaColumn.orderIndex.
5. Write only row data and safe metadata.
6. Store file through StorageService.
7. Move RUNNING -> COMPLETED with fileName, filePath and fileSizeBytes.
8. On failure, move RUNNING/PENDING -> FAILED with safe error message.
```

Rules:

```text
1. ACTIVE and DRAFT versions can be exported if user has permission and product allows it.
2. Export must reflect the exact datasetVersionId, not datasets.current_version_id at worker time.
3. Do not include deleted rows unless DRAFT semantics explicitly include them.
4. Do not mutate dataset rows during export.
5. Large datasets should be streamed/paginated to avoid memory pressure.
```

---

## 8. TestRun Result Export Flow

Endpoint from test run API:

```http
POST /api/v1/test-runs/{testRunId}/exports
```

Implementation owner:

```text
testrun/service/TestRunExportService
```

Flow:

```text
1. Authenticate user.
2. Resolve TestRun -> Project -> Workspace.
3. Check EXPORT_TEST_RESULT permission.
4. Validate TestRun is visible.
5. Validate resultMode; default to FINAL.
6. Create ExportJob with jobType = TEST_RUN_RESULTS, status = PENDING.
7. Store testRunId, resultMode, format and sanitized export options.
8. Dispatch async worker.
9. Return 202 Accepted with common ExportJobResponse.
```

Worker flow:

```text
1. Load ExportJob by id with lock or safe status transition.
2. Move PENDING -> RUNNING.
3. Query TestRun locked config summary.
4. Query TestRunTarget order.
5. Query TestCaseResult rows and TestCaseResultReview rows.
6. Compute final result when resultMode = FINAL.
7. Generate XLSX report.
8. Store file through StorageService.
9. Move RUNNING -> COMPLETED with file metadata.
10. On failure, move RUNNING/PENDING -> FAILED with safe error message.
```

Result mode rules:

```text
MACHINE:
  Export machine status, passed, score and evaluator details from test_case_results only.

FINAL:
  Export QC reviewed result when TestCaseResultReview exists.
  Fallback to machine result when review does not exist.
```

Export content rules:

```text
1. Include project/run metadata.
2. Include dataset row preview fields needed by QC.
3. Include one row per TestCaseResult for simple export, or grouped sheets for comparison if product chooses.
4. Include target displayName and targetType for comparison runs.
5. Include reviewStatus, finalPassed, finalScore and QC note for FINAL mode.
6. Do not include plaintext secrets.
7. Do not include unsanitized raw_request/raw_response.
8. If raw/extracted output is exported, it must already be sanitized and bounded in size.
```

---

## 9. ExportJob Status State Machine

Allowed transitions:

```text
PENDING -> RUNNING
PENDING -> CANCELLED
PENDING -> FAILED
RUNNING -> COMPLETED
RUNNING -> FAILED
RUNNING -> CANCELLED, only if cancellation is implemented and cooperative
```

Forbidden transitions:

```text
COMPLETED -> RUNNING
FAILED -> RUNNING
CANCELLED -> RUNNING
COMPLETED -> FAILED
```

Rules:

```text
1. Worker must transition status atomically.
2. Use optimistic lock or conditional update to avoid duplicate execution.
3. finishedAt must be set for COMPLETED, FAILED and CANCELLED.
4. startedAt must be set when entering RUNNING.
5. errorMessage must be set only for FAILED.
```

---

## 10. Async Execution

Export execution should be async for non-trivial files.

Recommended approach for MVP:

```text
1. Create job synchronously in DB.
2. Dispatch worker using @Async after job creation.
3. Return ExportJobResponse immediately.
4. Frontend polls GET /api/v1/export-jobs/{exportJobId} until COMPLETED or FAILED.
```

Rules:

```text
1. @Async must be used only on worker/dispatcher methods, not normal CRUD methods.
2. Async method should be on a Spring bean, not self-invoked inside same class.
3. Worker must log start, success and failure with exportJobId.
4. Worker must persist failure status even when Excel generation fails.
5. If the app crashes while jobs are PENDING/RUNNING, a recovery worker may reset stale RUNNING jobs to FAILED or PENDING depending on policy.
```

Example:

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportJobWorkerImpl implements ExportJobWorker {

  @Override
  @Async("exportExecutor")
  public void executeExportJob(UUID exportJobId) {
    log.info("Starting export job. exportJobId={}", exportJobId);
    // Load job, transition, generate file, update status.
  }
}
```

---

## 11. Transaction Rules

Job creation transaction:

```text
1. Validate source and permission.
2. Insert ExportJob in PENDING status.
3. Commit transaction.
4. Dispatch async execution after commit.
```

Worker transaction rules:

```text
1. Use a short transaction to claim job and set RUNNING.
2. Do file generation outside a long-running DB transaction.
3. Use read-only transactions or streaming queries to load export data.
4. Use a short transaction to set COMPLETED/FAILED and file metadata.
5. Do not hold a DB transaction while writing large XLSX files.
```

Recommended annotations:

```text
@Transactional for job creation.
@Transactional(readOnly = true) for source query methods.
@Transactional for status transition methods.
```

---

## 12. File Storage and Download

File storage must go through `common/storage/StorageService`.

Storage rules:

```text
1. Store export files under a controlled export directory.
2. Use generated safe file names.
3. Prevent path traversal.
4. Store internal filePath in ExportJob only.
5. Return downloadUrl, not filePath.
6. Download endpoint checks permission again.
7. Set correct Content-Type for XLSX.
8. Set Content-Disposition attachment with safe fileName.
```

Download endpoint:

```http
GET /api/v1/export-jobs/{exportJobId}/download
```

Download rules:

```text
1. ExportJob must exist and be visible to current user.
2. status must be COMPLETED.
3. filePath must exist and be inside allowed storage root.
4. If file is missing, return EXPORT_FILE_NOT_FOUND or mark job failed depending on policy.
5. Do not allow download for FAILED/RUNNING/PENDING jobs.
```

---

## 13. Excel Writer Rules

MVP format:

```text
XLSX
```

Implementation note:

```text
Use the Excel library configured in the actual backend build. Do not invent a new dependency without checking pom.xml/build.gradle.
```

If Apache POI is already available, use streaming APIs for large files where practical.

Dataset export sheet recommendation:

```text
Sheet: Dataset
Columns: rowIndex + dataset schema columns in order
```

TestRun result export sheet recommendation:

```text
Sheet: Summary
Sheet: Results
Optional Sheet: Comparison Matrix
Optional Sheet: Review Notes
```

Rules:

```text
1. Keep column names stable and English by default.
2. Do not put formulas that execute external links/macros.
3. Do not generate macro-enabled files.
4. Escape or neutralize cells starting with =, +, -, @ to reduce spreadsheet formula injection risk.
5. Limit very large text cells or place full debug content in artifacts, not Excel.
```

---

## 14. Security and Masking Rules

Export must not leak sensitive data.

Forbidden in export files:

```text
1. Plaintext secret values.
2. Authorization headers.
3. API keys.
4. Cookies.
5. Runner service tokens.
6. Unsanitized raw cURL.
7. Unsanitized Promptfoo config.
8. Unsanitized raw request/response.
```

Rules:

```text
1. Use VfqcSensitiveDataMasker before writing any optional debug content.
2. Prefer exporting extracted_output and grading_result over raw_response.
3. If raw_response_json is exported for debugging, it must be the sanitized persisted version and may still need size limits.
4. Never export encrypted secret storage fields.
5. Never include internal file paths in Excel.
```

---

## 15. Logging Rules

Use Lombok `@Slf4j` in implementations.

Log:

```text
1. Export job created: exportJobId, jobType, source id, createdBy.
2. Export started: exportJobId.
3. Export completed: exportJobId, fileSizeBytes.
4. Export failed: exportJobId, safe error code/message.
5. Download requested: exportJobId, userId.
```

Do not log:

```text
1. Exported row content.
2. Raw model response.
3. Raw request body.
4. Secret values.
5. Internal absolute paths unless debug-only and safe.
```

---

## 16. Error Codes

Recommended error codes:

```text
EXPORT_JOB_NOT_FOUND
EXPORT_JOB_ACCESS_DENIED
EXPORT_JOB_NOT_COMPLETED
EXPORT_JOB_ALREADY_RUNNING
EXPORT_JOB_ALREADY_TERMINAL
EXPORT_FILE_NOT_FOUND
EXPORT_FORMAT_UNSUPPORTED
EXPORT_SOURCE_NOT_FOUND
EXPORT_SOURCE_NOT_VISIBLE
DATASET_EXPORT_NOT_ALLOWED
TEST_RUN_EXPORT_NOT_ALLOWED
EXPORT_GENERATION_FAILED
EXPORT_DOWNLOAD_FAILED
```

Rules:

```text
1. Use module-specific codes in Problem Details.
2. Return 404 instead of 403 when source existence should not be leaked.
3. Do not include raw exception stack traces in detail.
4. Store safe error message on ExportJob for frontend display.
```

---

## 17. Controller Implementation

Common query/download controller:

```java
@RestController
@RequestMapping("/api/v1/export-jobs")
@RequiredArgsConstructor
@Tag(name = "Export Jobs", description = "Common export job status and download APIs")
public class ExportJobController {

  private final ExportJobService exportJobService;
  private final ExportDownloadService exportDownloadService;

  @Operation(summary = "Get export job status")
  @GetMapping("/{exportJobId}")
  public ExportJobResponse getExportJob(
      @PathVariable UUID exportJobId,
      @AuthenticationPrincipal CurrentUser currentUser) {
    return exportJobService.getExportJob(exportJobId, currentUser);
  }

  @Operation(summary = "Download completed export file")
  @GetMapping("/{exportJobId}/download")
  public ResponseEntity<Resource> download(
      @PathVariable UUID exportJobId,
      @AuthenticationPrincipal CurrentUser currentUser) {
    ExportJobDownloadResponse download = exportDownloadService.prepareDownload(exportJobId, currentUser);
    return ResponseEntity.ok()
        .contentType(download.contentType())
        .header(HttpHeaders.CONTENT_DISPOSITION, download.contentDisposition())
        .body(download.resource());
  }
}
```

Dataset and TestRun creation endpoints may live in their own modules:

```text
dataset/controller/DatasetExportController.java
testrun/controller/TestRunExportController.java
```

Rules:

```text
1. Controllers are thin adapters.
2. Use @Valid for request records.
3. Use Swagger/OpenAPI annotations.
4. Do not generate files in controllers.
5. Do not manually map entity to response in controller.
```

---

## 18. Request DTO Rules

Request DTOs must be Java records with validation and examples.

Dataset export request example:

```java
@Schema(description = "Dataset version export request")
public record CreateDatasetExportRequest(
    @NotNull
    @Schema(example = "XLSX")
    ExportFormat format,

    @Schema(example = "true")
    Boolean includeMetadata) {}
```

TestRun export request example:

```java
@Schema(description = "Test run result export request")
public record CreateTestRunExportRequest(
    @NotNull
    @Schema(example = "XLSX")
    ExportFormat format,

    @Schema(example = "FINAL")
    ExportResultMode resultMode,

    @Schema(example = "true")
    Boolean includeReviewNotes,

    @Schema(example = "false")
    Boolean includeDebugColumns) {}
```

Rules:

```text
1. Default resultMode to FINAL in service, not by relying only on JSON default.
2. Validate unsupported options before creating ExportJob.
3. Sanitize any free-text export title/fileName if product later allows it.
```

---

## 19. Testing Checklist

Unit tests:

```text
1. ExportJob status transition allows only valid transitions.
2. TEST_RUN_RESULTS default resultMode is FINAL.
3. ExportJobMapper never exposes filePath.
4. ExportFileNamePolicy removes unsafe characters.
5. Spreadsheet formula injection strings are escaped.
6. Worker records FAILED with safe error on generation exception.
```

Integration tests:

```text
1. Dataset export creates PENDING job and returns ExportJobResponse.
2. Dataset export worker generates XLSX for exact datasetVersionId.
3. TestRun result export uses FINAL result semantics by default.
4. MACHINE result export ignores QC review override.
5. Download endpoint rejects RUNNING/FAILED jobs.
6. Download endpoint checks permission again.
7. User from another workspace cannot access export job.
8. Completed export returns attachment with correct content type.
```

Security tests:

```text
1. Exported XLSX does not contain Authorization/API key/cookie/token values.
2. Exported XLSX does not contain internal file paths.
3. Formula injection cells are escaped.
4. Stack trace is not stored in ExportJob.errorMessage.
```

Performance tests:

```text
1. Large dataset export does not load all rows into memory when avoidable.
2. Large TestRun export uses optimized queries and avoids N+1 target/result lookups.
3. Async worker does not hold long DB transaction during file writing.
```

---

## 20. AI Coding Rules

When generating export backend code:

```text
1. Implement service interface first, then service/impl implementation.
2. Add Javadoc above every service interface method.
3. Use @Slf4j in service/impl classes.
4. Use @Transactional for job creation and status updates.
5. Do not hold DB transactions while writing large files.
6. Use @Async only for worker execution.
7. Use MapStruct for ExportJob -> ExportJobResponse mapping.
8. Use record DTOs with validation and @Schema examples.
9. Do not expose filePath in API response.
10. Do not log or export plaintext secrets/raw tokens.
11. Reuse common ExportJobResponse for dataset and test run exports.
12. Keep module-specific source validation in dataset/testrun modules.
```
