import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createPersonalSecret,
  createWorkspaceSecret,
  deleteSecret,
  disableSecret,
  getPersonalSecrets,
  getSecret,
  getWorkspaceSecrets,
  rotateSecret,
  updateSecret,
} from '@/features/secrets/api/secrets.api';
import type {
  CreateSecretRequest,
  RotateSecretRequest,
  UpdateSecretRequest,
} from '@/features/secrets/types/secret';

export const secretKeys = {
  all: ['secrets'] as const,
  personal: () => [...secretKeys.all, 'personal'] as const,
  workspace: (workspaceId: string) => [...secretKeys.all, 'workspace', workspaceId] as const,
  detail: (secretId: string) => [...secretKeys.all, 'detail', secretId] as const,
};

export function usePersonalSecretsQuery() {
  return useQuery({
    queryKey: secretKeys.personal(),
    queryFn: ({ signal }) => getPersonalSecrets(signal),
  });
}

export function useWorkspaceSecretsQuery(workspaceId: string) {
  return useQuery({
    queryKey: secretKeys.workspace(workspaceId),
    queryFn: ({ signal }) => getWorkspaceSecrets(workspaceId, signal),
    enabled: Boolean(workspaceId),
  });
}

export function useSecretQuery(secretId: string) {
  return useQuery({
    queryKey: secretKeys.detail(secretId),
    queryFn: ({ signal }) => getSecret(secretId, signal),
    enabled: Boolean(secretId),
  });
}

export function useCreatePersonalSecretMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateSecretRequest) => createPersonalSecret(data),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: secretKeys.personal() });
    },
  });
}

export function useCreateWorkspaceSecretMutation(workspaceId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateSecretRequest) => createWorkspaceSecret(workspaceId, data),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: secretKeys.workspace(workspaceId) });
    },
  });
}

export function useUpdateSecretMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ secretId, data }: { secretId: string; data: UpdateSecretRequest }) =>
      updateSecret(secretId, data),
    onSuccess: (data, variables) => {
      void queryClient.invalidateQueries({ queryKey: secretKeys.detail(variables.secretId) });
      if (data.scope === 'PERSONAL') {
        void queryClient.invalidateQueries({ queryKey: secretKeys.personal() });
      } else if (data.workspaceId) {
        void queryClient.invalidateQueries({ queryKey: secretKeys.workspace(data.workspaceId) });
      }
    },
  });
}

export function useRotateSecretMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ secretId, data }: { secretId: string; data: RotateSecretRequest }) =>
      rotateSecret(secretId, data),
    onSuccess: (data, variables) => {
      void queryClient.invalidateQueries({ queryKey: secretKeys.detail(variables.secretId) });
      if (data.scope === 'PERSONAL') {
        void queryClient.invalidateQueries({ queryKey: secretKeys.personal() });
      } else if (data.workspaceId) {
        void queryClient.invalidateQueries({ queryKey: secretKeys.workspace(data.workspaceId) });
      }
    },
  });
}

export function useDisableSecretMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (secretId: string) => disableSecret(secretId),
    onSuccess: (data, variables) => {
      void queryClient.invalidateQueries({ queryKey: secretKeys.detail(variables) });
      if (data.scope === 'PERSONAL') {
        void queryClient.invalidateQueries({ queryKey: secretKeys.personal() });
      } else if (data.workspaceId) {
        void queryClient.invalidateQueries({ queryKey: secretKeys.workspace(data.workspaceId) });
      }
    },
  });
}

export function useDeleteSecretMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (secretId: string) => deleteSecret(secretId),
    onSuccess: (_, variables) => {
      // Note: Delete response is void, we must invalidate everything to be safe
      void queryClient.invalidateQueries({ queryKey: secretKeys.detail(variables) });
      void queryClient.invalidateQueries({ queryKey: secretKeys.personal() });
      void queryClient.invalidateQueries({ queryKey: secretKeys.all }); // For workspace secrets, since we don't know the id here easily
    },
  });
}
