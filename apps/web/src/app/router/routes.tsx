import type { RouteObject } from 'react-router';

import { AppShell } from '@/app/layouts/app-shell';
import { HomePage } from '@/app/router/pages/home-page';
import { NotFoundPage } from '@/app/router/pages/not-found-page';
import { AuthenticatedGuard } from '@/app/guards/authenticated-guard';
import { PublicOnlyGuard } from '@/app/guards/public-only-guard';
import { LoginPage } from '@/features/auth/pages/login-page';

export const appRoutes: RouteObject[] = [
  {
    element: <PublicOnlyGuard />,
    children: [
      {
        path: '/login',
        element: <LoginPage />,
      },
    ],
  },
  {
    element: <AuthenticatedGuard />,
    children: [
      {
        element: <AppShell />,
        children: [
          {
            path: '/',
            element: <HomePage />,
          },
          {
            path: '/workspaces',
            element: (
              <div className="p-4">
                <h1 className="text-2xl font-bold">Workspaces</h1>
                <p>Welcome to your workspaces!</p>
              </div>
            ),
          },
        ],
      },
    ],
  },
  {
    path: '*',
    element: <NotFoundPage />,
  },
];
