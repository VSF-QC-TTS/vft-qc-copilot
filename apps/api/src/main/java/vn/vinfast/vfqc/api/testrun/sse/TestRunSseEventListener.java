package vn.vinfast.vfqc.api.testrun.sse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.vinfast.vfqc.api.runner.event.RunnerEventCommittedEvent;

@Component
@RequiredArgsConstructor
@Slf4j
class TestRunSseEventListener {

  private final TestRunSseService sseService;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onRunnerEventCommitted(RunnerEventCommittedEvent event) {
    try {
      sseService.broadcast(event.runnerEventId());
    } catch (Exception exception) {
      log.error("Failed to broadcast committed runner event: runnerEventId={}", event.runnerEventId(), exception);
    }
  }
}
