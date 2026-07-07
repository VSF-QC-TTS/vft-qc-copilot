package vn.vinfast.vfqc.api.testrun.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.vinfast.vfqc.api.testrun.entity.TestRun;
import vn.vinfast.vfqc.api.testrun.service.TestRunCommandService;

@Service
@Slf4j
public class NoopTestRunCommandService implements TestRunCommandService {

  @Override
  public void afterRunCreated(TestRun testRun) {
    log.debug("Phase 8 no-op runner command boundary invoked: testRunId={}", testRun.getId());
  }
}
