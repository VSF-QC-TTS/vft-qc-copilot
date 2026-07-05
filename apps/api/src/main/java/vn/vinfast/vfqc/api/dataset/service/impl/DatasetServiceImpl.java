package vn.vinfast.vfqc.api.dataset.service.impl;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
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
import vn.vinfast.vfqc.api.dataset.dto.DatasetResponse;
import vn.vinfast.vfqc.api.dataset.dto.DatasetVersionResponse;
import vn.vinfast.vfqc.api.dataset.dto.UpdateDatasetRequest;
import vn.vinfast.vfqc.api.dataset.entity.Dataset;
import vn.vinfast.vfqc.api.dataset.entity.DatasetVersion;
import vn.vinfast.vfqc.api.dataset.enums.DatasetScope;
import vn.vinfast.vfqc.api.dataset.enums.DatasetSourceType;
import vn.vinfast.vfqc.api.dataset.enums.DatasetVersionStatus;
import vn.vinfast.vfqc.api.dataset.mapper.DatasetMapper;
import vn.vinfast.vfqc.api.dataset.repository.DatasetRepository;
import vn.vinfast.vfqc.api.dataset.repository.DatasetSchemaRepository;
import vn.vinfast.vfqc.api.dataset.repository.DatasetVersionRepository;
import vn.vinfast.vfqc.api.dataset.service.DatasetService;

@Service
@RequiredArgsConstructor
@Slf4j
public class DatasetServiceImpl implements DatasetService {

  private final DatasetRepository datasetRepository;
  private final DatasetVersionRepository versionRepository;
  private final DatasetSchemaRepository schemaRepository;
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
            Dataset.builder()
                .scope(DatasetScope.PROJECT)
                .workspaceId(project.getWorkspaceId())
                .projectId(projectId)
                .name(normalized.displayName())
                .nameLookup(normalized.lookupName())
                .description(request.description())
                .sourceType(DatasetSourceType.MANUAL)
                .createdBy(user.id())
                .createdAt(now)
                .updatedAt(now)
                .build());
    versionRepository.save(
        DatasetVersion.builder()
            .datasetId(dataset.getId())
            .version(1)
            .schemaId(schema.getId())
            .schemaVersion(schema.getVersion())
            .rowCount(0)
            .status(DatasetVersionStatus.DRAFT)
            .createdBy(user.id())
            .createdAt(now)
            .build());
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
      dataset.setName(normalized.displayName());
      dataset.setNameLookup(normalized.lookupName());
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
    dataset.setDeletedAt(now);
    dataset.setUpdatedAt(now);
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

  private Dataset requireDataset(CurrentUser user, UUID datasetId, PermissionAction action) {
    var dataset =
        datasetRepository
            .findByIdAndDeletedAtIsNull(datasetId)
            .orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    projectPermissionService.requireProject(user, dataset.getProjectId(), action);
    return dataset;
  }
}
