import { describe, expect, it } from 'vitest';

import {
  buildCreateEvaluatorRequest,
  buildCreateVersionRequest,
  buildRuleRequest,
  emptyLlmJudgeDraft,
  expectedValueRequired,
  fieldSourceOptions,
  ruleBasedConfig,
  ruleDraftsFromVersion,
  ruleSummary,
} from '@/features/evaluators/components/evaluator-rule-utils';
import type {
  EvaluatorFieldSourceResponse,
  EvaluatorVersionResponse,
} from '@/features/evaluators/types/evaluator';

const fieldSources: EvaluatorFieldSourceResponse = {
  datasetVariables: [
    {
      path: '$dataset.expected',
      name: 'expected',
      displayName: 'Expected',
      dataType: 'TEXT',
      required: false,
    },
  ],
  extractedFields: [
    {
      fieldSource: 'extracted.answer',
      label: 'answer',
      dataType: 'TEXT',
      jsonPath: '$.answer',
    },
  ],
  responseFields: [
    { fieldSource: 'response.statusCode', label: 'HTTP status code', dataType: 'NUMBER' },
  ],
};

describe('evaluator rule utils', () => {
  it('maps backend field sources into safe rule options', () => {
    expect(fieldSourceOptions(fieldSources)).toEqual([
      { value: 'response.statusCode', label: 'Response · HTTP status code', dataType: 'NUMBER' },
      { value: 'dataset:$dataset.expected', label: 'Dataset · Expected', dataType: 'TEXT' },
      { value: 'extracted.answer', label: 'Extracted · answer', dataType: 'TEXT' },
    ]);
  });

  it('builds a numeric rule request matching backend validator requirements', () => {
    expect(
      buildRuleRequest(
        {
          fieldSource: 'response.statusCode',
          operator: 'NUMERIC_EQUALS',
          expectedKind: 'number',
          expectedRaw: '200',
          weight: '1',
          required: true,
        },
        1,
      ),
    ).toEqual({
      fieldSource: 'response.statusCode',
      operator: 'NUMERIC_EQUALS',
      expectedValue: { type: 'number', value: 200 },
      weight: 1,
      required: true,
      orderIndex: 1,
      config: {},
    });
  });

  it('omits expected value for optional-value operators', () => {
    expect(expectedValueRequired('IS_EMPTY')).toBe(false);
    expect(
      buildRuleRequest(
        {
          fieldSource: 'extracted.answer',
          operator: 'IS_EMPTY',
          expectedKind: 'literal',
          expectedRaw: 'ignored',
          weight: '2',
          required: false,
        },
        2,
      ),
    ).toEqual({
      fieldSource: 'extracted.answer',
      operator: 'IS_EMPTY',
      weight: 2,
      required: false,
      orderIndex: 2,
      config: {},
    });
  });

  it('builds create and version requests with immutable RULE_BASED config', () => {
    const rules = [
      {
        fieldSource: 'dataset:$dataset.expected',
        operator: 'CONTAINS' as const,
        expectedKind: 'literal' as const,
        expectedRaw: 'ok',
        weight: '1',
        required: true,
      },
    ];

    expect(buildCreateEvaluatorRequest(' Basic ', rules)).toEqual({
      name: 'Basic',
      type: 'RULE_BASED',
      config: ruleBasedConfig,
      rules: [
        {
          fieldSource: 'dataset:$dataset.expected',
          operator: 'CONTAINS',
          expectedValue: { type: 'literal', value: 'ok' },
          weight: 1,
          required: true,
          orderIndex: 1,
          config: {},
        },
      ],
    });
    expect(buildCreateVersionRequest(rules).config).toBe(ruleBasedConfig);
  });

  it('builds an LLM_JUDGE request without rule rows', () => {
    const llmJudge = {
      ...emptyLlmJudgeDraft(),
      rubric: 'Grade factual correctness.',
      inputTemplate: 'Actual: {{actual}}',
      defaultJudgeProviderId: '11111111-1111-4111-8111-111111111111',
      defaultJudgeModel: 'gpt-4.1-mini',
    };

    expect(buildCreateEvaluatorRequest(' Rubric ', [], 'LLM_JUDGE', llmJudge)).toEqual({
      name: 'Rubric',
      type: 'LLM_JUDGE',
      config: {
        llmJudge: {
          rubric: 'Grade factual correctness.',
          scoreScale: { min: 0, max: 1 },
          threshold: 0.7,
          passCondition: 'SCORE_AT_LEAST_THRESHOLD',
          inputTemplate: 'Actual: {{actual}}',
          outputSchema: 'VFQC_JUDGE_V1',
          defaultJudgeProviderId: '11111111-1111-4111-8111-111111111111',
          defaultJudgeModel: 'gpt-4.1-mini',
        },
      },
      rules: [],
    });
  });

  it('builds a MIXED request with rule aggregation and judge config', () => {
    const rules = [
      {
        fieldSource: 'response.statusCode',
        operator: 'NUMERIC_EQUALS' as const,
        expectedKind: 'number' as const,
        expectedRaw: '200',
        weight: '1',
        required: true,
      },
    ];
    const llmJudge = {
      ...emptyLlmJudgeDraft(),
      threshold: '0.8',
      ruleThreshold: '0.6',
    };

    expect(buildCreateVersionRequest(rules, 'MIXED', llmJudge)).toMatchObject({
      config: {
        ruleBased: {
          aggregation: 'ALL_REQUIRED_AND_WEIGHTED_THRESHOLD',
          passCondition: { type: 'SCORE_AT_LEAST_THRESHOLD', threshold: 0.6 },
        },
        llmJudge: {
          threshold: 0.8,
          passCondition: 'SCORE_AT_LEAST_THRESHOLD',
          outputSchema: 'VFQC_JUDGE_V1',
        },
      },
      rules: [
        {
          fieldSource: 'response.statusCode',
          operator: 'NUMERIC_EQUALS',
          expectedValue: { type: 'number', value: 200 },
        },
      ],
    });
  });

  it('converts version response into editable draft without mutating the version', () => {
    const version: EvaluatorVersionResponse = {
      id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
      evaluatorId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
      projectId: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
      name: 'Evaluator',
      type: 'RULE_BASED',
      version: 1,
      config: ruleBasedConfig,
      rules: [
        {
          id: 1,
          fieldSource: 'dataset:$dataset.expected',
          operator: 'CONTAINS',
          expectedValue: { type: 'datasetVariable', path: '$dataset.expected' },
          weight: 1,
          required: true,
          orderIndex: 1,
          config: {},
        },
      ],
      createdBy: 'dddddddd-dddd-4ddd-8ddd-dddddddddddd',
      createdByUser: null,
      createdAt: '2026-01-01T00:00:00Z',
    };

    expect(ruleDraftsFromVersion(version)).toEqual([
      {
        fieldSource: 'dataset:$dataset.expected',
        operator: 'CONTAINS',
        expectedKind: 'datasetVariable',
        expectedRaw: '$dataset.expected',
        weight: '1',
        required: true,
      },
    ]);
    const rule = version.rules.find((item) => item.id === 1);
    if (!rule) {
      throw new Error('Expected fixture rule');
    }
    expect(ruleSummary(rule)).toContain('contains $dataset.expected');
  });
});
