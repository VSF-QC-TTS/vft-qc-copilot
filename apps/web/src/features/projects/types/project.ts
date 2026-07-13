import { z } from 'zod';

const userSummarySchema = z.object({
  id: z.string(),
  name: z.string(),
  email: z.string(),
});

export const projectResponseSchema = z.object({
  id: z.string(),
  workspaceId: z.string(),
  name: z.string(),
  description: z.string().nullable(),
  createdBy: z.string(),
  createdByUser: userSummarySchema.optional().nullable(),
  createdAt: z.string(),
  updatedAt: z.string().nullable(),
  archivedAt: z.string().nullable(),
});

export type ProjectResponse = z.infer<typeof projectResponseSchema>;

export const projectCollectionSchema = z.object({
  items: z.array(projectResponseSchema),
  page: z.object({
    limit: z.number(),
    nextCursor: z.string().nullable(),
    hasMore: z.boolean(),
  }),
});

export const createProjectRequestSchema = z.object({
  name: z
    .string()
    .min(1, 'Tên Project không được để trống')
    .max(120, 'Tên Project tối đa 120 ký tự'),
  description: z.string().max(1000, 'Mô tả tối đa 1000 ký tự').optional(),
});
export type CreateProjectRequest = z.infer<typeof createProjectRequestSchema>;

export const updateProjectRequestSchema = z.object({
  name: z
    .string()
    .min(1, 'Tên Project không được để trống')
    .max(120, 'Tên Project tối đa 120 ký tự'),
  description: z.string().max(1000, 'Mô tả tối đa 1000 ký tự'),
});
export type UpdateProjectRequest = z.infer<typeof updateProjectRequestSchema>;
