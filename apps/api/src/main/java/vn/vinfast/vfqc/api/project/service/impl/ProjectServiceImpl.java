package vn.vinfast.vfqc.api.project.service.impl;

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
import vn.vinfast.vfqc.api.common.permission.WorkspacePermissionService;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.dataset.service.DatasetSchemaBootstrapService;
import vn.vinfast.vfqc.api.project.dto.CreateProjectRequest;
import vn.vinfast.vfqc.api.project.dto.ProjectResponse;
import vn.vinfast.vfqc.api.project.dto.UpdateProjectRequest;
import vn.vinfast.vfqc.api.project.entity.Project;
import vn.vinfast.vfqc.api.project.assembler.ProjectResponseAssembler;
import vn.vinfast.vfqc.api.project.repository.ProjectRepository;
import vn.vinfast.vfqc.api.project.service.ProjectService;
import vn.vinfast.vfqc.api.workspace.repository.WorkspaceRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectServiceImpl implements ProjectService {

  private final ProjectRepository projectRepository;
  private final WorkspaceRepository workspaceRepository;
  private final WorkspacePermissionService workspacePermissionService;
  private final ProjectPermissionService projectPermissionService;
  private final DatasetSchemaBootstrapService datasetSchemaBootstrapService;
  private final ResourceNameNormalizer nameNormalizer;
  private final ProjectResponseAssembler projectAssembler;
  private final Clock clock;

  @Transactional(readOnly = true)
  @Override
  public List<ProjectResponse> list(CurrentUser user, UUID workspaceId) {
    workspaceRepository
        .findByIdAndDeletedAtIsNull(workspaceId)
        .orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    workspacePermissionService.require(user, workspaceId, PermissionAction.VIEW);
    return projectAssembler.toResponses(
        projectRepository.findByWorkspaceIdAndDeletedAtIsNullOrderByCreatedAtDesc(workspaceId));
  }

  @Transactional
  @Override
  public ProjectResponse create(CurrentUser user, UUID workspaceId, CreateProjectRequest request) {
    workspaceRepository
        .findByIdAndDeletedAtIsNull(workspaceId)
        .orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    workspacePermissionService.require(user, workspaceId, PermissionAction.EDIT);
    var normalized = nameNormalizer.normalize(request.name(), 120);
    if (projectRepository.existsByWorkspaceIdAndNameLookupAndDeletedAtIsNull(
        workspaceId, normalized.lookupName())) {
      throw ErrorCode.PROJECT_NAME_ALREADY_EXISTS.throwIt();
    }
    var now = OffsetDateTime.now(clock);
    var project =
        projectRepository.save(
            Project.builder()
                .workspaceId(workspaceId)
                .name(normalized.displayName())
                .nameLookup(normalized.lookupName())
                .description(request.description())
                .createdBy(user.id())
                .createdAt(now)
                .updatedAt(now)
                .build());
    datasetSchemaBootstrapService.createDefaultProjectSchema(project.getId(), user.id());
    log.info(
        "Project created: projectId={} workspaceId={} actorId={}",
        project.getId(),
        workspaceId,
        user.id());
    return projectAssembler.toResponse(project);
  }

  @Transactional(readOnly = true)
  @Override
  public ProjectResponse get(CurrentUser user, UUID projectId) {
    return projectAssembler.toResponse(
        projectPermissionService.requireProject(user, projectId, PermissionAction.VIEW));
  }

  @Transactional
  @Override
  public ProjectResponse update(CurrentUser user, UUID projectId, UpdateProjectRequest request) {
    var project = projectPermissionService.requireProject(user, projectId, PermissionAction.EDIT);
    if (request.name() != null) {
      var normalized = nameNormalizer.normalize(request.name(), 120);
      if (!project.getNameLookup().equals(normalized.lookupName())
          && projectRepository.existsByWorkspaceIdAndNameLookupAndDeletedAtIsNull(
              project.getWorkspaceId(), normalized.lookupName())) {
        throw ErrorCode.PROJECT_NAME_ALREADY_EXISTS.throwIt();
      }
      project.setName(normalized.displayName());
      project.setNameLookup(normalized.lookupName());
    }
    if (request.description() != null) {
      project.setDescription(request.description());
    }
    project.setUpdatedAt(OffsetDateTime.now(clock));
    log.info("Project updated: projectId={} actorId={}", projectId, user.id());
    return projectAssembler.toResponse(project);
  }

  @Transactional
  @Override
  public ProjectResponse archive(CurrentUser user, UUID projectId) {
    var project = projectPermissionService.requireProject(user, projectId, PermissionAction.EDIT);
    var now = OffsetDateTime.now(clock);
    project.setArchivedAt(now);
    project.setUpdatedAt(now);
    log.info("Project archived: projectId={} actorId={}", projectId, user.id());
    return projectAssembler.toResponse(project);
  }

  @Transactional
  @Override
  public void delete(CurrentUser user, UUID projectId) {
    var project = projectPermissionService.requireProject(user, projectId, PermissionAction.MANAGE);
    var now = OffsetDateTime.now(clock);
    project.setDeletedAt(now);
    project.setUpdatedAt(now);
    log.info("Project soft-deleted: projectId={} actorId={}", projectId, user.id());
  }
}
