import { z } from 'zod';

const userSummarySchema = z.object({
  id: z.string().uuid(),
  email: z.string(),
  name: z.string(),
  avatarUrl: z.string().nullable().optional(),
});

export const chatbotTargetStatusSchema = z.enum([
  'DRAFT',
  'READY',
  'ACTIVE',
  'DISABLED',
  'DELETED',
]);
export type ChatbotTargetStatus = z.infer<typeof chatbotTargetStatusSchema>;

export const targetBodyTypeSchema = z.enum(['NONE', 'JSON', 'TEXT']);
export type TargetBodyType = z.infer<typeof targetBodyTypeSchema>;

export const bindingLocationSchema = z.enum(['HEADER', 'QUERY', 'BODY_JSON']);
export type BindingLocation = z.infer<typeof bindingLocationSchema>;

export const extractionSourceSchema = z.enum(['JSON_PATH', 'BODY_TEXT']);
export type ExtractionSource = z.infer<typeof extractionSourceSchema>;

export const secretBindingSchema = z.object({
  location: bindingLocationSchema,
  keyOrPath: z.string().min(1, 'Vui lòng nhập key/path'),
  secretId: z.string().uuid('Secret không hợp lệ'),
});
export type SecretBinding = z.infer<typeof secretBindingSchema>;

export const datasetMappingSchema = z.object({
  location: bindingLocationSchema,
  keyOrPath: z.string().min(1, 'Vui lòng nhập key/path'),
  columnName: z.string().min(1, 'Vui lòng nhập tên cột dataset'),
});
export type DatasetMapping = z.infer<typeof datasetMappingSchema>;

export const responseExtractionFieldSchema = z.object({
  fieldName: z.string().min(1, 'Vui lòng nhập tên field'),
  source: extractionSourceSchema,
  path: z.string().optional().or(z.literal('')),
  required: z.boolean(),
});
export type ResponseExtractionField = z.infer<typeof responseExtractionFieldSchema>;

export const responseExtractionConfigSchema = z.object({
  fields: z.array(responseExtractionFieldSchema),
});
export type ResponseExtractionConfig = z.infer<typeof responseExtractionConfigSchema>;

export const targetRequestConfigSchema = z.object({
  method: z.string().nullable().optional(),
  url: z.string().nullable().optional(),
  headers: z.record(z.string(), z.string()).nullable().optional(),
  queryParams: z.record(z.string(), z.string()).nullable().optional(),
  bodyType: targetBodyTypeSchema.nullable().optional(),
  body: z.string().nullable().optional(),
  secretBindings: z.array(secretBindingSchema).nullable().optional(),
  datasetMappings: z.array(datasetMappingSchema).nullable().optional(),
});
export type TargetRequestConfig = z.infer<typeof targetRequestConfigSchema>;

export const chatbotTargetResponseSchema = z.object({
  id: z.string().uuid(),
  projectId: z.string().uuid(),
  name: z.string(),
  description: z.string().nullable().optional(),
  rawCurlSanitized: z.string().nullable().optional(),
  requestConfig: targetRequestConfigSchema.nullable().optional(),
  responseExtractionConfig: responseExtractionConfigSchema.nullable().optional(),
  latestExampleId: z.number().nullable().optional(),
  status: chatbotTargetStatusSchema,
  createdBy: z.string().uuid(),
  createdByUser: userSummarySchema.nullable().optional(),
  createdAt: z.string(),
  updatedAt: z.string(),
});
export type ChatbotTargetResponse = z.infer<typeof chatbotTargetResponseSchema>;

export const chatbotTargetCollectionSchema = z.object({
  items: z.array(chatbotTargetResponseSchema),
});
export type ChatbotTargetCollection = z.infer<typeof chatbotTargetCollectionSchema>;

export const secretBindingCandidateSchema = z.object({
  location: bindingLocationSchema,
  keyOrPath: z.string(),
});
export type SecretBindingCandidate = z.infer<typeof secretBindingCandidateSchema>;

export const inspectCurlRequestSchema = z.object({
  rawCurl: z.string().min(1, 'Vui lòng nhập cURL').max(20000, 'cURL tối đa 20000 ký tự'),
});
export type InspectCurlRequest = z.infer<typeof inspectCurlRequestSchema>;

export const inspectCurlResponseSchema = z.object({
  sanitizedCurl: z.string(),
  findings: z.array(z.string()).nullable().optional(),
  parsedRequest: targetRequestConfigSchema,
  secretBindingCandidates: z.array(secretBindingCandidateSchema).nullable().optional(),
});
export type InspectCurlResponse = z.infer<typeof inspectCurlResponseSchema>;

export const createChatbotTargetRequestSchema = z.object({
  name: z.string().min(1, 'Vui lòng nhập tên target').max(160, 'Tên tối đa 160 ký tự'),
  description: z.string().max(1000, 'Mô tả tối đa 1000 ký tự').optional().or(z.literal('')),
  rawCurl: z.string().max(20000, 'cURL tối đa 20000 ký tự').optional().or(z.literal('')),
  method: z.string().optional(),
  url: z.string().max(2000, 'URL tối đa 2000 ký tự').optional().or(z.literal('')),
  headers: z.record(z.string(), z.string()).optional(),
  queryParams: z.record(z.string(), z.string()).optional(),
  bodyType: targetBodyTypeSchema.optional(),
  body: z.string().max(20000, 'Body tối đa 20000 ký tự').optional().or(z.literal('')),
  secretBindings: z.array(secretBindingSchema).optional(),
  datasetMappings: z.array(datasetMappingSchema).optional(),
  responseExtractionConfig: responseExtractionConfigSchema.optional(),
});
export type CreateChatbotTargetRequest = z.infer<typeof createChatbotTargetRequestSchema>;

export const updateChatbotTargetRequestSchema = createChatbotTargetRequestSchema
  .partial()
  .extend({ status: chatbotTargetStatusSchema.optional() });
export type UpdateChatbotTargetRequest = z.infer<typeof updateChatbotTargetRequestSchema>;

export const testChatbotTargetRequestSchema = z.object({
  datasetValues: z.record(z.string(), z.string()).optional(),
});
export type TestChatbotTargetRequest = z.infer<typeof testChatbotTargetRequestSchema>;

export const chatbotTargetExampleSchema = z.object({
  id: z.number(),
  request: z.record(z.string(), z.unknown()).nullable().optional(),
  responseStatusCode: z.number().nullable().optional(),
  responseHeaders: z.record(z.string(), z.unknown()).nullable().optional(),
  responseBody: z.record(z.string(), z.unknown()).nullable().optional(),
  extractedOutput: z.record(z.string(), z.unknown()).nullable().optional(),
  latencyMs: z.number().nullable().optional(),
  errorMessage: z.string().nullable().optional(),
  createdAt: z.string(),
});
export type ChatbotTargetExample = z.infer<typeof chatbotTargetExampleSchema>;

export const chatbotTargetExampleCollectionSchema = z.object({
  items: z.array(chatbotTargetExampleSchema),
});
export type ChatbotTargetExampleCollection = z.infer<typeof chatbotTargetExampleCollectionSchema>;

export const chatbotTargetReadinessSchema = z.object({
  ready: z.boolean(),
  blockers: z.array(
    z.object({
      code: z.string(),
      message: z.string(),
    }),
  ),
});
export type ChatbotTargetReadiness = z.infer<typeof chatbotTargetReadinessSchema>;
