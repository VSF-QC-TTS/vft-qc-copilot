import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { describe, expect, it, vi } from 'vitest';

import { PlatformAdminGuard } from '@/app/guards/platform-admin-guard';
import type { CurrentUserResponse } from '@/features/auth/types/auth.types';

const useAuthMock = vi.hoisted(() =>
  vi.fn<() => { user: CurrentUserResponse | null; isLoading: boolean; isAuthenticated: boolean }>(),
);

vi.mock('@/app/providers/auth-provider', () => ({
  useAuth: useAuthMock,
}));

describe('PlatformAdminGuard', () => {
  it('renders nested route for PLATFORM_ADMIN', () => {
    useAuthMock.mockReturnValue({
      user: user('PLATFORM_ADMIN'),
      isLoading: false,
      isAuthenticated: true,
    });

    renderGuard();

    expect(screen.getByText('Admin content')).toBeInTheDocument();
  });

  it('shows permission message for non-admin users', () => {
    useAuthMock.mockReturnValue({
      user: user('USER'),
      isLoading: false,
      isAuthenticated: true,
    });

    renderGuard();

    expect(screen.getByText('Không có quyền truy cập')).toBeInTheDocument();
    expect(screen.queryByText('Admin content')).not.toBeInTheDocument();
  });
});

function renderGuard() {
  render(
    <MemoryRouter initialEntries={['/admin/users']}>
      <Routes>
        <Route element={<PlatformAdminGuard />}>
          <Route path="/admin/users" element={<div>Admin content</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

function user(systemRole: 'PLATFORM_ADMIN' | 'USER'): CurrentUserResponse {
  return {
    id: systemRole === 'PLATFORM_ADMIN' ? 'admin-1' : 'user-1',
    email: systemRole === 'PLATFORM_ADMIN' ? 'admin@vinfast.vn' : 'user@vinfast.vn',
    displayName: systemRole === 'PLATFORM_ADMIN' ? 'Admin' : 'User',
    systemRole,
    status: 'ACTIVE',
  };
}
