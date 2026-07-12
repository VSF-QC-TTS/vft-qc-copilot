import { z } from 'zod';
import { describe, expect, it, vi } from 'vitest';

import { ApiError } from '@/shared/api/api-error';
import { createApiClient } from '@/shared/api/api-client';
import type { CsrfTokenManager } from '@/shared/api/csrf';
import { createRefreshCoordinator } from '@/shared/api/refresh-coordinator';
import { createTokenStore } from '@/shared/api/token-store';

const itemSchema = z.object({ id: z.number(), name: z.string() });

function createCsrfManager(token = 'csrf-token') {
  return {
    getToken: vi.fn<() => Promise<string>>().mockResolvedValue(token),
    refreshToken: vi.fn<() => Promise<string>>().mockResolvedValue(token),
  } satisfies CsrfTokenManager;
}

describe('apiClient', () => {
  it('parses validated JSON and captures the request ID', async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(JSON.stringify({ id: 1, name: 'Dataset' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json', 'X-Request-Id': 'request-1' },
      }),
    );
    const client = createApiClient({
      baseUrl: 'http://localhost:8080',
      fetch: fetchMock,
      tokenStore: createTokenStore(),
      csrf: createCsrfManager(),
    });

    await expect(
      client.requestJson('/api/v1/items/1', itemSchema, { auth: 'none' }),
    ).resolves.toEqual({ data: { id: 1, name: 'Dataset' }, requestId: 'request-1' });
  });

  it('supports 204 and Blob responses without parsing JSON', async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(new Response('export', { status: 200 }));
    const client = createApiClient({
      baseUrl: 'http://localhost:8080',
      fetch: fetchMock,
      tokenStore: createTokenStore(),
      csrf: createCsrfManager(),
    });

    await expect(client.requestVoid('/api/v1/logout', { auth: 'none' })).resolves.toEqual({
      data: undefined,
    });
    const result = await client.requestBlob('/api/v1/export', { auth: 'none' });
    await expect(result.data.text()).resolves.toBe('export');
  });

  it('normalizes ProblemDetail and empty 401 responses', async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            status: 409,
            code: 'DUPLICATE_RESOURCE',
            detail: 'Already exists',
            traceId: 'trace-1',
            fieldErrors: [{ field: 'name', message: 'Tên đã tồn tại' }],
          }),
          { status: 409, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      )
      .mockResolvedValueOnce(new Response(null, { status: 401 }));
    const client = createApiClient({
      baseUrl: 'http://localhost:8080',
      fetch: fetchMock,
      tokenStore: createTokenStore(),
      csrf: createCsrfManager(),
    });

    const problem = await client
      .requestJson('/api/v1/items', itemSchema, { auth: 'none' })
      .catch((error: unknown) => error);
    const empty401 = await client
      .requestJson('/api/v1/items', itemSchema, { auth: 'none' })
      .catch((error: unknown) => error);

    expect(problem).toBeInstanceOf(ApiError);
    expect(problem).toMatchObject({
      status: 409,
      code: 'DUPLICATE_RESOURCE',
      detail: 'Already exists',
      traceId: 'trace-1',
      fieldErrors: [{ field: 'name', message: 'Tên đã tồn tại' }],
      permissionError: false,
      authenticationError: false,
    });
    expect(empty401).toMatchObject({ status: 401, authenticationError: true });
  });

  it('preserves the status when an error body is invalid JSON', async () => {
    const client = createApiClient({
      baseUrl: 'http://localhost:8080',
      fetch: vi.fn<typeof fetch>().mockResolvedValue(
        new Response('{invalid', {
          status: 500,
          headers: { 'Content-Type': 'application/problem+json' },
        }),
      ),
      tokenStore: createTokenStore(),
      csrf: createCsrfManager(),
    });

    await expect(
      client.requestJson('/api/v1/items', itemSchema, { auth: 'none' }),
    ).rejects.toMatchObject({ status: 500, detail: '{invalid' });
  });

  it('adds credentials, Bearer token, and CSRF header to a mutation', async () => {
    const tokenStore = createTokenStore();
    tokenStore.setAccessToken('access-token');
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(new Response(null, { status: 204 }));
    const client = createApiClient({
      baseUrl: 'http://localhost:8080',
      fetch: fetchMock,
      tokenStore,
      csrf: createCsrfManager(),
    });

    await client.requestVoid('/api/v1/items/1', { method: 'DELETE' });

    const request = fetchMock.mock.calls[0];
    expect(request?.[0]).toBe('http://localhost:8080/api/v1/items/1');
    expect(request?.[1]).toMatchObject({ credentials: 'include', method: 'DELETE' });
    expect(new Headers(request?.[1]?.headers).get('Authorization')).toBe('Bearer access-token');
    expect(new Headers(request?.[1]?.headers).get('X-XSRF-TOKEN')).toBe('csrf-token');
  });

  it('preserves caller headers and rejects paths escaping the public API namespace', async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(new Response('export'));
    const client = createApiClient({
      baseUrl: 'http://localhost:8080/',
      fetch: fetchMock,
      tokenStore: createTokenStore(),
      csrf: createCsrfManager(),
    });

    await client.requestBlob('/api/v1/export', {
      auth: 'none',
      headers: { Accept: 'application/octet-stream' },
    });

    expect(new Headers(fetchMock.mock.calls[0]?.[1]?.headers).get('Accept')).toBe(
      'application/octet-stream',
    );
    expect(fetchMock.mock.calls[0]?.[0]).toBe('http://localhost:8080/api/v1/export');
    await expect(
      client.requestVoid('/api/v1/../internal/runner', { auth: 'none' }),
    ).rejects.toMatchObject({ name: 'ApiContractError' });
  });

  it('uses one refresh for concurrent 401 responses and replays each request once', async () => {
    const tokenStore = createTokenStore();
    tokenStore.setAccessToken('stale-token');
    const fetchMock = vi.fn<typeof fetch>().mockImplementation(async (_input, init) => {
      const authorization = new Headers(init?.headers).get('Authorization');
      if (authorization === 'Bearer stale-token') {
        return new Response(null, { status: 401 });
      }
      return new Response(JSON.stringify({ id: 1, name: 'Dataset' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    });
    const refreshAction = vi.fn<() => Promise<string>>(async () => {
      await Promise.resolve();
      return 'fresh-token';
    });
    const refresh = createRefreshCoordinator({ refreshAction });
    const client = createApiClient({
      baseUrl: 'http://localhost:8080',
      fetch: fetchMock,
      tokenStore,
      csrf: createCsrfManager(),
      refresh,
    });

    const results = await Promise.all([
      client.requestJson('/api/v1/items/1', itemSchema),
      client.requestJson('/api/v1/items/2', itemSchema),
      client.requestJson('/api/v1/items/3', itemSchema),
    ]);

    expect(results).toHaveLength(3);
    expect(refreshAction).toHaveBeenCalledOnce();
    expect(tokenStore.getAccessToken()).toBe('fresh-token');
    expect(fetchMock).toHaveBeenCalledTimes(6);
  });

  it('refreshes CSRF once after a mutation receives 403', async () => {
    const csrf = createCsrfManager();
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(new Response(null, { status: 403 }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    const client = createApiClient({
      baseUrl: 'http://localhost:8080',
      fetch: fetchMock,
      tokenStore: createTokenStore(),
      csrf,
    });

    await expect(
      client.requestVoid('/api/v1/auth/logout', { auth: 'none', method: 'POST' }),
    ).resolves.toEqual({ data: undefined });
    expect(csrf.refreshToken).toHaveBeenCalledOnce();
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('does not refresh an aborted request', async () => {
    const refreshAction = vi.fn<() => Promise<string>>();
    const refresh = createRefreshCoordinator({ refreshAction });
    const controller = new AbortController();
    controller.abort();
    const client = createApiClient({
      baseUrl: 'http://localhost:8080',
      fetch: vi.fn<typeof fetch>().mockRejectedValue(new DOMException('Aborted', 'AbortError')),
      tokenStore: createTokenStore(),
      csrf: createCsrfManager(),
      refresh,
    });

    await expect(
      client.requestJson('/api/v1/items', itemSchema, { signal: controller.signal }),
    ).rejects.toMatchObject({ name: 'AbortError' });
    expect(refreshAction).not.toHaveBeenCalled();
  });
});
