package vn.vinfast.vfqc.api.testrun.assembler;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.vinfast.vfqc.api.common.masking.VfqcSensitiveDataMasker;
import vn.vinfast.vfqc.api.testrun.dto.TestCaseResultDetailResponse;
import vn.vinfast.vfqc.api.testrun.dto.TestCaseResultReviewResponse;
import vn.vinfast.vfqc.api.testrun.dto.TestRunDetailResponse;
import vn.vinfast.vfqc.api.testrun.entity.TestCaseResult;
import vn.vinfast.vfqc.api.testrun.entity.TestCaseResultReview;
import vn.vinfast.vfqc.api.testrun.entity.TestRun;
import vn.vinfast.vfqc.api.testrun.entity.TestRunEvaluatorVersion;
import vn.vinfast.vfqc.api.testrun.entity.TestRunTarget;
import vn.vinfast.vfqc.api.testrun.enums.ResultMode;
import vn.vinfast.vfqc.api.testrun.enums.TestCaseResultStatus;
import vn.vinfast.vfqc.api.testrun.mapper.TestRunMapper;

@Component
@RequiredArgsConstructor
public class TestRunResponseAssembler {

  private final VfqcSensitiveDataMasker masker;
  private final TestRunMapper mapper;

  public TestRunDetailResponse toDetail(
      TestRun run, List<TestRunTarget> targets, List<TestRunEvaluatorVersion> evaluators) {
    return new TestRunDetailResponse(
        run.getId(),
        run.getProjectId(),
        run.getRunType(),
        run.getDatasetId(),
        run.getDatasetVersionId(),
        run.getDatasetSchemaId(),
        run.getDatasetSchemaVersion(),
        run.getJudgeProviderId(),
        run.getJudgeModel(),
        run.getStatus(),
        run.getProgressCompleted(),
        run.getProgressTotal(),
        run.getSummaryMetricsJson(),
        run.getDomainConfigSnapshotJson(),
        run.getPromptfooConfigSnapshotJson(),
        run.getDomainConfigHash(),
        run.getPromptfooConfigHash(),
        targets.stream().map(mapper::toTarget).toList(),
        evaluators.stream().map(mapper::toEvaluator).toList(),
        run.getStartedBy(),
        run.getStartedAt(),
        run.getFinishedAt(),
        run.getCancelRequestedAt(),
        run.getCreatedAt(),
        run.getUpdatedAt());
  }

  public TestCaseResultDetailResponse toResult(
      TestCaseResult result, TestCaseResultReview review, ResultMode mode) {
    var finalStatus = finalStatus(result, review, mode);
    var finalScore =
        mode == ResultMode.FINAL && review != null && review.getOverrideScore() != null
            ? review.getOverrideScore()
            : result.getScore();
    return new TestCaseResultDetailResponse(
        result.getId(),
        result.getTestRunId(),
        result.getTestRunTargetId(),
        result.getDatasetRowId(),
        result.getStatus(),
        result.getScore(),
        result.getPassed(),
        finalStatus,
        finalScore,
        finalPassed(result, finalStatus),
        result.getErrorCategory(),
        sanitize(result.getRequestJson()),
        sanitize(result.getResponseJson()),
        sanitize(result.getExtractedOutputJson()),
        sanitize(result.getGradingResultJson()),
        sanitize(result.getComponentResultsJson()),
        sanitize(result.getTokenUsageJson()),
        result.getLatencyMs(),
        masker.sanitizeText(result.getMachineSummary()).value(),
        review == null ? null : toReview(review),
        result.getCreatedAt(),
        result.getUpdatedAt());
  }

  public TestCaseResultReviewResponse toReview(TestCaseResultReview review) {
    return new TestCaseResultReviewResponse(
        review.getId(),
        review.getTestCaseResultId(),
        review.getReviewerId(),
        review.getReviewStatus(),
        review.getOverrideStatus(),
        review.getOverrideScore(),
        masker.sanitizeText(review.getNote()).value(),
        review.getCreatedAt(),
        review.getUpdatedAt());
  }

  public TestCaseResultStatus finalStatus(
      TestCaseResult result, TestCaseResultReview review, ResultMode mode) {
    if (mode == ResultMode.FINAL && review != null && review.getOverrideStatus() != null) {
      return review.getOverrideStatus();
    }
    return result.getStatus();
  }

  public Boolean finalPassed(TestCaseResult result, TestCaseResultStatus finalStatus) {
    if (finalStatus == TestCaseResultStatus.PASSED) {
      return true;
    }
    if (finalStatus == TestCaseResultStatus.FAILED) {
      return false;
    }
    return result.getPassed();
  }

  private Map<String, Object> sanitize(Map<String, Object> value) {
    return masker.sanitizeMap(value).value();
  }
}
