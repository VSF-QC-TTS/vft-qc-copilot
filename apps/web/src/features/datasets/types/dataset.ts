import { z } from 'zod';

const userSummarySchema = z.object({
  id: z.string().uuid(),
  email: z.string(),
  name: z.string(),
  avatarUrl: z.string().nullable().optional(),
});

export const datasetScopeSchema = z.enum(['PROJECT', 'WORKSPACE', 'PERSONAL']);
export const datasetVersionStatusSchema = z.enum(['DRAFT', 'ACTIVE', 'ARCHIVED', 'DISCARDED']);
export const datasetColumnDataTypeSchema = z.enum([
  'TEXT',
  'NUMBER',
  'BOOLEAN',
  'JSON',
  'DATE',
  'DATETIME',
]);
export const datasetSchemaScopeSchema = z.enum(['PROJECT', 'WORKSPACE', 'PERSONAL']);
export const datasetSourceTypeSchema = z.enum([
  'MANUAL',
  'EXCEL',
  'PROJECT_CLONE',
  'WORKSPACE_DATASET',
  'PERSONAL_DATASET',
]);
export const datasetImportStatusSchema = z.enum([
  'PENDING',
  'PREVIEWED',
  'IMPORTING',
  'COMPLETED',
  'FAILED',
  'CANCELLED',
]);
export const datasetImportTargetSchemaModeSchema = z.enum([
  'USE_CURRENT_SCHEMA',
  'EXTEND_CURRENT_SCHEMA',
  'CREATE_NEW_SCHEMA_VERSION',
]);
export const datasetImportMappingActionSchema = z.enum([
  'MAP_TO_EXISTING_COLUMN',
  'ADD_AS_NEW_COLUMN',
  'IGNORE',
]);
export const datasetImportSchemaCompatibilityStatusSchema = z.enum([
  'COMPATIBLE',
  'REQUIRES_MAPPING',
  'INCOMPATIBLE',
]);

export type DatasetVersionStatus = z.infer<typeof datasetVersionStatusSchema>;
export type DatasetColumnDataType = z.infer<typeof datasetColumnDataTypeSchema>;
export type DatasetImportTargetSchemaMode = z.infer<typeof datasetImportTargetSchemaModeSchema>;
export type DatasetImportMappingAction = z.infer<typeof datasetImportMappingActionSchema>;

export const datasetResponseSchema = z.object({
  id: z.string().uuid(),
  scope: datasetScopeSchema,
  projectId: z.string().uuid(),
  currentVersionId: z.string().uuid().nullable().optional(),
  name: z.string(),
  description: z.string().nullable().optional(),
  createdBy: z.string().uuid(),
  createdByUser: userSummarySchema.nullable().optional(),
  createdAt: z.string(),
  updatedAt: z.string(),
});
export type DatasetResponse = z.infer<typeof datasetResponseSchema>;

export const datasetCollectionSchema = z.object({
  items: z.array(datasetResponseSchema),
});

export const datasetVersionSchema = z.object({
  id: z.string().uuid(),
  datasetId: z.string().uuid(),
  version: z.number(),
  schemaId: z.string().uuid(),
  schemaVersion: z.number(),
  rowCount: z.number(),
  status: datasetVersionStatusSchema,
  createdBy: z.string().uuid(),
  createdByUser: userSummarySchema.nullable().optional(),
  createdAt: z.string(),
  publishedAt: z.string().nullable().optional(),
  discardedAt: z.string().nullable().optional(),
});
export type DatasetVersionResponse = z.infer<typeof datasetVersionSchema>;

export const datasetVersionCollectionSchema = z.object({
  items: z.array(datasetVersionSchema),
});

export const datasetRowSchema = z.object({
  id: z.number(),
  datasetVersionId: z.string().uuid(),
  rowIndex: z.number(),
  cells: z.record(z.string(), z.unknown()),
  revision: z.number(),
  createdAt: z.string(),
  updatedAt: z.string(),
});
export type DatasetRowResponse = z.infer<typeof datasetRowSchema>;

export const datasetRowCollectionSchema = z.object({
  items: z.array(datasetRowSchema),
  page: z.object({
    limit: z.number(),
    nextCursor: z.string().nullable(),
    hasMore: z.boolean(),
  }),
});
export type DatasetRowCollectionResponse = z.infer<typeof datasetRowCollectionSchema>;

export const datasetSchemaColumnSchema = z.object({
  id: z.number(),
  name: z.string(),
  displayName: z.string(),
  dataType: datasetColumnDataTypeSchema,
  required: z.boolean(),
  description: z.string().nullable().optional(),
  orderIndex: z.number(),
  variable: z.string(),
});
export type DatasetSchemaColumnResponse = z.infer<typeof datasetSchemaColumnSchema>;

export const datasetSchemaResponseSchema = z.object({
  id: z.string().uuid(),
  scope: datasetSchemaScopeSchema,
  projectId: z.string().uuid(),
  version: z.number(),
  name: z.string(),
  columns: z.array(datasetSchemaColumnSchema),
  createdBy: z.string().uuid(),
  createdByUser: userSummarySchema.nullable().optional(),
  createdAt: z.string(),
});
export type DatasetSchemaResponse = z.infer<typeof datasetSchemaResponseSchema>;

export const createDatasetRequestSchema = z.object({
  name: z.string().min(1, 'Vui lòng nhập tên Dataset').max(160, 'Tên tối đa 160 ký tự'),
  description: z.string().max(1000, 'Mô tả tối đa 1000 ký tự').optional().or(z.literal('')),
});
export type CreateDatasetRequest = z.infer<typeof createDatasetRequestSchema>;

export const createDatasetRowRequestSchema = z.object({
  cells: z.record(z.string(), z.unknown()),
});
export type CreateDatasetRowRequest = z.infer<typeof createDatasetRowRequestSchema>;

export const patchDatasetRowCellsRequestSchema = z.object({
  cells: z.record(z.string(), z.unknown()),
  expectedRevision: z.number(),
});
export type PatchDatasetRowCellsRequest = z.infer<typeof patchDatasetRowCellsRequestSchema>;

export const datasetImportPreviewRowSchema = z.object({
  rowIndex: z.number(),
  data: z.record(z.string(), z.unknown()),
});
export type DatasetImportPreviewRow = z.infer<typeof datasetImportPreviewRowSchema>;

export const datasetImportPreviewSchema = z.object({
  sheetName: z.string(),
  availableSheets: z.array(z.string()),
  columns: z.array(z.string()),
  rows: z.array(datasetImportPreviewRowSchema),
  totalRows: z.number(),
  previewRowCount: z.number(),
});
export type DatasetImportPreview = z.infer<typeof datasetImportPreviewSchema>;

export const datasetImportSchemaCompatibilitySchema = z.object({
  status: datasetImportSchemaCompatibilityStatusSchema,
  requiresMapping: z.boolean(),
  sourceColumnCount: z.number(),
  targetColumnCount: z.number(),
  missingRequiredColumns: z.array(z.string()).optional(),
  extraSourceColumns: z.array(z.string()).optional(),
  warnings: z.array(z.string()).optional(),
});
export type DatasetImportSchemaCompatibility = z.infer<
  typeof datasetImportSchemaCompatibilitySchema
>;

export const datasetImportMappingItemSchema = z.object({
  sourceColumn: z.string().min(1, 'Thiếu source column'),
  action: datasetImportMappingActionSchema,
  targetColumn: z.string().optional(),
  displayName: z.string().optional(),
  dataType: datasetColumnDataTypeSchema.optional(),
  required: z.boolean().optional(),
  description: z.string().optional(),
});
export type DatasetImportMappingItem = z.infer<typeof datasetImportMappingItemSchema>;

export const datasetImportMappingAuditSchema = z.object({
  targetSchemaMode: datasetImportTargetSchemaModeSchema.optional(),
  items: z.array(datasetImportMappingItemSchema),
});
export type DatasetImportMappingAudit = z.infer<typeof datasetImportMappingAuditSchema>;

export const datasetImportJobResponseSchema = z.object({
  id: z.string().uuid(),
  targetProjectId: z.string().uuid(),
  targetDatasetId: z.string().uuid().nullable().optional(),
  sourceType: datasetSourceTypeSchema,
  sourceFileName: z.string().nullable().optional(),
  status: datasetImportStatusSchema,
  schemaCompatibility: datasetImportSchemaCompatibilitySchema,
  preview: datasetImportPreviewSchema,
  mapping: datasetImportMappingAuditSchema.nullable().optional(),
  errorMessage: z.string().nullable().optional(),
  createdBy: z.string().uuid(),
  createdByUser: userSummarySchema.nullable().optional(),
  createdAt: z.string(),
  finishedAt: z.string().nullable().optional(),
});
export type DatasetImportJobResponse = z.infer<typeof datasetImportJobResponseSchema>;

export const confirmDatasetImportJobRequestSchema = z.object({
  datasetName: z.string().min(1, 'Vui lòng nhập tên Dataset').max(160, 'Tên tối đa 160 ký tự'),
  datasetDescription: z.string().max(1000, 'Mô tả tối đa 1000 ký tự').optional().or(z.literal('')),
  targetSchemaMode: datasetImportTargetSchemaModeSchema,
  mapping: z.array(datasetImportMappingItemSchema).min(1, 'Cần mapping ít nhất một cột'),
});
export type ConfirmDatasetImportJobRequest = z.infer<typeof confirmDatasetImportJobRequestSchema>;
