import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createDatasetExportJob,
  createTestRunExportJob,
  downloadExportJobFile,
  getExportJob,
} from '@/features/exports/api/export-jobs.api';
import { exportJobRefetchInterval } from '@/features/exports/components/export-job-utils';
import type {
  CreateDatasetExportRequest,
  CreateTestRunExportRequest,
  ExportJobResponse,
} from '@/features/exports/types/export-job';

export const exportJobKeys = {
  all: ['export-jobs'] as const,
  detail: (jobId: string) => [...exportJobKeys.all, 'detail', jobId] as const,
};

export function useExportJob(jobId: string | undefined) {
  return useQuery({
    queryKey: jobId ? exportJobKeys.detail(jobId) : exportJobKeys.all,
    queryFn: ({ signal }) => {
      if (!jobId) {
        throw new Error('Export job ID is required');
      }
      return getExportJob(jobId, signal);
    },
    enabled: Boolean(jobId),
    refetchInterval: (query) => exportJobRefetchInterval(query.state.data),
  });
}

export function useCreateDatasetExportJob(datasetVersionId: string | undefined) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data?: CreateDatasetExportRequest) => {
      if (!datasetVersionId) {
        throw new Error('Dataset version ID is required');
      }
      return createDatasetExportJob(datasetVersionId, data);
    },
    onSuccess: (job) => {
      cacheExportJob(queryClient, job);
    },
  });
}

export function useCreateTestRunExportJob(testRunId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data?: CreateTestRunExportRequest) => createTestRunExportJob(testRunId, data),
    onSuccess: (job) => {
      cacheExportJob(queryClient, job);
    },
  });
}

export function useDownloadExportJobFile() {
  return useMutation({
    mutationFn: (jobId: string) => downloadExportJobFile(jobId),
  });
}

function cacheExportJob(
  queryClient: ReturnType<typeof useQueryClient>,
  job: ExportJobResponse,
): void {
  void queryClient.setQueryData(exportJobKeys.detail(job.id), job);
}
