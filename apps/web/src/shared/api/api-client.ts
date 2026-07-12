import type { ZodType } from 'zod';

import {
  ApiContractError,
  createNetworkError,
  normalizeErrorResponse,
} from '@/shared/api/api-error';
import type { CsrfTokenManager } from '@/shared/api/csrf';
import { requiresCsrf } from '@/shared/api/csrf';
import type { RefreshCoordinator } from '@/shared/api/refresh-coordinator';
import type { TokenStore } from '@/shared/api/token-store';

type AuthenticationMode = 'required' | 'optional' | 'none';
type CsrfMode = 'auto' | 'required' | 'none';

export interface ApiRequestOptions {
  method?: string;
  headers?: HeadersInit;
  body?: BodyInit | null;
  signal?: AbortSignal;
  auth?: AuthenticationMode;
  csrf?: CsrfMode;
}

export interface ApiResponse<T> {
  data: T;
  requestId?: string;
}

interface CreateApiClientOptions {
  baseUrl: string;
  tokenStore: TokenStore;
  csrf: CsrfTokenManager;
  refresh?: RefreshCoordinator;
  fetch?: typeof fetch;
}

interface RetryState {
  authenticationRetried: boolean;
  csrfRetried: boolean;
  csrfToken?: string;
}

export interface ApiClient {
  requestJson<T>(
    path: string,
    schema: ZodType<T>,
    options?: ApiRequestOptions,
  ): Promise<ApiResponse<T>>;
  requestBlob(path: string, options?: ApiRequestOptions): Promise<ApiResponse<Blob>>;
  requestVoid(path: string, options?: ApiRequestOptions): Promise<ApiResponse<void>>;
}

function createResult<T>(response: Response, data: T): ApiResponse<T> {
  const requestId = response.headers.get('X-Request-Id');
  return requestId ? { data, requestId } : { data };
}

function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === 'AbortError';
}

function isReplayable(body: BodyInit | null | undefined): boolean {
  return !(typeof ReadableStream !== 'undefined' && body instanceof ReadableStream);
}

export function createApiClient({
  baseUrl,
  tokenStore,
  csrf,
  refresh,
  fetch: fetchImplementation = globalThis.fetch.bind(globalThis),
}: CreateApiClientOptions): ApiClient {
  const apiOrigin = new URL(baseUrl).origin;

  async function execute(
    path: string,
    options: ApiRequestOptions = {},
    retryState: RetryState = { authenticationRetried: false, csrfRetried: false },
  ): Promise<Response> {
    const requestUrl = new URL(path, `${apiOrigin}/`);
    if (!path.startsWith('/api/v1/') || !requestUrl.pathname.startsWith('/api/v1/')) {
      throw new ApiContractError('API paths must stay inside /api/v1/**');
    }

    const method = options.method?.toUpperCase() ?? 'GET';
    const authenticationMode = options.auth ?? 'required';
    const csrfMode = options.csrf ?? 'auto';
    const headers = new Headers(options.headers);
    const accessToken = tokenStore.getAccessToken();

    if (!headers.has('Accept')) {
      headers.set('Accept', 'application/json');
    }

    if (authenticationMode !== 'none' && accessToken) {
      headers.set('Authorization', `Bearer ${accessToken}`);
    }

    const csrfRequired = csrfMode === 'required' || (csrfMode === 'auto' && requiresCsrf(method));
    if (csrfRequired) {
      headers.set('X-XSRF-TOKEN', retryState.csrfToken ?? (await csrf.getToken()));
    }

    const requestInit: RequestInit = {
      credentials: 'include',
      headers,
      method,
      ...(options.body !== undefined ? { body: options.body } : {}),
      ...(options.signal ? { signal: options.signal } : {}),
    };

    let response: Response;
    try {
      response = await fetchImplementation(requestUrl.toString(), requestInit);
    } catch (error) {
      if (isAbortError(error) || options.signal?.aborted) {
        throw error;
      }
      throw createNetworkError(error);
    }

    const canRetry = !options.signal?.aborted && isReplayable(options.body);

    if (
      response.status === 401 &&
      authenticationMode !== 'none' &&
      refresh &&
      !retryState.authenticationRetried &&
      canRetry
    ) {
      const accessTokenAfterRefresh = await refresh.refresh();
      tokenStore.setAccessToken(accessTokenAfterRefresh);
      return execute(path, options, { ...retryState, authenticationRetried: true });
    }

    if (response.status === 403 && csrfRequired && !retryState.csrfRetried && canRetry) {
      const refreshedCsrfToken = await csrf.refreshToken();
      return execute(path, options, {
        ...retryState,
        csrfRetried: true,
        csrfToken: refreshedCsrfToken,
      });
    }

    if (!response.ok) {
      throw await normalizeErrorResponse(response);
    }

    return response;
  }

  return {
    async requestJson(path, schema, options) {
      const response = await execute(path, options);
      let json: unknown;
      try {
        json = JSON.parse(await response.text());
      } catch (error) {
        throw new ApiContractError('API response is not valid JSON', { cause: error });
      }

      const result = schema.safeParse(json);
      if (!result.success) {
        throw new ApiContractError('API response does not match its contract', {
          cause: result.error,
        });
      }

      return createResult(response, result.data);
    },
    async requestBlob(path, options) {
      const response = await execute(path, options);
      return createResult(response, await response.blob());
    },
    async requestVoid(path, options) {
      const response = await execute(path, options);
      return createResult(response, undefined);
    },
  };
}
