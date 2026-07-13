import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createEvaluator,
  createEvaluatorVersion,
  deleteEvaluator,
  getEvaluator,
  getEvaluatorFieldSources,
  getEvaluatorOperators,
  getEvaluatorVersion,
  getEvaluatorVersions,
  getProjectEvaluators,
  updateEvaluator,
  validateEvaluator,
} from '@/features/evaluators/api/evaluators.api';
import type {
  CreateEvaluatorRequest,
  CreateEvaluatorVersionRequest,
  UpdateEvaluatorRequest,
  ValidateEvaluatorRequest,
} from '@/features/evaluators/types/evaluator';

export const evaluatorKeys = {
  all: ['evaluators'] as const,
  project: (projectId: string) => [...evaluatorKeys.all, 'project', projectId] as const,
  operators: () => [...evaluatorKeys.all, 'operators'] as const,
  fieldSources: (projectId: string, chatbotTargetId?: string) =>
    [...evaluatorKeys.all, 'field-sources', projectId, chatbotTargetId ?? 'none'] as const,
  detail: (evaluatorId: string) => [...evaluatorKeys.all, 'detail', evaluatorId] as const,
  versions: (evaluatorId: string) => [...evaluatorKeys.detail(evaluatorId), 'versions'] as const,
  version: (versionId: string) => [...evaluatorKeys.all, 'version', versionId] as const,
};

export function useProjectEvaluators(projectId: string) {
  return useQuery({
    queryKey: evaluatorKeys.project(projectId),
    queryFn: ({ signal }) => getProjectEvaluators(projectId, signal),
    enabled: Boolean(projectId),
  });
}

export function useEvaluator(evaluatorId: string) {
  return useQuery({
    queryKey: evaluatorKeys.detail(evaluatorId),
    queryFn: ({ signal }) => getEvaluator(evaluatorId, signal),
    enabled: Boolean(evaluatorId),
  });
}

export function useEvaluatorVersions(evaluatorId: string) {
  return useQuery({
    queryKey: evaluatorKeys.versions(evaluatorId),
    queryFn: ({ signal }) => getEvaluatorVersions(evaluatorId, signal),
    enabled: Boolean(evaluatorId),
  });
}

export function useEvaluatorVersion(versionId: string) {
  return useQuery({
    queryKey: evaluatorKeys.version(versionId),
    queryFn: ({ signal }) => getEvaluatorVersion(versionId, signal),
    enabled: Boolean(versionId),
  });
}

export function useEvaluatorFieldSources(projectId: string, chatbotTargetId?: string) {
  return useQuery({
    queryKey: evaluatorKeys.fieldSources(projectId, chatbotTargetId),
    queryFn: ({ signal }) => getEvaluatorFieldSources(projectId, chatbotTargetId, signal),
    enabled: Boolean(projectId),
  });
}

export function useEvaluatorOperators() {
  return useQuery({
    queryKey: evaluatorKeys.operators(),
    queryFn: ({ signal }) => getEvaluatorOperators(signal),
  });
}

export function useCreateEvaluator(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateEvaluatorRequest) => createEvaluator(projectId, data),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: evaluatorKeys.project(projectId) });
    },
  });
}

export function useUpdateEvaluator(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ evaluatorId, data }: { evaluatorId: string; data: UpdateEvaluatorRequest }) =>
      updateEvaluator(evaluatorId, data),
    onSuccess: (evaluator) => {
      void queryClient.invalidateQueries({ queryKey: evaluatorKeys.project(projectId) });
      void queryClient.invalidateQueries({ queryKey: evaluatorKeys.detail(evaluator.id) });
    },
  });
}

export function useDeleteEvaluator(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (evaluatorId: string) => deleteEvaluator(evaluatorId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: evaluatorKeys.project(projectId) });
    },
  });
}

export function useCreateEvaluatorVersion(evaluatorId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateEvaluatorVersionRequest) => createEvaluatorVersion(evaluatorId, data),
    onSuccess: (version) => {
      void queryClient.invalidateQueries({ queryKey: evaluatorKeys.versions(evaluatorId) });
      void queryClient.invalidateQueries({ queryKey: evaluatorKeys.detail(evaluatorId) });
      void queryClient.setQueryData(evaluatorKeys.version(version.id), version);
    },
  });
}

export function useValidateEvaluator(projectId: string) {
  return useMutation({
    mutationFn: (data: ValidateEvaluatorRequest) => validateEvaluator(projectId, data),
  });
}
