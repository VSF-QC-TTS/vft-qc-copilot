package vn.vinfast.vfqc.api.workspace.service.impl;

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
import vn.vinfast.vfqc.api.common.permission.WorkspacePermissionService;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.workspace.dto.CreateWorkspaceRequest;
import vn.vinfast.vfqc.api.workspace.dto.UpdateWorkspaceRequest;
import vn.vinfast.vfqc.api.workspace.dto.WorkspaceResponse;
import vn.vinfast.vfqc.api.workspace.entity.Workspace;
import vn.vinfast.vfqc.api.workspace.entity.WorkspaceMember;
import vn.vinfast.vfqc.api.workspace.enums.WorkspaceMemberStatus;
import vn.vinfast.vfqc.api.workspace.enums.WorkspaceRole;
import vn.vinfast.vfqc.api.workspace.enums.WorkspaceType;
import vn.vinfast.vfqc.api.workspace.mapper.WorkspaceMapper;
import vn.vinfast.vfqc.api.workspace.repository.WorkspaceMemberRepository;
import vn.vinfast.vfqc.api.workspace.repository.WorkspaceRepository;
import vn.vinfast.vfqc.api.workspace.service.WorkspaceService;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkspaceServiceImpl implements WorkspaceService {

  private final WorkspaceRepository workspaceRepository;
  private final WorkspaceMemberRepository memberRepository;
  private final ResourceNameNormalizer nameNormalizer;
  private final WorkspacePermissionService permissionService;
  private final WorkspaceMapper workspaceMapper;
  private final Clock clock;

  @Transactional(readOnly = true)
  @Override
  public List<WorkspaceResponse> list(CurrentUser user) {
    if (user.platformAdmin()) {
      return workspaceRepository.findByDeletedAtIsNullOrderByCreatedAtDesc().stream()
          .map(workspace -> workspaceMapper.toResponse(workspace, null))
          .toList();
    }
    return workspaceRepository
        .findVisibleSummariesForUser(user.id(), WorkspaceMemberStatus.ACTIVE)
        .stream()
        .map(workspaceMapper::toResponse)
        .toList();
  }

  @Transactional
  @Override
  public WorkspaceResponse create(CurrentUser user, CreateWorkspaceRequest request) {
    var normalized = nameNormalizer.normalize(request.name(), 120);
    if (workspaceRepository.existsByNameLookupAndDeletedAtIsNull(normalized.lookupName())) {
      throw ErrorCode.WORKSPACE_NAME_ALREADY_EXISTS.throwIt();
    }
    var now = OffsetDateTime.now(clock);
    var workspace =
        workspaceRepository.save(
            new Workspace()
                .setName(normalized.displayName())
                .setNameLookup(normalized.lookupName())
                .setType(WorkspaceType.TEAM)
                .setCreatedBy(user.id())
                .setCreatedAt(now)
                .setUpdatedAt(now));
    memberRepository.save(
        new WorkspaceMember()
            .setWorkspaceId(workspace.getId())
            .setUserId(user.id())
            .setRole(WorkspaceRole.OWNER)
            .setStatus(WorkspaceMemberStatus.ACTIVE)
            .setInvitedBy(user.id())
            .setJoinedAt(now)
            .setCreatedAt(now)
            .setUpdatedAt(now));
    log.info("Workspace created: workspaceId={} actorId={}", workspace.getId(), user.id());
    return workspaceMapper.toResponse(workspace, WorkspaceRole.OWNER);
  }

  @Transactional(readOnly = true)
  @Override
  public WorkspaceResponse get(CurrentUser user, UUID workspaceId) {
    var workspace =
        workspaceRepository
            .findByIdAndDeletedAtIsNull(workspaceId)
            .orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    permissionService.require(user, workspaceId, PermissionAction.VIEW);
    return workspaceMapper.toResponse(
        workspace, permissionService.roleOrNull(user, workspace.getId()));
  }

  @Transactional
  @Override
  public WorkspaceResponse update(
      CurrentUser user, UUID workspaceId, UpdateWorkspaceRequest request) {
    var workspace =
        workspaceRepository
            .findByIdAndDeletedAtIsNull(workspaceId)
            .orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    permissionService.require(user, workspaceId, PermissionAction.MANAGE);
    if (request.name() != null) {
      var normalized = nameNormalizer.normalize(request.name(), 120);
      if (!workspace.getNameLookup().equals(normalized.lookupName())
          && workspaceRepository.existsByNameLookupAndDeletedAtIsNull(normalized.lookupName())) {
        throw ErrorCode.WORKSPACE_NAME_ALREADY_EXISTS.throwIt();
      }
      workspace.setName(normalized.displayName()).setNameLookup(normalized.lookupName());
    }
    workspace.setUpdatedAt(OffsetDateTime.now(clock));
    log.info("Workspace updated: workspaceId={} actorId={}", workspace.getId(), user.id());
    return workspaceMapper.toResponse(
        workspace, permissionService.roleOrNull(user, workspace.getId()));
  }

  @Transactional
  @Override
  public void delete(CurrentUser user, UUID workspaceId) {
    var workspace =
        workspaceRepository
            .findByIdAndDeletedAtIsNull(workspaceId)
            .orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    permissionService.require(user, workspaceId, PermissionAction.MANAGE);
    workspace.setDeletedAt(OffsetDateTime.now(clock));
    log.info("Workspace soft-deleted: workspaceId={} actorId={}", workspaceId, user.id());
  }
}
