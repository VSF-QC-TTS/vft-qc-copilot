import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { MemoryRouter } from 'react-router';

import { Breadcrumbs } from '@/app/layouts/breadcrumbs';

describe('Breadcrumbs', () => {
  it('renders correct labels and links', () => {
    render(
      <MemoryRouter>
        <Breadcrumbs items={[{ label: 'Workspace', href: '/workspaces' }, { label: 'Project' }]} />
      </MemoryRouter>,
    );

    expect(screen.getByText('Workspace')).toHaveAttribute('href', '/workspaces');
    expect(screen.getByText('Project')).toHaveAttribute('aria-current', 'page');
  });
});
