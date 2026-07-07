package vn.vinfast.vfqc.api.workspace.assembler;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.vinfast.vfqc.api.user.service.UserSummaryService;
import vn.vinfast.vfqc.api.workspace.dto.WorkspaceResponse;
import vn.vinfast.vfqc.api.workspace.entity.Workspace;
import vn.vinfast.vfqc.api.workspace.enums.WorkspaceRole;
import vn.vinfast.vfqc.api.workspace.mapper.WorkspaceMapper;
import vn.vinfast.vfqc.api.workspace.repository.WorkspaceSummaryView;

@Component
@RequiredArgsConstructor
public class WorkspaceResponseAssembler {

  private final WorkspaceMapper workspaceMapper;
  private final UserSummaryService userSummaryService;

  public WorkspaceResponse toResponse(Workspace workspace, WorkspaceRole currentUserRole) {
    return withCreatedByUser(
        workspaceMapper.toResponse(workspace, currentUserRole), summaryMap(workspace.getCreatedBy()));
  }

  public List<WorkspaceResponse> toEntityResponses(List<Workspace> workspaces) {
    var usersById =
        userSummaryService.summariesById(workspaces.stream().map(Workspace::getCreatedBy).toList());
    return workspaces.stream()
        .map(workspace -> withCreatedByUser(workspaceMapper.toResponse(workspace, null), usersById))
        .toList();
  }

  public List<WorkspaceResponse> toSummaryResponses(List<WorkspaceSummaryView> views) {
    var usersById =
        userSummaryService.summariesById(views.stream().map(WorkspaceSummaryView::getCreatedBy).toList());
    return views.stream()
        .map(view -> withCreatedByUser(workspaceMapper.toResponse(view), usersById))
        .toList();
  }

  private WorkspaceResponse withCreatedByUser(
      WorkspaceResponse response,
      java.util.Map<UUID, vn.vinfast.vfqc.api.user.dto.UserSummaryResponse> usersById) {
    return new WorkspaceResponse(
        response.id(),
        response.name(),
        response.type(),
        response.currentUserRole(),
        response.createdBy(),
        usersById.get(response.createdBy()),
        response.createdAt(),
        response.updatedAt());
  }

  private java.util.Map<UUID, vn.vinfast.vfqc.api.user.dto.UserSummaryResponse> summaryMap(UUID userId) {
    var summary = userSummaryService.toSummaryById(userId);
    return summary == null ? java.util.Map.of() : java.util.Map.of(userId, summary);
  }
}
