import { render, screen } from '@testing-library/react';
import { createMemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';

import { Application } from '@/app/application';
import { appRoutes } from '@/app/router/routes';

describe('Application', () => {
  it('renders the login page when unauthenticated', async () => {
    const router = createMemoryRouter(appRoutes, { initialEntries: ['/'] });

    render(<Application router={router} />);

    // Since we are unauthenticated (no fetch mock), it redirects to /login
    const loginHeading = await screen.findByRole('heading', { name: 'Đăng nhập vào hệ thống' });
    expect(loginHeading).toBeVisible();
  });

  it('renders a Vietnamese not-found page for an unknown route', async () => {
    const router = createMemoryRouter(appRoutes, { initialEntries: ['/khong-ton-tai'] });

    render(<Application router={router} />);

    const notFoundHeading = await screen.findByRole('heading', { name: 'Không tìm thấy trang' });
    expect(notFoundHeading).toBeVisible();
  });
});
