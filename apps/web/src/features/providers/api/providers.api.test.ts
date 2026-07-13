import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  getProviderTestLogs,
  getWorkspaceProviders,
  testProvider,
} from '@/features/providers/api/providers.api';

const requestJsonMock = vi.hoisted(() =>
  vi.fn<(path: string, schema: unknown, init?: RequestInit) => Promise<{ data: unknown }>>(),
);

vi.mock('@/shared/api/api', () => ({
  apiClient: {
    requestJson: requestJsonMock,
  },
}));

describe('providers API', () => {
  beforeEach(() => {
    requestJsonMock.mockReset();
  });

  it('loads workspace providers from the backend collection response', async () => {
    const signal = new AbortController().signal;
    const provider = providerResponse();
    requestJsonMock.mockResolvedValueOnce({
      data: {
        items: [provider],
        page: { limit: 1, nextCursor: null, hasMore: false },
      },
    });

    await expect(getWorkspaceProviders(provider.workspaceId ?? '', signal)).resolves.toEqual([
      provider,
    ]);

    expect(requestJsonMock).toHaveBeenCalledWith(
      `/api/v1/workspaces/${provider.workspaceId}/llm-providers`,
      expect.any(Object),
      { signal },
    );
  });

  it('loads provider test logs as a paged sanitized collection', async () => {
    const signal = new AbortController().signal;
    const log = {
      id: 1,
      status: 'SUCCESS',
      model: 'gpt-4.1-mini',
      requestJsonSanitized: { prompt: 'ping' },
      responsePreview: 'pong',
      errorCode: null,
      errorMessage: null,
      latencyMs: 120,
      tokenUsageJson: { total: 2 },
      testedAt: '2026-01-01T00:00:00Z',
    };
    requestJsonMock.mockResolvedValueOnce({
      data: {
        items: [log],
        page: { limit: 20, nextCursor: null, hasMore: false },
      },
    });

    await expect(getProviderTestLogs('provider-1', signal)).resolves.toEqual([log]);

    expect(requestJsonMock).toHaveBeenCalledWith(
      '/api/v1/llm-providers/provider-1/test-logs?limit=20',
      expect.any(Object),
      { signal },
    );
  });

  it('tests a provider without sending secret material from the frontend', async () => {
    const response = {
      status: 'SUCCESS',
      model: 'gpt-4.1-mini',
      request: { prompt: 'ping' },
      responsePreview: 'pong',
      errorCode: null,
      errorMessage: null,
      latencyMs: 120,
      tokenUsage: { total: 2 },
    };
    requestJsonMock.mockResolvedValueOnce({ data: response });

    await expect(
      testProvider('provider-1', {
        model: 'gpt-4.1-mini',
        prompt: 'ping',
      }),
    ).resolves.toEqual(response);

    expect(requestJsonMock).toHaveBeenCalledWith(
      '/api/v1/llm-providers/provider-1/test',
      expect.any(Object),
      {
        method: 'POST',
        body: JSON.stringify({
          model: 'gpt-4.1-mini',
          prompt: 'ping',
        }),
      },
    );
  });
});

function providerResponse() {
  return {
    id: '11111111-1111-4111-8111-111111111111',
    scope: 'WORKSPACE',
    ownerUserId: '22222222-2222-4222-8222-222222222222',
    ownerUser: {
      id: '22222222-2222-4222-8222-222222222222',
      email: 'qc.engineer@vinfast.vn',
      name: 'QC Engineer',
      avatarUrl: null,
    },
    workspaceId: '33333333-3333-4333-8333-333333333333',
    name: 'OpenAI QA',
    providerType: 'OPENAI_COMPATIBLE',
    secret: {
      id: '44444444-4444-4444-8444-444444444444',
      scope: 'WORKSPACE',
      ownerUserId: '22222222-2222-4222-8222-222222222222',
      ownerUser: {
        id: '22222222-2222-4222-8222-222222222222',
        email: 'qc.engineer@vinfast.vn',
        name: 'QC Engineer',
        avatarUrl: null,
      },
      workspaceId: '33333333-3333-4333-8333-333333333333',
      name: 'OpenAI API key',
      status: 'ACTIVE',
    },
    baseUrl: 'https://api.openai.com/v1',
    defaultModel: 'gpt-4.1-mini',
    availableModels: null,
    config: null,
    status: 'ACTIVE',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  };
}
