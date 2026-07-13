import { apiClient } from '@/shared/api/api';
import { projectResponseSchema } from '@/features/projects/types/project';
import { projectSetupStatusResponseSchema } from '@/features/projects/types/project-setup-status';

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
