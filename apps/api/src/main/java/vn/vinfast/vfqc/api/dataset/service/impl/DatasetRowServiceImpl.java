package vn.vinfast.vfqc.api.dataset.service.impl;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vinfast.vfqc.api.common.api.CollectionResponse;
import vn.vinfast.vfqc.api.common.api.PageInfo;
import vn.vinfast.vfqc.api.common.error.ErrorCode;
import vn.vinfast.vfqc.api.common.permission.PermissionAction;
import vn.vinfast.vfqc.api.common.permission.ProjectPermissionService;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.dataset.dto.CreateDatasetRowRequest;
import vn.vinfast.vfqc.api.dataset.dto.DatasetRowResponse;
import vn.vinfast.vfqc.api.dataset.dto.PatchDatasetRowCellsRequest;
import vn.vinfast.vfqc.api.dataset.entity.DatasetRow;
import vn.vinfast.vfqc.api.dataset.entity.DatasetVersion;
import vn.vinfast.vfqc.api.dataset.enums.DatasetVersionStatus;
import vn.vinfast.vfqc.api.dataset.mapper.DatasetMapper;
import vn.vinfast.vfqc.api.dataset.repository.DatasetRepository;
import vn.vinfast.vfqc.api.dataset.repository.DatasetRowRepository;
import vn.vinfast.vfqc.api.dataset.repository.DatasetVersionRepository;
import vn.vinfast.vfqc.api.dataset.service.DatasetRowHashService;
import vn.vinfast.vfqc.api.dataset.service.DatasetRowService;
import vn.vinfast.vfqc.api.dataset.validator.DatasetCellValidator;

@Service
@RequiredArgsConstructor
@Slf4j
public class DatasetRowServiceImpl implements DatasetRowService {

  private final DatasetRepository datasetRepository;
  private final DatasetVersionRepository versionRepository;
  private final DatasetRowRepository rowRepository;
  private final ProjectPermissionService projectPermissionService;
  private final DatasetCellValidator cellValidator;
  private final DatasetRowHashService rowHashService;
  private final DatasetMapper datasetMapper;
  private final Clock clock;

  @Transactional(readOnly = true)
  @Override
  public CollectionResponse<DatasetRowResponse> rows(
      CurrentUser user, UUID versionId, Pageable pageable) {
    var version =
        versionRepository.findById(versionId).orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    requireDatasetView(user, version.getDatasetId());
    var page = rowRepository.findByDatasetVersionIdOrderByRowIndexAsc(versionId, pageable);
    return new CollectionResponse<>(
        page.getContent().stream().map(datasetMapper::toResponse).toList(),
        new PageInfo(page.getSize(), null, page.hasNext()));
  }

  @Transactional
  @Override
  public DatasetRowResponse addRow(
      CurrentUser user, UUID versionId, CreateDatasetRowRequest request) {
    var version = requireDraftVersion(user, versionId);
    cellValidator.validate(version.getSchemaId(), request.cells());
    var now = OffsetDateTime.now(clock);
    var rowIndex = (int) rowRepository.countByDatasetVersionId(versionId);
    var row =
        rowRepository.save(
            DatasetRow.builder()
                .datasetVersionId(versionId)
                .rowIndex(rowIndex)
                .cellsJson(request.cells())
                .revision(0)
                .rowHash(rowHashService.hash(request.cells()))
                .createdAt(now)
                .updatedAt(now)
                .build());
    version.setRowCount(rowIndex + 1);
    log.debug(
        "Dataset row added: versionId={} rowId={} actorId={}", versionId, row.getId(), user.id());
    return datasetMapper.toResponse(row);
  }

  @Transactional
  @Override
  public DatasetRowResponse patchRow(
      CurrentUser user, Long rowId, PatchDatasetRowCellsRequest request) {
    var row = rowRepository.findById(rowId).orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    var version = requireDraftVersion(user, row.getDatasetVersionId());
    if (row.getRevision() != request.expectedRevision()) {
      throw ErrorCode.DATASET_ROW_REVISION_CONFLICT.throwIt(
          "Dataset row was changed by another request. Refresh the row before saving.");
    }
    cellValidator.validate(version.getSchemaId(), request.cells());
    row.setCellsJson(request.cells());
    row.setRevision(row.getRevision() + 1);
    row.setRowHash(rowHashService.hash(request.cells()));
    row.setUpdatedAt(OffsetDateTime.now(clock));
    log.debug(
        "Dataset row patched: rowId={} versionId={} actorId={}", rowId, version.getId(), user.id());
    return datasetMapper.toResponse(row);
  }

  private void requireDatasetView(CurrentUser user, UUID datasetId) {
    var dataset =
        datasetRepository
            .findByIdAndDeletedAtIsNull(datasetId)
            .orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    projectPermissionService.requireProject(user, dataset.getProjectId(), PermissionAction.VIEW);
  }

  private DatasetVersion requireDraftVersion(CurrentUser user, UUID versionId) {
    var version =
        versionRepository.findById(versionId).orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    var dataset =
        datasetRepository
            .findByIdAndDeletedAtIsNull(version.getDatasetId())
            .orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    projectPermissionService.requireProject(user, dataset.getProjectId(), PermissionAction.EDIT);
    if (version.getStatus() != DatasetVersionStatus.DRAFT) {
      throw ErrorCode.DATASET_VERSION_NOT_EDITABLE.throwIt();
    }
    return version;
  }
}
