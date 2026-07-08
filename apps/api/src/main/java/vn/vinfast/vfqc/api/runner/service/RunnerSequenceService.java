package vn.vinfast.vfqc.api.runner.service;

import java.util.UUID;

public interface RunnerSequenceService {

  /**
   * Allocates the next backend-owned event sequence number for a locked test run transaction.
   *
   * @param testRunId test run id
   * @return next monotonic sequence number
   */
  long nextSequence(UUID testRunId);
}
