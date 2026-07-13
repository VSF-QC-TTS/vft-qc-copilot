import { z } from 'zod';

const userSummarySchema = z.object({
  id: z.string().uuid(),
  email: z.string().optional(),
  name: z.string().optional(),
  avatarUrl: z.string().nullable().optional(),
});

export const evaluatorTypeSchema = z.enum(['RULE_BASED', 'LLM_JUDGE', 'MIXED']);
export type EvaluatorType = z.infer<typeof evaluatorTypeSchema>;
export const evaluatorOperatorSchema = z.enum([
  'EQUALS',
  'NOT_EQUALS',
  'CONTAINS',
  'NOT_CONTAINS',
  'REGEX_MATCH',
  'STARTS_WITH',
  'ENDS_WITH',
  'JSON_PATH_EXISTS',
  'NUMERIC_EQUALS',
  'NUMERIC_GT',
  'NUMERIC_GTE',
  'NUMERIC_LT',
  'NUMERIC_LTE',
  'IS_EMPTY',
  'IS_NOT_EMPTY',
]);

export type EvaluatorOperator = z.infer<typeof evaluatorOperatorSchema>;

export const evaluatorExpectedValueSchema = z.record(z.string(), z.unknown());

export const evaluatorRuleRequestSchema = z.object({
  fieldSource: z.string().min(1, 'Vui lòng chọn field source'),
  operator: evaluatorOperatorSchema,
  expectedValue: evaluatorExpectedValueSchema.optional(),
  weight: z.number().positive('Weight phải lớn hơn 0').optional(),
  required: z.boolean(),
  orderIndex: z.number().int().positive(),
  config: z.record(z.string(), z.unknown()).optional(),
});
export type EvaluatorRuleRequest = z.infer<typeof evaluatorRuleRequestSchema>;

const allRulesPassConditionSchema = z.object({
  type: z.literal('ALL_RULES_PASS'),
});

const anyRulePassConditionSchema = z.object({
  type: z.literal('ANY_RULE_PASS'),
});

const scoreAtLeastThresholdConditionSchema = z.object({
  type: z.literal('SCORE_AT_LEAST_THRESHOLD'),
  threshold: z.number().min(0, 'Threshold tối thiểu là 0').max(1, 'Threshold tối đa là 1'),
});

export const ruleBasedConfigSchema = z.object({
  ruleBased: z.discriminatedUnion('aggregation', [
    z.object({
      aggregation: z.literal('ALL_PASS'),
      passCondition: allRulesPassConditionSchema,
    }),
    z.object({
      aggregation: z.literal('ANY_PASS'),
      passCondition: anyRulePassConditionSchema,
    }),
    z.object({
      aggregation: z.literal('ALL_REQUIRED_AND_WEIGHTED_THRESHOLD'),
      passCondition: scoreAtLeastThresholdConditionSchema,
    }),
  ]),
});
export type RuleBasedConfig = z.infer<typeof ruleBasedConfigSchema>;

export const llmJudgeConfigSchema = z.object({
  llmJudge: z.object({
    rubric: z.string().min(1, 'Vui lòng nhập rubric'),
    scoreScale: z
      .object({
        min: z.number(),
        max: z.number(),
      })
      .refine((value) => value.min < value.max, {
        message: 'Score min phải nhỏ hơn score max',
      }),
    threshold: z.number().min(0, 'Threshold tối thiểu là 0').max(1, 'Threshold tối đa là 1'),
    passCondition: z.literal('SCORE_AT_LEAST_THRESHOLD'),
    inputTemplate: z.string().min(1, 'Vui lòng nhập input template'),
    outputSchema: z.literal('VFQC_JUDGE_V1'),
    defaultJudgeProviderId: z.string().uuid('Provider không hợp lệ').optional(),
    defaultJudgeModel: z.string().min(1, 'Model không được để trống').optional(),
  }),
});
export type LlmJudgeConfig = z.infer<typeof llmJudgeConfigSchema>;

export const mixedEvaluatorConfigSchema = ruleBasedConfigSchema.merge(llmJudgeConfigSchema);
export type MixedEvaluatorConfig = z.infer<typeof mixedEvaluatorConfigSchema>;

const evaluatorNameSchema = z
  .string()
  .min(1, 'Vui lòng nhập tên Evaluator')
  .max(160, 'Tên tối đa 160 ký tự');

export const createEvaluatorRequestSchema = z.discriminatedUnion('type', [
  z.object({
    name: evaluatorNameSchema,
    type: z.literal('RULE_BASED'),
    config: ruleBasedConfigSchema,
    rules: z.array(evaluatorRuleRequestSchema).min(1, 'Cần ít nhất một rule'),
  }),
  z.object({
    name: evaluatorNameSchema,
    type: z.literal('LLM_JUDGE'),
    config: llmJudgeConfigSchema,
    rules: z.array(evaluatorRuleRequestSchema).max(0, 'LLM Judge không dùng rules'),
  }),
  z.object({
    name: evaluatorNameSchema,
    type: z.literal('MIXED'),
    config: mixedEvaluatorConfigSchema,
    rules: z.array(evaluatorRuleRequestSchema).min(1, 'Cần ít nhất một rule'),
  }),
]);
export type CreateEvaluatorRequest = z.infer<typeof createEvaluatorRequestSchema>;

export const createEvaluatorVersionRequestSchema = z.union([
  z.object({
    config: ruleBasedConfigSchema,
    rules: z.array(evaluatorRuleRequestSchema).min(1, 'Cần ít nhất một rule'),
  }),
  z.object({
    config: llmJudgeConfigSchema,
    rules: z.array(evaluatorRuleRequestSchema).max(0, 'LLM Judge không dùng rules'),
  }),
  z.object({
    config: mixedEvaluatorConfigSchema,
    rules: z.array(evaluatorRuleRequestSchema).min(1, 'Cần ít nhất một rule'),
  }),
]);
export type CreateEvaluatorVersionRequest = z.infer<typeof createEvaluatorVersionRequestSchema>;

const evaluatorValidationContextSchema = z
  .object({
    datasetSchemaId: z.string().uuid().optional(),
    datasetSchemaVersion: z.number().optional(),
    chatbotTargetId: z.string().uuid().optional(),
    exampleId: z.number().optional(),
  })
  .optional();

export const validateEvaluatorRequestSchema = z.discriminatedUnion('type', [
  z.object({
    type: z.literal('RULE_BASED'),
    config: ruleBasedConfigSchema,
    rules: z.array(evaluatorRuleRequestSchema).min(1, 'Cần ít nhất một rule'),
    context: evaluatorValidationContextSchema,
  }),
  z.object({
    type: z.literal('LLM_JUDGE'),
    config: llmJudgeConfigSchema,
    rules: z.array(evaluatorRuleRequestSchema).max(0, 'LLM Judge không dùng rules'),
    context: evaluatorValidationContextSchema,
  }),
  z.object({
    type: z.literal('MIXED'),
    config: mixedEvaluatorConfigSchema,
    rules: z.array(evaluatorRuleRequestSchema).min(1, 'Cần ít nhất một rule'),
    context: evaluatorValidationContextSchema,
  }),
]);
export type ValidateEvaluatorRequest = z.infer<typeof validateEvaluatorRequestSchema>;

export const updateEvaluatorRequestSchema = z.object({
  name: z.string().min(1, 'Vui lòng nhập tên Evaluator').max(160, 'Tên tối đa 160 ký tự'),
});
export type UpdateEvaluatorRequest = z.infer<typeof updateEvaluatorRequestSchema>;

export const evaluatorVersionSummarySchema = z.object({
  id: z.string().uuid(),
  version: z.number(),
  createdAt: z.string(),
  createdByUser: userSummarySchema.nullable().optional(),
});
export type EvaluatorVersionSummaryResponse = z.infer<typeof evaluatorVersionSummarySchema>;

export const evaluatorResponseSchema = z.object({
  id: z.string().uuid(),
  projectId: z.string().uuid(),
  name: z.string(),
  type: evaluatorTypeSchema,
  latestVersion: evaluatorVersionSummarySchema.nullable().optional(),
  createdBy: z.string().uuid(),
  createdByUser: userSummarySchema.nullable().optional(),
  createdAt: z.string(),
  updatedAt: z.string(),
});
export type EvaluatorResponse = z.infer<typeof evaluatorResponseSchema>;

export const evaluatorCollectionSchema = z.object({
  items: z.array(evaluatorResponseSchema),
});

export const evaluatorRuleResponseSchema = z.object({
  id: z.number(),
  fieldSource: z.string(),
  operator: evaluatorOperatorSchema,
  expectedValue: evaluatorExpectedValueSchema,
  weight: z.number().nullable().optional(),
  required: z.boolean(),
  orderIndex: z.number(),
  config: z.record(z.string(), z.unknown()),
});
export type EvaluatorRuleResponse = z.infer<typeof evaluatorRuleResponseSchema>;

export const evaluatorVersionResponseSchema = z.object({
  id: z.string().uuid(),
  evaluatorId: z.string().uuid(),
  projectId: z.string().uuid(),
  name: z.string(),
  type: evaluatorTypeSchema,
  version: z.number(),
  config: z.record(z.string(), z.unknown()),
  rules: z.array(evaluatorRuleResponseSchema),
  createdBy: z.string().uuid(),
  createdByUser: userSummarySchema.nullable().optional(),
  createdAt: z.string(),
});
export type EvaluatorVersionResponse = z.infer<typeof evaluatorVersionResponseSchema>;

export const evaluatorVersionCollectionSchema = z.object({
  items: z.array(evaluatorVersionResponseSchema),
});

export const evaluatorDatasetVariableSchema = z.object({
  path: z.string(),
  name: z.string(),
  displayName: z.string(),
  dataType: z.string(),
  required: z.boolean(),
});

export const evaluatorExtractedFieldSchema = z.object({
  fieldSource: z.string(),
  label: z.string(),
  dataType: z.string(),
  jsonPath: z.string(),
});

export const evaluatorResponseFieldSchema = z.object({
  fieldSource: z.string(),
  label: z.string(),
  dataType: z.string(),
});

export const evaluatorFieldSourceResponseSchema = z.object({
  datasetVariables: z.array(evaluatorDatasetVariableSchema),
  extractedFields: z.array(evaluatorExtractedFieldSchema),
  responseFields: z.array(evaluatorResponseFieldSchema),
});
export type EvaluatorFieldSourceResponse = z.infer<typeof evaluatorFieldSourceResponseSchema>;

export const evaluatorOperatorResponseSchema = z.object({
  operator: evaluatorOperatorSchema,
  label: z.string(),
  expectedValueRequired: z.boolean(),
  compatibleDataTypes: z.array(z.string()),
  description: z.string(),
});
export type EvaluatorOperatorResponse = z.infer<typeof evaluatorOperatorResponseSchema>;

export const evaluatorOperatorCollectionSchema = z.object({
  items: z.array(evaluatorOperatorResponseSchema),
});

export const evaluatorValidationResponseSchema = z.object({
  valid: z.boolean(),
  warnings: z.array(z.object({ code: z.string(), message: z.string() })),
  normalized: z.record(z.string(), z.unknown()),
});
export type EvaluatorValidationResponse = z.infer<typeof evaluatorValidationResponseSchema>;
