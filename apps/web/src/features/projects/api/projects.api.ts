import { apiClient } from '@/shared/api/api';
import {
  CreateProjectRequest,
  UpdateProjectRequest,
  projectResponseSchema,
} from '@/features/projects/types/project';
import { projectSetupStatusResponseSchema } from '@/features/projects/types/project-setup-status';

export async function createProject(workspaceId: string, data: CreateProjectRequest) {
  const response = await apiClient.requestJson(
    `/api/v1/workspaces/${workspaceId}/projects`,
    projectResponseSchema,
    {
      method: 'POST',
      body: JSON.stringify(data),
    },
  );
  return response.data;
}

export async function getProject(projectId: string, signal?: AbortSignal) {
  const response = await apiClient.requestJson(
    `/api/v1/projects/${projectId}`,
    projectResponseSchema,
    {
      ...(signal && { signal }),
    },
  );
  return response.data;
}

export async function updateProject(projectId: string, data: UpdateProjectRequest) {
  const response = await apiClient.requestJson(
    `/api/v1/projects/${projectId}`,
    projectResponseSchema,
    {
      method: 'PATCH',
      body: JSON.stringify(data),
    },
  );
  return response.data;
}

export async function archiveProject(projectId: string) {
  const response = await apiClient.requestJson(
    `/api/v1/projects/${projectId}/archive`,
    projectResponseSchema,
    {
      method: 'POST',
    },
  );
  return response.data;
}

export async function deleteProject(projectId: string) {
  const response = await apiClient.requestVoid(`/api/v1/projects/${projectId}`, {
    method: 'DELETE',
  });
  return response.data;
}

export async function getProjectSetupStatus(projectId: string, signal?: AbortSignal) {
  const response = await apiClient.requestJson(
    `/api/v1/projects/${projectId}/setup-status`,
    projectSetupStatusResponseSchema,
    {
      ...(signal && { signal }),
    },
  );
  return response.data;
}
