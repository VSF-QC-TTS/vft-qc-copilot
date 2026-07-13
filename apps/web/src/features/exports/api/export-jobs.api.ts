import { apiClient } from '@/shared/api/api';
import {
  exportJobResponseSchema,
  type CreateDatasetExportRequest,
  type CreateTestRunExportRequest,
} from '@/features/exports/types/export-job';

export async function createDatasetExportJob(
  datasetVersionId: string,
  data: CreateDatasetExportRequest = { format: 'XLSX' },
) {
  const response = await apiClient.requestJson(
    `/api/v1/dataset-versions/${datasetVersionId}/export-jobs`,
    exportJobResponseSchema,
    {
      method: 'POST',
      body: JSON.stringify({ format: data.format ?? 'XLSX' }),
    },
  );
  return response.data;
}

export async function createTestRunExportJob(
  testRunId: string,
  data: CreateTestRunExportRequest = { format: 'XLSX', resultMode: 'FINAL' },
) {
  const response = await apiClient.requestJson(
    `/api/v1/test-runs/${testRunId}/export-jobs`,
    exportJobResponseSchema,
    {
      method: 'POST',
      body: JSON.stringify({
        format: data.format ?? 'XLSX',
        resultMode: data.resultMode ?? 'FINAL',
      }),
    },
  );
  return response.data;
}

export async function getExportJob(jobId: string, signal?: AbortSignal) {
  const response = await apiClient.requestJson(
    `/api/v1/export-jobs/${jobId}`,
    exportJobResponseSchema,
    { ...(signal ? { signal } : {}) },
  );
  return response.data;
}

export async function downloadExportJobFile(jobId: string, signal?: AbortSignal) {
  const response = await apiClient.requestBlob(`/api/v1/export-jobs/${jobId}/download`, {
    ...(signal ? { signal } : {}),
  });
  return response.data;
}
