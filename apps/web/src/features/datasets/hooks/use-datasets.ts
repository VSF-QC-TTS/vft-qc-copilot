import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  addDatasetRow,
  createDataset,
  createDatasetDraft,
  deleteDataset,
  discardDatasetVersion,
  getDataset,
  getDatasetRows,
  getDatasetVersions,
  getProjectDatasetSchema,
  getProjectDatasets,
  patchDatasetRow,
  publishDatasetVersion,
} from '@/features/datasets/api/datasets.api';
import type {
  CreateDatasetRequest,
  CreateDatasetRowRequest,
  PatchDatasetRowCellsRequest,
} from '@/features/datasets/types/dataset';

export const datasetKeys = {
  all: ['datasets'] as const,
  project: (projectId: string) => [...datasetKeys.all, 'project', projectId] as const,
  schema: (projectId: string) => [...datasetKeys.all, 'schema', projectId] as const,
  detail: (datasetId: string) => [...datasetKeys.all, 'detail', datasetId] as const,
  versions: (datasetId: string) => [...datasetKeys.detail(datasetId), 'versions'] as const,
  rows: (versionId: string) => [...datasetKeys.all, 'version', versionId, 'rows'] as const,
};

export function useProjectDatasetSchema(projectId: string) {
  return useQuery({
    queryKey: datasetKeys.schema(projectId),
    queryFn: ({ signal }) => getProjectDatasetSchema(projectId, signal),
    enabled: Boolean(projectId),
  });
}

export function useProjectDatasets(projectId: string) {
  return useQuery({
    queryKey: datasetKeys.project(projectId),
    queryFn: ({ signal }) => getProjectDatasets(projectId, signal),
    enabled: Boolean(projectId),
  });
}

export function useDataset(datasetId: string) {
  return useQuery({
    queryKey: datasetKeys.detail(datasetId),
    queryFn: ({ signal }) => getDataset(datasetId, signal),
    enabled: Boolean(datasetId),
  });
}

export function useDatasetVersions(datasetId: string) {
  return useQuery({
    queryKey: datasetKeys.versions(datasetId),
    queryFn: ({ signal }) => getDatasetVersions(datasetId, signal),
    enabled: Boolean(datasetId),
  });
}

export function useDatasetRows(versionId: string) {
  return useQuery({
    queryKey: datasetKeys.rows(versionId),
    queryFn: ({ signal }) => getDatasetRows(versionId, signal),
    enabled: Boolean(versionId),
  });
}

export function useCreateDataset(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateDatasetRequest) => createDataset(projectId, data),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: datasetKeys.project(projectId) });
    },
  });
}

export function useDeleteDataset(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (datasetId: string) => deleteDataset(datasetId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: datasetKeys.project(projectId) });
    },
  });
}

export function useCreateDatasetDraft(datasetId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => createDatasetDraft(datasetId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: datasetKeys.versions(datasetId) });
      void queryClient.invalidateQueries({ queryKey: datasetKeys.detail(datasetId) });
    },
  });
}

export function useAddDatasetRow(versionId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateDatasetRowRequest) => addDatasetRow(versionId, data),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: datasetKeys.rows(versionId) });
    },
  });
}

export function usePatchDatasetRow(versionId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ rowId, data }: { rowId: number; data: PatchDatasetRowCellsRequest }) =>
      patchDatasetRow(rowId, data),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: datasetKeys.rows(versionId) });
    },
  });
}

export function usePublishDatasetVersion(datasetId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (versionId: string) => publishDatasetVersion(versionId),
    onSuccess: (_, versionId) => {
      void queryClient.invalidateQueries({ queryKey: datasetKeys.versions(datasetId) });
      void queryClient.invalidateQueries({ queryKey: datasetKeys.detail(datasetId) });
      void queryClient.invalidateQueries({ queryKey: datasetKeys.rows(versionId) });
    },
  });
}

export function useDiscardDatasetVersion(datasetId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (versionId: string) => discardDatasetVersion(versionId),
    onSuccess: (_, versionId) => {
      void queryClient.invalidateQueries({ queryKey: datasetKeys.versions(datasetId) });
      void queryClient.invalidateQueries({ queryKey: datasetKeys.detail(datasetId) });
      void queryClient.invalidateQueries({ queryKey: datasetKeys.rows(versionId) });
    },
  });
}
