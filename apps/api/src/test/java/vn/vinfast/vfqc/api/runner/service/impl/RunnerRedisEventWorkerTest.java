package vn.vinfast.vfqc.api.runner.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import vn.vinfast.vfqc.api.runner.config.RunnerRedisEventWorkerProperties;
import vn.vinfast.vfqc.api.runner.service.RunnerRedisEventConsumer;

class RunnerRedisEventWorkerTest {

  @Test
  void start_WhenDisabled_DoesNotConsumeEvents() {
    var consumed = new AtomicInteger(0);
    RunnerRedisEventConsumer consumer =
        () -> {
          consumed.incrementAndGet();
          return 0;
        };
    var worker = new RunnerRedisEventWorker(consumer, properties(false));

    worker.start();

    assertThat(worker.isRunning()).isFalse();
    assertThat(consumed).hasValue(0);
  }

  @Test
  void start_WhenEnabled_ConsumesEventsUntilStopped() throws InterruptedException {
    var firstPoll = new CountDownLatch(1);
    RunnerRedisEventConsumer consumer =
        () -> {
          firstPoll.countDown();
          return 0;
        };
    var worker = new RunnerRedisEventWorker(consumer, properties(true));

    worker.start();

    assertThat(firstPoll.await(1, TimeUnit.SECONDS)).isTrue();
    assertThat(worker.isRunning()).isTrue();

    worker.stop();

    assertThat(worker.isRunning()).isFalse();
  }

  @Test
  void start_WhenConsumerFails_ContinuesPolling() throws InterruptedException {
    var attempts = new AtomicInteger(0);
    var successfulPoll = new CountDownLatch(1);
    RunnerRedisEventConsumer consumer =
        () -> {
          if (attempts.incrementAndGet() == 1) {
            throw new IllegalStateException("temporary redis failure");
          }
          successfulPoll.countDown();
          return 0;
        };
    var worker = new RunnerRedisEventWorker(consumer, properties(true));

    worker.start();

    assertThat(successfulPoll.await(1, TimeUnit.SECONDS)).isTrue();
    assertThat(attempts).hasValueGreaterThanOrEqualTo(2);

    worker.stop();
  }

  private RunnerRedisEventWorkerProperties properties(boolean enabled) {
    return new RunnerRedisEventWorkerProperties(enabled, 10, 10);
  }
}
