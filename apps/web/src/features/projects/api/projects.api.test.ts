import { describe, expect, it, vi } from 'vitest';

import { getProjectSetupStatus } from '@/features/projects/api/projects.api';

const requestJsonMock = vi.hoisted(() =>
  vi.fn<(path: string, schema: unknown, init?: RequestInit) => Promise<{ data: unknown }>>(),
);

vi.mock('@/shared/api/api', () => ({
  apiClient: {
    requestJson: requestJsonMock,
  },
}));

describe('projects API', () => {
  it('fetches project setup status from the backend aggregate endpoint', async () => {
    const signal = new AbortController().signal;
    const setupStatus = {
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
    };
    requestJsonMock.mockResolvedValueOnce({ data: setupStatus });

    await expect(getProjectSetupStatus('project-1', signal)).resolves.toEqual(setupStatus);

    expect(requestJsonMock).toHaveBeenCalledWith(
      '/api/v1/projects/project-1/setup-status',
      expect.any(Object),
      { signal },
    );
  });
});
