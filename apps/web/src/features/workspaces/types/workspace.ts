import { z } from 'zod';

export const workspaceRoleSchema = z.enum(['OWNER', 'ADMIN', 'MEMBER', 'VIEWER']);
export type WorkspaceRole = z.infer<typeof workspaceRoleSchema>;

export const workspaceMemberStatusSchema = z.enum(['INVITED', 'ACTIVE', 'DISABLED']);
export type WorkspaceMemberStatus = z.infer<typeof workspaceMemberStatusSchema>;

export const workspaceTypeSchema = z.enum(['PERSONAL', 'TEAM']);
export type WorkspaceType = z.infer<typeof workspaceTypeSchema>;

const userSummarySchema = z.object({
  id: z.string(),
  email: z.string(),
  name: z.string(),
  avatarUrl: z.string().nullable().optional(),
});
export type UserSummaryResponse = z.infer<typeof userSummarySchema>;

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

export const workspaceMemberResponseSchema = z.object({
  id: z.number(),
  workspaceId: z.string(),
  userId: z.string(),
  user: userSummarySchema,
  role: workspaceRoleSchema,
  status: workspaceMemberStatusSchema,
  invitedBy: z.string().nullable().optional(),
  invitedByUser: userSummarySchema.nullable().optional(),
  joinedAt: z.string().nullable().optional(),
  createdAt: z.string(),
  updatedAt: z.string().nullable(),
});
export type WorkspaceMemberResponse = z.infer<typeof workspaceMemberResponseSchema>;

export const workspaceMemberCollectionSchema = z.object({
  items: z.array(workspaceMemberResponseSchema),
  page: z.object({
    limit: z.number(),
    nextCursor: z.string().nullable(),
    hasMore: z.boolean(),
  }),
});
export type WorkspaceMemberCollection = z.infer<typeof workspaceMemberCollectionSchema>;

export const addWorkspaceMemberRequestSchema = z.object({
  email: z.string().email('Email không hợp lệ').min(1, 'Vui lòng nhập email'),
  role: workspaceRoleSchema,
});
export type AddWorkspaceMemberRequest = z.infer<typeof addWorkspaceMemberRequestSchema>;

export const updateWorkspaceMemberRoleRequestSchema = z.object({
  role: workspaceRoleSchema,
});
export type UpdateWorkspaceMemberRoleRequest = z.infer<
  typeof updateWorkspaceMemberRoleRequestSchema
>;

export const updateWorkspaceRequestSchema = z.object({
  name: z
    .string()
    .min(1, 'Tên Workspace không được để trống')
    .max(120, 'Tên Workspace tối đa 120 ký tự'),
});
export type UpdateWorkspaceRequest = z.infer<typeof updateWorkspaceRequestSchema>;

export const createWorkspaceRequestSchema = z.object({
  name: z
    .string()
    .min(1, 'Tên Workspace không được để trống')
    .max(120, 'Tên Workspace tối đa 120 ký tự'),
});
export type CreateWorkspaceRequest = z.infer<typeof createWorkspaceRequestSchema>;
