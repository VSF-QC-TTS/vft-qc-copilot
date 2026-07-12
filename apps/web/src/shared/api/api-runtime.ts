import type { ApiClient } from '@/shared/api/api-client';
import { createApiClient } from '@/shared/api/api-client';
import { createCsrfTokenManager } from '@/shared/api/csrf';
import { createRefreshAccessToken } from '@/shared/api/refresh-access-token';
import { createRefreshCoordinator } from '@/shared/api/refresh-coordinator';
import type { TokenStore } from '@/shared/api/token-store';
import { createTokenStore } from '@/shared/api/token-store';

export interface ApiRuntime {
  apiClient: ApiClient;
  tokenStore: TokenStore;
}

interface CreateApiRuntimeOptions {
  baseUrl: string;
  fetch?: typeof fetch;
  onAuthenticationFailure?: () => void;
}

export function createApiRuntime({
  baseUrl,
  fetch: fetchOption,
  onAuthenticationFailure,
}: CreateApiRuntimeOptions): ApiRuntime {
  const fetchImplementation = fetchOption ?? globalThis.fetch.bind(globalThis);
  const tokenStore = createTokenStore();
  const csrf = createCsrfTokenManager({ baseUrl, fetch: fetchImplementation });
  const refreshAction = createRefreshAccessToken({
    baseUrl,
    csrf,
    fetch: fetchImplementation,
  });
  const refresh = createRefreshCoordinator({
    refreshAction,
    onFailure() {
      tokenStore.clear();
      onAuthenticationFailure?.();
    },
  });
  const apiClient = createApiClient({
    baseUrl,
    tokenStore,
    csrf,
    refresh,
    fetch: fetchImplementation,
  });

  return { apiClient, tokenStore };
}
