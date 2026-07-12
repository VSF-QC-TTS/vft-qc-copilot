import { ApiContractError, normalizeErrorResponse } from '@/shared/api/api-error';

const CSRF_COOKIE_NAME = 'XSRF-TOKEN';
const CSRF_PATH = '/api/v1/auth/csrf';
const MUTATION_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

export interface CsrfTokenManager {
  getToken(): Promise<string>;
  refreshToken(): Promise<string>;
}

interface CreateCsrfTokenManagerOptions {
  baseUrl: string;
  fetch?: typeof fetch;
  readCookies?: () => string;
}

export function requiresCsrf(method: string): boolean {
  return MUTATION_METHODS.has(method.toUpperCase());
}

export function readCookie(cookieHeader: string, name: string): string | undefined {
  const prefix = `${name}=`;
  const pair = cookieHeader
    .split(';')
    .map((item) => item.trim())
    .find((item) => item.startsWith(prefix));

  return pair ? decodeURIComponent(pair.slice(prefix.length)) : undefined;
}

export function createCsrfTokenManager({
  baseUrl,
  fetch: fetchImplementation = globalThis.fetch.bind(globalThis),
  readCookies = () => document.cookie,
}: CreateCsrfTokenManagerOptions): CsrfTokenManager {
  let initialization: Promise<string> | undefined;

  async function initialize(): Promise<string> {
    const response = await fetchImplementation(`${baseUrl}${CSRF_PATH}`, {
      credentials: 'include',
      headers: { Accept: 'application/json' },
      method: 'GET',
    });

    if (!response.ok) {
      throw await normalizeErrorResponse(response);
    }

    const token = readCookie(readCookies(), CSRF_COOKIE_NAME);
    if (!token) {
      throw new ApiContractError('CSRF endpoint did not expose the XSRF-TOKEN cookie');
    }

    return token;
  }

  function initializeOnce(): Promise<string> {
    if (!initialization) {
      initialization = initialize().finally(() => {
        initialization = undefined;
      });
    }
    return initialization;
  }

  return {
    getToken() {
      const token = readCookie(readCookies(), CSRF_COOKIE_NAME);
      return token ? Promise.resolve(token) : initializeOnce();
    },
    refreshToken() {
      return initializeOnce();
    },
  };
}
