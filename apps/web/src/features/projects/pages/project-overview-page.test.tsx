import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { ProjectOverviewPage } from '@/features/projects/pages/project-overview-page';
import type { ProjectSetupStatusResponse } from '@/features/projects/types/project-setup-status';

type SetupStatusHookResult = {
  data?: ProjectSetupStatusResponse;
  isLoading: boolean;
  error: unknown;
};

const useProjectSetupStatusMock = vi.hoisted(() => vi.fn<() => SetupStatusHookResult>());

const outletContext = {
  workspace: {
    id: 'workspace-1',
    name: 'QA Workspace',
    type: 'TEAM',
    currentUserRole: 'ADMIN',
    createdBy: 'user-1',
    createdByUser: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: null,
  },
  project: {
    id: 'project-1',
    workspaceId: 'workspace-1',
    name: 'VF Assistant Regression',
    description: null,
    createdBy: 'user-1',
    createdByUser: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: null,
    archivedAt: null,
  },
};

vi.mock('react-router', async () => {
  const actual = await vi.importActual<typeof import('react-router')>('react-router');
  return {
    ...actual,
    useOutletContext: () => outletContext,
  };
});

vi.mock('@/features/projects/hooks/use-projects', () => ({
  useProjectSetupStatus: useProjectSetupStatusMock,
}));

describe('ProjectOverviewPage', () => {
  it('renders backend setup status as the main project workspace', () => {
    useProjectSetupStatusMock.mockReturnValue({
      data: {
        projectId: 'project-1',
        workspaceId: 'workspace-1',
        steps: {
          hasDataset: true,
          hasActiveDatasetVersion: false,
          hasActiveChatbotTarget: true,
          hasResponseExtraction: false,
          hasEvaluator: false,
          hasUsableJudgeProvider: true,
        },
        counts: {
          datasets: 1,
          activeDatasetVersions: 0,
          activeChatbotTargets: 1,
          activeChatbotTargetsWithExtraction: 0,
          activeEvaluators: 0,
          usableJudgeProviders: 1,
          usableBaselineProviders: 1,
          comparisonCandidates: 1,
        },
        canRunSingle: false,
        canRunComparison: false,
        blockers: [
          {
            code: 'PROJECT_SETUP_NO_ACTIVE_DATASET',
            step: 'DATASET',
            severity: 'ERROR',
            message: 'Publish at least one dataset version before starting a run.',
            actionEndpoint: '/api/v1/projects/project-1/datasets',
          },
        ],
      },
      isLoading: false,
      error: null,
    });

    render(<ProjectOverviewPage />);

    expect(screen.getByRole('heading', { name: 'VF Assistant Regression' })).toBeVisible();
    expect(screen.getByRole('heading', { name: 'Thiết lập Project' })).toBeVisible();
    expect(screen.getByText('Dataset')).toBeVisible();
    expect(screen.getByText('Active dataset version')).toBeVisible();
    expect(
      screen.getByText('Publish at least one dataset version before starting a run.'),
    ).toBeVisible();
    expect(screen.getByText('Chưa sẵn sàng chạy')).toBeVisible();
    expect(screen.queryByText('Hoạt động')).not.toBeInTheDocument();
  });
});
