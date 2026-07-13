import { apiClient } from '@/shared/api/api';
import {
  AddWorkspaceMemberRequest,
  CreateWorkspaceRequest,
  UpdateWorkspaceMemberRoleRequest,
  UpdateWorkspaceRequest,
  workspaceCollectionSchema,
  workspaceMemberCollectionSchema,
  workspaceMemberResponseSchema,
  workspaceResponseSchema,
} from '@/features/workspaces/types/workspace';
import { projectCollectionSchema } from '@/features/projects/types/project';

export async function createWorkspace(data: CreateWorkspaceRequest) {
  const response = await apiClient.requestJson('/api/v1/workspaces', workspaceResponseSchema, {
    method: 'POST',
    body: JSON.stringify(data),
  });
  return response.data;
}

export async function getWorkspaces(signal?: AbortSignal) {
  const response = await apiClient.requestJson('/api/v1/workspaces', workspaceCollectionSchema, {
    ...(signal && { signal }),
  });
  return response.data;
}

export async function getWorkspace(workspaceId: string, signal?: AbortSignal) {
  const response = await apiClient.requestJson(
    `/api/v1/workspaces/${workspaceId}`,
    workspaceResponseSchema,
    {
      ...(signal && { signal }),
    },
  );
  return response.data;
}

export async function updateWorkspace(workspaceId: string, data: UpdateWorkspaceRequest) {
  const response = await apiClient.requestJson(
    `/api/v1/workspaces/${workspaceId}`,
    workspaceResponseSchema,
    {
      method: 'PATCH',
      body: JSON.stringify(data),
    },
  );
  return response.data;
}

export async function getWorkspaceProjects(workspaceId: string, signal?: AbortSignal) {
  const response = await apiClient.requestJson(
    `/api/v1/workspaces/${workspaceId}/projects`,
    projectCollectionSchema,
    {
      ...(signal && { signal }),
    },
  );
  return response.data;
}

export async function getWorkspaceMembers(workspaceId: string, signal?: AbortSignal) {
  const response = await apiClient.requestJson(
    `/api/v1/workspaces/${workspaceId}/members`,
    workspaceMemberCollectionSchema,
    {
      ...(signal && { signal }),
    },
  );
  return response.data;
}

export async function addWorkspaceMember(workspaceId: string, data: AddWorkspaceMemberRequest) {
  const response = await apiClient.requestJson(
    `/api/v1/workspaces/${workspaceId}/members`,
    workspaceMemberResponseSchema,
    {
      method: 'POST',
      body: JSON.stringify(data),
    },
  );
  return response.data;
}

export async function updateWorkspaceMemberRole(
  workspaceId: string,
  memberId: number,
  data: UpdateWorkspaceMemberRoleRequest,
) {
  const response = await apiClient.requestJson(
    `/api/v1/workspaces/${workspaceId}/members/${memberId}/role`,
    workspaceMemberResponseSchema,
    {
      method: 'PATCH',
      body: JSON.stringify(data),
    },
  );
  return response.data;
}
