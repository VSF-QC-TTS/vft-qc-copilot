import { RouteObject, Navigate } from 'react-router';

import { AppShell } from '@/app/layouts/app-shell';
import { HomePage } from '@/app/router/pages/home-page';
import { NotFoundPage } from '@/app/router/pages/not-found-page';
import { AuthenticatedGuard } from '@/app/guards/authenticated-guard';
import { PublicOnlyGuard } from '@/app/guards/public-only-guard';
import { LoginPage } from '@/features/auth/pages/login-page';
import { RegisterPage } from '@/features/auth/pages/register-page';
import { ForgotPasswordPage } from '@/features/auth/pages/forgot-password-page';
import { ResetPasswordPage } from '@/features/auth/pages/reset-password-page';
import { VerifyEmailPage } from '@/features/auth/pages/verify-email-page';

import { WorkspaceListPage } from '@/features/workspaces/pages/workspace-list-page';
import { WorkspaceOverviewPage } from '@/features/workspaces/pages/workspace-overview-page';
import { WorkspaceContextLayout } from '@/app/layouts/workspace-context-layout';
import { WorkspaceSettingsLayout } from '@/features/workspaces/pages/workspace-settings-layout';
import { WorkspaceSettingsGeneralPage } from '@/features/workspaces/pages/workspace-settings-general-page';
import { WorkspaceSettingsMembersPage } from '@/features/workspaces/pages/workspace-settings-members-page';

import { ProjectListPage } from '@/features/projects/pages/project-list-page';
import { ProjectOverviewPage } from '@/features/projects/pages/project-overview-page';
import { ProjectContextLayout } from '@/app/layouts/project-context-layout';

export const appRoutes: RouteObject[] = [
  {
    element: <PublicOnlyGuard />,
    children: [
      {
        path: '/login',
        element: <LoginPage />,
      },
      {
        path: '/register',
        element: <RegisterPage />,
      },
      {
        path: '/forgot-password',
        element: <ForgotPasswordPage />,
      },
      {
        path: '/reset-password',
        element: <ResetPasswordPage />,
      },
      {
        path: '/verify-email',
        element: <VerifyEmailPage />,
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
            element: <WorkspaceListPage />,
          },
          {
            path: '/workspaces/:workspaceId',
            element: <WorkspaceContextLayout />,
            children: [
              {
                index: true,
                element: <WorkspaceOverviewPage />,
              },
              {
                path: 'projects',
                element: <ProjectListPage />,
              },
              {
                path: 'projects/:projectId',
                element: <ProjectContextLayout />,
                children: [
                  {
                    index: true,
                    element: <ProjectOverviewPage />,
                  },
                ],
              },
              {
                path: 'settings',
                element: <WorkspaceSettingsLayout />,
                children: [
                  {
                    index: true,
                    element: <Navigate to="general" replace />,
                  },
                  {
                    path: 'general',
                    element: <WorkspaceSettingsGeneralPage />,
                  },
                  {
                    path: 'members',
                    element: <WorkspaceSettingsMembersPage />,
                  },
                ],
              },
            ],
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
