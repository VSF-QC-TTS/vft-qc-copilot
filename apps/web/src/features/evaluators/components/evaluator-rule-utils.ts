import type {
  CreateEvaluatorRequest,
  CreateEvaluatorVersionRequest,
  EvaluatorFieldSourceResponse,
  EvaluatorOperator,
  EvaluatorRuleRequest,
  EvaluatorRuleResponse,
  EvaluatorType,
  EvaluatorVersionResponse,
  LlmJudgeConfig,
  MixedEvaluatorConfig,
  RuleBasedConfig,
  ValidateEvaluatorRequest,
} from '@/features/evaluators/types/evaluator';
import { llmJudgeConfigSchema, ruleBasedConfigSchema } from '@/features/evaluators/types/evaluator';

export type ExpectedValueKind = 'literal' | 'datasetVariable' | 'number' | 'boolean' | 'json';

export interface RuleDraft {
  fieldSource: string;
  operator: EvaluatorOperator;
  expectedKind: ExpectedValueKind;
  expectedRaw: string;
  weight: string;
  required: boolean;
}

export interface LlmJudgeDraft {
  rubric: string;
  inputTemplate: string;
  threshold: string;
  scoreMin: string;
  scoreMax: string;
  ruleThreshold: string;
  defaultJudgeProviderId: string;
  defaultJudgeModel: string;
}

export interface FieldSourceOption {
  value: string;
  label: string;
  dataType: string;
}

export const ruleBasedConfig = {
  ruleBased: {
    aggregation: 'ALL_PASS',
    passCondition: {
      type: 'ALL_RULES_PASS',
    },
  },
} satisfies RuleBasedConfig;

export function mixedRuleBasedConfig(threshold: number): RuleBasedConfig {
  return {
    ruleBased: {
      aggregation: 'ALL_REQUIRED_AND_WEIGHTED_THRESHOLD',
      passCondition: {
        type: 'SCORE_AT_LEAST_THRESHOLD',
        threshold,
      },
    },
  };
}

export function emptyRuleDraft(fieldSource = 'response.statusCode'): RuleDraft {
  return {
    fieldSource,
    operator: 'NUMERIC_EQUALS',
    expectedKind: 'number',
    expectedRaw: '200',
    weight: '1',
    required: true,
  };
}

export function emptyLlmJudgeDraft(): LlmJudgeDraft {
  return {
    rubric:
      'Grade correctness, completeness, hallucination risk and clarity. Return a score from 0 to 1.',
    inputTemplate: 'Question: {{question}}\nExpected: {{expected}}\nActual: {{actual}}',
    threshold: '0.7',
    scoreMin: '0',
    scoreMax: '1',
    ruleThreshold: '0.7',
    defaultJudgeProviderId: '',
    defaultJudgeModel: '',
  };
}

export function fieldSourceOptions(sources: EvaluatorFieldSourceResponse | undefined) {
  if (!sources) return [];
  const datasetVariables = sources.datasetVariables.map((variable) => ({
    value: `dataset:${variable.path}`,
    label: `Dataset · ${variable.displayName || variable.name}`,
    dataType: variable.dataType,
  }));
  const extractedFields = sources.extractedFields.map((field) => ({
    value: field.fieldSource,
    label: `Extracted · ${field.label}`,
    dataType: field.dataType,
  }));
  const responseFields = sources.responseFields.map((field) => ({
    value: field.fieldSource,
    label: `Response · ${field.label}`,
    dataType: field.dataType,
  }));
  return [...responseFields, ...datasetVariables, ...extractedFields];
}

export function expectedValueRequired(operator: EvaluatorOperator) {
  return !['IS_EMPTY', 'IS_NOT_EMPTY', 'JSON_PATH_EXISTS'].includes(operator);
}

export function buildRuleRequest(draft: RuleDraft, orderIndex: number): EvaluatorRuleRequest {
  return {
    fieldSource: draft.fieldSource,
    operator: draft.operator,
    ...(expectedValueRequired(draft.operator)
      ? { expectedValue: expectedValueFromDraft(draft) }
      : {}),
    weight: Number(draft.weight || 1),
    required: draft.required,
    orderIndex,
    config: {},
  };
}

export function buildCreateEvaluatorRequest(
  name: string,
  rules: RuleDraft[],
  type: EvaluatorType = 'RULE_BASED',
  llmJudge: LlmJudgeDraft = emptyLlmJudgeDraft(),
): CreateEvaluatorRequest {
  const trimmedName = name.trim();
  if (type === 'LLM_JUDGE') {
    return {
      name: trimmedName,
      type: 'LLM_JUDGE',
      config: buildLlmJudgeConfig(llmJudge),
      rules: [],
    };
  }
  if (type === 'MIXED') {
    return {
      name: trimmedName,
      type: 'MIXED',
      config: buildMixedEvaluatorConfig(llmJudge),
      rules: buildRulesForType(type, rules),
    };
  }
  return {
    name: trimmedName,
    type: 'RULE_BASED',
    config: ruleBasedConfig,
    rules: buildRulesForType(type, rules),
  };
}

export function buildCreateVersionRequest(
  rules: RuleDraft[],
  type: EvaluatorType = 'RULE_BASED',
  llmJudge: LlmJudgeDraft = emptyLlmJudgeDraft(),
): CreateEvaluatorVersionRequest {
  if (type === 'LLM_JUDGE') {
    return {
      config: buildLlmJudgeConfig(llmJudge),
      rules: [],
    };
  }
  if (type === 'MIXED') {
    return {
      config: buildMixedEvaluatorConfig(llmJudge),
      rules: buildRulesForType(type, rules),
    };
  }
  return {
    config: ruleBasedConfig,
    rules: buildRulesForType(type, rules),
  };
}

export function buildValidateEvaluatorRequest(
  rules: RuleDraft[],
  type: EvaluatorType = 'RULE_BASED',
  llmJudge: LlmJudgeDraft = emptyLlmJudgeDraft(),
): ValidateEvaluatorRequest {
  if (type === 'LLM_JUDGE') {
    return {
      type: 'LLM_JUDGE',
      config: buildLlmJudgeConfig(llmJudge),
      rules: [],
    };
  }
  if (type === 'MIXED') {
    return {
      type: 'MIXED',
      config: buildMixedEvaluatorConfig(llmJudge),
      rules: buildRulesForType(type, rules),
    };
  }
  return {
    type: 'RULE_BASED',
    config: ruleBasedConfig,
    rules: buildRulesForType(type, rules),
  };
}

export function ruleDraftsFromVersion(version: EvaluatorVersionResponse): RuleDraft[] {
  return version.rules
    .slice()
    .sort((left, right) => left.orderIndex - right.orderIndex)
    .map(ruleDraftFromResponse);
}

export function llmJudgeDraftFromVersion(version: EvaluatorVersionResponse): LlmJudgeDraft {
  const fallback = emptyLlmJudgeDraft();
  const parsed = llmJudgeConfigSchema.safeParse(version.config);
  if (!parsed.success) return fallback;
  const config = parsed.data.llmJudge;
  return {
    rubric: config.rubric,
    inputTemplate: config.inputTemplate,
    threshold: String(config.threshold),
    scoreMin: String(config.scoreScale.min),
    scoreMax: String(config.scoreScale.max),
    ruleThreshold: ruleThresholdFromVersion(version),
    defaultJudgeProviderId: config.defaultJudgeProviderId ?? '',
    defaultJudgeModel: config.defaultJudgeModel ?? '',
  };
}

export function ruleSummary(rule: EvaluatorRuleResponse | EvaluatorRuleRequest) {
  const expected = expectedValueRequired(rule.operator)
    ? ` ${expectedValueLabel(rule.expectedValue)}`
    : '';
  const required = rule.required ? ' · bắt buộc' : ' · tùy chọn';
  const weight = rule.weight ? ` · weight ${String(rule.weight)}` : '';
  return `${rule.fieldSource} ${operatorLabel(rule.operator)}${expected}${required}${weight}`;
}

export function llmJudgeSummary(config: Record<string, unknown>) {
  const parsed = llmJudgeConfigSchema.safeParse(config);
  if (!parsed.success) return 'Chưa có LLM Judge config hợp lệ';
  const provider = parsed.data.llmJudge.defaultJudgeProviderId ? ' · có provider mặc định' : '';
  return `Threshold ${String(parsed.data.llmJudge.threshold)} · ${parsed.data.llmJudge.outputSchema}${provider}`;
}

export function operatorLabel(operator: EvaluatorOperator) {
  const labels: Record<EvaluatorOperator, string> = {
    EQUALS: 'equals',
    NOT_EQUALS: 'not equals',
    CONTAINS: 'contains',
    NOT_CONTAINS: 'not contains',
    REGEX_MATCH: 'regex match',
    STARTS_WITH: 'starts with',
    ENDS_WITH: 'ends with',
    JSON_PATH_EXISTS: 'JSON path exists',
    NUMERIC_EQUALS: 'numeric equals',
    NUMERIC_GT: 'greater than',
    NUMERIC_GTE: 'greater than or equal',
    NUMERIC_LT: 'less than',
    NUMERIC_LTE: 'less than or equal',
    IS_EMPTY: 'is empty',
    IS_NOT_EMPTY: 'is not empty',
  };
  return labels[operator];
}

function expectedValueFromDraft(draft: RuleDraft) {
  if (draft.expectedKind === 'number') {
    return { type: 'number', value: Number(draft.expectedRaw) };
  }
  if (draft.expectedKind === 'boolean') {
    return { type: 'boolean', value: draft.expectedRaw === 'true' };
  }
  if (draft.expectedKind === 'datasetVariable') {
    return { type: 'datasetVariable', path: draft.expectedRaw };
  }
  if (draft.expectedKind === 'json') {
    return { type: 'json', value: JSON.parse(draft.expectedRaw) };
  }
  return { type: 'literal', value: draft.expectedRaw };
}

function buildMixedEvaluatorConfig(llmJudge: LlmJudgeDraft): MixedEvaluatorConfig {
  const llmConfig = buildLlmJudgeConfig(llmJudge);
  return {
    ...mixedRuleBasedConfig(probabilityFromText(llmJudge.ruleThreshold, 0.7)),
    ...llmConfig,
  };
}

function buildLlmJudgeConfig(draft: LlmJudgeDraft): LlmJudgeConfig {
  return {
    llmJudge: {
      rubric: draft.rubric.trim(),
      scoreScale: {
        min: Number(draft.scoreMin || 0),
        max: Number(draft.scoreMax || 1),
      },
      threshold: probabilityFromText(draft.threshold, 0.7),
      passCondition: 'SCORE_AT_LEAST_THRESHOLD',
      inputTemplate: draft.inputTemplate.trim(),
      outputSchema: 'VFQC_JUDGE_V1',
      ...(draft.defaultJudgeProviderId
        ? { defaultJudgeProviderId: draft.defaultJudgeProviderId }
        : {}),
      ...(draft.defaultJudgeModel.trim()
        ? { defaultJudgeModel: draft.defaultJudgeModel.trim() }
        : {}),
    },
  };
}

function buildRulesForType(type: EvaluatorType, rules: RuleDraft[]) {
  if (type === 'LLM_JUDGE') return [];
  return rules.map((rule, index) => buildRuleRequest(rule, index + 1));
}

function ruleDraftFromResponse(rule: EvaluatorRuleResponse): RuleDraft {
  const kind = expectedKindFromValue(rule.expectedValue);
  return {
    fieldSource: rule.fieldSource,
    operator: rule.operator,
    expectedKind: kind,
    expectedRaw: expectedRawFromValue(rule.expectedValue, kind),
    weight: String(rule.weight ?? 1),
    required: rule.required,
  };
}

function expectedKindFromValue(value: Record<string, unknown> | undefined): ExpectedValueKind {
  const type = value?.type;
  if (type === 'datasetVariable') return 'datasetVariable';
  if (type === 'number') return 'number';
  if (type === 'boolean') return 'boolean';
  if (type === 'json') return 'json';
  return 'literal';
}

function expectedRawFromValue(value: Record<string, unknown> | undefined, kind: ExpectedValueKind) {
  if (!value) return '';
  if (kind === 'datasetVariable') return scalarToText(value.path);
  if (kind === 'json') return JSON.stringify(value.value ?? {});
  if (kind === 'boolean') return String(Boolean(value.value));
  return scalarToText(value.value);
}

function ruleThresholdFromVersion(version: EvaluatorVersionResponse) {
  const parsed = ruleBasedConfigSchema.safeParse(version.config);
  if (!parsed.success) return emptyLlmJudgeDraft().ruleThreshold;
  if (parsed.data.ruleBased.aggregation !== 'ALL_REQUIRED_AND_WEIGHTED_THRESHOLD') {
    return emptyLlmJudgeDraft().ruleThreshold;
  }
  return String(parsed.data.ruleBased.passCondition.threshold);
}

function expectedValueLabel(value: Record<string, unknown> | undefined) {
  if (!value) return '';
  const type = value.type;
  if (type === 'datasetVariable') return scalarToText(value.path);
  if (type === 'json') return JSON.stringify(value.value ?? {});
  return scalarToText(value.value);
}

function scalarToText(value: unknown) {
  if (value === null || value === undefined) return '';
  if (typeof value === 'string') return value;
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  return JSON.stringify(value);
}

function probabilityFromText(value: string, fallback: number) {
  const number = Number(value);
  if (!Number.isFinite(number)) return fallback;
  return Math.min(1, Math.max(0, number));
}
