package vn.vinfast.vfqc.api.common.permission;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.vinfast.vfqc.api.common.error.ErrorCode;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.project.entity.Project;
import vn.vinfast.vfqc.api.project.repository.ProjectRepository;

@Service
@RequiredArgsConstructor
public class ProjectPermissionService {

  private final ProjectRepository projectRepository;
  private final WorkspacePermissionService workspacePermissionService;

  public Project requireProject(CurrentUser user, UUID projectId, PermissionAction action) {
    var project =
        projectRepository
            .findByIdAndDeletedAtIsNull(projectId)
            .orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    workspacePermissionService.require(user, project.getWorkspaceId(), action);
    return project;
  }
}
