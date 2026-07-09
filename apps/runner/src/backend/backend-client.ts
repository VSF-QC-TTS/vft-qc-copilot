import type {
  RegisterRunnerInstanceRequest,
  RunnerControlStateResponse,
  RunnerEventIngestionResponse,
  RunnerEventRequest,
  RunnerHeartbeatRequest,
  RunnerInstanceResponse,
  RunnerRuntimeSecretsRequest,
  RunnerRuntimeSecretsResponse,
  RunnerTestRunConfigResponse,
} from './dto/runner-api.dto.js';

export interface BackendClient {
  registerInstance(request: RegisterRunnerInstanceRequest): Promise<RunnerInstanceResponse>;
  heartbeat(request: RunnerHeartbeatRequest): Promise<RunnerInstanceResponse>;
  getTestRunConfig(testRunId: string, page: number, size: number): Promise<RunnerTestRunConfigResponse>;
  resolveRuntimeSecrets(
    testRunId: string,
    request: RunnerRuntimeSecretsRequest,
  ): Promise<RunnerRuntimeSecretsResponse>;
  getControlState(testRunId: string): Promise<RunnerControlStateResponse>;
  sendEvent(event: RunnerEventRequest): Promise<RunnerEventIngestionResponse>;
}
