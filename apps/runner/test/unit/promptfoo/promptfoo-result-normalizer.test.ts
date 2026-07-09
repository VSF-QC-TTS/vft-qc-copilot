import { describe, expect, it } from 'vitest';

import type { PromptfooExecutor } from '../../../src/promptfoo/promptfoo-executor.js';
import { DefaultPromptfooResultNormalizer } from '../../../src/promptfoo/promptfoo-result-normalizer.js';
import {
  judgeParseErrorRawResult,
  successfulRawResult,
  targetTimeoutRawResult,
} from '../../fixtures/promptfoo/promptfoo-fixtures.js';

describe('DefaultPromptfooResultNormalizer', () => {
  it('maps target timeout to TIMEOUT testcase errors', () => {
    const normalizer = new DefaultPromptfooResultNormalizer();

    const results = normalizer.normalize(targetTimeoutRawResult());

    expect(results).toEqual([
      {
        testRunTargetId: 'c1350b87-c35c-40b2-bbb1-b6f8815720fd',
        datasetRowId: 101,
        status: 'TIMEOUT',
        errorCategory: 'TARGET_TIMEOUT',
        latencyMs: 30_000,
        machineSummary: 'Target request timed out',
      },
    ]);
  });

  it('maps judge parse errors to JUDGE_ERROR', () => {
    const normalizer = new DefaultPromptfooResultNormalizer();

    const results = normalizer.normalize(judgeParseErrorRawResult());

    expect(results).toEqual([
      {
        testRunTargetId: 'c1350b87-c35c-40b2-bbb1-b6f8815720fd',
        datasetRowId: 101,
        status: 'JUDGE_ERROR',
        errorCategory: 'JUDGE_PARSE_ERROR',
        machineSummary: 'Judge returned invalid JSON',
      },
    ]);
  });

  it('keeps the VFQC testcase result schema stable for successful results', () => {
    const normalizer = new DefaultPromptfooResultNormalizer();

    const results = normalizer.normalize(successfulRawResult());

    expect(results).toEqual([
      {
        testRunTargetId: 'c1350b87-c35c-40b2-bbb1-b6f8815720fd',
        datasetRowId: 101,
        status: 'PASSED',
        score: 0.9,
        passed: true,
        request: { body: { question: 'VinFast VF 8 warranty?' } },
        response: { body: { answer: 'Warranty information' } },
        gradingResult: { reason: 'Matched expected facts' },
        latencyMs: 321,
        machineSummary: 'Passed rule and judge checks.',
      },
    ]);
  });

  it('normalizes Promptfoo EvaluateSummary results', () => {
    const normalizer = new DefaultPromptfooResultNormalizer();

    const results = normalizer.normalize({
      version: 3,
      results: [
        {
          provider: { id: 'vfqc-chatbot:c1350b87-c35c-40b2-bbb1-b6f8815720fd' },
          testCase: {
            metadata: {
              datasetRowId: 101,
            },
          },
          success: false,
          score: 0.2,
          error: 'Target request failed',
          response: {
            output: { answer: 'not ok' },
            tokenUsage: { total: 11 },
          },
          gradingResult: { reason: 'Expected ok' },
          latencyMs: 123,
        },
      ],
      stats: { successes: 0, failures: 1, errors: 0 },
    });

    expect(results).toEqual([
      {
        testRunTargetId: 'c1350b87-c35c-40b2-bbb1-b6f8815720fd',
        datasetRowId: 101,
        status: 'ERROR',
        score: 0.2,
        passed: false,
        errorCategory: 'PROMPTFOO_ERROR',
        response: { output: { answer: 'not ok' }, tokenUsage: { total: 11 } },
        gradingResult: { reason: 'Expected ok' },
        tokenUsage: { total: 11 },
        latencyMs: 123,
        machineSummary: 'Target request failed',
      },
    ]);
  });

  it('keeps PromptfooExecutor as a mocked boundary for this slice', async () => {
    const executor = new FakePromptfooExecutor(successfulRawResult());

    const rawResult = await executor.execute({ cache: false });

    expect(rawResult).toEqual(successfulRawResult());
  });
});

class FakePromptfooExecutor implements PromptfooExecutor {
  public constructor(private readonly result: unknown) {}

  public async execute(_config: Record<string, unknown>): Promise<unknown> {
    return this.result;
  }
}
