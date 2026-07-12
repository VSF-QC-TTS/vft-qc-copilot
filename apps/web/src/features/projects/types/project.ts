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
