import { useQuery } from '@tanstack/react-query';
import { getProject, getProjectSetupStatus } from '@/features/projects/api/projects.api';

export const projectKeys = {
  all: ['projects'] as const,
  detail: (projectId: string) => [...projectKeys.all, 'detail', projectId] as const,
  setupStatus: (projectId: string) => [...projectKeys.all, 'setup-status', projectId] as const,
};

export function useProject(projectId: string | undefined) {
  return useQuery({
    queryKey: projectKeys.detail(projectId!),
    queryFn: ({ signal }) => getProject(projectId!, signal),
    enabled: !!projectId,
    staleTime: 5 * 60 * 1000,
  });
}

export function useProjectSetupStatus(projectId: string | undefined) {
  return useQuery({
    queryKey: projectKeys.setupStatus(projectId!),
    queryFn: ({ signal }) => getProjectSetupStatus(projectId!, signal),
    enabled: !!projectId,
    staleTime: 60 * 1000,
  });
}
