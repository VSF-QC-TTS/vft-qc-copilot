package vn.vinfast.vfqc.api.testrun.service;

import java.util.UUID;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.testrun.dto.CreateTestRunRequest;
import vn.vinfast.vfqc.api.testrun.model.LockedRunConfig;
import vn.vinfast.vfqc.api.testrun.service.impl.TestRunSnapshotServiceImpl;

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

  /**
   * Inspects and validates a test run configuration without locking.
   *
   * <p>Returns blockers, warnings, counts, and a draft locked config for preflight UI display.
   *
   * @param user current authenticated user
   * @param projectId project identifier
   * @param request create-run request
   * @return inspection result with blockers, warnings, and counts
   */
  TestRunSnapshotServiceImpl.Inspection inspect(CurrentUser user, UUID projectId, CreateTestRunRequest request);
}
