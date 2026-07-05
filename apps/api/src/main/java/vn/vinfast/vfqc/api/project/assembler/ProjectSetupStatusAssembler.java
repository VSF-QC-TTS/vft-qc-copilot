package vn.vinfast.vfqc.api.project.assembler;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.vinfast.vfqc.api.project.dto.ProjectSetupStatusResponse;
import vn.vinfast.vfqc.api.project.entity.Project;

@Component
public class ProjectSetupStatusAssembler {

  public ProjectSetupStatusResponse toResponse(
      Project project, long datasetCount, long activeVersions) {
    var steps =
        new ProjectSetupStatusResponse.ProjectSetupSteps(
            datasetCount > 0, activeVersions > 0, false, false, false, false);
    var counts =
        new ProjectSetupStatusResponse.ProjectSetupCounts(
            datasetCount, activeVersions, 0, 0, 0, 0, 0, 0);
    var blockers =
        activeVersions > 0
            ? List.<ProjectSetupStatusResponse.ProjectSetupBlocker>of()
            : List.of(noActiveDatasetBlocker(project.getId()));
    return new ProjectSetupStatusResponse(
        project.getId(), project.getWorkspaceId(), steps, counts, false, false, blockers);
  }

  private ProjectSetupStatusResponse.ProjectSetupBlocker noActiveDatasetBlocker(UUID projectId) {
    return new ProjectSetupStatusResponse.ProjectSetupBlocker(
        "PROJECT_SETUP_NO_ACTIVE_DATASET",
        "DATASET",
        "ERROR",
        "Publish at least one dataset version before starting a run.",
        "/api/v1/projects/" + projectId + "/datasets");
  }
}
