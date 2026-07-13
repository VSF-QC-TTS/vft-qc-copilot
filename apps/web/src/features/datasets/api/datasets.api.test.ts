import { beforeEach, describe, expect, it, vi } from 'vitest';

import { getDatasetRows } from '@/features/datasets/api/datasets.api';

const requestJsonMock = vi.hoisted(() =>
  vi.fn<(path: string, schema: unknown, init?: RequestInit) => Promise<{ data: unknown }>>(),
);

vi.mock('@/shared/api/api', () => ({
  apiClient: {
    requestJson: requestJsonMock,
  },
}));

describe('datasets API', () => {
  beforeEach(() => {
    requestJsonMock.mockReset();
  });

  it('requests dataset rows with explicit backend pagination params', async () => {
    const signal = new AbortController().signal;
    const page = {
      items: [
        {
          id: 1,
          datasetVersionId: '11111111-1111-4111-8111-111111111111',
          rowIndex: 20,
          cells: { question: 'Q21', expected: 'A21' },
          revision: 0,
          createdAt: '2026-01-01T00:00:00Z',
          updatedAt: '2026-01-01T00:00:00Z',
        },
      ],
      page: {
        limit: 20,
        nextCursor: null,
        hasMore: true,
      },
    };
    requestJsonMock.mockResolvedValueOnce({ data: page });

    await expect(
      getDatasetRows('11111111-1111-4111-8111-111111111111', {
        page: 1,
        size: 20,
        signal,
      }),
    ).resolves.toEqual(page);

    expect(requestJsonMock).toHaveBeenCalledWith(
      '/api/v1/dataset-versions/11111111-1111-4111-8111-111111111111/rows?page=1&size=20',
      expect.any(Object),
      { signal },
    );
  });
});
