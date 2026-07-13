import { beforeEach, describe, expect, it, vi } from 'vitest';

import { getTestCaseResult, getTestRunResults } from '@/features/test-runs/api/test-runs.api';
import { testRunResultPageResponseSchema } from '@/features/test-runs/types/test-run';

const requestJsonMock = vi.hoisted(() =>
  vi.fn<(path: string, schema: unknown, init?: RequestInit) => Promise<{ data: unknown }>>(),
);

vi.mock('@/shared/api/api', () => ({
  apiClient: {
    requestJson: requestJsonMock,
  },
}));

const TEST_RUN_ID = '11111111-1111-4111-8111-111111111111';

describe('test runs results API', () => {
  beforeEach(() => {
    requestJsonMock.mockReset();
  });

  it('maps result list mode and page state to backend query params', async () => {
    const signal = new AbortController().signal;
    const page = { items: [result()], page: { limit: 20, nextCursor: null, hasMore: false } };
    requestJsonMock.mockResolvedValueOnce({ data: page });

    await expect(
      getTestRunResults(TEST_RUN_ID, { mode: 'MACHINE', page: 2, size: 20, signal }),
    ).resolves.toEqual(page);

    expect(requestJsonMock).toHaveBeenCalledWith(
      `/api/v1/test-runs/${TEST_RUN_ID}/results?mode=MACHINE&page=2&size=20`,
      expect.any(Object),
      { signal },
    );
  });

  it('maps result detail to the implemented backend endpoint', async () => {
    const signal = new AbortController().signal;
    const detail = result({ finalStatus: 'PASSED', finalScore: 0.95, finalPassed: true });
    requestJsonMock.mockResolvedValueOnce({ data: detail });

    await expect(getTestCaseResult(9122, 'FINAL', signal)).resolves.toEqual(detail);

    expect(requestJsonMock).toHaveBeenCalledWith(
      '/api/v1/test-case-results/9122?mode=FINAL',
      expect.any(Object),
      { signal },
    );
  });

  it('validates the backend PageInfo shape without total-page fields', () => {
    const valid = testRunResultPageResponseSchema.safeParse({
      items: [result()],
      page: { limit: 20, nextCursor: null, hasMore: true },
    });
    const invalid = testRunResultPageResponseSchema.safeParse({
      items: [result()],
      page: { number: 0, size: 20, totalPages: 10 },
    });

    expect(valid.success).toBe(true);
    expect(invalid.success).toBe(false);
  });
});

function result(patch: Partial<ReturnType<typeof baseResult>> = {}): ReturnType<typeof baseResult> {
  return { ...baseResult(), ...patch };
}

function baseResult() {
  return {
    id: 9122,
    testRunId: TEST_RUN_ID,
    testRunTargetId: '22222222-2222-4222-8222-222222222222',
    datasetRowId: 18001,
    machineStatus: 'FAILED',
    machineScore: 0.42,
    machinePassed: false,
    finalStatus: 'FAILED',
    finalScore: 0.42,
    finalPassed: false,
    errorCategory: null,
    request: { headers: { Authorization: 'Bearer ****' } },
    response: { body: { answer: 'VF 8 có hai phiên bản.' } },
    extractedOutput: { answer: 'VF 8 có hai phiên bản.' },
    gradingResult: { reason: 'Answer is incomplete.' },
    componentResults: {},
    tokenUsage: { totalTokens: 410 },
    latencyMs: 1200,
    machineSummary: 'Judge summary',
    review: null,
    createdAt: '2026-07-02T10:00:12+07:00',
    updatedAt: '2026-07-02T10:00:12+07:00',
  };
}
