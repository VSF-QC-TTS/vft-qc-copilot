import { apiClient } from '@/shared/api/api';
import {
  runPreflightResponseSchema,
  testCaseResultDetailResponseSchema,
  testRunCollectionSchema,
  testRunDetailResponseSchema,
  testRunResultPageResponseSchema,
  type CreateTestRunRequest,
  type ResultMode,
  type RunPreflightRequest,
} from '@/features/test-runs/types/test-run';

export async function preflightTestRun(projectId: string, data: RunPreflightRequest) {
  const response = await apiClient.requestJson(
    `/api/v1/projects/${projectId}/test-runs/preflight`,
    runPreflightResponseSchema,
    { method: 'POST', body: JSON.stringify(data) },
  );
  return response.data;
}

export async function createTestRun(projectId: string, data: CreateTestRunRequest) {
  const response = await apiClient.requestJson(
    `/api/v1/projects/${projectId}/test-runs`,
    testRunDetailResponseSchema,
    { method: 'POST', body: JSON.stringify(data) },
  );
  return response.data;
}

export async function getProjectTestRuns(projectId: string, signal?: AbortSignal) {
  const response = await apiClient.requestJson(
    `/api/v1/projects/${projectId}/test-runs`,
    testRunCollectionSchema,
    { ...(signal && { signal }) },
  );
  return response.data.items;
}

export async function getTestRun(testRunId: string, signal?: AbortSignal) {
  const response = await apiClient.requestJson(
    `/api/v1/test-runs/${testRunId}`,
    testRunDetailResponseSchema,
    { ...(signal && { signal }) },
  );
  return response.data;
}

export async function cancelTestRun(testRunId: string, reason?: string) {
  const response = await apiClient.requestJson(
    `/api/v1/test-runs/${testRunId}/cancel`,
    testRunDetailResponseSchema,
    {
      method: 'POST',
      body: JSON.stringify({ reason: reason?.trim() || undefined }),
    },
  );
  return response.data;
}

export interface GetTestRunResultsParams {
  mode: ResultMode;
  page: number;
  size: number;
  signal?: AbortSignal;
}

export async function getTestRunResults(
  testRunId: string,
  { mode, page, size, signal }: GetTestRunResultsParams,
) {
  const searchParams = new URLSearchParams({
    mode,
    page: String(page),
    size: String(size),
  });
  const response = await apiClient.requestJson(
    `/api/v1/test-runs/${testRunId}/results?${searchParams.toString()}`,
    testRunResultPageResponseSchema,
    { ...(signal ? { signal } : {}) },
  );
  return response.data;
}

export async function getTestCaseResult(resultId: number, mode: ResultMode, signal?: AbortSignal) {
  const searchParams = new URLSearchParams({ mode });
  const response = await apiClient.requestJson(
    `/api/v1/test-case-results/${String(resultId)}?${searchParams.toString()}`,
    testCaseResultDetailResponseSchema,
    { ...(signal ? { signal } : {}) },
  );
  return response.data;
}
