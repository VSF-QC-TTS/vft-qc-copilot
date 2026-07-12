import { apiClient } from '@/shared/api/api';
import { projectResponseSchema } from '@/features/projects/types/project';

export async function getProject(projectId: string, signal?: AbortSignal) {
  const response = await apiClient.requestJson(
    `/api/v1/projects/${projectId}`,
    projectResponseSchema,
    {
      ...(signal && { signal }),
    }
  );
  return response.data;
}
