package vn.vinfast.vfqc.api.project.service;

import java.util.List;
import java.util.UUID;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.project.dto.CreateProjectRequest;
import vn.vinfast.vfqc.api.project.dto.ProjectResponse;
import vn.vinfast.vfqc.api.project.dto.UpdateProjectRequest;
import vn.vinfast.vfqc.api.project.entity.Project;

public interface ProjectService {

  /**
   * Lists non-deleted projects in a workspace visible to the current user.
   *
   * @param user authenticated user
   * @param workspaceId workspace id
   * @return {@link Project} summaries
   */
  List<ProjectResponse> list(CurrentUser user, UUID workspaceId);

  /**
   * Creates a project and its default PROJECT dataset schema in one transaction.
   *
   * @param user authenticated creator
   * @param workspaceId parent workspace id
   * @param request project creation request
   * @return created {@link Project} summary
   */
  ProjectResponse create(CurrentUser user, UUID workspaceId, CreateProjectRequest request);

  /**
   * Loads a project if the current user has view permission.
   *
   * @param user authenticated user
   * @param projectId project id
   * @return {@link Project} summary
   */
  ProjectResponse get(CurrentUser user, UUID projectId);

  /**
   * Updates project metadata.
   *
   * @param user authenticated user with edit permission
   * @param projectId project id
   * @param request partial update request
   * @return updated {@link Project} summary
   */
  ProjectResponse update(CurrentUser user, UUID projectId, UpdateProjectRequest request);

  /**
   * Archives a project without deleting historical data.
   *
   * @param user authenticated user with edit permission
   * @param projectId project id
   * @return archived {@link Project} summary
   */
  ProjectResponse archive(CurrentUser user, UUID projectId);

  /**
   * Soft-deletes a project.
   *
   * @param user authenticated user with manage permission
   * @param projectId {@link Project} id
   */
  void delete(CurrentUser user, UUID projectId);
}
