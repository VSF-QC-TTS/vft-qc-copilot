import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { MemoryRouter } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import { AppShell } from '@/app/layouts/app-shell';
import { ThemeProvider } from '@/app/providers/theme-provider';

function renderShell() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider>
        <MemoryRouter>
          <AppShell />
        </MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>,
  );
}

describe('AppShell', () => {
  it('renders sidebar, header and main content area', () => {
    renderShell();

    expect(screen.getByLabelText('Navigation chính')).toBeInTheDocument();
    expect(screen.getAllByLabelText('Chuyển Workspace').length).toBeGreaterThan(0);
    expect(screen.getAllByLabelText('Đăng xuất')[0]).toBeInTheDocument();
    expect(screen.getByRole('main')).toBeInTheDocument();
  });

  it('opens and closes tablet navigation', async () => {
    const user = userEvent.setup();
    renderShell();

    await user.click(screen.getByRole('button', { name: 'Mở menu' }));
    expect(screen.getByRole('dialog', { name: 'Điều hướng ứng dụng' })).toBeInTheDocument();

    await user.keyboard('{Escape}');
    expect(screen.queryByRole('dialog', { name: 'Điều hướng ứng dụng' })).not.toBeInTheDocument();
  });
});
