import { beforeEach, describe, expect, it, vi } from 'vitest';

import { createCsrfTokenManager, requiresCsrf } from '@/shared/api/csrf';

describe('csrf', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=; Max-Age=0; Path=/';
  });

  it.each(['POST', 'PUT', 'PATCH', 'DELETE'])('requires a token for %s', (method) => {
    expect(requiresCsrf(method)).toBe(true);
  });

  it('does not require a token for GET', () => {
    expect(requiresCsrf('GET')).toBe(false);
  });

  it('initializes the cookie once and reuses its decoded value', async () => {
    const fetchMock = vi.fn<typeof fetch>().mockImplementation(async () => {
      document.cookie = 'XSRF-TOKEN=csrf%20token; Path=/';
      return new Response('{}', { status: 200, headers: { 'Content-Type': 'application/json' } });
    });
    const manager = createCsrfTokenManager({
      baseUrl: 'http://localhost:8080',
      fetch: fetchMock,
    });

    await expect(manager.getToken()).resolves.toBe('csrf token');
    await expect(manager.getToken()).resolves.toBe('csrf token');

    expect(fetchMock).toHaveBeenCalledOnce();
    expect(fetchMock).toHaveBeenCalledWith('http://localhost:8080/api/v1/auth/csrf', {
      credentials: 'include',
      headers: { Accept: 'application/json' },
      method: 'GET',
    });
  });
});
