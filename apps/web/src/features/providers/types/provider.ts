import { z } from 'zod';
import { secretSummarySchema } from '@/features/secrets/types/secret';

export const providerScopeSchema = z.enum(['PERSONAL', 'WORKSPACE']);
export type ProviderScope = z.infer<typeof providerScopeSchema>;

export const providerTypeSchema = z.enum(['OPENAI_COMPATIBLE', 'GOOGLE_GENAI', 'CUSTOM_HTTP']);
export type ProviderType = z.infer<typeof providerTypeSchema>;

export const providerStatusSchema = z.enum(['ACTIVE', 'DISABLED', 'TEST_FAILED']);
export type ProviderStatus = z.infer<typeof providerStatusSchema>;

export const providerTestStatusSchema = z.enum(['SUCCESS', 'FAILED']);
export type ProviderTestStatus = z.infer<typeof providerTestStatusSchema>;

const userSummarySchema = z.object({
  id: z.string().uuid(),
  email: z.string(),
  name: z.string(),
  avatarUrl: z.string().nullable().optional(),
});

export const providerResponseSchema = z.object({
  id: z.string().uuid(),
  scope: providerScopeSchema,
  ownerUserId: z.string().uuid(),
  ownerUser: userSummarySchema,
  workspaceId: z.string().uuid().nullable().optional(),
  name: z.string(),
  providerType: providerTypeSchema,
  secret: secretSummarySchema,
  baseUrl: z.string().nullable().optional(),
  defaultModel: z.string(),
  availableModels: z.record(z.string(), z.unknown()).nullable().optional(),
  config: z.record(z.string(), z.unknown()).nullable().optional(),
  status: providerStatusSchema,
  createdAt: z.string(),
  updatedAt: z.string(),
});
export type ProviderResponse = z.infer<typeof providerResponseSchema>;

export const providerCollectionSchema = z.object({
  items: z.array(providerResponseSchema),
  page: z.object({
    limit: z.number(),
    nextCursor: z.string().nullable(),
    hasMore: z.boolean(),
  }),
});
export type ProviderCollectionResponse = z.infer<typeof providerCollectionSchema>;

export const createProviderRequestSchema = z.object({
  name: z.string().min(1, 'Vui lòng nhập tên provider').max(160, 'Tên tối đa 160 ký tự'),
  providerType: providerTypeSchema,
  secretId: z.string().uuid('Secret không hợp lệ'),
  baseUrl: z.string().max(500, 'Base URL tối đa 500 ký tự').optional().or(z.literal('')),
  defaultModel: z
    .string()
    .min(1, 'Default model không được để trống')
    .max(200, 'Default model tối đa 200 ký tự'),
});
export type CreateProviderRequest = z.infer<typeof createProviderRequestSchema>;

export const updateProviderRequestSchema = z.object({
  name: z.string().max(160, 'Tên tối đa 160 ký tự').optional(),
  secretId: z.string().uuid('Secret không hợp lệ').optional(),
  baseUrl: z.string().max(500, 'Base URL tối đa 500 ký tự').optional().or(z.literal('')),
  defaultModel: z.string().max(200, 'Default model tối đa 200 ký tự').optional(),
});
export type UpdateProviderRequest = z.infer<typeof updateProviderRequestSchema>;

export const testProviderRequestSchema = z.object({
  model: z.string().max(200, 'Model tối đa 200 ký tự').optional().or(z.literal('')),
  prompt: z.string().max(1000, 'Prompt tối đa 1000 ký tự').optional().or(z.literal('')),
});
export type TestProviderRequest = z.infer<typeof testProviderRequestSchema>;

export const providerTestResponseSchema = z.object({
  status: providerTestStatusSchema,
  model: z.string(),
  request: z.record(z.string(), z.unknown()).nullable().optional(),
  responsePreview: z.string().nullable().optional(),
  errorCode: z.string().nullable().optional(),
  errorMessage: z.string().nullable().optional(),
  latencyMs: z.number().nullable().optional(),
  tokenUsage: z.record(z.string(), z.unknown()).nullable().optional(),
});
export type ProviderTestResponse = z.infer<typeof providerTestResponseSchema>;

export const providerTestLogResponseSchema = z.object({
  id: z.number(),
  status: providerTestStatusSchema,
  model: z.string(),
  requestJsonSanitized: z.record(z.string(), z.unknown()).nullable().optional(),
  responsePreview: z.string().nullable().optional(),
  errorCode: z.string().nullable().optional(),
  errorMessage: z.string().nullable().optional(),
  latencyMs: z.number().nullable().optional(),
  tokenUsageJson: z.record(z.string(), z.unknown()).nullable().optional(),
  testedAt: z.string(),
});
export type ProviderTestLogResponse = z.infer<typeof providerTestLogResponseSchema>;

export const providerTestLogCollectionSchema = z.object({
  items: z.array(providerTestLogResponseSchema),
  page: z.object({
    limit: z.number(),
    nextCursor: z.string().nullable(),
    hasMore: z.boolean(),
  }),
});
export type ProviderTestLogCollectionResponse = z.infer<typeof providerTestLogCollectionSchema>;
