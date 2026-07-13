import { apiClient } from '@/shared/api/api';
import {
  type CreateProviderRequest,
  type UpdateProviderRequest,
  type TestProviderRequest,
  providerCollectionSchema,
  providerResponseSchema,
  providerTestResponseSchema,
  providerTestLogCollectionSchema,
} from '@/features/providers/types/provider';

// WORKSPACE SCOPE
export async function getWorkspaceProviders(workspaceId: string, signal?: AbortSignal) {
  const response = await apiClient.requestJson(
    `/api/v1/workspaces/${workspaceId}/llm-providers`,
    providerCollectionSchema,
    { ...(signal && { signal }) },
  );
  return response.data.items;
}

export async function createWorkspaceProvider(workspaceId: string, data: CreateProviderRequest) {
  const response = await apiClient.requestJson(
    `/api/v1/workspaces/${workspaceId}/llm-providers`,
    providerResponseSchema,
    {
      method: 'POST',
      body: JSON.stringify(data),
    },
  );
  return response.data;
}

// PERSONAL SCOPE
export async function getPersonalProviders(signal?: AbortSignal) {
  const response = await apiClient.requestJson(
    `/api/v1/me/llm-providers`,
    providerCollectionSchema,
    { ...(signal && { signal }) },
  );
  return response.data.items;
}

export async function createPersonalProvider(data: CreateProviderRequest) {
  const response = await apiClient.requestJson(`/api/v1/me/llm-providers`, providerResponseSchema, {
    method: 'POST',
    body: JSON.stringify(data),
  });
  return response.data;
}

// COMMON ACTIONS
export async function getProvider(providerId: string, signal?: AbortSignal) {
  const response = await apiClient.requestJson(
    `/api/v1/llm-providers/${providerId}`,
    providerResponseSchema,
    { ...(signal && { signal }) },
  );
  return response.data;
}

export async function updateProvider(providerId: string, data: UpdateProviderRequest) {
  const response = await apiClient.requestJson(
    `/api/v1/llm-providers/${providerId}`,
    providerResponseSchema,
    {
      method: 'PATCH',
      body: JSON.stringify(data),
    },
  );
  return response.data;
}

export async function deleteProvider(providerId: string) {
  await apiClient.requestVoid(`/api/v1/llm-providers/${providerId}`, {
    method: 'DELETE',
  });
}

// TESTING
export async function testProvider(providerId: string, data: TestProviderRequest) {
  const response = await apiClient.requestJson(
    `/api/v1/llm-providers/${providerId}/test`,
    providerTestResponseSchema,
    {
      method: 'POST',
      body: JSON.stringify(data),
    },
  );
  return response.data;
}

export async function getProviderTestLogs(providerId: string, signal?: AbortSignal) {
  const response = await apiClient.requestJson(
    `/api/v1/llm-providers/${providerId}/test-logs?limit=20`,
    providerTestLogCollectionSchema,
    { ...(signal && { signal }) },
  );
  return response.data.items;
}
