import { apiClient } from '@/shared/api/api';
import {
  runPreflightResponseSchema,
  testRunCollectionSchema,
  testRunDetailResponseSchema,
  type CreateTestRunRequest,
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
