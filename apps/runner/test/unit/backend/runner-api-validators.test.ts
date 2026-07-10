import { describe, expect, it } from 'vitest';

import {
  RunnerRuntimeSecretsRequestSchema,
  RunnerTestRunConfigResponseSchema,
} from '../../../src/backend/dto/runner-api.dto.js';

const uuid = '27a47393-169d-4f01-92a0-59cf2dc10f8a';

const validConfigFixture = {
  testRunId: uuid,
  projectId: 'f559d1e9-0c5f-4ce3-8906-30a15bb02c7a',
  runType: 'SINGLE_TARGET',
  status: 'RUNNING',
  datasetId: 'f538afc3-f5df-4a5e-9d20-86e76e6999be',
  datasetVersionId: '89737237-f57c-4411-a013-1566748dc6d7',
  datasetSchemaId: '068283ff-7567-4b55-af35-f15d6e454c2f',
  datasetSchemaVersion: 1,
  progressTotal: 1,
  page: 0,
  size: 100,
  hasNext: false,
  domainSnapshot: { domain: 'sales' },
  promptfooSnapshot: { providers: [] },
  targets: [
    {
      id: 'ec3ab814-9650-4f99-a05b-83291db7e3f5',
      targetType: 'CHATBOT_TARGET',
      chatbotTargetId: '08151f5d-9565-4bda-83e6-86d56dcc6abc',
      providerId: null,
      model: null,
      name: 'Sales chatbot',
      orderIndex: 0,
      config: { endpoint: 'https://example.test/chat' },
    },
  ],
  evaluators: [
    {
      id: 1,
      evaluatorId: 'cc673043-2959-4f02-b6e3-379bdca3645b',
      evaluatorVersionId: 'ed767714-fdad-4c89-a972-29377d775ec3',
      evaluatorType: 'ASSERTION',
      version: 1,
      orderIndex: 0,
      config: { metric: 'answer' },
      rules: [{ operator: 'contains', value: 'VinFast' }],
    },
  ],
  rows: [
    {
      id: 1,
      rowIndex: 0,
      cells: { question: 'Hello' },
      revision: 1,
      rowHash: 'row-hash-1',
    },
  ],
};

describe('runner API validators', () => {
  it('parses a valid test run config fixture', () => {
    const parsed = RunnerTestRunConfigResponseSchema.parse(validConfigFixture);

    expect(parsed.testRunId).toBe(uuid);
    expect(parsed.targets[0]?.targetType).toBe('CHATBOT_TARGET');
    expect(parsed.hasNext).toBe(false);
  });

  it('rejects invalid enum values loudly', () => {
    const invalidConfig = { ...validConfigFixture, status: 'PENDING' };

    expect(() => RunnerTestRunConfigResponseSchema.parse(invalidConfig)).toThrow();
  });

  it('rejects missing required fields loudly', () => {
    const invalidConfig = { ...validConfigFixture, testRunId: undefined };

    expect(() => RunnerTestRunConfigResponseSchema.parse(invalidConfig)).toThrow();
  });

  it('requires at least one runtime secret id', () => {
    expect(() => RunnerRuntimeSecretsRequestSchema.parse({ secretIds: [] })).toThrow();
  });
});
