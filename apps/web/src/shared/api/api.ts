import { createApiRuntime } from '@/shared/api/api-runtime';

export const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';
const runtime = createApiRuntime({ baseUrl: apiBaseUrl });

export const apiClient = runtime.apiClient;
export const accessTokenStore = runtime.tokenStore;
export const refreshAccessToken = runtime.refreshAccessToken;

export function setAuthenticationFailureHandler(handler: () => void) {
  runtime.setAuthenticationFailureHandler(handler);
}
