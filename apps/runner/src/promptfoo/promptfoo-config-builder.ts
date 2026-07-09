import type { EvaluatorSnapshot, TargetSnapshot } from '../backend/dto/runner-api.dto.js';
import type { SensitiveDataMasker } from '../masking/sensitive-data-masker.js';
import type {
  PromptfooAdapterInput,
  PromptfooAssertionConfig,
  PromptfooConfigObject,
  PromptfooGeneratedConfig,
  PromptfooProviderConfig,
} from './promptfoo-adapter.js';

const SECRET_REF_PATTERN = /\$\{secret:([0-9a-fA-F-]{36})\}/gu;

export interface PromptfooConfigBuilder {
  build(input: PromptfooAdapterInput): PromptfooGeneratedConfig;
}

export interface PromptfooConfigBuilderOptions {
  readonly masker: SensitiveDataMasker;
}

export class DefaultPromptfooConfigBuilder implements PromptfooConfigBuilder {
  public constructor(private readonly options: PromptfooConfigBuilderOptions) {}

  public build(input: PromptfooAdapterInput): PromptfooGeneratedConfig {
    const referencedSecretIds = new Set<string>();
    const configObject: PromptfooConfigObject = {
      cache: false,
      providers: [...input.targets].sort(byOrderIndex).map((target) => this.mapTarget(target, input, referencedSecretIds)),
      tests: [...input.rows].sort((left, right) => left.rowIndex - right.rowIndex).map((row) => ({
        vars: row.cells,
        metadata: {
          datasetRowId: row.id,
          rowIndex: row.rowIndex,
          rowHash: row.rowHash,
        },
      })),
      defaultTest: {
        assert: [...input.evaluators].sort(byOrderIndex).flatMap((evaluator) => mapEvaluator(evaluator)),
      },
      metadata: {
        testRunId: input.testRunId,
        cacheDisabled: true,
      },
    };

    return {
      configObject,
      sanitizedPreview: this.options.masker.mask(configObject) as PromptfooConfigObject,
      referencedSecretIds: [...referencedSecretIds].sort(),
    };
  }

  private mapTarget(
    target: TargetSnapshot,
    input: PromptfooAdapterInput,
    referencedSecretIds: Set<string>,
  ): PromptfooProviderConfig {
    const mappedConfig = resolvePromptfooValue(mapDatasetVariables(target.config), input.runtimeSecretsById, referencedSecretIds);

    return {
      id: providerId(target),
      label: target.name,
      config: {
        ...mappedConfig,
        vfqc: {
          testRunTargetId: target.id,
          targetType: target.targetType,
          orderIndex: target.orderIndex,
        },
      },
    };
  }
}

function providerId(target: TargetSnapshot): string {
  if (target.targetType === 'CHATBOT_TARGET') {
    return `vfqc-chatbot:${target.id}`;
  }

  const providerIdValue = target.providerId ?? 'llm';
  const model = target.model ?? 'unknown-model';
  return `${providerIdValue}:${model}`;
}

function mapEvaluator(evaluator: EvaluatorSnapshot): readonly PromptfooAssertionConfig[] {
  if (evaluator.evaluatorType === 'RULE_BASED') {
    return mapRuleAssertions(evaluator);
  }
  if (evaluator.evaluatorType === 'LLM_JUDGE') {
    return mapJudgeAssertion(evaluator);
  }
  if (evaluator.evaluatorType === 'MIXED') {
    return [...mapRuleAssertions(evaluator), ...mapJudgeAssertion(evaluator)];
  }
  return [
    {
      type: 'vfqc-unsupported-evaluator',
      metadata: evaluatorMetadata(evaluator),
    },
  ];
}

function mapRuleAssertions(evaluator: EvaluatorSnapshot): readonly PromptfooAssertionConfig[] {
  return evaluator.rules.map((rule) => ({
    type: mapOperator(readString(rule, 'operator') ?? 'UNKNOWN'),
    value: rule.expectedValue ?? rule.expectedValueJson,
    metadata: {
      ...evaluatorMetadata(evaluator),
      fieldSource: readString(rule, 'fieldSource') ?? '',
      operator: readString(rule, 'operator') ?? 'UNKNOWN',
      required: readBoolean(rule, 'required') ?? false,
      weight: readNumber(rule, 'weight') ?? 1,
    },
  }));
}

function mapJudgeAssertion(evaluator: EvaluatorSnapshot): readonly PromptfooAssertionConfig[] {
  const rubric = readString(evaluator.config, 'rubric');
  if (rubric === undefined) {
    return [];
  }
  const provider = judgeProvider(evaluator.config);
  const assertion: PromptfooAssertionConfig = {
    type: 'llm-rubric',
    value: rubric,
    metadata: evaluatorMetadata(evaluator),
  };
  if (provider !== undefined) {
    return [{ ...assertion, provider }];
  }

  return [assertion];
}

function judgeProvider(config: Record<string, unknown>): string | undefined {
  const providerIdValue = readString(config, 'judgeProviderId');
  const model = readString(config, 'judgeModel');
  if (providerIdValue === undefined || model === undefined) {
    return undefined;
  }
  return `${providerIdValue}:${model}`;
}

function evaluatorMetadata(evaluator: EvaluatorSnapshot): Record<string, unknown> {
  return {
    evaluatorId: evaluator.evaluatorId,
    evaluatorVersionId: evaluator.evaluatorVersionId,
    evaluatorType: evaluator.evaluatorType,
    orderIndex: evaluator.orderIndex,
  };
}

function mapOperator(operator: string): string {
  const normalized = operator.toUpperCase();
  const mapping: Record<string, string> = {
    EQUALS: 'equals',
    NUMERIC_EQUALS: 'equals',
    CONTAINS: 'contains',
    NOT_CONTAINS: 'not-contains',
    REGEX_MATCH: 'regex',
    JSON_PATH_EXISTS: 'is-json',
    IS_EMPTY: 'is-empty',
    IS_NOT_EMPTY: 'is-not-empty',
  };
  return mapping[normalized] ?? 'vfqc-rule';
}

function mapDatasetVariables(value: unknown): unknown {
  if (typeof value === 'string') {
    return value.replace(/\$dataset\.([A-Za-z0-9_]+)/gu, '{{$1}}');
  }
  if (Array.isArray(value)) {
    return value.map((item) => mapDatasetVariables(item));
  }
  if (isRecord(value)) {
    return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, mapDatasetVariables(item)]));
  }
  return value;
}

function resolvePromptfooValue(value: unknown, runtimeSecretsById: Record<string, string>, referencedSecretIds: Set<string>): Record<string, unknown> {
  const resolved = resolveSecretRefs(value, runtimeSecretsById, referencedSecretIds);
  if (!isRecord(resolved)) {
    return {};
  }
  return { ...resolved };
}

function resolveSecretRefs(value: unknown, runtimeSecretsById: Record<string, string>, referencedSecretIds: Set<string>): unknown {
  if (typeof value === 'string') {
    return value.replace(SECRET_REF_PATTERN, (_match, secretId: string) => {
      referencedSecretIds.add(secretId);
      return runtimeSecretsById[secretId] ?? '';
    });
  }
  if (Array.isArray(value)) {
    return value.map((item) => resolveSecretRefs(item, runtimeSecretsById, referencedSecretIds));
  }
  if (isRecord(value)) {
    return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, resolveSecretRefs(item, runtimeSecretsById, referencedSecretIds)]));
  }
  return value;
}

function byOrderIndex(left: { readonly orderIndex: number }, right: { readonly orderIndex: number }): number {
  return left.orderIndex - right.orderIndex;
}

function readString(record: Record<string, unknown>, key: string): string | undefined {
  const value = record[key];
  return typeof value === 'string' && value.length > 0 ? value : undefined;
}

function readNumber(record: Record<string, unknown>, key: string): number | undefined {
  const value = record[key];
  return typeof value === 'number' ? value : undefined;
}

function readBoolean(record: Record<string, unknown>, key: string): boolean | undefined {
  const value = record[key];
  return typeof value === 'boolean' ? value : undefined;
}

function isRecord(value: unknown): value is Readonly<Record<string, unknown>> {
  return typeof value === 'object' && value !== null;
}
