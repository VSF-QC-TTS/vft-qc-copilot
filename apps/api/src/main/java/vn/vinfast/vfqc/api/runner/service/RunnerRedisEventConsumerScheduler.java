package vn.vinfast.vfqc.api.runner.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "vfqc.runner.redis-events", name = "enabled", havingValue = "true")
class RunnerRedisEventConsumerScheduler {

  private final RunnerRedisEventConsumer consumer;

  @Scheduled(fixedDelayString = "${vfqc.runner.redis-events.poll-interval-ms:1000}")
  void consumeEvents() {
    try {
      var consumed = consumer.consumeOnce();
      if (consumed > 0) {
        log.debug("Runner Redis event consumer processed records: count={}", consumed);
      }
    } catch (RuntimeException exception) {
      log.warn("Runner Redis event consumer poll failed: errorType={}", exception.getClass().getSimpleName());
    }
  }
}
