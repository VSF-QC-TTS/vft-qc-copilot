package vn.vinfast.vfqc.api.testrun.service;

import java.util.UUID;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.testrun.dto.RunPreflightRequest;
import vn.vinfast.vfqc.api.testrun.dto.RunPreflightResponse;

public interface TestRunPreflightService {

  /**
   * Validates whether a test run can be created without mutating state.
   *
   * <p>Requires project view permission. The response contains blockers and warnings for the UI;
   * create-run performs the same authoritative checks again before writing snapshots.
   *
   * @param user current authenticated user
   * @param projectId project that would own the run
   * @param request dataset, target, evaluator, and option selection
   * @return preflight result with counts and blocker/warning messages
   */
  RunPreflightResponse preflight(CurrentUser user, UUID projectId, RunPreflightRequest request);
}
