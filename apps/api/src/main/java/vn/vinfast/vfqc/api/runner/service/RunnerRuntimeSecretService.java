package vn.vinfast.vfqc.api.runner.service;

import java.util.UUID;
import vn.vinfast.vfqc.api.runner.dto.RunnerRuntimeSecretsRequest;
import vn.vinfast.vfqc.api.runner.dto.RunnerRuntimeSecretsResponse;

public interface RunnerRuntimeSecretService {

  /**
   * Resolves plaintext secrets only when their ids are referenced by the locked TestRun snapshots.
   * Returned plaintext must never be logged or persisted.
   *
   * @param testRunId locked test run id
   * @param request requested secret ids
   * @return plaintext values keyed by secret id string
   */
  RunnerRuntimeSecretsResponse resolve(UUID testRunId, RunnerRuntimeSecretsRequest request);
}
