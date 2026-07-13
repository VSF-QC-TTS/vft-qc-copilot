import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  cancelTestRun,
  createTestRun,
  getProjectTestRuns,
  getTestCaseResult,
  getTestRun,
  getTestRunResults,
  preflightTestRun,
} from '@/features/test-runs/api/test-runs.api';
import type {
  CreateTestRunRequest,
  ResultMode,
  RunPreflightRequest,
} from '@/features/test-runs/types/test-run';

export const testRunKeys = {
  all: ['test-runs'] as const,
  project: (projectId: string) => [...testRunKeys.all, 'project', projectId] as const,
  detail: (testRunId: string) => [...testRunKeys.all, 'detail', testRunId] as const,
  results: (testRunId: string) => [...testRunKeys.all, 'results', testRunId] as const,
  resultsPage: (testRunId: string, mode: ResultMode, page: number, size: number) =>
    [...testRunKeys.results(testRunId), mode, page, size] as const,
  resultDetail: (resultId: number, mode: ResultMode) =>
    [...testRunKeys.all, 'result-detail', resultId, mode] as const,
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

export function useTestRunResults(testRunId: string, mode: ResultMode, page: number, size: number) {
  return useQuery({
    queryKey: testRunKeys.resultsPage(testRunId, mode, page, size),
    queryFn: ({ signal }) => getTestRunResults(testRunId, { mode, page, size, signal }),
    enabled: Boolean(testRunId),
  });
}

export function useTestCaseResult(resultId: number | undefined, mode: ResultMode) {
  return useQuery({
    queryKey: resultId === undefined ? testRunKeys.all : testRunKeys.resultDetail(resultId, mode),
    queryFn: ({ signal }) => {
      if (resultId === undefined) {
        throw new Error('Result ID is required');
      }
      return getTestCaseResult(resultId, mode, signal);
    },
    enabled: resultId !== undefined,
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

export function useCancelTestRun(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ testRunId, reason }: { testRunId: string; reason?: string }) =>
      cancelTestRun(testRunId, reason),
    onSuccess: (testRun) => {
      void queryClient.invalidateQueries({ queryKey: testRunKeys.project(projectId) });
      void queryClient.setQueryData(testRunKeys.detail(testRun.id), testRun);
    },
  });
}
