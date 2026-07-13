import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  addWorkspaceMember,
  createWorkspace,
  getWorkspaceMembers,
  updateWorkspaceMemberRole,
} from '@/features/workspaces/api/workspaces.api';

const requestJsonMock = vi.hoisted(() =>
  vi.fn<(path: string, schema: unknown, init?: RequestInit) => Promise<{ data: unknown }>>(),
);

vi.mock('@/shared/api/api', () => ({
  apiClient: {
    requestJson: requestJsonMock,
  },
}));

describe('workspaces API', () => {
  beforeEach(() => {
    requestJsonMock.mockReset();
  });

  it('creates a workspace through the backend lifecycle endpoint', async () => {
    const workspace = {
      id: 'workspace-1',
      name: 'VF QA Platform',
      type: 'TEAM',
      currentUserRole: 'OWNER',
      createdBy: 'user-1',
      createdByUser: null,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    };
    requestJsonMock.mockResolvedValueOnce({ data: workspace });

    await expect(createWorkspace({ name: 'VF QA Platform' })).resolves.toEqual(workspace);

    expect(requestJsonMock).toHaveBeenCalledWith('/api/v1/workspaces', expect.any(Object), {
      method: 'POST',
      body: JSON.stringify({ name: 'VF QA Platform' }),
    });
  });

  it('fetches workspace members as a backend collection response', async () => {
    const signal = new AbortController().signal;
    const memberCollection = {
      items: [
        {
          id: 1,
          workspaceId: 'workspace-1',
          userId: 'user-1',
          user: {
            id: 'user-1',
            email: 'qa.engineer@vinfast.vn',
            name: 'QA Engineer',
            avatarUrl: null,
          },
          role: 'ADMIN',
          status: 'ACTIVE',
          invitedBy: null,
          invitedByUser: null,
          joinedAt: '2026-01-01T00:00:00Z',
          createdAt: '2026-01-01T00:00:00Z',
          updatedAt: null,
        },
      ],
      page: {
        limit: 20,
        nextCursor: null,
        hasMore: false,
      },
    };
    requestJsonMock.mockResolvedValueOnce({ data: memberCollection });

    await expect(getWorkspaceMembers('workspace-1', signal)).resolves.toEqual(memberCollection);

    expect(requestJsonMock).toHaveBeenCalledWith(
      '/api/v1/workspaces/workspace-1/members',
      expect.any(Object),
      { signal },
    );
  });

  it('validates member mutation responses against WorkspaceMemberResponse', async () => {
    const member = {
      id: 1,
      workspaceId: 'workspace-1',
      userId: 'user-1',
      user: {
        id: 'user-1',
        email: 'qa.engineer@vinfast.vn',
        name: 'QA Engineer',
        avatarUrl: null,
      },
      role: 'VIEWER',
      status: 'ACTIVE',
      invitedBy: 'user-2',
      invitedByUser: null,
      joinedAt: '2026-01-01T00:00:00Z',
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-02T00:00:00Z',
    };
    requestJsonMock.mockResolvedValue({ data: member });

    await expect(
      addWorkspaceMember('workspace-1', {
        email: 'qa.engineer@vinfast.vn',
        role: 'VIEWER',
      }),
    ).resolves.toEqual(member);
    await expect(
      updateWorkspaceMemberRole('workspace-1', 1, {
        role: 'MEMBER',
      }),
    ).resolves.toEqual(member);

    expect(requestJsonMock).toHaveBeenNthCalledWith(
      1,
      '/api/v1/workspaces/workspace-1/members',
      expect.any(Object),
      {
        method: 'POST',
        body: JSON.stringify({ email: 'qa.engineer@vinfast.vn', role: 'VIEWER' }),
      },
    );
    expect(requestJsonMock).toHaveBeenNthCalledWith(
      2,
      '/api/v1/workspaces/workspace-1/members/1/role',
      expect.any(Object),
      {
        method: 'PATCH',
        body: JSON.stringify({ role: 'MEMBER' }),
      },
    );
  });
});
