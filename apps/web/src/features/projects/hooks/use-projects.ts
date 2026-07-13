import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  archiveProject,
  createProject,
  deleteProject,
  getProject,
  getProjectSetupStatus,
  updateProject,
} from '@/features/projects/api/projects.api';
import { workspaceKeys } from '@/features/workspaces/hooks/use-workspaces';
import type { CreateProjectRequest, UpdateProjectRequest } from '@/features/projects/types/project';

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

export function useCreateProjectMutation(workspaceId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateProjectRequest) => createProject(workspaceId, data),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: workspaceKeys.projects(workspaceId) });
    },
  });
}

export function useUpdateProjectMutation(projectId: string, workspaceId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: UpdateProjectRequest) => updateProject(projectId, data),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: projectKeys.detail(projectId) });
      void queryClient.invalidateQueries({ queryKey: projectKeys.setupStatus(projectId) });
      void queryClient.invalidateQueries({ queryKey: workspaceKeys.projects(workspaceId) });
    },
  });
}

export function useArchiveProjectMutation(projectId: string, workspaceId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => archiveProject(projectId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: projectKeys.detail(projectId) });
      void queryClient.invalidateQueries({ queryKey: workspaceKeys.projects(workspaceId) });
    },
  });
}

export function useDeleteProjectMutation(projectId: string, workspaceId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => deleteProject(projectId),
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: projectKeys.detail(projectId) });
      void queryClient.invalidateQueries({ queryKey: workspaceKeys.projects(workspaceId) });
    },
  });
}
