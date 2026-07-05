package vn.vinfast.vfqc.api.common.permission;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.vinfast.vfqc.api.common.error.ErrorCode;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.workspace.entity.WorkspaceMember;
import vn.vinfast.vfqc.api.workspace.enums.WorkspaceMemberStatus;
import vn.vinfast.vfqc.api.workspace.enums.WorkspaceRole;
import vn.vinfast.vfqc.api.workspace.repository.WorkspaceMemberRepository;

@Service
@RequiredArgsConstructor
public class WorkspacePermissionService {

  private final WorkspaceMemberRepository memberRepository;

  public void require(CurrentUser user, UUID workspaceId, PermissionAction action) {
    if (user.platformAdmin()) {
      return;
    }
    var member =
        memberRepository
            .findByWorkspaceIdAndUserIdAndStatus(
                workspaceId, user.id(), WorkspaceMemberStatus.ACTIVE)
            .orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    if (!allowed(member.getRole(), action)) {
      throw ErrorCode.ACCESS_DENIED.throwIt();
    }
  }

  public WorkspaceRole roleOrNull(CurrentUser user, UUID workspaceId) {
    if (user.platformAdmin()) {
      return null;
    }
    return memberRepository
        .findByWorkspaceIdAndUserIdAndStatus(workspaceId, user.id(), WorkspaceMemberStatus.ACTIVE)
        .map(WorkspaceMember::getRole)
        .orElse(null);
  }

  private boolean allowed(WorkspaceRole role, PermissionAction action) {
    return switch (role) {
      case OWNER, ADMIN -> true;
      case MEMBER -> action != PermissionAction.MANAGE;
      case VIEWER -> action == PermissionAction.VIEW;
    };
  }
}
