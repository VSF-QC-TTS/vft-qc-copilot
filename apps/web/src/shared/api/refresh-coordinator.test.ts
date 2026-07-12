import { describe, expect, it, vi } from 'vitest';

import { createRefreshCoordinator } from '@/shared/api/refresh-coordinator';

describe('refreshCoordinator', () => {
  it('shares one refresh across concurrent callers', async () => {
    let resolveRefresh: ((token: string) => void) | undefined;
    const refreshAction = vi.fn<() => Promise<string>>(
      () =>
        new Promise<string>((resolve) => {
          resolveRefresh = resolve;
        }),
    );
    const coordinator = createRefreshCoordinator({ refreshAction });

    const results = [coordinator.refresh(), coordinator.refresh(), coordinator.refresh()];
    resolveRefresh?.('fresh-token');

    await expect(Promise.all(results)).resolves.toEqual([
      'fresh-token',
      'fresh-token',
      'fresh-token',
    ]);
    expect(refreshAction).toHaveBeenCalledOnce();
  });

  it('notifies once when a shared refresh fails', async () => {
    const failure = new Error('refresh failed');
    const onFailure = vi.fn<() => void>();
    const coordinator = createRefreshCoordinator({
      refreshAction: vi.fn<() => Promise<string>>().mockRejectedValue(failure),
      onFailure,
    });

    const results = await Promise.allSettled([coordinator.refresh(), coordinator.refresh()]);

    expect(results).toEqual([
      { status: 'rejected', reason: failure },
      { status: 'rejected', reason: failure },
    ]);
    expect(onFailure).toHaveBeenCalledOnce();
  });
});
