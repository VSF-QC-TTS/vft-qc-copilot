package vn.vinfast.vfqc.api.testrun.service.impl;

import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vinfast.vfqc.api.common.api.PageInfo;
import vn.vinfast.vfqc.api.common.error.ErrorCode;
import vn.vinfast.vfqc.api.common.permission.PermissionAction;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.testrun.assembler.TestRunResponseAssembler;
import vn.vinfast.vfqc.api.testrun.dto.TestCaseResultDetailResponse;
import vn.vinfast.vfqc.api.testrun.dto.TestRunResultPageResponse;
import vn.vinfast.vfqc.api.testrun.entity.TestCaseResult;
import vn.vinfast.vfqc.api.testrun.entity.TestCaseResultReview;
import vn.vinfast.vfqc.api.testrun.enums.ResultMode;
import vn.vinfast.vfqc.api.testrun.repository.TestCaseResultRepository;
import vn.vinfast.vfqc.api.testrun.repository.TestCaseResultReviewRepository;
import vn.vinfast.vfqc.api.testrun.service.TestRunQueryService;
import vn.vinfast.vfqc.api.testrun.service.TestRunResultService;

@Service
@RequiredArgsConstructor
public class TestRunResultServiceImpl implements TestRunResultService {

  private final TestCaseResultRepository resultRepository;
  private final TestCaseResultReviewRepository reviewRepository;
  private final TestRunQueryService queryService;
  private final TestRunResponseAssembler assembler;

  @Override
  @Transactional(readOnly = true)
  public TestRunResultPageResponse list(
      CurrentUser user, UUID testRunId, ResultMode mode, Pageable pageable) {
    queryService.requireRun(user, testRunId, PermissionAction.VIEW);
    var page = resultRepository.findByTestRunIdOrderByIdAsc(testRunId, pageable);
    var resultIds = page.getContent().stream().map(TestCaseResult::getId).toList();
    var reviews =
        reviewRepository.findByTestCaseResultIdIn(resultIds).stream()
            .collect(Collectors.toMap(TestCaseResultReview::getTestCaseResultId, Function.identity()));
    var safeMode = mode == null ? ResultMode.FINAL : mode;
    return new TestRunResultPageResponse(
        page.getContent().stream()
            .map(result -> assembler.toResult(result, reviews.get(result.getId()), safeMode))
            .toList(),
        new PageInfo(page.getSize(), null, page.hasNext()));
  }

  @Override
  @Transactional(readOnly = true)
  public TestCaseResultDetailResponse get(CurrentUser user, Long resultId, ResultMode mode) {
    var result = resultRepository.findById(resultId).orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    queryService.requireRun(user, result.getTestRunId(), PermissionAction.VIEW);
    var review = reviewRepository.findByTestCaseResultId(resultId).orElse(null);
    return assembler.toResult(result, review, mode == null ? ResultMode.FINAL : mode);
  }
}
