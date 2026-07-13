import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  addWorkspaceMember,
  createWorkspace,
  getWorkspace,
  getWorkspaceMembers,
  getWorkspaceProjects,
  getWorkspaces,
  updateWorkspace,
  updateWorkspaceMemberRole,
} from '@/features/workspaces/api/workspaces.api';
import type {
  AddWorkspaceMemberRequest,
  CreateWorkspaceRequest,
  UpdateWorkspaceMemberRoleRequest,
  UpdateWorkspaceRequest,
} from '@/features/workspaces/types/workspace';

export const workspaceKeys = {
  all: ['workspaces'] as const,
  list: () => [...workspaceKeys.all, 'list'] as const,
  detail: (workspaceId: string) => [...workspaceKeys.all, 'detail', workspaceId] as const,
  projects: (workspaceId: string) => [...workspaceKeys.all, 'projects', workspaceId] as const,
  members: (workspaceId: string) => [...workspaceKeys.all, 'members', workspaceId] as const,
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

export function useWorkspaceMembers(workspaceId: string | undefined) {
  return useQuery({
    queryKey: workspaceKeys.members(workspaceId!),
    queryFn: ({ signal }) => getWorkspaceMembers(workspaceId!, signal),
    enabled: !!workspaceId,
    staleTime: 5 * 60 * 1000,
  });
}

export function useCreateWorkspaceMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateWorkspaceRequest) => createWorkspace(data),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: workspaceKeys.list() });
    },
  });
}

export function useAddWorkspaceMemberMutation(workspaceId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: AddWorkspaceMemberRequest) => addWorkspaceMember(workspaceId, data),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: workspaceKeys.members(workspaceId) });
    },
  });
}

export function useUpdateWorkspaceMemberRoleMutation(workspaceId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      memberId,
      data,
    }: {
      memberId: number;
      data: UpdateWorkspaceMemberRoleRequest;
    }) => updateWorkspaceMemberRole(workspaceId, memberId, data),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: workspaceKeys.members(workspaceId) });
    },
  });
}

export function useUpdateWorkspaceMutation(workspaceId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: UpdateWorkspaceRequest) => updateWorkspace(workspaceId, data),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: workspaceKeys.detail(workspaceId) });
      void queryClient.invalidateQueries({ queryKey: workspaceKeys.list() });
    },
  });
}
