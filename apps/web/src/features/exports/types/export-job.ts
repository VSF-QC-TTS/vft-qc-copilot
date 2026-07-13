import { z } from 'zod';

export const exportFormatSchema = z.enum(['XLSX']);
export type ExportFormat = z.infer<typeof exportFormatSchema>;

export const exportResultModeSchema = z.enum(['FINAL', 'MACHINE']);
export type ExportResultMode = z.infer<typeof exportResultModeSchema>;

export const exportJobStatusSchema = z.enum([
  'PENDING',
  'RUNNING',
  'COMPLETED',
  'FAILED',
  'CANCELLED',
  'EXPIRED',
]);
export type ExportJobStatus = z.infer<typeof exportJobStatusSchema>;

export const exportJobTypeSchema = z.enum(['DATASET_VERSION', 'TEST_RUN_RESULTS']);
export type ExportJobType = z.infer<typeof exportJobTypeSchema>;

export const createDatasetExportRequestSchema = z.object({
  format: exportFormatSchema.optional(),
});
export type CreateDatasetExportRequest = z.infer<typeof createDatasetExportRequestSchema>;

export const createTestRunExportRequestSchema = z.object({
  format: exportFormatSchema.optional(),
  resultMode: exportResultModeSchema.optional(),
});
export type CreateTestRunExportRequest = z.infer<typeof createTestRunExportRequestSchema>;

export const exportJobResponseSchema = z.object({
  id: z.string().uuid(),
  jobType: exportJobTypeSchema,
  format: exportFormatSchema,
  status: exportJobStatusSchema,
  datasetVersionId: z.string().uuid().nullable().optional(),
  testRunId: z.string().uuid().nullable().optional(),
  resultMode: exportResultModeSchema.nullable().optional(),
  fileName: z.string().nullable().optional(),
  fileSizeBytes: z.number().nullable().optional(),
  errorCode: z.string().nullable().optional(),
  errorMessage: z.string().nullable().optional(),
  createdBy: z.string().uuid(),
  createdAt: z.string(),
  startedAt: z.string().nullable().optional(),
  finishedAt: z.string().nullable().optional(),
  expiresAt: z.string().nullable().optional(),
});
export type ExportJobResponse = z.infer<typeof exportJobResponseSchema>;
