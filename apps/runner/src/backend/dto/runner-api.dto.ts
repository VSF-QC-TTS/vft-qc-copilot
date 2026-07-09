import { z } from 'zod';

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

const nullableUuidSchema = z.uuid().nullish();
const nullableTextSchema = z.string().min(1).nullish();
const timestampSchema = z.string().min(1);
const jsonObjectSchema = z.record(z.string(), z.unknown());

export const RegisterRunnerInstanceRequestSchema = z.object({
  runnerKey: z.string().min(1),
  hostname: nullableTextSchema,
  version: nullableTextSchema,
});
export type RegisterRunnerInstanceRequest = z.infer<typeof RegisterRunnerInstanceRequestSchema>;

export const RunnerHeartbeatRequestSchema = z.object({
  runnerKey: z.string().min(1),
  status: z.enum(RUNNER_INSTANCE_STATUSES).nullish(),
  currentCommandId: nullableUuidSchema,
  currentTestRunId: nullableUuidSchema,
});
export type RunnerHeartbeatRequest = z.infer<typeof RunnerHeartbeatRequestSchema>;

export const RunnerInstanceResponseSchema = z.object({
  id: z.uuid(),
  runnerKey: z.string().min(1),
  hostname: nullableTextSchema,
  version: nullableTextSchema,
  status: z.enum(RUNNER_INSTANCE_STATUSES),
  currentCommandId: nullableUuidSchema,
  currentTestRunId: nullableUuidSchema,
  lastHeartbeatAt: timestampSchema.nullish(),
  registeredAt: timestampSchema,
  updatedAt: timestampSchema,
});
export type RunnerInstanceResponse = z.infer<typeof RunnerInstanceResponseSchema>;

export const TargetSnapshotSchema = z.object({
  id: z.uuid(),
  targetType: z.enum(TEST_RUN_TARGET_TYPES),
  chatbotTargetId: nullableUuidSchema,
  providerId: nullableUuidSchema,
  model: nullableTextSchema,
  name: z.string().min(1),
  orderIndex: z.number().int().nonnegative(),
  config: jsonObjectSchema,
});
export type TargetSnapshot = z.infer<typeof TargetSnapshotSchema>;

export const EvaluatorSnapshotSchema = z.object({
  id: z.number().int().nonnegative(),
  evaluatorId: z.uuid(),
  evaluatorVersionId: z.uuid(),
  evaluatorType: z.string().min(1),
  version: z.number().int().positive(),
  orderIndex: z.number().int().nonnegative(),
  config: jsonObjectSchema,
  rules: z.array(jsonObjectSchema),
});
export type EvaluatorSnapshot = z.infer<typeof EvaluatorSnapshotSchema>;

export const DatasetRowSnapshotSchema = z.object({
  id: z.number().int().nonnegative(),
  rowIndex: z.number().int().nonnegative(),
  cells: jsonObjectSchema,
  revision: z.number().int().positive(),
  rowHash: z.string().min(1),
});
export type DatasetRowSnapshot = z.infer<typeof DatasetRowSnapshotSchema>;

export const RunnerTestRunConfigResponseSchema = z.object({
  testRunId: z.uuid(),
  projectId: z.uuid(),
  runType: z.enum(TEST_RUN_TYPES),
  status: z.enum(TEST_RUN_STATUSES),
  datasetId: z.uuid(),
  datasetVersionId: z.uuid(),
  datasetSchemaId: z.uuid(),
  datasetSchemaVersion: z.number().int().positive(),
  progressTotal: z.number().int().nonnegative(),
  domainSnapshot: jsonObjectSchema,
  promptfooSnapshot: jsonObjectSchema,
  targets: z.array(TargetSnapshotSchema),
  evaluators: z.array(EvaluatorSnapshotSchema),
  rows: z.array(DatasetRowSnapshotSchema),
});
export type RunnerTestRunConfigResponse = z.infer<typeof RunnerTestRunConfigResponseSchema>;

export const RunnerRuntimeSecretsRequestSchema = z.object({
  secretIds: z.array(z.uuid()).nonempty(),
});
export type RunnerRuntimeSecretsRequest = z.infer<typeof RunnerRuntimeSecretsRequestSchema>;

export const RunnerRuntimeSecretsResponseSchema = z.object({
  secrets: z.record(z.string(), z.string()),
});
export type RunnerRuntimeSecretsResponse = z.infer<typeof RunnerRuntimeSecretsResponseSchema>;

export const RunnerControlStateResponseSchema = z.object({
  testRunId: z.uuid(),
  status: z.enum(TEST_RUN_STATUSES),
  cancellationRequested: z.boolean(),
});
export type RunnerControlStateResponse = z.infer<typeof RunnerControlStateResponseSchema>;

export const RunnerEventRequestSchema = z.object({
  eventId: z.string().min(1),
  testRunId: z.uuid(),
  eventType: z.enum(RUNNER_EVENT_TYPES),
  runnerId: nullableUuidSchema,
  commandId: nullableUuidSchema,
  occurredAt: timestampSchema.nullish(),
  payload: jsonObjectSchema.nullish(),
});
export type RunnerEventRequest = z.infer<typeof RunnerEventRequestSchema>;

export const RunnerEventIngestionResponseSchema = z.object({
  eventId: z.string().min(1),
  testRunId: z.uuid(),
  sequenceNo: z.number().int().positive(),
  status: z.enum(RUNNER_EVENT_STATUSES),
});
export type RunnerEventIngestionResponse = z.infer<typeof RunnerEventIngestionResponseSchema>;
