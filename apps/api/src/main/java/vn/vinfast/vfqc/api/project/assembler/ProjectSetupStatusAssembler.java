package vn.vinfast.vfqc.api.project.assembler;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.vinfast.vfqc.api.project.dto.ProjectSetupStatusResponse;
import vn.vinfast.vfqc.api.project.entity.Project;

@Component
public class ProjectSetupStatusAssembler {

  public ProjectSetupStatusResponse toResponse(
      Project project,
      long datasetCount,
      long activeVersions,
      long activeTargets,
      long activeTargetsWithExtraction,
      long activeEvaluators,
      long usableJudgeProviders) {
    var steps =
        new ProjectSetupStatusResponse.ProjectSetupSteps(
            datasetCount > 0,
            activeVersions > 0,
            activeTargets > 0,
            activeTargetsWithExtraction > 0,
            activeEvaluators > 0,
            usableJudgeProviders > 0);
    var counts =
        new ProjectSetupStatusResponse.ProjectSetupCounts(
            datasetCount,
            activeVersions,
            activeTargets,
            activeTargetsWithExtraction,
            activeEvaluators,
            usableJudgeProviders,
            usableJudgeProviders,
            activeTargets);
    var blockers =
        activeVersions > 0
            ? List.<ProjectSetupStatusResponse.ProjectSetupBlocker>of()
            : List.of(noActiveDatasetBlocker(project.getId()));
    return new ProjectSetupStatusResponse(
        project.getId(),
        project.getWorkspaceId(),
        steps,
        counts,
        activeVersions > 0 && activeTargetsWithExtraction > 0 && activeEvaluators > 0,
        activeVersions > 0 && activeTargets > 1 && activeEvaluators > 0,
        blockers);
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
