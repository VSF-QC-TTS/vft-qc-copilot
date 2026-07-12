import { z } from 'zod';

export const workspaceRoleSchema = z.enum(['OWNER', 'ADMIN', 'MEMBER', 'VIEWER']);
export type WorkspaceRole = z.infer<typeof workspaceRoleSchema>;

export const workspaceTypeSchema = z.enum(['PERSONAL', 'TEAM']);
export type WorkspaceType = z.infer<typeof workspaceTypeSchema>;

const userSummarySchema = z.object({
  id: z.string(),
  name: z.string(),
  email: z.string(),
});

export const workspaceResponseSchema = z.object({
  id: z.string(),
  name: z.string(),
  type: workspaceTypeSchema,
  currentUserRole: workspaceRoleSchema.nullable(),
  createdBy: z.string(),
  createdByUser: userSummarySchema.optional().nullable(),
  createdAt: z.string(),
  updatedAt: z.string().nullable(),
});

export type WorkspaceResponse = z.infer<typeof workspaceResponseSchema>;

export const workspaceCollectionSchema = z.object({
  items: z.array(workspaceResponseSchema),
  page: z.object({
    limit: z.number(),
    nextCursor: z.string().nullable(),
    hasMore: z.boolean(),
  }),
});
