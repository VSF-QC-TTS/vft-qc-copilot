package vn.vinfast.vfqc.api.dataset.service.impl;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vinfast.vfqc.api.common.error.ErrorCode;
import vn.vinfast.vfqc.api.common.naming.ResourceNameNormalizer;
import vn.vinfast.vfqc.api.common.permission.PermissionAction;
import vn.vinfast.vfqc.api.common.permission.ProjectPermissionService;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.dataset.dto.CreateDatasetRequest;
import vn.vinfast.vfqc.api.dataset.dto.CreateDatasetRowRequest;
import vn.vinfast.vfqc.api.dataset.dto.DatasetResponse;
import vn.vinfast.vfqc.api.dataset.dto.DatasetRowResponse;
import vn.vinfast.vfqc.api.dataset.dto.DatasetVersionResponse;
import vn.vinfast.vfqc.api.dataset.dto.PatchDatasetRowCellsRequest;
import vn.vinfast.vfqc.api.dataset.dto.UpdateDatasetRequest;
import vn.vinfast.vfqc.api.dataset.entity.Dataset;
import vn.vinfast.vfqc.api.dataset.entity.DatasetRow;
import vn.vinfast.vfqc.api.dataset.entity.DatasetVersion;
import vn.vinfast.vfqc.api.dataset.enums.DatasetScope;
import vn.vinfast.vfqc.api.dataset.enums.DatasetSourceType;
import vn.vinfast.vfqc.api.dataset.enums.DatasetVersionStatus;
import vn.vinfast.vfqc.api.dataset.mapper.DatasetMapper;
import vn.vinfast.vfqc.api.dataset.repository.DatasetRepository;
import vn.vinfast.vfqc.api.dataset.repository.DatasetRowRepository;
import vn.vinfast.vfqc.api.dataset.repository.DatasetSchemaColumnRepository;
import vn.vinfast.vfqc.api.dataset.repository.DatasetSchemaRepository;
import vn.vinfast.vfqc.api.dataset.repository.DatasetVersionRepository;
import vn.vinfast.vfqc.api.dataset.service.DatasetService;

@Service
@RequiredArgsConstructor
@Slf4j
public class DatasetServiceImpl implements DatasetService {

  private final DatasetRepository datasetRepository;
  private final DatasetVersionRepository versionRepository;
  private final DatasetRowRepository rowRepository;
  private final DatasetSchemaRepository schemaRepository;
  private final DatasetSchemaColumnRepository columnRepository;
  private final ProjectPermissionService projectPermissionService;
  private final ResourceNameNormalizer nameNormalizer;
  private final DatasetMapper datasetMapper;
  private final Clock clock;

  @Transactional(readOnly = true)
  @Override
  public List<DatasetResponse> list(CurrentUser user, UUID projectId) {
    projectPermissionService.requireProject(user, projectId, PermissionAction.VIEW);
    return datasetRepository
        .findByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(projectId)
        .stream()
        .map(datasetMapper::toResponse)
        .toList();
  }

  @Transactional
  @Override
  public DatasetResponse create(CurrentUser user, UUID projectId, CreateDatasetRequest request) {
    var project = projectPermissionService.requireProject(user, projectId, PermissionAction.EDIT);
    var schema =
        schemaRepository
            .findTopByProjectIdOrderByVersionDesc(projectId)
            .orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    var normalized = nameNormalizer.normalize(request.name(), 160);
    if (datasetRepository.existsByProjectIdAndNameLookupAndDeletedAtIsNull(
        projectId, normalized.lookupName())) {
      throw ErrorCode.DATASET_NAME_ALREADY_EXISTS.throwIt();
    }
    var now = OffsetDateTime.now(clock);
    var dataset =
        datasetRepository.save(
            new Dataset()
                .setScope(DatasetScope.PROJECT)
                .setWorkspaceId(project.getWorkspaceId())
                .setProjectId(projectId)
                .setName(normalized.displayName())
                .setNameLookup(normalized.lookupName())
                .setDescription(request.description())
                .setSourceType(DatasetSourceType.MANUAL)
                .setCreatedBy(user.id())
                .setCreatedAt(now)
                .setUpdatedAt(now));
    versionRepository.save(
        new DatasetVersion()
            .setDatasetId(dataset.getId())
            .setVersion(1)
            .setSchemaId(schema.getId())
            .setSchemaVersion(schema.getVersion())
            .setRowCount(0)
            .setStatus(DatasetVersionStatus.DRAFT)
            .setCreatedBy(user.id())
            .setCreatedAt(now));
    log.info(
        "Dataset created: datasetId={} projectId={} workspaceId={} actorId={}",
        dataset.getId(),
        projectId,
        project.getWorkspaceId(),
        user.id());
    return datasetMapper.toResponse(dataset);
  }

  @Transactional(readOnly = true)
  @Override
  public DatasetResponse get(CurrentUser user, UUID datasetId) {
    var dataset = requireDataset(user, datasetId, PermissionAction.VIEW);
    return datasetMapper.toResponse(dataset);
  }

  @Transactional
  @Override
  public DatasetResponse update(CurrentUser user, UUID datasetId, UpdateDatasetRequest request) {
    var dataset = requireDataset(user, datasetId, PermissionAction.EDIT);
    if (request.name() != null) {
      var normalized = nameNormalizer.normalize(request.name(), 160);
      if (!dataset.getNameLookup().equals(normalized.lookupName())
          && datasetRepository.existsByProjectIdAndNameLookupAndDeletedAtIsNull(
              dataset.getProjectId(), normalized.lookupName())) {
        throw ErrorCode.DATASET_NAME_ALREADY_EXISTS.throwIt();
      }
      dataset.setName(normalized.displayName()).setNameLookup(normalized.lookupName());
    }
    if (request.description() != null) {
      dataset.setDescription(request.description());
    }
    dataset.setUpdatedAt(OffsetDateTime.now(clock));
    log.info("Dataset updated: datasetId={} actorId={}", datasetId, user.id());
    return datasetMapper.toResponse(dataset);
  }

  @Transactional
  @Override
  public void delete(CurrentUser user, UUID datasetId) {
    var dataset = requireDataset(user, datasetId, PermissionAction.EDIT);
    var now = OffsetDateTime.now(clock);
    dataset.setDeletedAt(now).setUpdatedAt(now);
    log.info("Dataset soft-deleted: datasetId={} actorId={}", datasetId, user.id());
  }

  @Transactional(readOnly = true)
  @Override
  public List<DatasetVersionResponse> versions(CurrentUser user, UUID datasetId) {
    requireDataset(user, datasetId, PermissionAction.VIEW);
    return versionRepository.findByDatasetIdOrderByVersionDesc(datasetId).stream()
        .map(datasetMapper::toResponse)
        .toList();
  }

  @Transactional
  @Override
  public DatasetVersionResponse createDraft(CurrentUser user, UUID datasetId) {
    var dataset = requireDataset(user, datasetId, PermissionAction.EDIT);
    if (versionRepository
        .findByDatasetIdAndStatus(datasetId, DatasetVersionStatus.DRAFT)
        .isPresent()) {
      throw ErrorCode.DATASET_DRAFT_ALREADY_EXISTS.throwIt();
    }
    var base =
        dataset.getCurrentVersionId() == null
            ? null
            : versionRepository.findById(dataset.getCurrentVersionId()).orElse(null);
    var latest =
        versionRepository
            .findTopByDatasetIdOrderByVersionDesc(datasetId)
            .orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    var now = OffsetDateTime.now(clock);
    var draft =
        versionRepository.save(
            new DatasetVersion()
                .setDatasetId(datasetId)
                .setVersion(latest.getVersion() + 1)
                .setSchemaId(latest.getSchemaId())
                .setSchemaVersion(latest.getSchemaVersion())
                .setBaseVersionId(base == null ? null : base.getId())
                .setRowCount(0)
                .setStatus(DatasetVersionStatus.DRAFT)
                .setCreatedBy(user.id())
                .setCreatedAt(now));
    if (base != null) {
      var copied =
          rowRepository.findByDatasetVersionIdOrderByRowIndexAsc(base.getId()).stream()
              .map(
                  row ->
                      new DatasetRow()
                          .setDatasetVersionId(draft.getId())
                          .setRowIndex(row.getRowIndex())
                          .setCellsJson(row.getCellsJson())
                          .setRevision(0)
                          .setCreatedAt(now)
                          .setUpdatedAt(now))
              .toList();
      rowRepository.saveAll(copied);
      draft.setRowCount(copied.size());
    }
    log.info(
        "Dataset draft created: datasetId={} versionId={} actorId={}",
        datasetId,
        draft.getId(),
        user.id());
    return datasetMapper.toResponse(draft);
  }

  @Transactional(readOnly = true)
  @Override
  public List<DatasetRowResponse> rows(CurrentUser user, UUID versionId) {
    var version =
        versionRepository.findById(versionId).orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    requireDataset(user, version.getDatasetId(), PermissionAction.VIEW);
    return rowRepository.findByDatasetVersionIdOrderByRowIndexAsc(versionId).stream()
        .map(datasetMapper::toResponse)
        .toList();
  }

  @Transactional
  @Override
  public DatasetRowResponse addRow(
      CurrentUser user, UUID versionId, CreateDatasetRowRequest request) {
    var version = requireDraftVersion(user, versionId);
    validateCells(version, request.cells());
    var now = OffsetDateTime.now(clock);
    var rowIndex = (int) rowRepository.countByDatasetVersionId(versionId);
    var row =
        rowRepository.save(
            new DatasetRow()
                .setDatasetVersionId(versionId)
                .setRowIndex(rowIndex)
                .setCellsJson(request.cells())
                .setRevision(0)
                .setCreatedAt(now)
                .setUpdatedAt(now));
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
    validateCells(version, request.cells());
    row.setCellsJson(request.cells())
        .setRevision(row.getRevision() + 1)
        .setUpdatedAt(OffsetDateTime.now(clock));
    log.debug(
        "Dataset row patched: rowId={} versionId={} actorId={}", rowId, version.getId(), user.id());
    return datasetMapper.toResponse(row);
  }

  @Transactional
  @Override
  public DatasetVersionResponse publish(CurrentUser user, UUID versionId) {
    var version = requireDraftVersion(user, versionId);
    var dataset =
        datasetRepository
            .findByIdAndDeletedAtIsNull(version.getDatasetId())
            .orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    var oldActive =
        versionRepository.findByDatasetIdAndStatus(dataset.getId(), DatasetVersionStatus.ACTIVE);
    oldActive.ifPresent(active -> active.setStatus(DatasetVersionStatus.ARCHIVED));
    version
        .setStatus(DatasetVersionStatus.ACTIVE)
        .setPublishedAt(OffsetDateTime.now(clock))
        .setRowCount((int) rowRepository.countByDatasetVersionId(versionId));
    dataset.setCurrentVersionId(version.getId()).setUpdatedAt(OffsetDateTime.now(clock));
    log.info(
        "Dataset version published: datasetId={} versionId={} actorId={}",
        dataset.getId(),
        versionId,
        user.id());
    return datasetMapper.toResponse(version);
  }

  @Transactional
  @Override
  public DatasetVersionResponse discard(CurrentUser user, UUID versionId) {
    var version = requireDraftVersion(user, versionId);
    version.setStatus(DatasetVersionStatus.DISCARDED).setDiscardedAt(OffsetDateTime.now(clock));
    log.info(
        "Dataset draft discarded: datasetId={} versionId={} actorId={}",
        version.getDatasetId(),
        versionId,
        user.id());
    return datasetMapper.toResponse(version);
  }

  private Dataset requireDataset(CurrentUser user, UUID datasetId, PermissionAction action) {
    var dataset =
        datasetRepository
            .findByIdAndDeletedAtIsNull(datasetId)
            .orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    projectPermissionService.requireProject(user, dataset.getProjectId(), action);
    return dataset;
  }

  private DatasetVersion requireDraftVersion(CurrentUser user, UUID versionId) {
    var version =
        versionRepository.findById(versionId).orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    requireDataset(user, version.getDatasetId(), PermissionAction.EDIT);
    if (version.getStatus() != DatasetVersionStatus.DRAFT) {
      throw ErrorCode.DATASET_VERSION_NOT_EDITABLE.throwIt();
    }
    return version;
  }

  private void validateCells(DatasetVersion version, Map<String, Object> cells) {
    var columns = columnRepository.findBySchemaIdOrderByOrderIndexAsc(version.getSchemaId());
    var names = new HashSet<String>();
    for (var column : columns) {
      names.add(column.getName());
      if (column.isRequired()
          && (cells.get(column.getName()) == null
              || String.valueOf(cells.get(column.getName())).isBlank())) {
        throw ErrorCode.DATASET_REQUIRED_COLUMN_MISSING.throwIt(
            "Required dataset column is missing: " + column.getName());
      }
    }
    for (var key : cells.keySet()) {
      if (!names.contains(key)) {
        throw ErrorCode.DATASET_UNKNOWN_COLUMN.throwIt("Unknown dataset column: " + key);
      }
    }
  }
}
