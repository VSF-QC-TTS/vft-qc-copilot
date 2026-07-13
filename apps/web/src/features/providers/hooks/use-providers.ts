import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  getWorkspaceProviders,
  createWorkspaceProvider,
  getPersonalProviders,
  createPersonalProvider,
  getProvider,
  updateProvider,
  deleteProvider,
  testProvider,
  getProviderTestLogs,
} from '@/features/providers/api/providers.api';
import type {
  CreateProviderRequest,
  UpdateProviderRequest,
  TestProviderRequest,
} from '@/features/providers/types/provider';

const providersKeys = {
  all: ['providers'] as const,
  workspace: (workspaceId: string) => [...providersKeys.all, 'workspace', workspaceId] as const,
  personal: () => [...providersKeys.all, 'personal'] as const,
  detail: (providerId: string) => [...providersKeys.all, 'detail', providerId] as const,
  testLogs: (providerId: string) => [...providersKeys.detail(providerId), 'test-logs'] as const,
};

export function useWorkspaceProviders(workspaceId: string) {
  return useQuery({
    queryKey: providersKeys.workspace(workspaceId),
    queryFn: ({ signal }) => getWorkspaceProviders(workspaceId, signal),
  });
}

export function usePersonalProviders() {
  return useQuery({
    queryKey: providersKeys.personal(),
    queryFn: ({ signal }) => getPersonalProviders(signal),
  });
}

export function useProvider(providerId: string) {
  return useQuery({
    queryKey: providersKeys.detail(providerId),
    queryFn: ({ signal }) => getProvider(providerId, signal),
  });
}

export function useCreateWorkspaceProvider(workspaceId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateProviderRequest) => createWorkspaceProvider(workspaceId, data),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: providersKeys.workspace(workspaceId) });
    },
  });
}

export function useCreatePersonalProvider() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateProviderRequest) => createPersonalProvider(data),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: providersKeys.personal() });
    },
  });
}

export function useUpdateProvider() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ providerId, data }: { providerId: string; data: UpdateProviderRequest }) =>
      updateProvider(providerId, data),
    onSuccess: (_, { providerId }) => {
      void queryClient.invalidateQueries({ queryKey: providersKeys.detail(providerId) });
      void queryClient.invalidateQueries({ queryKey: providersKeys.all });
    },
  });
}

export function useDeleteProvider() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (providerId: string) => deleteProvider(providerId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: providersKeys.all });
    },
  });
}

export function useTestProvider() {
  return useMutation({
    mutationFn: ({ providerId, data }: { providerId: string; data: TestProviderRequest }) =>
      testProvider(providerId, data),
  });
}

export function useProviderTestLogs(providerId: string) {
  return useQuery({
    queryKey: providersKeys.testLogs(providerId),
    queryFn: ({ signal }) => getProviderTestLogs(providerId, signal),
  });
}
