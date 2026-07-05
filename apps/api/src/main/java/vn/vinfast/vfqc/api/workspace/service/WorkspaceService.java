package vn.vinfast.vfqc.api.workspace.service;

import java.util.List;
import java.util.UUID;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.workspace.dto.CreateWorkspaceRequest;
import vn.vinfast.vfqc.api.workspace.dto.UpdateWorkspaceRequest;
import vn.vinfast.vfqc.api.workspace.dto.WorkspaceResponse;

public interface WorkspaceService {

  /**
   * Lists workspaces visible to the current user.
   *
   * @param user authenticated user
   * @return API-safe workspace summaries with current user's workspace role
   */
  List<WorkspaceResponse> list(CurrentUser user);

  /**
   * Creates a TEAM workspace and an OWNER membership for the creator in one transaction.
   *
   * @param user authenticated creator
   * @param request workspace creation request
   * @return created workspace summary
   */
  WorkspaceResponse create(CurrentUser user, CreateWorkspaceRequest request);

  /**
   * Loads a workspace if the user has view access.
   *
   * @param user authenticated user
   * @param workspaceId workspace id
   * @return workspace summary
   */
  WorkspaceResponse get(CurrentUser user, UUID workspaceId);

  /**
   * Updates workspace metadata for users with manage permission.
   *
   * @param user authenticated user
   * @param workspaceId workspace id
   * @param request partial metadata update
   * @return updated workspace summary
   */
  WorkspaceResponse update(CurrentUser user, UUID workspaceId, UpdateWorkspaceRequest request);

  /**
   * Soft-deletes a workspace for users with manage permission.
   *
   * @param user authenticated user
   * @param workspaceId workspace id
   */
  void delete(CurrentUser user, UUID workspaceId);
}
