package vn.vinfast.vfqc.api.testrun.service;

import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.testrun.dto.TestCaseResultReviewResponse;
import vn.vinfast.vfqc.api.testrun.dto.UpsertTestCaseResultReviewRequest;

public interface TestRunReviewService {

  /**
   * Creates or updates a QC review for a machine result.
   *
   * <p>Requires project edit permission in the current permission model. The review is stored in a
   * separate row and never mutates the machine result.
   *
   * @param user current authenticated user
   * @param resultId test-case result identifier
   * @param request review status, optional override, and note
   * @return persisted review
   */
  TestCaseResultReviewResponse upsert(
      CurrentUser user, Long resultId, UpsertTestCaseResultReviewRequest request);
}
