package vn.vinfast.vfqc.api.runner.service;

public interface RunnerRedisEventConsumer {

  /**
   * Consumes available runner event stream messages once. Failed transactional ingestion must leave
   * messages unacknowledged for retry or DLQ handling.
   *
   * @return number of consumed messages
   */
  int consumeOnce();
}
