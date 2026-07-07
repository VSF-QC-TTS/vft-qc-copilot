package vn.vinfast.vfqc.api.dataset.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.dataset.dto.ConfirmDatasetImportJobRequest;
import vn.vinfast.vfqc.api.dataset.dto.DatasetImportJobResponse;
import vn.vinfast.vfqc.api.dataset.dto.DatasetResponse;
import vn.vinfast.vfqc.api.dataset.enums.DatasetSourceType;
import vn.vinfast.vfqc.api.dataset.service.DatasetImportService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Dataset Import Jobs", description = "Dataset Excel import preview and confirm APIs")
public class DatasetImportJobController {

  private final DatasetImportService importService;

  @PostMapping(
      value = "/projects/{projectId}/dataset-import-jobs",
      consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create dataset import preview job")
  @ApiResponse(responseCode = "201", description = "Import preview created")
  public DatasetImportJobResponse createExcelPreview(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID projectId,
      @RequestParam DatasetSourceType sourceType,
      @RequestPart MultipartFile file,
      @RequestParam(required = false) String sheetName,
      @RequestParam(required = false) Integer previewRowCount) {
    return importService.createExcelPreview(user, projectId, sourceType, file, sheetName, previewRowCount);
  }

  @GetMapping("/dataset-import-jobs/{importJobId}")
  @Operation(summary = "Get dataset import job")
  @ApiResponse(responseCode = "200", description = "Import job returned")
  public DatasetImportJobResponse get(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID importJobId) {
    return importService.get(user, importJobId);
  }

  @PostMapping("/dataset-import-jobs/{importJobId}/confirm")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Confirm dataset import job")
  @ApiResponse(responseCode = "201", description = "Dataset imported")
  public DatasetResponse confirm(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID importJobId,
      @Valid @org.springframework.web.bind.annotation.RequestBody ConfirmDatasetImportJobRequest request) {
    return importService.confirm(user, importJobId, request);
  }
}
