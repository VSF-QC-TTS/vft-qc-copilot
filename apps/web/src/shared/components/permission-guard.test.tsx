import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { PermissionGuard } from './permission-guard';
import type { CurrentUserResponse } from '@/features/auth/types/auth.types';

type CurrentUserQueryResult = {
  data?: {
    data: CurrentUserResponse;
  };
};

const useCurrentUserQueryMock = vi.hoisted(() => vi.fn<() => CurrentUserQueryResult>());

vi.mock('@/features/auth/hooks/use-auth-queries', () => ({
  useCurrentUserQuery: useCurrentUserQueryMock,
}));

describe('PermissionGuard', () => {
  const normalUser: CurrentUserResponse = {
    id: 'user-1',
    email: 'user@example.com',
    displayName: 'User',
    systemRole: 'USER',
    status: 'ACTIVE',
  };

  const platformAdmin: CurrentUserResponse = {
    id: 'admin-1',
    email: 'admin@example.com',
    displayName: 'Admin',
    systemRole: 'PLATFORM_ADMIN',
    status: 'ACTIVE',
  };

  const mockUserQuery = (user: CurrentUserResponse) => {
    useCurrentUserQueryMock.mockReturnValue({ data: { data: user } });
  };

  it('renders children if allowed', () => {
    mockUserQuery(normalUser);
    render(
      <PermissionGuard capability="VIEW" workspaceRole="VIEWER">
        <div>Allowed Content</div>
      </PermissionGuard>,
    );
    expect(screen.getByText('Allowed Content')).toBeInTheDocument();
  });

  it('hides content if hide is true and not allowed', () => {
    mockUserQuery(normalUser);
    render(
      <PermissionGuard capability="MANAGE" workspaceRole="VIEWER" hide>
        <div>Should be hidden</div>
      </PermissionGuard>,
    );
    expect(screen.queryByText('Should be hidden')).not.toBeInTheDocument();
  });

  it('renders fallback if provided and not allowed', () => {
    mockUserQuery(normalUser);
    render(
      <PermissionGuard
        capability="MANAGE"
        workspaceRole="VIEWER"
        fallback={<div>Fallback Content</div>}
      >
        <div>Should not render</div>
      </PermissionGuard>,
    );
    expect(screen.getByText('Fallback Content')).toBeInTheDocument();
    expect(screen.queryByText('Should not render')).not.toBeInTheDocument();
  });

  it('passes isAllowed to render prop', () => {
    mockUserQuery(normalUser);
    render(
      <PermissionGuard capability="MANAGE" workspaceRole="VIEWER">
        {(isAllowed) => <button disabled={!isAllowed}>Action</button>}
      </PermissionGuard>,
    );
    expect(screen.getByRole('button')).toBeDisabled();
  });

  it('allows PLATFORM_ADMIN even with null role', () => {
    mockUserQuery(platformAdmin);
    render(
      <PermissionGuard capability="MANAGE" workspaceRole={null} hide>
        <div>Admin Content</div>
      </PermissionGuard>,
    );
    expect(screen.getByText('Admin Content')).toBeInTheDocument();
  });
});
