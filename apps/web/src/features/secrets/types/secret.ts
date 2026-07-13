import { z } from 'zod';

export const secretScopeSchema = z.enum(['PERSONAL', 'WORKSPACE']);
export type SecretScope = z.infer<typeof secretScopeSchema>;

export const secretStatusSchema = z.enum(['ACTIVE', 'DISABLED']);
export type SecretStatus = z.infer<typeof secretStatusSchema>;

const userSummarySchema = z.object({
  id: z.string().uuid(),
  email: z.string(),
  name: z.string(),
  avatarUrl: z.string().nullable().optional(),
});

export const secretResponseSchema = z.object({
  id: z.string().uuid(),
  scope: secretScopeSchema,
  ownerUserId: z.string().uuid(),
  ownerUser: userSummarySchema,
  workspaceId: z.string().uuid().nullable().optional(),
  name: z.string(),
  maskedPreview: z.string(),
  status: secretStatusSchema,
  createdAt: z.string(),
  updatedAt: z.string(),
});
export type SecretResponse = z.infer<typeof secretResponseSchema>;

export const secretSummarySchema = z.object({
  id: z.string().uuid(),
  scope: secretScopeSchema,
  ownerUserId: z.string().uuid(),
  ownerUser: userSummarySchema,
  workspaceId: z.string().uuid().nullable().optional(),
  name: z.string(),
  status: secretStatusSchema,
});
export type SecretSummaryResponse = z.infer<typeof secretSummarySchema>;

export const secretCollectionSchema = z.object({
  items: z.array(secretResponseSchema),
  page: z.object({
    limit: z.number(),
    nextCursor: z.string().nullable(),
    hasMore: z.boolean(),
  }),
});
export type SecretCollection = z.infer<typeof secretCollectionSchema>;

export const createSecretRequestSchema = z.object({
  name: z.string().min(1, 'Vui lòng nhập tên secret').max(160, 'Tên tối đa 160 ký tự'),
  value: z.string().min(1, 'Vui lòng nhập giá trị secret'),
});
export type CreateSecretRequest = z.infer<typeof createSecretRequestSchema>;

export const updateSecretRequestSchema = z.object({
  name: z.string().min(1, 'Vui lòng nhập tên secret').max(160, 'Tên tối đa 160 ký tự'),
});
export type UpdateSecretRequest = z.infer<typeof updateSecretRequestSchema>;

export const rotateSecretRequestSchema = z.object({
  value: z.string().min(1, 'Vui lòng nhập giá trị mới'),
});
export type RotateSecretRequest = z.infer<typeof rotateSecretRequestSchema>;
