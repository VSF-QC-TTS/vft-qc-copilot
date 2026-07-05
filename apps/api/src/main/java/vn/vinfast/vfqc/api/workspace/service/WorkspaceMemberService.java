package vn.vinfast.vfqc.api.workspace.service;

import java.util.List;
import java.util.UUID;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.workspace.dto.AddWorkspaceMemberRequest;
import vn.vinfast.vfqc.api.workspace.dto.UpdateWorkspaceMemberRoleRequest;
import vn.vinfast.vfqc.api.workspace.dto.WorkspaceMemberResponse;

public interface WorkspaceMemberService {

  /**
   * Lists members in a workspace visible to the current user.
   *
   * @param user authenticated user
   * @param workspaceId workspace id
   * @return workspace member summaries
   */
  List<WorkspaceMemberResponse> list(CurrentUser user, UUID workspaceId);

  /**
   * Adds an existing user as an active member of a workspace.
   *
   * @param user authenticated actor with manage permission
   * @param workspaceId workspace id
   * @param request member email and role
   * @return created member row summary
   */
  WorkspaceMemberResponse add(
      CurrentUser user, UUID workspaceId, AddWorkspaceMemberRequest request);

  /**
   * Updates a workspace member role.
   *
   * @param user authenticated actor with manage permission
   * @param workspaceId workspace id
   * @param memberId workspace member row id
   * @param request new role request
   * @return updated member row summary
   */
  WorkspaceMemberResponse updateRole(
      CurrentUser user, UUID workspaceId, Long memberId, UpdateWorkspaceMemberRoleRequest request);
}
