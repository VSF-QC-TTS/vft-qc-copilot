package vn.vinfast.vfqc.api.testrun.service.impl;

import java.time.Clock;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vinfast.vfqc.api.common.error.ErrorCode;
import vn.vinfast.vfqc.api.common.masking.VfqcSensitiveDataMasker;
import vn.vinfast.vfqc.api.common.permission.PermissionAction;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.testrun.assembler.TestRunResponseAssembler;
import vn.vinfast.vfqc.api.testrun.dto.TestCaseResultReviewResponse;
import vn.vinfast.vfqc.api.testrun.dto.UpsertTestCaseResultReviewRequest;
import vn.vinfast.vfqc.api.testrun.entity.TestCaseResultReview;
import vn.vinfast.vfqc.api.testrun.repository.TestCaseResultRepository;
import vn.vinfast.vfqc.api.testrun.repository.TestCaseResultReviewRepository;
import vn.vinfast.vfqc.api.testrun.service.TestRunQueryService;
import vn.vinfast.vfqc.api.testrun.service.TestRunReviewService;

@Service
@RequiredArgsConstructor
public class TestRunReviewServiceImpl implements TestRunReviewService {

  private final TestCaseResultRepository resultRepository;
  private final TestCaseResultReviewRepository reviewRepository;
  private final TestRunQueryService queryService;
  private final TestRunResponseAssembler assembler;
  private final VfqcSensitiveDataMasker masker;
  private final Clock clock;

  @Override
  @Transactional
  public TestCaseResultReviewResponse upsert(
      CurrentUser user, Long resultId, UpsertTestCaseResultReviewRequest request) {
    var result = resultRepository.findById(resultId).orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    queryService.requireRun(user, result.getTestRunId(), PermissionAction.EDIT);
    var now = OffsetDateTime.now(clock);
    var review =
        reviewRepository
            .findByTestCaseResultId(resultId)
            .orElseGet(
                () ->
                    TestCaseResultReview.builder()
                        .testCaseResultId(resultId)
                        .reviewerId(user.id())
                        .createdAt(now)
                        .build());
    review.setReviewerId(user.id());
    review.setReviewStatus(request.reviewStatus());
    review.setOverrideStatus(request.overrideStatus());
    review.setOverrideScore(request.overrideScore());
    review.setNote(masker.sanitizeText(request.note()).value());
    review.setUpdatedAt(now);
    return assembler.toReview(reviewRepository.save(review));
  }
}
