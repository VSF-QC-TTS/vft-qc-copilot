import { RouteObject, Navigate } from 'react-router';

import { AppShell } from '@/app/layouts/app-shell';
import { HomePage } from '@/app/router/pages/home-page';
import { NotFoundPage } from '@/app/router/pages/not-found-page';
import { RouteErrorPage } from '@/app/router/pages/route-error-page';
import { AuthenticatedGuard } from '@/app/guards/authenticated-guard';
import { PublicOnlyGuard } from '@/app/guards/public-only-guard';
import { PlatformAdminGuard } from '@/app/guards/platform-admin-guard';
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
import { WorkspaceSecretsPage } from '@/features/secrets/pages/workspace-secrets-page';
import { PersonalSettingsLayout } from '@/features/secrets/pages/personal-settings-layout';
import { PersonalSecretsPage } from '@/features/secrets/pages/personal-secrets-page';
import { PersonalProvidersPage } from '@/features/providers/pages/personal-providers-page';
import { WorkspaceProvidersPage } from '@/features/providers/pages/workspace-providers-page';

import { ProjectListPage } from '@/features/projects/pages/project-list-page';
import { ProjectOverviewPage } from '@/features/projects/pages/project-overview-page';
import { ProjectSettingsPage } from '@/features/projects/pages/project-settings-page';
import { ProjectContextLayout } from '@/app/layouts/project-context-layout';
import { ChatbotTargetListPage } from '@/features/chatbot-targets/pages/chatbot-target-list-page';
import { ChatbotTargetCreatePage } from '@/features/chatbot-targets/pages/chatbot-target-create-page';
import { ChatbotTargetDetailPage } from '@/features/chatbot-targets/pages/chatbot-target-detail-page';
import { DatasetListPage } from '@/features/datasets/pages/dataset-list-page';
import { DatasetCreatePage } from '@/features/datasets/pages/dataset-create-page';
import { DatasetDetailPage } from '@/features/datasets/pages/dataset-detail-page';
import { DatasetImportPage } from '@/features/datasets/pages/dataset-import-page';
import { EvaluatorListPage } from '@/features/evaluators/pages/evaluator-list-page';
import { EvaluatorCreatePage } from '@/features/evaluators/pages/evaluator-create-page';
import { EvaluatorDetailPage } from '@/features/evaluators/pages/evaluator-detail-page';
import { TestRunCreatePage } from '@/features/test-runs/pages/test-run-create-page';
import { TestRunDetailPage } from '@/features/test-runs/pages/test-run-detail-page';
import { AdminUsersPage } from '@/features/admin-users/pages/admin-users-page';

export const appRoutes: RouteObject[] = [
  {
    element: <PublicOnlyGuard />,
    errorElement: <RouteErrorPage />,
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
    errorElement: <RouteErrorPage />,
    children: [
      {
        element: <AppShell />,
        children: [
          {
            path: '/',
            element: <HomePage />,
          },
          {
            path: '/settings',
            element: <PersonalSettingsLayout />,
            children: [
              {
                index: true,
                element: <Navigate to="secrets" replace />,
              },
              {
                path: 'secrets',
                element: <PersonalSecretsPage />,
              },
              {
                path: 'providers',
                element: <PersonalProvidersPage />,
              },
            ],
          },
          {
            path: '/workspaces',
            element: <WorkspaceListPage />,
          },
          {
            element: <PlatformAdminGuard />,
            children: [
              {
                path: '/admin/users',
                element: <AdminUsersPage />,
              },
            ],
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
                  {
                    path: 'settings',
                    element: <ProjectSettingsPage />,
                  },
                  {
                    path: 'targets',
                    element: <ChatbotTargetListPage />,
                  },
                  {
                    path: 'targets/new',
                    element: <ChatbotTargetCreatePage />,
                  },
                  {
                    path: 'targets/:targetId',
                    element: <ChatbotTargetDetailPage />,
                  },
                  {
                    path: 'datasets',
                    element: <DatasetListPage />,
                  },
                  {
                    path: 'datasets/new',
                    element: <DatasetCreatePage />,
                  },
                  {
                    path: 'datasets/import',
                    element: <DatasetImportPage />,
                  },
                  {
                    path: 'datasets/:datasetId',
                    element: <DatasetDetailPage />,
                  },
                  {
                    path: 'datasets/:datasetId/versions/:versionId',
                    element: <DatasetDetailPage />,
                  },
                  {
                    path: 'evaluators',
                    element: <EvaluatorListPage />,
                  },
                  {
                    path: 'evaluators/new',
                    element: <EvaluatorCreatePage />,
                  },
                  {
                    path: 'evaluators/:evaluatorId',
                    element: <EvaluatorDetailPage />,
                  },
                  {
                    path: 'evaluators/:evaluatorId/versions/:versionId',
                    element: <EvaluatorDetailPage />,
                  },
                  {
                    path: 'test-runs/new',
                    element: <TestRunCreatePage />,
                  },
                  {
                    path: 'test-runs/:testRunId',
                    element: <TestRunDetailPage />,
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
                  {
                    path: 'secrets',
                    element: <WorkspaceSecretsPage />,
                  },
                  {
                    path: 'providers',
                    element: <WorkspaceProvidersPage />,
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
