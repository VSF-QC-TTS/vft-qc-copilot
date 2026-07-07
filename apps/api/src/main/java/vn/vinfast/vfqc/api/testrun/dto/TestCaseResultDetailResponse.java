package vn.vinfast.vfqc.api.testrun.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import vn.vinfast.vfqc.api.testrun.enums.TestCaseResultStatus;

public record TestCaseResultDetailResponse(
    Long id,
    UUID testRunId,
    UUID testRunTargetId,
    Long datasetRowId,
    TestCaseResultStatus machineStatus,
    BigDecimal machineScore,
    Boolean machinePassed,
    TestCaseResultStatus finalStatus,
    BigDecimal finalScore,
    Boolean finalPassed,
    String errorCategory,
    Map<String, Object> request,
    Map<String, Object> response,
    Map<String, Object> extractedOutput,
    Map<String, Object> gradingResult,
    Map<String, Object> componentResults,
    Map<String, Object> tokenUsage,
    Integer latencyMs,
    String machineSummary,
    TestCaseResultReviewResponse review,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
