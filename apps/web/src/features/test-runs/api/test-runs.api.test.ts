import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  getTestCaseResult,
  getTestRunComparisonMatrix,
  getTestRunResults,
  upsertTestCaseResultReview,
} from '@/features/test-runs/api/test-runs.api';
import {
  comparisonMatrixResponseSchema,
  testRunResultPageResponseSchema,
} from '@/features/test-runs/types/test-run';

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

  it('maps comparison matrix mode and page state to the server-driven endpoint', async () => {
    const signal = new AbortController().signal;
    const matrix = comparisonMatrix();
    requestJsonMock.mockResolvedValueOnce({ data: matrix });

    await expect(
      getTestRunComparisonMatrix(TEST_RUN_ID, {
        mode: 'FINAL',
        page: 1,
        size: 20,
        signal,
      }),
    ).resolves.toEqual(matrix);

    expect(requestJsonMock).toHaveBeenCalledWith(
      `/api/v1/test-runs/${TEST_RUN_ID}/comparison-matrix?mode=FINAL&page=1&size=20`,
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

  it('validates the implemented backend comparison matrix shape', () => {
    const valid = comparisonMatrixResponseSchema.safeParse(comparisonMatrix());
    const invalid = comparisonMatrixResponseSchema.safeParse({
      targets: comparisonMatrix().columns,
      rows: comparisonMatrix().rows,
      page: { limit: 20, nextCursor: null, hasMore: false },
    });

    expect(valid.success).toBe(true);
    expect(invalid.success).toBe(false);
  });

  it('saves a QC review without mutating machine result fields on the frontend', async () => {
    const review = {
      id: 77,
      testCaseResultId: 9122,
      reviewerId: '33333333-3333-4333-8333-333333333333',
      reviewStatus: 'OVERRIDDEN',
      overrideStatus: 'PASSED',
      overrideScore: 0.95,
      note: 'Backend returns sanitized note',
      createdAt: '2026-07-02T10:00:12+07:00',
      updatedAt: '2026-07-02T10:00:12+07:00',
    };
    requestJsonMock.mockResolvedValueOnce({ data: review });

    await expect(
      upsertTestCaseResultReview(9122, {
        reviewStatus: 'OVERRIDDEN',
        overrideStatus: 'PASSED',
        overrideScore: 0.95,
        note: 'Backend should sanitize this note',
      }),
    ).resolves.toEqual(review);

    expect(requestJsonMock).toHaveBeenCalledWith(
      '/api/v1/test-case-results/9122/review',
      expect.any(Object),
      {
        method: 'PUT',
        body: JSON.stringify({
          reviewStatus: 'OVERRIDDEN',
          overrideStatus: 'PASSED',
          overrideScore: 0.95,
          note: 'Backend should sanitize this note',
        }),
      },
    );
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

function comparisonMatrix() {
  return {
    columns: [
      {
        testRunTargetId: '22222222-2222-4222-8222-222222222222',
        name: 'Internal Chatbot',
        orderIndex: 0,
      },
      {
        testRunTargetId: '44444444-4444-4444-8444-444444444444',
        name: 'Baseline model',
        orderIndex: 1,
      },
    ],
    rows: [
      {
        datasetRowId: 18001,
        rowIndex: 0,
        cells: [
          {
            testRunTargetId: '22222222-2222-4222-8222-222222222222',
            resultId: 9122,
            finalStatus: 'FAILED',
            finalScore: 0.42,
            finalPassed: false,
            errorCategory: null,
            reviewed: true,
          },
          {
            testRunTargetId: '44444444-4444-4444-8444-444444444444',
            resultId: null,
            finalStatus: null,
            finalScore: null,
            finalPassed: null,
            errorCategory: null,
            reviewed: false,
          },
        ],
      },
    ],
    page: { limit: 20, nextCursor: null, hasMore: false },
  };
}
