import { render, screen } from '@testing-library/react';
import { createMemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';

import { Application } from '@/app/application';
import { appRoutes } from '@/app/router/routes';

describe('Application', () => {
  it('renders the frontend root', () => {
    const router = createMemoryRouter(appRoutes, { initialEntries: ['/'] });

    render(<Application router={router} />);

    expect(screen.getByRole('heading', { name: 'VinFast QC Copilot' })).toBeVisible();
  });

  it('renders a Vietnamese not-found page for an unknown route', () => {
    const router = createMemoryRouter(appRoutes, { initialEntries: ['/khong-ton-tai'] });

    render(<Application router={router} />);

    expect(screen.getByRole('heading', { name: 'Không tìm thấy trang' })).toBeVisible();
  });
});
