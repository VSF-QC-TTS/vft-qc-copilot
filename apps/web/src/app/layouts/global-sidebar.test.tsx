import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import { GlobalSidebar } from '@/app/layouts/global-sidebar';
import type { CurrentUserResponse } from '@/features/auth/types/auth.types';

const useAuthMock = vi.hoisted(() =>
  vi.fn<() => { user: CurrentUserResponse | null; isLoading: boolean; isAuthenticated: boolean }>(),
);

vi.mock('@/app/providers/auth-provider', () => ({
  useAuth: useAuthMock,
}));

function renderWithProviders(ui: React.ReactElement) {
  useAuthMock.mockReturnValue({
    user: {
      id: 'user-1',
      email: 'user@vinfast.vn',
      displayName: 'User',
      systemRole: 'USER',
      status: 'ACTIVE',
    },
    isLoading: false,
    isAuthenticated: true,
  });
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>{ui}</MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('GlobalSidebar', () => {
  it('renders all Vietnamese navigation labels', () => {
    renderWithProviders(<GlobalSidebar />);

    expect(screen.getByRole('link', { name: 'Project' })).toHaveAttribute('href', '/workspaces');
    expect(screen.getByRole('link', { name: 'Personal Secret' })).toHaveAttribute(
      'href',
      '/settings/secrets',
    );
    expect(screen.getByRole('link', { name: 'Personal LLM Provider' })).toHaveAttribute(
      'href',
      '/settings/providers',
    );
    expect(screen.queryByRole('link', { name: 'Admin Users' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Dataset' })).not.toBeInTheDocument();
  });

  it('shows the admin users route only for PLATFORM_ADMIN', () => {
    useAuthMock.mockReturnValue({
      user: {
        id: 'admin-1',
        email: 'admin@vinfast.vn',
        displayName: 'Admin',
        systemRole: 'PLATFORM_ADMIN',
        status: 'ACTIVE',
      },
      isLoading: false,
      isAuthenticated: true,
    });
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <GlobalSidebar />
        </MemoryRouter>
      </QueryClientProvider>,
    );

    expect(screen.getByRole('link', { name: 'Admin Users' })).toHaveAttribute(
      'href',
      '/admin/users',
    );
  });

  it('can toggle collapse state', async () => {
    const user = userEvent.setup();
    renderWithProviders(<GlobalSidebar />);

    const toggleButton = screen.getByLabelText('Thu gọn sidebar');
    await user.click(toggleButton);

    expect(screen.getByLabelText('Mở rộng sidebar')).toBeInTheDocument();
    expect(screen.queryByText('Project')).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Project' })).toHaveAttribute('title', 'Project');
  });
});
