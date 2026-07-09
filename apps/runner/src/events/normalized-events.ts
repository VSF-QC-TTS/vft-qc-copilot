import { z } from 'zod';

export type NormalizedEventPayload = Record<string, unknown>;

export const TESTCASE_RESULT_STATUSES = ['PASSED', 'FAILED', 'ERROR', 'JUDGE_ERROR', 'TIMEOUT'] as const;
export type TestcaseResultStatus = (typeof TESTCASE_RESULT_STATUSES)[number];

const jsonObjectSchema = z.record(z.string(), z.unknown());
const optionalJsonObjectSchema = jsonObjectSchema.optional();

export const RunStartedPayloadSchema = z.object({
  status: z.literal('RUNNING'),
});
export type RunStartedPayload = z.infer<typeof RunStartedPayloadSchema>;

export const TestcaseResultPayloadSchema = z.object({
  testRunTargetId: z.uuid(),
  datasetRowId: z.number().int().nonnegative(),
  status: z.enum(TESTCASE_RESULT_STATUSES),
  score: z.union([z.number(), z.string()]).optional(),
  passed: z.boolean().optional(),
  errorCategory: z.string().min(1).optional(),
  request: optionalJsonObjectSchema,
  response: optionalJsonObjectSchema,
  extractedOutput: optionalJsonObjectSchema,
  gradingResult: optionalJsonObjectSchema,
  componentResults: optionalJsonObjectSchema,
  tokenUsage: optionalJsonObjectSchema,
  latencyMs: z.union([z.number().int().nonnegative(), z.string()]).optional(),
  machineSummary: z.string().min(1).optional(),
});
export type TestcaseResultPayload = z.infer<typeof TestcaseResultPayloadSchema>;

export const RunProgressPayloadSchema = z.object({
  completed: z.number().int().nonnegative().optional(),
  total: z.number().int().nonnegative().optional(),
});
export type RunProgressPayload = z.infer<typeof RunProgressPayloadSchema>;

export const RunCompletedPayloadSchema = z.object({
  summary: jsonObjectSchema.optional(),
});
export type RunCompletedPayload = z.infer<typeof RunCompletedPayloadSchema>;

export const RunFailedPayloadSchema = z.object({
  error: z.string().min(1).optional(),
  errorCategory: z.string().min(1).optional(),
  message: z.string().min(1).optional(),
});
export type RunFailedPayload = z.infer<typeof RunFailedPayloadSchema>;

export const RunCancelledPayloadSchema = z.object({
  reason: z.string().min(1).optional(),
});
export type RunCancelledPayload = z.infer<typeof RunCancelledPayloadSchema>;

export const RunLogPayloadSchema = z.object({
  level: z.enum(['debug', 'info', 'warn', 'error']).optional(),
  message: z.string().min(1),
  data: jsonObjectSchema.optional(),
});
export type RunLogPayload = z.infer<typeof RunLogPayloadSchema>;

export const ArtifactCreatedPayloadSchema = z
  .object({
    artifactType: z.string().min(1).optional(),
    name: z.string().min(1).optional(),
    uri: z.string().min(1).optional(),
    sanitized: z.boolean().optional(),
    contentHash: z.string().min(1).optional(),
  })
  .strict();
export type ArtifactCreatedPayload = z.infer<typeof ArtifactCreatedPayloadSchema>;
