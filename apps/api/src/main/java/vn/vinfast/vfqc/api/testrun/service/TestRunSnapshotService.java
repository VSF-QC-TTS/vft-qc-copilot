package vn.vinfast.vfqc.api.testrun.service;

import java.util.UUID;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.testrun.dto.CreateTestRunRequest;
import vn.vinfast.vfqc.api.testrun.model.LockedRunConfig;

public interface TestRunSnapshotService {

  /**
   * Validates and locks sanitized run configuration from mutable project resources.
   *
   * <p>Requires an already authorized caller. The returned value is pure in-memory data suitable for
   * persistence as TestRun snapshots; no database writes happen in this method.
   *
   * @param user current authenticated user
   * @param projectId project identifier
   * @param request create-run request
   * @return locked sanitized run configuration
   */
  LockedRunConfig lock(CurrentUser user, UUID projectId, CreateTestRunRequest request);
}
