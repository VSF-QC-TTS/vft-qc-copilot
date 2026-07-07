package vn.vinfast.vfqc.api.testrun.service;

import vn.vinfast.vfqc.api.testrun.entity.TestRun;

public interface TestRunCommandService {

  /**
   * Handles the post-create run command boundary.
   *
   * <p>Phase 8 intentionally does not persist runner commands or publish Redis messages. The
   * implementation is a no-op placeholder so Phase 9 can replace it with a durable command
   * publisher without changing create-run orchestration.
   *
   * @param testRun persisted test run
   */
  void afterRunCreated(TestRun testRun);
}
