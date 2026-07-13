import { apiClient } from '@/shared/api/api';
import {
  datasetCollectionSchema,
  datasetResponseSchema,
  datasetRowCollectionSchema,
  datasetRowSchema,
  datasetSchemaResponseSchema,
  datasetVersionCollectionSchema,
  datasetVersionSchema,
  type CreateDatasetRequest,
  type CreateDatasetRowRequest,
  type PatchDatasetRowCellsRequest,
} from '@/features/datasets/types/dataset';

export async function getProjectDatasetSchema(projectId: string, signal?: AbortSignal) {
  const response = await apiClient.requestJson(
    `/api/v1/projects/${projectId}/dataset-schema`,
    datasetSchemaResponseSchema,
    { ...(signal && { signal }) },
  );
  return response.data;
}

export async function getProjectDatasets(projectId: string, signal?: AbortSignal) {
  const response = await apiClient.requestJson(
    `/api/v1/projects/${projectId}/datasets`,
    datasetCollectionSchema,
    { ...(signal && { signal }) },
  );
  return response.data.items;
}

export async function createDataset(projectId: string, data: CreateDatasetRequest) {
  const response = await apiClient.requestJson(
    `/api/v1/projects/${projectId}/datasets`,
    datasetResponseSchema,
    { method: 'POST', body: JSON.stringify(data) },
  );
  return response.data;
}

export async function getDataset(datasetId: string, signal?: AbortSignal) {
  const response = await apiClient.requestJson(
    `/api/v1/datasets/${datasetId}`,
    datasetResponseSchema,
    { ...(signal && { signal }) },
  );
  return response.data;
}

export async function deleteDataset(datasetId: string) {
  await apiClient.requestVoid(`/api/v1/datasets/${datasetId}`, { method: 'DELETE' });
}

export async function getDatasetVersions(datasetId: string, signal?: AbortSignal) {
  const response = await apiClient.requestJson(
    `/api/v1/datasets/${datasetId}/versions`,
    datasetVersionCollectionSchema,
    { ...(signal && { signal }) },
  );
  return response.data.items;
}

export async function createDatasetDraft(datasetId: string) {
  const response = await apiClient.requestJson(
    `/api/v1/datasets/${datasetId}/drafts`,
    datasetVersionSchema,
    { method: 'POST' },
  );
  return response.data;
}

export async function getDatasetRows(versionId: string, signal?: AbortSignal) {
  const response = await apiClient.requestJson(
    `/api/v1/dataset-versions/${versionId}/rows`,
    datasetRowCollectionSchema,
    { ...(signal && { signal }) },
  );
  return response.data.items;
}

export async function addDatasetRow(versionId: string, data: CreateDatasetRowRequest) {
  const response = await apiClient.requestJson(
    `/api/v1/dataset-versions/${versionId}/rows`,
    datasetRowSchema,
    { method: 'POST', body: JSON.stringify(data) },
  );
  return response.data;
}

export async function patchDatasetRow(rowId: number, data: PatchDatasetRowCellsRequest) {
  const response = await apiClient.requestJson(
    `/api/v1/dataset-rows/${String(rowId)}`,
    datasetRowSchema,
    { method: 'PATCH', body: JSON.stringify(data) },
  );
  return response.data;
}

export async function publishDatasetVersion(versionId: string) {
  const response = await apiClient.requestJson(
    `/api/v1/dataset-versions/${versionId}/publish`,
    datasetVersionSchema,
    { method: 'POST' },
  );
  return response.data;
}

export async function discardDatasetVersion(versionId: string) {
  const response = await apiClient.requestJson(
    `/api/v1/dataset-versions/${versionId}/discard`,
    datasetVersionSchema,
    { method: 'POST' },
  );
  return response.data;
}
