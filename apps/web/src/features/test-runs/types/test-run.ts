import { z } from 'zod';
import { evaluatorTypeSchema } from '@/features/evaluators/types/evaluator';

export const testRunTypeSchema = z.enum(['SINGLE_TARGET', 'COMPARISON']);
export type TestRunType = z.infer<typeof testRunTypeSchema>;

export const testRunStatusSchema = z.enum([
  'CREATED',
  'QUEUED',
  'RUNNING',
  'CANCEL_REQUESTED',
  'COMPLETED',
  'FAILED',
  'CANCELLED',
]);
export type TestRunStatus = z.infer<typeof testRunStatusSchema>;

export const testRunTargetTypeSchema = z.enum(['CHATBOT_TARGET', 'LLM_BASELINE']);
export type TestRunTargetType = z.infer<typeof testRunTargetTypeSchema>;

export const runTargetRequestSchema = z.discriminatedUnion('targetType', [
  z.object({
    targetType: z.literal('CHATBOT_TARGET'),
    chatbotTargetId: z.string().uuid('Target không hợp lệ'),
    name: z.string().min(1, 'Tên target không được để trống').optional(),
  }),
  z.object({
    targetType: z.literal('LLM_BASELINE'),
    providerId: z.string().uuid('Provider không hợp lệ'),
    model: z.string().min(1, 'Model baseline không được để trống'),
    name: z.string().min(1, 'Tên baseline không được để trống').optional(),
  }),
]);
export type RunTargetRequest = z.infer<typeof runTargetRequestSchema>;

export const runEvaluatorVersionRequestSchema = z.object({
  evaluatorVersionId: z.string().uuid('Evaluator version không hợp lệ'),
});
export type RunEvaluatorVersionRequest = z.infer<typeof runEvaluatorVersionRequestSchema>;

export const runOptionsRequestSchema = z.object({
  maxRows: z.number().int().min(1, 'Max rows tối thiểu là 1').max(10000).optional(),
  includePromptfooSnapshot: z.boolean().optional(),
});
export type RunOptionsRequest = z.infer<typeof runOptionsRequestSchema>;

export const createTestRunRequestSchema = z.object({
  datasetId: z.string().uuid('Dataset không hợp lệ'),
  runType: testRunTypeSchema.optional(),
  targets: z.array(runTargetRequestSchema).min(1, 'Cần ít nhất một target'),
  evaluators: z.array(runEvaluatorVersionRequestSchema).min(1, 'Cần ít nhất một evaluator'),
  options: runOptionsRequestSchema.optional(),
});
export type CreateTestRunRequest = z.infer<typeof createTestRunRequestSchema>;

export const runPreflightRequestSchema = createTestRunRequestSchema.omit({ runType: true });
export type RunPreflightRequest = z.infer<typeof runPreflightRequestSchema>;

export const preflightMessageSchema = z.object({
  code: z.string(),
  message: z.string(),
  resourceId: z.string().nullable().optional(),
});
export type PreflightMessageResponse = z.infer<typeof preflightMessageSchema>;

export const runPreflightResponseSchema = z.object({
  runnable: z.boolean(),
  blockers: z.array(preflightMessageSchema),
  warnings: z.array(preflightMessageSchema),
  counts: z.object({
    rows: z.number(),
    targets: z.number(),
    evaluators: z.number(),
  }),
});
export type RunPreflightResponse = z.infer<typeof runPreflightResponseSchema>;

export const testRunTargetResponseSchema = z.object({
  id: z.string().uuid(),
  targetType: testRunTargetTypeSchema,
  chatbotTargetId: z.string().uuid().nullable().optional(),
  providerId: z.string().uuid().nullable().optional(),
  model: z.string().nullable().optional(),
  name: z.string(),
  orderIndex: z.number(),
  config: z.record(z.string(), z.unknown()),
});
export type TestRunTargetResponse = z.infer<typeof testRunTargetResponseSchema>;

export const testRunEvaluatorVersionResponseSchema = z.object({
  id: z.number(),
  evaluatorId: z.string().uuid(),
  evaluatorVersionId: z.string().uuid(),
  evaluatorType: evaluatorTypeSchema,
  version: z.number(),
  orderIndex: z.number(),
  config: z.record(z.string(), z.unknown()),
  rules: z.array(z.record(z.string(), z.unknown())),
});
export type TestRunEvaluatorVersionResponse = z.infer<typeof testRunEvaluatorVersionResponseSchema>;

export const testRunDetailResponseSchema = z.object({
  id: z.string().uuid(),
  projectId: z.string().uuid(),
  runType: testRunTypeSchema,
  datasetId: z.string().uuid(),
  datasetVersionId: z.string().uuid(),
  datasetSchemaId: z.string().uuid(),
  datasetSchemaVersion: z.number(),
  judgeProviderId: z.string().uuid().nullable().optional(),
  judgeModel: z.string().nullable().optional(),
  status: testRunStatusSchema,
  progressCompleted: z.number(),
  progressTotal: z.number(),
  summaryMetrics: z.record(z.string(), z.unknown()),
  domainConfigSnapshot: z.record(z.string(), z.unknown()),
  promptfooConfigSnapshot: z.record(z.string(), z.unknown()),
  domainConfigHash: z.string(),
  promptfooConfigHash: z.string(),
  targets: z.array(testRunTargetResponseSchema),
  evaluators: z.array(testRunEvaluatorVersionResponseSchema),
  startedBy: z.string().uuid(),
  startedAt: z.string().nullable().optional(),
  finishedAt: z.string().nullable().optional(),
  cancelRequestedAt: z.string().nullable().optional(),
  createdAt: z.string(),
  updatedAt: z.string(),
});
export type TestRunDetailResponse = z.infer<typeof testRunDetailResponseSchema>;

export const testRunSummaryResponseSchema = z.object({
  id: z.string().uuid(),
  projectId: z.string().uuid(),
  runType: testRunTypeSchema,
  datasetId: z.string().uuid(),
  datasetVersionId: z.string().uuid(),
  status: testRunStatusSchema,
  progressCompleted: z.number(),
  progressTotal: z.number(),
  summaryMetrics: z.record(z.string(), z.unknown()),
  startedBy: z.string().uuid(),
  startedAt: z.string().nullable().optional(),
  finishedAt: z.string().nullable().optional(),
  createdAt: z.string(),
  updatedAt: z.string(),
});
export type TestRunSummaryResponse = z.infer<typeof testRunSummaryResponseSchema>;

export const testRunCollectionSchema = z.object({
  items: z.array(testRunSummaryResponseSchema),
});
