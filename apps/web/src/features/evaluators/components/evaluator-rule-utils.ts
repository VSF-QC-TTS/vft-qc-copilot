import type {
  CreateEvaluatorRequest,
  CreateEvaluatorVersionRequest,
  EvaluatorFieldSourceResponse,
  EvaluatorOperator,
  EvaluatorRuleRequest,
  EvaluatorRuleResponse,
  EvaluatorVersionResponse,
  RuleBasedConfig,
  ValidateEvaluatorRequest,
} from '@/features/evaluators/types/evaluator';

export type ExpectedValueKind = 'literal' | 'datasetVariable' | 'number' | 'boolean' | 'json';

export interface RuleDraft {
  fieldSource: string;
  operator: EvaluatorOperator;
  expectedKind: ExpectedValueKind;
  expectedRaw: string;
  weight: string;
  required: boolean;
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
      type: 'ALL_RULE_PASS',
    },
  },
} satisfies RuleBasedConfig;

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
): CreateEvaluatorRequest {
  return {
    name: name.trim(),
    type: 'RULE_BASED',
    config: ruleBasedConfig,
    rules: rules.map((rule, index) => buildRuleRequest(rule, index + 1)),
  };
}

export function buildCreateVersionRequest(rules: RuleDraft[]): CreateEvaluatorVersionRequest {
  return {
    config: ruleBasedConfig,
    rules: rules.map((rule, index) => buildRuleRequest(rule, index + 1)),
  };
}

export function buildValidateEvaluatorRequest(rules: RuleDraft[]): ValidateEvaluatorRequest {
  return {
    type: 'RULE_BASED',
    config: ruleBasedConfig,
    rules: rules.map((rule, index) => buildRuleRequest(rule, index + 1)),
  };
}

export function ruleDraftsFromVersion(version: EvaluatorVersionResponse): RuleDraft[] {
  return version.rules
    .slice()
    .sort((left, right) => left.orderIndex - right.orderIndex)
    .map(ruleDraftFromResponse);
}

export function ruleSummary(rule: EvaluatorRuleResponse | EvaluatorRuleRequest) {
  const expected = expectedValueRequired(rule.operator)
    ? ` ${expectedValueLabel(rule.expectedValue)}`
    : '';
  const required = rule.required ? ' · bắt buộc' : ' · tùy chọn';
  const weight = rule.weight ? ` · weight ${String(rule.weight)}` : '';
  return `${rule.fieldSource} ${operatorLabel(rule.operator)}${expected}${required}${weight}`;
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
    return { type: 'json', value: JSON.parse(draft.expectedRaw) as unknown };
  }
  return { type: 'literal', value: draft.expectedRaw };
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
