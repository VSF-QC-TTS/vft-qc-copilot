package vn.vinfast.vfqc.api.runner.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import vn.vinfast.vfqc.api.testrun.enums.TestRunStatus;
import vn.vinfast.vfqc.api.testrun.enums.TestRunTargetType;
import vn.vinfast.vfqc.api.testrun.enums.TestRunType;

public record RunnerTestRunConfigResponse(
    UUID testRunId,
    UUID projectId,
    TestRunType runType,
    TestRunStatus status,
    UUID datasetId,
    UUID datasetVersionId,
    UUID datasetSchemaId,
    int datasetSchemaVersion,
    int progressTotal,
    Map<String, Object> domainSnapshot,
    Map<String, Object> promptfooSnapshot,
    List<TargetSnapshot> targets,
    List<EvaluatorSnapshot> evaluators,
    List<DatasetRowSnapshot> rows) {

  public record TargetSnapshot(
      UUID id,
      TestRunTargetType targetType,
      UUID chatbotTargetId,
      UUID providerId,
      String model,
      String name,
      int orderIndex,
      Map<String, Object> config) {}

  public record EvaluatorSnapshot(
      Long id,
      UUID evaluatorId,
      UUID evaluatorVersionId,
      String evaluatorType,
      int version,
      int orderIndex,
      Map<String, Object> config,
      List<Map<String, Object>> rules) {}

  public record DatasetRowSnapshot(Long id, int rowIndex, Map<String, Object> cells, int revision, String rowHash) {}
}
