import type {
  DatasetRowResponse,
  DatasetSchemaColumnResponse,
  DatasetVersionResponse,
  DatasetVersionStatus,
  PatchDatasetRowCellsRequest,
} from '@/features/datasets/types/dataset';

export function versionStatusLabel(status: DatasetVersionStatus) {
  if (status === 'ACTIVE') return 'Đang hoạt động';
  if (status === 'ARCHIVED') return 'Đã lưu trữ';
  if (status === 'DISCARDED') return 'Đã hủy';
  return 'Bản nháp';
}

export function isDraftVersion(version: DatasetVersionResponse | undefined) {
  return version?.status === 'DRAFT';
}

export function preferredVersion(versions: DatasetVersionResponse[] | undefined) {
  return (
    versions?.find((version) => version.status === 'DRAFT') ??
    versions?.find((version) => version.status === 'ACTIVE') ??
    versions?.[0]
  );
}

export function defaultCells(columns: DatasetSchemaColumnResponse[]) {
  return Object.fromEntries(columns.map((column) => [column.name, '']));
}

export function rowPatchRequest(
  row: DatasetRowResponse,
  cells: Record<string, string>,
): PatchDatasetRowCellsRequest {
  return {
    cells,
    expectedRevision: row.revision,
  };
}
