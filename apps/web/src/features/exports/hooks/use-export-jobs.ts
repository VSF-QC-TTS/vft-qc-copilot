import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createDatasetExportJob,
  createTestRunExportJob,
  downloadExportJobFile,
  getExportJob,
  listDatasetExportJobs,
  listTestRunExportJobs,
} from '@/features/exports/api/export-jobs.api';
import { exportJobRefetchInterval } from '@/features/exports/components/export-job-utils';
import type {
  CreateDatasetExportRequest,
  CreateTestRunExportRequest,
  ExportJobResponse,
} from '@/features/exports/types/export-job';

export const exportJobKeys = {
  all: ['export-jobs'] as const,
  lists: () => [...exportJobKeys.all, 'list'] as const,
  datasetList: (datasetVersionId: string, size: number) =>
    [...exportJobKeys.lists(), 'dataset-version', datasetVersionId, size] as const,
  testRunList: (testRunId: string, size: number) =>
    [...exportJobKeys.lists(), 'test-run', testRunId, size] as const,
  detail: (jobId: string) => [...exportJobKeys.all, 'detail', jobId] as const,
};

type ExportJobsSource =
  | {
      sourceType: 'dataset-version';
      sourceId: string | undefined;
    }
  | {
      sourceType: 'test-run';
      sourceId: string | undefined;
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

export function useRecentExportJobs(source: ExportJobsSource, size = 5) {
  const sourceId = source.sourceId;
  return useQuery({
    queryKey:
      source.sourceType === 'dataset-version'
        ? exportJobKeys.datasetList(sourceId ?? '', size)
        : exportJobKeys.testRunList(sourceId ?? '', size),
    queryFn: ({ signal }) => {
      if (!sourceId) {
        throw new Error('Export source ID is required');
      }
      return source.sourceType === 'dataset-version'
        ? listDatasetExportJobs(sourceId, { size, signal })
        : listTestRunExportJobs(sourceId, { size, signal });
    },
    enabled: Boolean(sourceId),
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
      void queryClient.invalidateQueries({ queryKey: exportJobKeys.lists() });
    },
  });
}

export function useCreateTestRunExportJob(testRunId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data?: CreateTestRunExportRequest) => createTestRunExportJob(testRunId, data),
    onSuccess: (job) => {
      cacheExportJob(queryClient, job);
      void queryClient.invalidateQueries({ queryKey: exportJobKeys.lists() });
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
