import { z } from 'zod';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { createApiRuntime } from '@/shared/api/api-runtime';

describe('apiRuntime', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=csrf-token; Path=/';
  });

  it('refreshes through the cookie and replays an authenticated request', async () => {
    const fetchMock = vi.fn<typeof fetch>().mockImplementation(async (input, init) => {
      const url = input instanceof Request ? input.url : input instanceof URL ? input.href : input;
      if (url.endsWith('/api/v1/auth/refresh')) {
        return new Response(
          JSON.stringify({ accessToken: 'fresh-token', tokenType: 'Bearer', expiresIn: 900 }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        );
      }

      if (new Headers(init?.headers).get('Authorization') !== 'Bearer fresh-token') {
        return new Response(null, { status: 401 });
      }

      return new Response(JSON.stringify({ id: 7 }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    });
    const runtime = createApiRuntime({
      baseUrl: 'http://localhost:8080',
      fetch: fetchMock,
    });

    await expect(
      runtime.apiClient.requestJson('/api/v1/items/7', z.object({ id: z.number() })),
    ).resolves.toEqual({ data: { id: 7 } });
    expect(runtime.tokenStore.getAccessToken()).toBe('fresh-token');
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });
});
