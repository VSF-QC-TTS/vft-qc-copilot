import { useQuery } from '@tanstack/react-query';
import { getProject } from '@/features/projects/api/projects.api';

export const projectKeys = {
  all: ['projects'] as const,
  detail: (projectId: string) => [...projectKeys.all, 'detail', projectId] as const,
};

export function useProject(projectId: string | undefined) {
  return useQuery({
    queryKey: projectKeys.detail(projectId!),
    queryFn: ({ signal }) => getProject(projectId!, signal),
    enabled: !!projectId,
    staleTime: 5 * 60 * 1000,
  });
}
