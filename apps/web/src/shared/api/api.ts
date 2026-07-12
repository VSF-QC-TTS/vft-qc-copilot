import { createApiRuntime } from '@/shared/api/api-runtime';

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';
const runtime = createApiRuntime({ baseUrl: apiBaseUrl });

export const apiClient = runtime.apiClient;
export const accessTokenStore = runtime.tokenStore;

export function setAuthenticationFailureHandler(handler: () => void) {
  runtime.setAuthenticationFailureHandler(handler);
}
