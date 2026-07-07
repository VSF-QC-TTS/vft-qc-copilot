package vn.vinfast.vfqc.api.testrun.service.impl;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vinfast.vfqc.api.common.api.PageInfo;
import vn.vinfast.vfqc.api.common.permission.PermissionAction;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.dataset.entity.DatasetRow;
import vn.vinfast.vfqc.api.dataset.repository.DatasetRowRepository;
import vn.vinfast.vfqc.api.testrun.assembler.TestRunResponseAssembler;
import vn.vinfast.vfqc.api.testrun.dto.ComparisonMatrixResponse;
import vn.vinfast.vfqc.api.testrun.dto.ComparisonMatrixResponse.Cell;
import vn.vinfast.vfqc.api.testrun.dto.ComparisonMatrixResponse.Column;
import vn.vinfast.vfqc.api.testrun.dto.ComparisonMatrixResponse.Row;
import vn.vinfast.vfqc.api.testrun.entity.TestCaseResult;
import vn.vinfast.vfqc.api.testrun.entity.TestCaseResultReview;
import vn.vinfast.vfqc.api.testrun.enums.ResultMode;
import vn.vinfast.vfqc.api.testrun.repository.TestCaseResultRepository;
import vn.vinfast.vfqc.api.testrun.repository.TestCaseResultReviewRepository;
import vn.vinfast.vfqc.api.testrun.repository.TestRunTargetRepository;
import vn.vinfast.vfqc.api.testrun.service.TestRunComparisonMatrixService;

@Service
@RequiredArgsConstructor
public class TestRunComparisonMatrixServiceImpl implements TestRunComparisonMatrixService {

  private final TestRunQueryServiceImpl queryService;
  private final TestRunTargetRepository targetRepository;
  private final TestCaseResultRepository resultRepository;
  private final TestCaseResultReviewRepository reviewRepository;
  private final DatasetRowRepository datasetRowRepository;
  private final TestRunResponseAssembler assembler;

  @Override
  @Transactional(readOnly = true)
  public ComparisonMatrixResponse matrix(
      CurrentUser user, UUID testRunId, ResultMode mode, Pageable pageable) {
    var run = queryService.requireRun(user, testRunId, PermissionAction.VIEW);
    var targets = targetRepository.findByTestRunIdOrderByOrderIndexAsc(testRunId);
    var rowPage = datasetRowRepository.findByDatasetVersionIdOrderByRowIndexAsc(run.getDatasetVersionId(), pageable);
    var rowIds = rowPage.getContent().stream().map(DatasetRow::getId).toList();
    var results = resultRepository.findByTestRunIdAndDatasetRowIdIn(testRunId, rowIds);
    var reviews =
        reviewRepository.findByTestCaseResultIdIn(results.stream().map(TestCaseResult::getId).toList()).stream()
            .collect(Collectors.toMap(TestCaseResultReview::getTestCaseResultId, Function.identity()));
    var resultsByKey =
        results.stream().collect(Collectors.toMap(result -> key(result.getDatasetRowId(), result.getTestRunTargetId()), Function.identity()));
    var safeMode = mode == null ? ResultMode.FINAL : mode;
    return new ComparisonMatrixResponse(
        targets.stream().map(target -> new Column(target.getId(), target.getName(), target.getOrderIndex())).toList(),
        rowPage.getContent().stream()
            .map(row -> row(row, targets, resultsByKey, reviews, safeMode))
            .toList(),
        new PageInfo(rowPage.getSize(), null, rowPage.hasNext()));
  }

  private Row row(
      DatasetRow row,
      List<vn.vinfast.vfqc.api.testrun.entity.TestRunTarget> targets,
      Map<String, TestCaseResult> resultsByKey,
      Map<Long, TestCaseResultReview> reviews,
      ResultMode mode) {
    return new Row(
        row.getId(),
        row.getRowIndex(),
        targets.stream()
            .map(target -> cell(target.getId(), resultsByKey.get(key(row.getId(), target.getId())), reviews, mode))
            .toList());
  }

  private Cell cell(
      UUID targetId, TestCaseResult result, Map<Long, TestCaseResultReview> reviews, ResultMode mode) {
    if (result == null) {
      return new Cell(targetId, null, null, null, null, null, false);
    }
    var review = reviews.get(result.getId());
    var finalStatus = assembler.finalStatus(result, review, mode);
    var finalScore =
        mode == ResultMode.FINAL && review != null && review.getOverrideScore() != null
            ? review.getOverrideScore()
            : result.getScore();
    return new Cell(
        targetId,
        result.getId(),
        finalStatus,
        finalScore,
        assembler.finalPassed(result, finalStatus),
        result.getErrorCategory(),
        review != null);
  }

  private String key(Long rowId, UUID targetId) {
    return rowId + ":" + targetId;
  }
}
