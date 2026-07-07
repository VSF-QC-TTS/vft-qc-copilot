package vn.vinfast.vfqc.api.testrun.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import vn.vinfast.vfqc.api.testrun.enums.TestRunStatus;
import vn.vinfast.vfqc.api.testrun.enums.TestRunType;

public record TestRunSummaryResponse(
    UUID id,
    UUID projectId,
    TestRunType runType,
    UUID datasetId,
    UUID datasetVersionId,
    TestRunStatus status,
    int progressCompleted,
    int progressTotal,
    Map<String, Object> summaryMetrics,
    UUID startedBy,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
