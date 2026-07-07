package vn.vinfast.vfqc.api.testrun.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import vn.vinfast.vfqc.api.testrun.enums.TestRunStatus;
import vn.vinfast.vfqc.api.testrun.enums.TestRunType;

public record TestRunDetailResponse(
    UUID id,
    UUID projectId,
    TestRunType runType,
    UUID datasetId,
    UUID datasetVersionId,
    UUID datasetSchemaId,
    int datasetSchemaVersion,
    UUID judgeProviderId,
    String judgeModel,
    TestRunStatus status,
    int progressCompleted,
    int progressTotal,
    Map<String, Object> summaryMetrics,
    Map<String, Object> domainConfigSnapshot,
    Map<String, Object> promptfooConfigSnapshot,
    String domainConfigHash,
    String promptfooConfigHash,
    List<TestRunTargetResponse> targets,
    List<TestRunEvaluatorVersionResponse> evaluators,
    UUID startedBy,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    OffsetDateTime cancelRequestedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
