package vn.vinfast.vfqc.api.testrun.model;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import vn.vinfast.vfqc.api.evaluator.enums.EvaluatorType;
import vn.vinfast.vfqc.api.testrun.enums.TestRunTargetType;
import vn.vinfast.vfqc.api.testrun.enums.TestRunType;

public record LockedRunConfig(
    UUID projectId,
    TestRunType runType,
    UUID datasetId,
    UUID datasetVersionId,
    UUID datasetSchemaId,
    int datasetSchemaVersion,
    int rowCount,
    UUID judgeProviderId,
    String judgeModel,
    List<LockedRunTargetConfig> targets,
    List<LockedEvaluatorConfig> evaluators,
    Map<String, Object> domainSnapshot,
    Map<String, Object> promptfooSnapshot) {

  public record LockedRunTargetConfig(
      TestRunTargetType targetType,
      UUID chatbotTargetId,
      UUID providerId,
      String model,
      String name,
      int orderIndex,
      Map<String, Object> config) {}

  public record LockedEvaluatorConfig(
      UUID evaluatorId,
      UUID evaluatorVersionId,
      EvaluatorType evaluatorType,
      int version,
      int orderIndex,
      Map<String, Object> config,
      List<Map<String, Object>> rules) {}
}
