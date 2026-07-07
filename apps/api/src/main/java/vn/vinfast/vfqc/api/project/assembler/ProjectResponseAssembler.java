package vn.vinfast.vfqc.api.project.assembler;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.vinfast.vfqc.api.project.dto.ProjectResponse;
import vn.vinfast.vfqc.api.project.entity.Project;
import vn.vinfast.vfqc.api.project.mapper.ProjectMapper;
import vn.vinfast.vfqc.api.user.dto.UserSummaryResponse;
import vn.vinfast.vfqc.api.user.service.UserSummaryService;

@Component
@RequiredArgsConstructor
public class ProjectResponseAssembler {

  private final ProjectMapper projectMapper;
  private final UserSummaryService userSummaryService;

  public ProjectResponse toResponse(Project project) {
    return withCreatedByUser(projectMapper.toResponse(project), summaryMap(project.getCreatedBy()));
  }

  public List<ProjectResponse> toResponses(List<Project> projects) {
    var usersById =
        userSummaryService.summariesById(projects.stream().map(Project::getCreatedBy).toList());
    return projects.stream()
        .map(project -> withCreatedByUser(projectMapper.toResponse(project), usersById))
        .toList();
  }

  private ProjectResponse withCreatedByUser(
      ProjectResponse response, java.util.Map<UUID, UserSummaryResponse> usersById) {
    return new ProjectResponse(
        response.id(),
        response.workspaceId(),
        response.name(),
        response.description(),
        response.createdBy(),
        usersById.get(response.createdBy()),
        response.createdAt(),
        response.updatedAt(),
        response.archivedAt());
  }

  private java.util.Map<UUID, UserSummaryResponse> summaryMap(UUID userId) {
    var summary = userSummaryService.toSummaryById(userId);
    return summary == null ? java.util.Map.of() : java.util.Map.of(userId, summary);
  }
}
