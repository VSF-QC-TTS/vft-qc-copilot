package vn.vinfast.vfqc.api.runner.service;

import vn.vinfast.vfqc.api.runner.dto.RegisterRunnerInstanceRequest;
import vn.vinfast.vfqc.api.runner.dto.RunnerHeartbeatRequest;
import vn.vinfast.vfqc.api.runner.dto.RunnerInstanceResponse;

public interface RunnerInstanceService {

  /**
   * Upserts a stable runner instance registration and refreshes its heartbeat metadata.
   *
   * @param request runner registration payload
   * @return persisted runner metadata
   */
  RunnerInstanceResponse register(RegisterRunnerInstanceRequest request);

  /**
   * Records a runner heartbeat and the command/run currently being processed.
   *
   * @param request heartbeat payload
   * @return updated runner metadata
   */
  RunnerInstanceResponse heartbeat(RunnerHeartbeatRequest request);
}
