package vn.vinfast.vfqc.api.dataset.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.vinfast.vfqc.api.common.api.CollectionResponse;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.dataset.dto.CreateDatasetRequest;
import vn.vinfast.vfqc.api.dataset.dto.CreateDatasetRowRequest;
import vn.vinfast.vfqc.api.dataset.dto.DatasetResponse;
import vn.vinfast.vfqc.api.dataset.dto.DatasetRowResponse;
import vn.vinfast.vfqc.api.dataset.dto.DatasetSchemaResponse;
import vn.vinfast.vfqc.api.dataset.dto.DatasetVariableResponse;
import vn.vinfast.vfqc.api.dataset.dto.DatasetVersionResponse;
import vn.vinfast.vfqc.api.dataset.dto.PatchDatasetRowCellsRequest;
import vn.vinfast.vfqc.api.dataset.dto.UpdateDatasetRequest;
import vn.vinfast.vfqc.api.dataset.service.DatasetDraftService;
import vn.vinfast.vfqc.api.dataset.service.DatasetRowService;
import vn.vinfast.vfqc.api.dataset.service.DatasetSchemaService;
import vn.vinfast.vfqc.api.dataset.service.DatasetService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Datasets", description = "Dataset schema, versions, rows, and draft lifecycle APIs")
public class DatasetController {

  private final DatasetSchemaService schemaService;
  private final DatasetService datasetService;
  private final DatasetDraftService draftService;
  private final DatasetRowService rowService;

  @GetMapping("/projects/{projectId}/dataset-schema")
  @Operation(summary = "Get latest project dataset schema")
  @ApiResponse(responseCode = "200", description = "Dataset schema returned")
  public DatasetSchemaResponse schema(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID projectId) {
    return schemaService.getProjectSchema(user, projectId);
  }

  @GetMapping("/projects/{projectId}/dataset-variables")
  @Operation(summary = "List project dataset variables")
  @ApiResponse(responseCode = "200", description = "Dataset variables returned")
  public CollectionResponse<DatasetVariableResponse> variables(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID projectId) {
    return CollectionResponse.of(schemaService.variables(user, projectId));
  }

  @GetMapping("/projects/{projectId}/datasets")
  @Operation(summary = "List project datasets")
  @ApiResponse(responseCode = "200", description = "Datasets returned")
  CollectionResponse<DatasetResponse> list(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID projectId) {
    return CollectionResponse.of(datasetService.list(user, projectId));
  }

  @PostMapping("/projects/{projectId}/datasets")
  @Operation(summary = "Create project dataset")
  @ApiResponse(responseCode = "200", description = "Dataset created with initial draft version")
  public DatasetResponse create(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID projectId,
      @Valid @RequestBody CreateDatasetRequest request) {
    return datasetService.create(user, projectId, request);
  }

  @GetMapping("/datasets/{datasetId}")
  @Operation(summary = "Get dataset")
  @ApiResponse(responseCode = "200", description = "Dataset returned")
  public DatasetResponse get(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID datasetId) {
    return datasetService.get(user, datasetId);
  }

  @PatchMapping("/datasets/{datasetId}")
  @Operation(summary = "Update dataset metadata")
  @ApiResponse(responseCode = "200", description = "Dataset updated")
  public DatasetResponse update(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID datasetId,
      @Valid @RequestBody UpdateDatasetRequest request) {
    return datasetService.update(user, datasetId, request);
  }

  @DeleteMapping("/datasets/{datasetId}")
  @Operation(summary = "Delete dataset")
  @ApiResponse(responseCode = "204", description = "Dataset soft-deleted")
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID datasetId) {
    datasetService.delete(user, datasetId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/datasets/{datasetId}/versions")
  @Operation(summary = "List dataset versions")
  @ApiResponse(responseCode = "200", description = "Dataset versions returned")
  public CollectionResponse<DatasetVersionResponse> versions(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID datasetId) {
    return CollectionResponse.of(datasetService.versions(user, datasetId));
  }

  @PostMapping("/datasets/{datasetId}/drafts")
  @Operation(summary = "Create dataset draft")
  @ApiResponse(responseCode = "200", description = "Draft version created")
  public DatasetVersionResponse createDraft(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID datasetId) {
    return draftService.createDraft(user, datasetId);
  }

  @GetMapping("/dataset-versions/{versionId}/rows")
  @Operation(summary = "List dataset version rows")
  @ApiResponse(responseCode = "200", description = "Dataset rows returned")
  public CollectionResponse<DatasetRowResponse> rows(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID versionId) {
    return CollectionResponse.of(rowService.rows(user, versionId));
  }

  @PostMapping("/dataset-versions/{versionId}/rows")
  @Operation(summary = "Add row to dataset draft")
  @ApiResponse(responseCode = "200", description = "Dataset row created")
  public DatasetRowResponse addRow(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID versionId,
      @Valid @RequestBody CreateDatasetRowRequest request) {
    return rowService.addRow(user, versionId, request);
  }

  @PatchMapping("/dataset-rows/{rowId}")
  @Operation(summary = "Patch dataset row cells")
  @ApiResponse(responseCode = "200", description = "Dataset row updated")
  public DatasetRowResponse patchRow(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable Long rowId,
      @Valid @RequestBody PatchDatasetRowCellsRequest request) {
    return rowService.patchRow(user, rowId, request);
  }

  @PostMapping("/dataset-versions/{versionId}/publish")
  @Operation(summary = "Publish dataset draft")
  @ApiResponse(responseCode = "200", description = "Draft published as active version")
  public DatasetVersionResponse publish(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID versionId) {
    return draftService.publish(user, versionId);
  }

  @PostMapping("/dataset-versions/{versionId}/discard")
  @Operation(summary = "Discard dataset draft")
  @ApiResponse(responseCode = "200", description = "Draft discarded")
  public DatasetVersionResponse discard(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID versionId) {
    return draftService.discard(user, versionId);
  }
}
