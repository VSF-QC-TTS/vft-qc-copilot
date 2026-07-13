import { render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { describe, expect, it } from 'vitest';

import { PersonalSettingsLayout } from '@/features/secrets/pages/personal-settings-layout';

function renderPersonalSettings(path = '/settings/providers') {
  const router = createMemoryRouter(
    [
      {
        path: '/settings/*',
        element: <PersonalSettingsLayout />,
      },
    ],
    { initialEntries: [path] },
  );

  return render(<RouterProvider router={router} />);
}

describe('PersonalSettingsLayout', () => {
  it('exposes secrets and providers sub-navigation', () => {
    renderPersonalSettings();

    expect(screen.getByRole('link', { name: 'Secrets' })).toHaveAttribute(
      'href',
      '/settings/secrets',
    );
    expect(screen.getByRole('link', { name: 'Providers' })).toHaveAttribute(
      'href',
      '/settings/providers',
    );
  });
});
