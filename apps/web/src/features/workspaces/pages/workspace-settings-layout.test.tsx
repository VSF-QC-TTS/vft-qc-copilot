import { render, screen } from '@testing-library/react';
import { createMemoryRouter, Outlet, RouterProvider } from 'react-router';
import { describe, expect, it } from 'vitest';

import { WorkspaceSettingsLayout } from '@/features/workspaces/pages/workspace-settings-layout';
import type { WorkspaceResponse } from '@/features/workspaces/types/workspace';

const workspace: WorkspaceResponse = {
  id: '11111111-1111-4111-8111-111111111111',
  name: 'QC Workspace',
  type: 'TEAM',
  currentUserRole: 'ADMIN',
  createdBy: '22222222-2222-4222-8222-222222222222',
  createdByUser: null,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: null,
};

function renderWorkspaceSettings(path = `/workspaces/${workspace.id}/settings/providers`) {
  const router = createMemoryRouter(
    [
      {
        path: '/workspaces/:workspaceId/settings',
        element: <Outlet context={{ workspace }} />,
        children: [
          {
            path: '*',
            element: <WorkspaceSettingsLayout />,
          },
        ],
      },
    ],
    { initialEntries: [path] },
  );

  return render(<RouterProvider router={router} />);
}

describe('WorkspaceSettingsLayout', () => {
  it('exposes general, members, secrets, and providers sub-navigation', () => {
    renderWorkspaceSettings();

    expect(screen.getByRole('link', { name: 'Cài đặt chung' })).toHaveAttribute(
      'href',
      `/workspaces/${workspace.id}/settings/general`,
    );
    expect(screen.getByRole('link', { name: 'Thành viên' })).toHaveAttribute(
      'href',
      `/workspaces/${workspace.id}/settings/members`,
    );
    expect(screen.getByRole('link', { name: 'Secrets' })).toHaveAttribute(
      'href',
      `/workspaces/${workspace.id}/settings/secrets`,
    );
    expect(screen.getByRole('link', { name: 'Providers' })).toHaveAttribute(
      'href',
      `/workspaces/${workspace.id}/settings/providers`,
    );
  });
});
