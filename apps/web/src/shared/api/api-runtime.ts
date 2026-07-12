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
  setAuthenticationFailureHandler: (handler: () => void) => void;
}

interface CreateApiRuntimeOptions {
  baseUrl: string;
  fetch?: typeof fetch;
}

export function createApiRuntime({
  baseUrl,
  fetch: fetchOption,
}: CreateApiRuntimeOptions): ApiRuntime {
  let authenticationFailureHandler: (() => void) | undefined;
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
      authenticationFailureHandler?.();
    },
  });
  const apiClient = createApiClient({
    baseUrl,
    tokenStore,
    csrf,
    refresh,
    fetch: fetchImplementation,
  });

  return { 
    apiClient, 
    tokenStore, 
    setAuthenticationFailureHandler: (handler) => {
      authenticationFailureHandler = handler;
    } 
  };
}
