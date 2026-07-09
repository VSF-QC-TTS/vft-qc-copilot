package vn.vinfast.vfqc.api.runner.service.impl;

import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;
import vn.vinfast.vfqc.api.runner.config.RunnerRedisEventWorkerProperties;
import vn.vinfast.vfqc.api.runner.service.RunnerRedisEventConsumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class RunnerRedisEventWorker implements SmartLifecycle {

  private final RunnerRedisEventConsumer consumer;
  private final RunnerRedisEventWorkerProperties properties;
  private final AtomicBoolean running = new AtomicBoolean(false);
  private Thread workerThread;

  @Override
  public void start() {
    if (!properties.isEnabled() || !running.compareAndSet(false, true)) {
      return;
    }
    workerThread = Thread.ofVirtual().name("vfqc-runner-redis-events").start(this::runLoop);
    log.info("Runner Redis event worker started");
  }

  @Override
  public void stop() {
    if (!running.compareAndSet(true, false)) {
      return;
    }
    if (workerThread != null) {
      workerThread.interrupt();
    }
    log.info("Runner Redis event worker stopped");
  }

  @Override
  public boolean isRunning() {
    return running.get();
  }

  @Override
  public boolean isAutoStartup() {
    return true;
  }

  @Override
  public int getPhase() {
    return Integer.MAX_VALUE - 1000;
  }

  private void runLoop() {
    while (running.get() && !Thread.currentThread().isInterrupted()) {
      try {
        var consumed = consumer.consumeOnce();
        if (consumed == 0) {
          sleep(properties.idleSleepMs());
        }
      } catch (RuntimeException exception) {
        log.warn("Runner Redis event worker poll failed: errorType={}", exception.getClass().getSimpleName());
        sleep(properties.errorSleepMs());
      }
    }
    running.set(false);
  }

  private void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      running.set(false);
    }
  }
}
