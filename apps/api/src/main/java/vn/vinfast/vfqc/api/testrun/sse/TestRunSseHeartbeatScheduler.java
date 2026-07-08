package vn.vinfast.vfqc.api.testrun.sse;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class TestRunSseHeartbeatScheduler {

  private final TestRunSseService sseService;

  @Scheduled(fixedDelayString = "${vfqc.sse.heartbeat-interval-ms:15000}")
  void sendHeartbeat() {
    sseService.sendHeartbeat();
  }
}
