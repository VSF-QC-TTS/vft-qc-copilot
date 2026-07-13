import type {
  ConfirmDatasetImportJobRequest,
  DatasetImportJobResponse,
  DatasetImportMappingItem,
  DatasetImportTargetSchemaMode,
  DatasetSchemaColumnResponse,
} from '@/features/datasets/types/dataset';

export const DEFAULT_IMPORT_PREVIEW_ROWS = 10;
export const MAX_IMPORT_PREVIEW_ROWS = 20;
export const MAX_IMPORT_ROWS = 500;

export interface DatasetImportDraft {
  datasetName: string;
  datasetDescription: string;
  targetSchemaMode: DatasetImportTargetSchemaMode;
  mapping: DatasetImportMappingItem[];
}

export interface DatasetImportValidation {
  canConfirm: boolean;
  messages: string[];
}

export function previewRowCount(value: number) {
  if (Number.isNaN(value)) return DEFAULT_IMPORT_PREVIEW_ROWS;
  return Math.min(Math.max(Math.trunc(value), 1), MAX_IMPORT_PREVIEW_ROWS);
}

export function importMappingFromJob(
  job: DatasetImportJobResponse,
  schemaColumns: DatasetSchemaColumnResponse[],
) {
  const schemaNames = new Set(schemaColumns.map((column) => column.name));
  const suggested = new Map(
    (job.mapping?.items ?? []).map((item) => [item.sourceColumn, item] as const),
  );

  return job.preview.columns.map((sourceColumn) => {
    const existing = suggested.get(sourceColumn);
    if (existing) {
      return normalizeMappingItem(existing);
    }
    if (schemaNames.has(sourceColumn)) {
      return {
        sourceColumn,
        action: 'MAP_TO_EXISTING_COLUMN',
        targetColumn: sourceColumn,
      } satisfies DatasetImportMappingItem;
    }
    return {
      sourceColumn,
      action: 'IGNORE',
    } satisfies DatasetImportMappingItem;
  });
}

export function validateImportDraft(
  job: DatasetImportJobResponse,
  draft: DatasetImportDraft,
): DatasetImportValidation {
  const messages: string[] = [];
  const sources = new Set(job.preview.columns);
  const mappedSources = new Set<string>();
  const activeTargets = new Set<string>();
  const hasNewColumns = draft.mapping.some((item) => item.action === 'ADD_AS_NEW_COLUMN');

  if (job.preview.totalRows > MAX_IMPORT_ROWS) {
    messages.push(`File vượt quá giới hạn ${MAX_IMPORT_ROWS} dòng import.`);
  }

  if (!draft.datasetName.trim()) {
    messages.push('Vui lòng nhập tên Dataset.');
  }

  for (const item of draft.mapping) {
    const sourceColumn = item.sourceColumn.trim();
    if (!sources.has(sourceColumn)) {
      messages.push(`Source column không tồn tại: ${sourceColumn}`);
      continue;
    }
    if (mappedSources.has(sourceColumn)) {
      messages.push(`Source column bị mapping trùng: ${sourceColumn}`);
    }
    mappedSources.add(sourceColumn);

    if (item.action === 'IGNORE') {
      continue;
    }

    const targetColumn = item.targetColumn?.trim();
    if (!targetColumn) {
      messages.push(`Cần chọn target column cho ${sourceColumn}.`);
      continue;
    }

    if (activeTargets.has(targetColumn)) {
      messages.push(`Target column bị mapping trùng: ${targetColumn}`);
    }
    activeTargets.add(targetColumn);

    if (item.action === 'ADD_AS_NEW_COLUMN' && !item.displayName?.trim()) {
      messages.push(`Cần nhập display name cho cột mới ${targetColumn}.`);
    }
  }

  for (const sourceColumn of sources) {
    if (!mappedSources.has(sourceColumn)) {
      messages.push(`Chưa mapping source column: ${sourceColumn}`);
    }
  }

  if (hasNewColumns && draft.targetSchemaMode === 'USE_CURRENT_SCHEMA') {
    messages.push('Cột mới cần chế độ tạo schema version mới.');
  }

  return {
    canConfirm: messages.length === 0,
    messages,
  };
}

export function buildConfirmImportRequest(
  draft: DatasetImportDraft,
): ConfirmDatasetImportJobRequest {
  return {
    datasetName: draft.datasetName.trim(),
    datasetDescription: draft.datasetDescription.trim(),
    targetSchemaMode: draft.targetSchemaMode,
    mapping: draft.mapping.map(normalizeMappingItem),
  };
}

export function normalizeMappingItem(item: DatasetImportMappingItem): DatasetImportMappingItem {
  if (item.action === 'IGNORE') {
    return {
      sourceColumn: item.sourceColumn,
      action: 'IGNORE',
    };
  }

  const targetColumn = item.targetColumn?.trim() ?? '';
  if (item.action === 'ADD_AS_NEW_COLUMN') {
    return {
      sourceColumn: item.sourceColumn,
      action: 'ADD_AS_NEW_COLUMN',
      targetColumn,
      displayName: item.displayName?.trim() || targetColumn,
      dataType: item.dataType ?? 'TEXT',
      required: item.required ?? false,
      ...(item.description?.trim() ? { description: item.description.trim() } : {}),
    };
  }

  return {
    sourceColumn: item.sourceColumn,
    action: 'MAP_TO_EXISTING_COLUMN',
    targetColumn,
  };
}

export function hasNewSchemaColumns(mapping: DatasetImportMappingItem[]) {
  return mapping.some((item) => item.action === 'ADD_AS_NEW_COLUMN');
}
