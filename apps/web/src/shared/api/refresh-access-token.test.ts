import { describe, expect, it, vi } from 'vitest';

import { createRefreshAccessToken } from '@/shared/api/refresh-access-token';
import type { CsrfTokenManager } from '@/shared/api/csrf';

describe('refreshAccessToken', () => {
  it('rotates through the HttpOnly cookie and returns a validated access token', async () => {
    const csrf: CsrfTokenManager = {
      getToken: vi.fn<() => Promise<string>>().mockResolvedValue('csrf-token'),
      refreshToken: vi.fn<() => Promise<string>>().mockResolvedValue('csrf-token'),
    };
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValue(
        new Response(
          JSON.stringify({ accessToken: 'fresh-token', tokenType: 'Bearer', expiresIn: 900 }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        ),
      );
    const refreshAccessToken = createRefreshAccessToken({
      baseUrl: 'http://localhost:8080',
      csrf,
      fetch: fetchMock,
    });

    await expect(refreshAccessToken()).resolves.toBe('fresh-token');
    expect(fetchMock).toHaveBeenCalledWith('http://localhost:8080/api/v1/auth/refresh', {
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'X-XSRF-TOKEN': 'csrf-token',
      },
      method: 'POST',
    });
  });
});
