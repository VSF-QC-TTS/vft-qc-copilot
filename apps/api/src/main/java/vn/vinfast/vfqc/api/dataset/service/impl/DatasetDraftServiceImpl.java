package vn.vinfast.vfqc.api.dataset.service.impl;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vinfast.vfqc.api.common.error.ErrorCode;
import vn.vinfast.vfqc.api.common.permission.PermissionAction;
import vn.vinfast.vfqc.api.common.permission.ProjectPermissionService;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.dataset.dto.DatasetVersionResponse;
import vn.vinfast.vfqc.api.dataset.entity.DatasetRow;
import vn.vinfast.vfqc.api.dataset.entity.DatasetVersion;
import vn.vinfast.vfqc.api.dataset.enums.DatasetVersionStatus;
import vn.vinfast.vfqc.api.dataset.mapper.DatasetMapper;
import vn.vinfast.vfqc.api.dataset.repository.DatasetRepository;
import vn.vinfast.vfqc.api.dataset.repository.DatasetRowRepository;
import vn.vinfast.vfqc.api.dataset.repository.DatasetVersionRepository;
import vn.vinfast.vfqc.api.dataset.service.DatasetDraftService;

@Service
@RequiredArgsConstructor
@Slf4j
public class DatasetDraftServiceImpl implements DatasetDraftService {

  private final DatasetRepository datasetRepository;
  private final DatasetVersionRepository versionRepository;
  private final DatasetRowRepository rowRepository;
  private final ProjectPermissionService projectPermissionService;
  private final DatasetMapper datasetMapper;
  private final Clock clock;

  @Transactional
  @Override
  public DatasetVersionResponse createDraft(CurrentUser user, UUID datasetId) {
    var dataset =
        datasetRepository
            .findByIdAndDeletedAtIsNull(datasetId)
            .orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    projectPermissionService.requireProject(user, dataset.getProjectId(), PermissionAction.EDIT);
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
            DatasetVersion.builder()
                .datasetId(datasetId)
                .version(latest.getVersion() + 1)
                .schemaId(latest.getSchemaId())
                .schemaVersion(latest.getSchemaVersion())
                .baseVersionId(base == null ? null : base.getId())
                .rowCount(0)
                .status(DatasetVersionStatus.DRAFT)
                .createdBy(user.id())
                .createdAt(now)
                .build());
    if (base != null) {
      var copied =
          rowRepository.findByDatasetVersionIdOrderByRowIndexAsc(base.getId()).stream()
              .map(
                  row ->
                      DatasetRow.builder()
                          .datasetVersionId(draft.getId())
                          .rowIndex(row.getRowIndex())
                          .cellsJson(row.getCellsJson())
                          .revision(0)
                          .createdAt(now)
                          .updatedAt(now)
                          .build())
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
    version.setStatus(DatasetVersionStatus.ACTIVE);
    version.setPublishedAt(OffsetDateTime.now(clock));
    version.setRowCount((int) rowRepository.countByDatasetVersionId(versionId));
    dataset.setCurrentVersionId(version.getId());
    dataset.setUpdatedAt(OffsetDateTime.now(clock));
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
    version.setStatus(DatasetVersionStatus.DISCARDED);
    version.setDiscardedAt(OffsetDateTime.now(clock));
    log.info(
        "Dataset draft discarded: datasetId={} versionId={} actorId={}",
        version.getDatasetId(),
        versionId,
        user.id());
    return datasetMapper.toResponse(version);
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
