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
import vn.vinfast.vfqc.api.common.permission.PermissionAction;
import vn.vinfast.vfqc.api.common.permission.ProjectPermissionService;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.dataset.dto.DatasetSchemaResponse;
import vn.vinfast.vfqc.api.dataset.dto.DatasetVariableResponse;
import vn.vinfast.vfqc.api.dataset.entity.DatasetSchema;
import vn.vinfast.vfqc.api.dataset.entity.DatasetSchemaColumn;
import vn.vinfast.vfqc.api.dataset.enums.DatasetColumnDataType;
import vn.vinfast.vfqc.api.dataset.enums.DatasetSchemaScope;
import vn.vinfast.vfqc.api.dataset.mapper.DatasetMapper;
import vn.vinfast.vfqc.api.dataset.repository.DatasetSchemaColumnRepository;
import vn.vinfast.vfqc.api.dataset.repository.DatasetSchemaRepository;
import vn.vinfast.vfqc.api.dataset.service.DatasetSchemaBootstrapService;
import vn.vinfast.vfqc.api.dataset.service.DatasetSchemaService;

@Service
@RequiredArgsConstructor
@Slf4j
public class DatasetSchemaServiceImpl
    implements DatasetSchemaService, DatasetSchemaBootstrapService {

  private final DatasetSchemaRepository schemaRepository;
  private final DatasetSchemaColumnRepository columnRepository;
  private final ProjectPermissionService projectPermissionService;
  private final DatasetMapper datasetMapper;
  private final Clock clock;

  @Override
  @Transactional
  public void createDefaultProjectSchema(UUID projectId, UUID createdBy) {
    if (schemaRepository.findTopByProjectIdOrderByVersionDesc(projectId).isPresent()) {
      return;
    }
    var now = OffsetDateTime.now(clock);
    var schema =
        schemaRepository.save(
            DatasetSchema.builder()
                .scope(DatasetSchemaScope.PROJECT)
                .projectId(projectId)
                .version(1)
                .name("Default Project Dataset Schema")
                .createdBy(createdBy)
                .createdAt(now)
                .build());
    columnRepository.save(
        DatasetSchemaColumn.builder()
            .schemaId(schema.getId())
            .name("question")
            .displayName("Question")
            .dataType(DatasetColumnDataType.TEXT)
            .required(true)
            .orderIndex(0)
            .createdAt(now)
            .updatedAt(now)
            .build());
    columnRepository.save(
        DatasetSchemaColumn.builder()
            .schemaId(schema.getId())
            .name("expected")
            .displayName("Expected")
            .dataType(DatasetColumnDataType.TEXT)
            .required(false)
            .orderIndex(1)
            .createdAt(now)
            .updatedAt(now)
            .build());
    log.info(
        "Default dataset schema created: projectId={} schemaId={} actorId={}",
        projectId,
        schema.getId(),
        createdBy);
  }

  @Transactional(readOnly = true)
  @Override
  public DatasetSchemaResponse getProjectSchema(CurrentUser user, UUID projectId) {
    projectPermissionService.requireProject(user, projectId, PermissionAction.VIEW);
    var schema =
        schemaRepository
            .findTopByProjectIdOrderByVersionDesc(projectId)
            .orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    return toResponse(schema);
  }

  @Transactional(readOnly = true)
  @Override
  public List<DatasetVariableResponse> variables(CurrentUser user, UUID projectId) {
    return getProjectSchema(user, projectId).columns().stream()
        .map(
            column ->
                new DatasetVariableResponse(
                    column.name(), column.variable(), column.dataType(), column.required()))
        .toList();
  }

  private DatasetSchemaResponse toResponse(DatasetSchema schema) {
    var columns =
        columnRepository.findBySchemaIdOrderByOrderIndexAsc(schema.getId()).stream()
            .map(datasetMapper::toResponse)
            .toList();
    return datasetMapper.toResponse(schema, columns);
  }
}
