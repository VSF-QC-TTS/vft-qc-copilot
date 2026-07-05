package vn.vinfast.vfqc.api.project.dto;

import java.util.List;
import java.util.UUID;

public record ProjectSetupStatusResponse(
    UUID projectId,
    UUID workspaceId,
    ProjectSetupSteps steps,
    ProjectSetupCounts counts,
    boolean canRunSingle,
    boolean canRunComparison,
    List<ProjectSetupBlocker> blockers) {

  public record ProjectSetupSteps(
      boolean hasDataset,
      boolean hasActiveDatasetVersion,
      boolean hasActiveChatbotTarget,
      boolean hasResponseExtraction,
      boolean hasEvaluator,
      boolean hasUsableJudgeProvider) {}

  public record ProjectSetupCounts(
      long datasets,
      long activeDatasetVersions,
      long activeChatbotTargets,
      long activeChatbotTargetsWithExtraction,
      long activeEvaluators,
      long usableJudgeProviders,
      long usableBaselineProviders,
      long comparisonCandidates) {}

  public record ProjectSetupBlocker(
      String code, String step, String severity, String message, String actionEndpoint) {}
}
