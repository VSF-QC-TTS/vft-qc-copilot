package vn.vinfast.vfqc.api.testrun.service;

import java.util.UUID;
import org.springframework.data.domain.Pageable;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.testrun.dto.TestCaseResultDetailResponse;
import vn.vinfast.vfqc.api.testrun.dto.TestRunResultPageResponse;
import vn.vinfast.vfqc.api.testrun.enums.ResultMode;

public interface TestRunResultService {

  /**
   * Lists persisted test-case results for a run.
   *
   * <p>Requires project view permission through the parent run. Machine results are immutable; final
   * mode overlays review data without modifying the stored machine result.
   *
   * @param user current authenticated user
   * @param testRunId test run identifier
   * @param mode result mode to render
   * @param pageable page request
   * @return paged result details
   */
  TestRunResultPageResponse list(CurrentUser user, UUID testRunId, ResultMode mode, Pageable pageable);

  /**
   * Loads one test-case result detail.
   *
   * <p>Requires project view permission through the parent run. The response is sanitized and can
   * render either machine or final reviewed values.
   *
   * @param user current authenticated user
   * @param resultId result identifier
   * @param mode result mode to render
   * @return result detail
   */
  TestCaseResultDetailResponse get(CurrentUser user, Long resultId, ResultMode mode);
}
