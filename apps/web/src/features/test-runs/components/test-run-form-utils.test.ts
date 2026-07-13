import { describe, expect, it } from 'vitest';
import {
  activeRunDatasets,
  buildCreateTestRunRequest,
  buildRunPreflightRequest,
  validateRunSelection,
  type TestRunSelection,
} from '@/features/test-runs/components/test-run-form-utils';
import type { ChatbotTargetResponse } from '@/features/chatbot-targets/types/chatbot-target';
import type { DatasetResponse } from '@/features/datasets/types/dataset';
import type { EvaluatorResponse } from '@/features/evaluators/types/evaluator';
import type { ProviderResponse } from '@/features/providers/types/provider';

const PROJECT_ID = '22222222-2222-4222-8222-222222222222';
const ACTIVE_DATASET_ID = '11111111-1111-4111-8111-111111111111';
const TARGET_A_ID = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
const TARGET_B_ID = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
const PROVIDER_ID = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc';
const EVALUATOR_VERSION_ID = '99999999-9999-4999-8999-999999999999';

const datasets: DatasetResponse[] = [
  {
    id: ACTIVE_DATASET_ID,
    scope: 'PROJECT',
    projectId: PROJECT_ID,
    currentVersionId: '33333333-3333-4333-8333-333333333333',
    name: 'Dataset',
    description: null,
    createdBy: '44444444-4444-4444-8444-444444444444',
    createdByUser: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  },
  {
    id: '55555555-5555-4555-8555-555555555555',
    scope: 'PROJECT',
    projectId: PROJECT_ID,
    currentVersionId: null,
    name: 'Draft dataset',
    description: null,
    createdBy: '44444444-4444-4444-8444-444444444444',
    createdByUser: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  },
];

const targets: ChatbotTargetResponse[] = [
  target(TARGET_A_ID, 'Target A'),
  target(TARGET_B_ID, 'Target B'),
];

const providers: ProviderResponse[] = [
  {
    id: PROVIDER_ID,
    scope: 'WORKSPACE',
    ownerUserId: '44444444-4444-4444-8444-444444444444',
    ownerUser: {
      id: '44444444-4444-4444-8444-444444444444',
      email: 'owner@vinfast.vn',
      name: 'Owner',
    },
    workspaceId: 'dddddddd-dddd-4ddd-8ddd-dddddddddddd',
    name: 'OpenAI',
    providerType: 'OPENAI_COMPATIBLE',
    secret: {
      id: 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee',
      scope: 'WORKSPACE',
      ownerUserId: '44444444-4444-4444-8444-444444444444',
      ownerUser: {
        id: '44444444-4444-4444-8444-444444444444',
        email: 'owner@vinfast.vn',
        name: 'Owner',
      },
      workspaceId: 'dddddddd-dddd-4ddd-8ddd-dddddddddddd',
      name: 'Secret',
      status: 'ACTIVE',
    },
    baseUrl: null,
    defaultModel: 'gpt-4.1-mini',
    availableModels: null,
    config: null,
    status: 'ACTIVE',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  },
];

const evaluators: EvaluatorResponse[] = [
  {
    id: 'ffffffff-ffff-4fff-8fff-ffffffffffff',
    projectId: PROJECT_ID,
    name: 'Evaluator',
    type: 'RULE_BASED',
    latestVersion: {
      id: EVALUATOR_VERSION_ID,
      version: 1,
      createdAt: '2026-01-01T00:00:00Z',
      createdByUser: null,
    },
    createdBy: '44444444-4444-4444-8444-444444444444',
    createdByUser: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  },
];

describe('test run form utils', () => {
  it('builds backend create and preflight payloads from selected resources', () => {
    const selection = baseSelection();

    expect(buildCreateTestRunRequest(targets, providers, evaluators, selection)).toEqual({
      datasetId: ACTIVE_DATASET_ID,
      runType: 'SINGLE_TARGET',
      targets: [
        {
          targetType: 'CHATBOT_TARGET',
          chatbotTargetId: TARGET_A_ID,
          name: 'Target A',
        },
      ],
      evaluators: [{ evaluatorVersionId: EVALUATOR_VERSION_ID }],
      options: { maxRows: 20, includePromptfooSnapshot: false },
    });

    expect(buildRunPreflightRequest(targets, providers, evaluators, selection)).not.toHaveProperty(
      'runType',
    );
  });

  it('validates single and comparison target counts before API calls', () => {
    expect(
      validateRunSelection(datasets, targets, providers, evaluators, {
        ...baseSelection(),
        runType: 'SINGLE_TARGET',
        chatbotTargetIds: [TARGET_A_ID, TARGET_B_ID],
      }),
    ).toContain('Single run cần đúng 1 target.');

    expect(
      validateRunSelection(datasets, targets, providers, evaluators, {
        ...baseSelection(),
        runType: 'COMPARISON',
        chatbotTargetIds: [TARGET_A_ID],
      }),
    ).toContain('Comparison run cần ít nhất 2 target.');
  });

  it('filters datasets without active versions for run authoring', () => {
    expect(activeRunDatasets(datasets)).toEqual([
      expect.objectContaining({ id: ACTIVE_DATASET_ID }),
    ]);
  });

  it('builds LLM baseline target requests with provider default model', () => {
    const request = buildCreateTestRunRequest(targets, providers, evaluators, {
      ...baseSelection(),
      runType: 'COMPARISON',
      chatbotTargetIds: [TARGET_A_ID],
      baselines: [{ providerId: PROVIDER_ID, model: '', enabled: true }],
    });

    expect(request.targets).toEqual([
      { targetType: 'CHATBOT_TARGET', chatbotTargetId: TARGET_A_ID, name: 'Target A' },
      {
        targetType: 'LLM_BASELINE',
        providerId: PROVIDER_ID,
        model: 'gpt-4.1-mini',
        name: 'OpenAI baseline',
      },
    ]);
  });
});

function baseSelection(): TestRunSelection {
  return {
    datasetId: ACTIVE_DATASET_ID,
    runType: 'SINGLE_TARGET',
    chatbotTargetIds: [TARGET_A_ID],
    baselines: [],
    evaluatorVersionIds: [EVALUATOR_VERSION_ID],
    maxRows: '20',
  };
}

function target(id: string, name: string): ChatbotTargetResponse {
  return {
    id,
    projectId: PROJECT_ID,
    name,
    description: null,
    rawCurlSanitized: null,
    requestConfig: null,
    responseExtractionConfig: { fields: [] },
    latestExampleId: null,
    status: 'ACTIVE',
    createdBy: '44444444-4444-4444-8444-444444444444',
    createdByUser: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  };
}
