package vn.vinfast.vfqc.api.testrun.state;

import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import vn.vinfast.vfqc.api.common.error.ErrorCode;
import vn.vinfast.vfqc.api.testrun.enums.TestRunStatus;

@Component
public class TestRunStateMachine {

  private static final Map<TestRunStatus, Set<TestRunStatus>> ALLOWED =
      Map.of(
          TestRunStatus.CREATED, Set.of(TestRunStatus.QUEUED),
          TestRunStatus.QUEUED, Set.of(TestRunStatus.RUNNING, TestRunStatus.CANCELLED),
          TestRunStatus.RUNNING,
              Set.of(TestRunStatus.COMPLETED, TestRunStatus.FAILED, TestRunStatus.CANCEL_REQUESTED),
          TestRunStatus.CANCEL_REQUESTED, Set.of(TestRunStatus.CANCELLED, TestRunStatus.COMPLETED));

  public void requireTransition(TestRunStatus current, TestRunStatus next) {
    if (!ALLOWED.getOrDefault(current, Set.of()).contains(next)) {
      throw ErrorCode.INVALID_STATE.throwIt("Invalid test run status transition");
    }
  }
}
