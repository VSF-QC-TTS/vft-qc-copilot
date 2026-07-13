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

export type DatasetVersionStatus = z.infer<typeof datasetVersionStatusSchema>;

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
});

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
