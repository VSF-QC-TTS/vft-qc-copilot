import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { MemoryRouter } from 'react-router';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import { GlobalSidebar } from '@/app/layouts/global-sidebar';

function renderWithProviders(ui: React.ReactElement) {
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
    expect(screen.queryByRole('link', { name: 'Dataset' })).not.toBeInTheDocument();
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
