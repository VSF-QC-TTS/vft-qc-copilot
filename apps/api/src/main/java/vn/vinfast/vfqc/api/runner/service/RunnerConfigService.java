package vn.vinfast.vfqc.api.runner.service;

import java.util.UUID;
import org.springframework.data.domain.Pageable;
import vn.vinfast.vfqc.api.runner.dto.RunnerControlStateResponse;
import vn.vinfast.vfqc.api.runner.dto.RunnerTestRunConfigResponse;

public interface RunnerConfigService {

  /**
   * Returns the immutable locked TestRun snapshots and dataset row page needed by the runner.
   *
   * @param testRunId locked test run id
   * @param pageable row page request
   * @return sanitized execution config
   */
  RunnerTestRunConfigResponse config(UUID testRunId, Pageable pageable);

  /**
   * Returns the latest control state so a runner can poll for cancellation.
   *
   * @param testRunId test run id
   * @return current run status and cancellation flag
   */
  RunnerControlStateResponse controlState(UUID testRunId);
}
