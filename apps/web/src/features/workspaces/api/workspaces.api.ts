import { apiClient } from '@/shared/api/api';
import {
  workspaceCollectionSchema,
  workspaceResponseSchema,
} from '@/features/workspaces/types/workspace';
import { projectCollectionSchema } from '@/features/projects/types/project';

export async function getWorkspaces(signal?: AbortSignal) {
  const response = await apiClient.requestJson('/api/v1/workspaces', workspaceCollectionSchema, {
    ...(signal && { signal }),
  });
  return response.data;
}

export async function getWorkspace(workspaceId: string, signal?: AbortSignal) {
  const response = await apiClient.requestJson(
    `/api/v1/workspaces/${workspaceId}`,
    workspaceResponseSchema,
    {
      ...(signal && { signal }),
    }
  );
  return response.data;
}

export async function getWorkspaceProjects(workspaceId: string, signal?: AbortSignal) {
  const response = await apiClient.requestJson(
    `/api/v1/workspaces/${workspaceId}/projects`,
    projectCollectionSchema,
    {
      ...(signal && { signal }),
    }
  );
  return response.data;
}
