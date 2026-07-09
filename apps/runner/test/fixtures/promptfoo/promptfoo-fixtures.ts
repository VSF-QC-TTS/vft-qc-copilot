import type { PromptfooAdapterInput } from '../../../src/promptfoo/promptfoo-adapter.js';

const testRunId = '27a47393-169d-4f01-92a0-59cf2dc10f8a';
const chatbotTargetId = 'c1350b87-c35c-40b2-bbb1-b6f8815720fd';
const baselineTargetId = '08151f5d-9565-4bda-83e6-86d56dcc6abc';
const evaluatorId = 'cc673043-2959-4f02-b6e3-379bdca3645b';
const evaluatorVersionId = 'ed767714-fdad-4c89-a972-29377d775ec3';
const secretId = '7e1e7b32-6875-4308-88d8-aa56b18ff7e8';

export const promptfooFakeSecret = 'vfqc_secret_1234567890abcdef';

export function singleChatbotInput(): PromptfooAdapterInput {
  return {
    testRunId,
    targets: [
      {
        id: chatbotTargetId,
        targetType: 'CHATBOT_TARGET',
        chatbotTargetId: '7a030eef-6b09-44ef-8c41-61ae5d72d461',
        providerId: null,
        model: null,
        name: 'Sales chatbot',
        orderIndex: 0,
        config: {
          method: 'POST',
          url: 'https://chatbot.example.test/message',
          headers: {
            Authorization: `Bearer \${secret:${secretId}}`,
          },
          body: {
            question: '$dataset.question',
          },
          timeoutMs: 30_000,
        },
      },
    ],
    evaluators: [ruleBasedEvaluator()],
    rows: [
      {
        id: 101,
        rowIndex: 0,
        cells: {
          question: 'VinFast VF 8 warranty?',
          expected: 'Warranty information',
        },
        revision: 1,
        rowHash: 'row-hash-101',
      },
    ],
    promptfooSnapshot: { cache: false },
    runtimeSecretsById: {
      [secretId]: promptfooFakeSecret,
    },
  };
}

export function comparisonInput(): PromptfooAdapterInput {
  const input = singleChatbotInput();
  return {
    ...input,
    targets: [
      ...input.targets,
      {
        id: baselineTargetId,
        targetType: 'LLM_BASELINE',
        chatbotTargetId: null,
        providerId: 'openai',
        model: 'gpt-4.1-mini',
        name: 'Baseline',
        orderIndex: 1,
        config: {
          prompt: '$dataset.question',
          temperature: 0,
        },
      },
    ],
  };
}

export function llmJudgeInput(): PromptfooAdapterInput {
  return {
    ...singleChatbotInput(),
    evaluators: [
      {
        id: 2,
        evaluatorId,
        evaluatorVersionId,
        evaluatorType: 'LLM_JUDGE',
        version: 1,
        orderIndex: 0,
        config: {
          rubric: 'Answer must match the expected warranty facts.',
          judgeProviderId: 'openai',
          judgeModel: 'gpt-4.1-mini',
        },
        rules: [],
      },
    ],
  };
}

export function mixedEvaluatorInput(): PromptfooAdapterInput {
  return {
    ...singleChatbotInput(),
    evaluators: [
      {
        ...ruleBasedEvaluator(),
        evaluatorType: 'MIXED',
        config: {
          rubric: 'Answer must be grounded and concise.',
          judgeProviderId: 'openai',
          judgeModel: 'gpt-4.1-mini',
          aggregation: 'all',
        },
      },
    ],
  };
}

export function targetTimeoutRawResult(): unknown {
  return {
    results: [
      {
        testRunTargetId: chatbotTargetId,
        datasetRowId: 101,
        status: 'timeout',
        error: 'Target request timed out',
        latencyMs: 30_000,
      },
    ],
    summary: { durationMs: 30_000 },
  };
}

export function judgeParseErrorRawResult(): unknown {
  return {
    results: [
      {
        testRunTargetId: chatbotTargetId,
        datasetRowId: 101,
        status: 'error',
        errorKind: 'judge_parse_error',
        error: 'Judge returned invalid JSON',
      },
    ],
  };
}

export function successfulRawResult(): unknown {
  return {
    results: [
      {
        testRunTargetId: chatbotTargetId,
        datasetRowId: 101,
        status: 'success',
        score: 0.9,
        passed: true,
        request: { body: { question: 'VinFast VF 8 warranty?' } },
        response: { body: { answer: 'Warranty information' } },
        gradingResult: { reason: 'Matched expected facts' },
        latencyMs: 321,
        machineSummary: 'Passed rule and judge checks.',
      },
    ],
  };
}

function ruleBasedEvaluator(): PromptfooAdapterInput['evaluators'][number] {
  return {
    id: 1,
    evaluatorId,
    evaluatorVersionId,
    evaluatorType: 'RULE_BASED',
    version: 1,
    orderIndex: 0,
    config: { mode: 'all' },
    rules: [
      {
        fieldSource: 'dataset:$dataset.expected',
        operator: 'CONTAINS',
        expectedValue: 'Warranty',
        weight: 1,
        required: true,
      },
    ],
  };
}
