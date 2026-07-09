package vn.vinfast.vfqc.api.runner.service;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class RunnerRedisEventConsumerSchedulerTest {

  @Test
  void consumeEvents_WhenConsumerSucceeds_CallsConsumerOnce() {
    var consumer = mock(RunnerRedisEventConsumer.class);
    var scheduler = new RunnerRedisEventConsumerScheduler(consumer);

    scheduler.consumeEvents();

    verify(consumer).consumeOnce();
  }

  @Test
  void consumeEvents_WhenConsumerFails_DoesNotPropagateFailure() {
    var consumer = mock(RunnerRedisEventConsumer.class);
    doThrow(new IllegalStateException("poll failed")).when(consumer).consumeOnce();
    var scheduler = new RunnerRedisEventConsumerScheduler(consumer);

    assertThatNoException().isThrownBy(scheduler::consumeEvents);

    verify(consumer).consumeOnce();
  }
}
