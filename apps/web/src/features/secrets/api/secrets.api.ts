import { apiClient } from '@/shared/api/api';
import {
  secretCollectionSchema,
  secretResponseSchema,
  type CreateSecretRequest,
  type RotateSecretRequest,
  type UpdateSecretRequest,
} from '@/features/secrets/types/secret';

export async function getPersonalSecrets(signal?: AbortSignal) {
  const response = await apiClient.requestJson('/api/v1/me/secrets', secretCollectionSchema, {
    ...(signal && { signal }),
  });
  return response.data;
}

export async function createPersonalSecret(data: CreateSecretRequest) {
  const response = await apiClient.requestJson('/api/v1/me/secrets', secretResponseSchema, {
    method: 'POST',
    body: JSON.stringify(data),
  });
  return response.data;
}

export async function getWorkspaceSecrets(workspaceId: string, signal?: AbortSignal) {
  const response = await apiClient.requestJson(
    `/api/v1/workspaces/${workspaceId}/secrets`,
    secretCollectionSchema,
    {
      ...(signal && { signal }),
    },
  );
  return response.data;
}

export async function createWorkspaceSecret(workspaceId: string, data: CreateSecretRequest) {
  const response = await apiClient.requestJson(
    `/api/v1/workspaces/${workspaceId}/secrets`,
    secretResponseSchema,
    {
      method: 'POST',
      body: JSON.stringify(data),
    },
  );
  return response.data;
}

export async function getSecret(secretId: string, signal?: AbortSignal) {
  const response = await apiClient.requestJson(
    `/api/v1/secrets/${secretId}`,
    secretResponseSchema,
    {
      ...(signal && { signal }),
    },
  );
  return response.data;
}

export async function updateSecret(secretId: string, data: UpdateSecretRequest) {
  const response = await apiClient.requestJson(
    `/api/v1/secrets/${secretId}`,
    secretResponseSchema,
    {
      method: 'PATCH',
      body: JSON.stringify(data),
    },
  );
  return response.data;
}

export async function rotateSecret(secretId: string, data: RotateSecretRequest) {
  const response = await apiClient.requestJson(
    `/api/v1/secrets/${secretId}/rotate`,
    secretResponseSchema,
    {
      method: 'POST',
      body: JSON.stringify(data),
    },
  );
  return response.data;
}

export async function disableSecret(secretId: string) {
  const response = await apiClient.requestJson(
    `/api/v1/secrets/${secretId}/disable`,
    secretResponseSchema,
    {
      method: 'POST',
    },
  );
  return response.data;
}

export async function deleteSecret(secretId: string) {
  await apiClient.requestVoid(`/api/v1/secrets/${secretId}`, {
    method: 'DELETE',
  });
}
