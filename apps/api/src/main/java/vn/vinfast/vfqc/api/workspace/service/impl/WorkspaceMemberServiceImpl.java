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
import vn.vinfast.vfqc.api.common.permission.PermissionAction;
import vn.vinfast.vfqc.api.common.permission.WorkspacePermissionService;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.user.repository.UserRepository;
import vn.vinfast.vfqc.api.workspace.dto.AddWorkspaceMemberRequest;
import vn.vinfast.vfqc.api.workspace.dto.UpdateWorkspaceMemberRoleRequest;
import vn.vinfast.vfqc.api.workspace.dto.WorkspaceMemberResponse;
import vn.vinfast.vfqc.api.workspace.entity.WorkspaceMember;
import vn.vinfast.vfqc.api.workspace.enums.WorkspaceMemberStatus;
import vn.vinfast.vfqc.api.workspace.mapper.WorkspaceMemberMapper;
import vn.vinfast.vfqc.api.workspace.repository.WorkspaceMemberRepository;
import vn.vinfast.vfqc.api.workspace.repository.WorkspaceRepository;
import vn.vinfast.vfqc.api.workspace.service.WorkspaceMemberService;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkspaceMemberServiceImpl implements WorkspaceMemberService {

  private final WorkspaceMemberRepository memberRepository;
  private final WorkspaceRepository workspaceRepository;
  private final UserRepository userRepository;
  private final WorkspacePermissionService permissionService;
  private final WorkspaceMemberMapper memberMapper;
  private final Clock clock;

  @Transactional(readOnly = true)
  @Override
  public List<WorkspaceMemberResponse> list(CurrentUser user, UUID workspaceId) {
    workspaceRepository
        .findByIdAndDeletedAtIsNull(workspaceId)
        .orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    permissionService.require(user, workspaceId, PermissionAction.VIEW);
    return memberRepository.findByWorkspaceIdOrderByCreatedAtAsc(workspaceId).stream()
        .map(memberMapper::toResponse)
        .toList();
  }

  @Transactional
  @Override
  public WorkspaceMemberResponse add(
      CurrentUser user, UUID workspaceId, AddWorkspaceMemberRequest request) {
    workspaceRepository
        .findByIdAndDeletedAtIsNull(workspaceId)
        .orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    permissionService.require(user, workspaceId, PermissionAction.MANAGE);
    var targetUser =
        userRepository
            .findByEmailIgnoreCaseAndDeletedAtIsNull(request.email())
            .orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    if (memberRepository.existsByWorkspaceIdAndUserId(workspaceId, targetUser.getId())) {
      throw ErrorCode.DUPLICATE_RESOURCE.throwIt();
    }
    var now = OffsetDateTime.now(clock);
    var member =
        memberRepository.save(
            WorkspaceMember.builder()
                .workspaceId(workspaceId)
                .userId(targetUser.getId())
                .role(request.role())
                .status(WorkspaceMemberStatus.ACTIVE)
                .invitedBy(user.id())
                .joinedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build());
    log.info(
        "Workspace member added: workspaceId={} memberId={} targetUserId={} actorId={}",
        workspaceId,
        member.getId(),
        targetUser.getId(),
        user.id());
    return memberMapper.toResponse(member);
  }

  @Transactional
  @Override
  public WorkspaceMemberResponse updateRole(
      CurrentUser user, UUID workspaceId, Long memberId, UpdateWorkspaceMemberRoleRequest request) {
    permissionService.require(user, workspaceId, PermissionAction.MANAGE);
    var member =
        memberRepository.findById(memberId).orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    if (!workspaceId.equals(member.getWorkspaceId())) {
      throw ErrorCode.RESOURCE_NOT_FOUND.throwIt();
    }
    member.setRole(request.role());
    member.setUpdatedAt(OffsetDateTime.now(clock));
    log.info(
        "Workspace member role updated: workspaceId={} memberId={} role={} actorId={}",
        workspaceId,
        memberId,
        request.role(),
        user.id());
    return memberMapper.toResponse(member);
  }
}
