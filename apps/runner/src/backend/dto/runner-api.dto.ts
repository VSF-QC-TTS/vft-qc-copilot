export const RUNNER_COMMAND_TYPES = ['START_RUN', 'CANCEL_RUN'] as const;
export type RunnerCommandType = (typeof RUNNER_COMMAND_TYPES)[number];

export const RUNNER_COMMAND_STATUSES = ['PENDING', 'PUBLISHED', 'ACKNOWLEDGED', 'FAILED'] as const;
export type RunnerCommandStatus = (typeof RUNNER_COMMAND_STATUSES)[number];

export const RUNNER_INSTANCE_STATUSES = ['IDLE', 'BUSY', 'OFFLINE'] as const;
export type RunnerInstanceStatus = (typeof RUNNER_INSTANCE_STATUSES)[number];

export const RUNNER_EVENT_STATUSES = ['APPLIED', 'DUPLICATE', 'INVALID_STATE', 'FAILED'] as const;
export type RunnerEventStatus = (typeof RUNNER_EVENT_STATUSES)[number];

export const RUNNER_EVENT_TYPES = [
  'RUN_STARTED',
  'RUN_PROGRESS',
  'TESTCASE_COMPLETED',
  'TESTCASE_ERROR',
  'RUN_COMPLETED',
  'RUN_FAILED',
  'RUN_CANCELLED',
  'RUN_LOG',
  'ARTIFACT_CREATED',
] as const;
export type RunnerEventType = (typeof RUNNER_EVENT_TYPES)[number];

export const TEST_RUN_STATUSES = [
  'CREATED',
  'QUEUED',
  'RUNNING',
  'CANCEL_REQUESTED',
  'COMPLETED',
  'FAILED',
  'CANCELLED',
] as const;
export type TestRunStatus = (typeof TEST_RUN_STATUSES)[number];

export const TEST_RUN_TARGET_TYPES = ['CHATBOT_TARGET', 'LLM_BASELINE'] as const;
export type TestRunTargetType = (typeof TEST_RUN_TARGET_TYPES)[number];

export const TEST_RUN_TYPES = ['SINGLE_TARGET', 'COMPARISON'] as const;
export type TestRunType = (typeof TEST_RUN_TYPES)[number];

export interface RegisterRunnerInstanceRequest {
  readonly runnerKey: string;
  readonly hostname?: string | null;
  readonly version?: string | null;
}

export interface RunnerHeartbeatRequest {
  readonly runnerKey: string;
  readonly status?: RunnerInstanceStatus | null;
  readonly currentCommandId?: string | null;
  readonly currentTestRunId?: string | null;
}

export interface RunnerInstanceResponse {
  readonly id: string;
  readonly runnerKey: string;
  readonly hostname?: string | null;
  readonly version?: string | null;
  readonly status: RunnerInstanceStatus;
  readonly currentCommandId?: string | null;
  readonly currentTestRunId?: string | null;
  readonly lastHeartbeatAt?: string | null;
  readonly registeredAt: string;
  readonly updatedAt: string;
}

export interface TargetSnapshot {
  readonly id: string;
  readonly targetType: TestRunTargetType;
  readonly chatbotTargetId?: string | null;
  readonly providerId?: string | null;
  readonly model?: string | null;
  readonly name: string;
  readonly orderIndex: number;
  readonly config: Record<string, unknown>;
}

export interface EvaluatorSnapshot {
  readonly id: number;
  readonly evaluatorId: string;
  readonly evaluatorVersionId: string;
  readonly evaluatorType: string;
  readonly version: number;
  readonly orderIndex: number;
  readonly config: Record<string, unknown>;
  readonly rules: readonly Record<string, unknown>[];
}

export interface DatasetRowSnapshot {
  readonly id: number;
  readonly rowIndex: number;
  readonly cells: Record<string, unknown>;
  readonly revision: number;
  readonly rowHash: string;
}

export interface RunnerTestRunConfigResponse {
  readonly testRunId: string;
  readonly projectId: string;
  readonly runType: TestRunType;
  readonly status: TestRunStatus;
  readonly datasetId: string;
  readonly datasetVersionId: string;
  readonly datasetSchemaId: string;
  readonly datasetSchemaVersion: number;
  readonly progressTotal: number;
  readonly domainSnapshot: Record<string, unknown>;
  readonly promptfooSnapshot: Record<string, unknown>;
  readonly targets: readonly TargetSnapshot[];
  readonly evaluators: readonly EvaluatorSnapshot[];
  readonly rows: readonly DatasetRowSnapshot[];
}

export interface RunnerRuntimeSecretsRequest {
  readonly secretIds: readonly string[];
}

export interface RunnerRuntimeSecretsResponse {
  readonly secrets: Record<string, string>;
}

export interface RunnerControlStateResponse {
  readonly testRunId: string;
  readonly status: TestRunStatus;
  readonly cancellationRequested: boolean;
}

export interface RunnerEventRequest {
  readonly eventId: string;
  readonly testRunId: string;
  readonly eventType: RunnerEventType;
  readonly runnerId?: string | null;
  readonly commandId?: string | null;
  readonly occurredAt?: string | null;
  readonly payload?: Record<string, unknown> | null;
}

export interface RunnerEventIngestionResponse {
  readonly eventId: string;
  readonly testRunId: string;
  readonly sequenceNo: number;
  readonly status: RunnerEventStatus;
}
