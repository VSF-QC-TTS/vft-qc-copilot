import { useQuery } from '@tanstack/react-query';
import {
  getWorkspace,
  getWorkspaceProjects,
  getWorkspaces,
} from '@/features/workspaces/api/workspaces.api';

export const workspaceKeys = {
  all: ['workspaces'] as const,
  list: () => [...workspaceKeys.all, 'list'] as const,
  detail: (workspaceId: string) => [...workspaceKeys.all, 'detail', workspaceId] as const,
  projects: (workspaceId: string) => [...workspaceKeys.all, 'projects', workspaceId] as const,
};

export function useWorkspaces() {
  return useQuery({
    queryKey: workspaceKeys.list(),
    queryFn: ({ signal }) => getWorkspaces(signal),
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
}

export function useWorkspace(workspaceId: string | undefined) {
  return useQuery({
    queryKey: workspaceKeys.detail(workspaceId!),
    queryFn: ({ signal }) => getWorkspace(workspaceId!, signal),
    enabled: !!workspaceId,
    staleTime: 5 * 60 * 1000,
  });
}

export function useWorkspaceProjects(workspaceId: string | undefined) {
  return useQuery({
    queryKey: workspaceKeys.projects(workspaceId!),
    queryFn: ({ signal }) => getWorkspaceProjects(workspaceId!, signal),
    enabled: !!workspaceId,
    staleTime: 5 * 60 * 1000,
  });
}
