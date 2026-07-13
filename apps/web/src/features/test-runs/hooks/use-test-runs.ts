import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createTestRun,
  getProjectTestRuns,
  getTestRun,
  preflightTestRun,
} from '@/features/test-runs/api/test-runs.api';
import type {
  CreateTestRunRequest,
  RunPreflightRequest,
} from '@/features/test-runs/types/test-run';

export const testRunKeys = {
  all: ['test-runs'] as const,
  project: (projectId: string) => [...testRunKeys.all, 'project', projectId] as const,
  detail: (testRunId: string) => [...testRunKeys.all, 'detail', testRunId] as const,
};

export function useProjectTestRuns(projectId: string) {
  return useQuery({
    queryKey: testRunKeys.project(projectId),
    queryFn: ({ signal }) => getProjectTestRuns(projectId, signal),
    enabled: Boolean(projectId),
  });
}

export function useTestRun(testRunId: string) {
  return useQuery({
    queryKey: testRunKeys.detail(testRunId),
    queryFn: ({ signal }) => getTestRun(testRunId, signal),
    enabled: Boolean(testRunId),
  });
}

export function usePreflightTestRun(projectId: string) {
  return useMutation({
    mutationFn: (data: RunPreflightRequest) => preflightTestRun(projectId, data),
  });
}

export function useCreateTestRun(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateTestRunRequest) => createTestRun(projectId, data),
    onSuccess: (testRun) => {
      void queryClient.invalidateQueries({ queryKey: testRunKeys.project(projectId) });
      void queryClient.setQueryData(testRunKeys.detail(testRun.id), testRun);
    },
  });
}
