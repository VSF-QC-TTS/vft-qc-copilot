package vn.vinfast.vfqc.api.testrun.service;

import java.util.UUID;
import org.springframework.data.domain.Pageable;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.testrun.dto.ComparisonMatrixResponse;
import vn.vinfast.vfqc.api.testrun.enums.ResultMode;

public interface TestRunComparisonMatrixService {

  /**
   * Builds a paged comparison matrix grouped by dataset row and run target.
   *
   * <p>Requires project view permission through the parent run. The matrix exposes final status,
   * score, pass/fail, and review markers only; it does not include raw request or response bodies.
   *
   * @param user current authenticated user
   * @param testRunId test run identifier
   * @param mode result mode to render
   * @param pageable dataset-row page request
   * @return comparison matrix page
   */
  ComparisonMatrixResponse matrix(CurrentUser user, UUID testRunId, ResultMode mode, Pageable pageable);
}
