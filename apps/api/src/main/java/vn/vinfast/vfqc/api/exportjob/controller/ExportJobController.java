package vn.vinfast.vfqc.api.exportjob.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.exportjob.dto.CreateDatasetExportRequest;
import vn.vinfast.vfqc.api.exportjob.dto.CreateTestRunExportRequest;
import vn.vinfast.vfqc.api.exportjob.dto.ExportJobResponse;
import vn.vinfast.vfqc.api.exportjob.service.ExportDownloadService;
import vn.vinfast.vfqc.api.exportjob.service.ExportJobQueryService;
import vn.vinfast.vfqc.api.exportjob.service.ExportJobService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Export Jobs", description = "Dataset and TestRun result export APIs")
public class ExportJobController {

  private final ExportJobService exportJobService;
  private final ExportJobQueryService queryService;
  private final ExportDownloadService downloadService;

  @PostMapping("/dataset-versions/{datasetVersionId}/export-jobs")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create dataset version export job")
  @ApiResponse(responseCode = "201", description = "Export job created")
  public ExportJobResponse createDatasetExport(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID datasetVersionId,
      @Valid @RequestBody(required = false) CreateDatasetExportRequest request) {
    var safeRequest = request == null ? new CreateDatasetExportRequest(null) : request;
    return exportJobService.createDatasetExport(user, datasetVersionId, safeRequest.safeFormat());
  }

  @PostMapping("/test-runs/{testRunId}/export-jobs")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create TestRun result export job")
  @ApiResponse(responseCode = "201", description = "Export job created")
  public ExportJobResponse createTestRunExport(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID testRunId,
      @Valid @RequestBody(required = false) CreateTestRunExportRequest request) {
    var safeRequest = request == null ? new CreateTestRunExportRequest(null, null) : request;
    return exportJobService.createTestRunExport(
        user, testRunId, safeRequest.safeFormat(), safeRequest.safeResultMode());
  }

  @GetMapping("/export-jobs/{jobId}")
  @Operation(summary = "Get export job")
  @ApiResponse(responseCode = "200", description = "Export job returned")
  public ExportJobResponse get(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID jobId) {
    return queryService.get(user, jobId);
  }

  @GetMapping("/export-jobs/{jobId}/download")
  @Operation(summary = "Download completed export file")
  @ApiResponse(responseCode = "200", description = "Export file returned")
  public ResponseEntity<Resource> download(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID jobId) {
    var download = downloadService.download(user, jobId);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(download.contentType()))
        .contentLength(download.sizeBytes())
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(download.fileName()).build().toString())
        .body(download.resource());
  }
}
