import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  archiveProject,
  createProject,
  deleteProject,
  getProjectSetupStatus,
  updateProject,
} from '@/features/projects/api/projects.api';

const requestJsonMock = vi.hoisted(() =>
  vi.fn<(path: string, schema: unknown, init?: RequestInit) => Promise<{ data: unknown }>>(),
);
const requestVoidMock = vi.hoisted(() =>
  vi.fn<(path: string, init?: RequestInit) => Promise<{ data: void }>>(),
);

vi.mock('@/shared/api/api', () => ({
  apiClient: {
    requestJson: requestJsonMock,
    requestVoid: requestVoidMock,
  },
}));

describe('projects API', () => {
  beforeEach(() => {
    requestJsonMock.mockReset();
    requestVoidMock.mockReset();
  });

  it('maps project lifecycle mutations to backend endpoints', async () => {
    const project = {
      id: 'project-1',
      workspaceId: 'workspace-1',
      name: 'VF Assistant Regression',
      description: 'Regression suite',
      createdBy: 'user-1',
      createdByUser: null,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
      archivedAt: null,
    };
    requestJsonMock.mockResolvedValue({ data: project });
    requestVoidMock.mockResolvedValueOnce({ data: undefined });

    await expect(
      createProject('workspace-1', {
        name: 'VF Assistant Regression',
        description: 'Regression suite',
      }),
    ).resolves.toEqual(project);
    await expect(
      updateProject('project-1', {
        name: 'VF Assistant Regression',
        description: 'Updated scope',
      }),
    ).resolves.toEqual(project);
    await expect(archiveProject('project-1')).resolves.toEqual(project);
    await expect(deleteProject('project-1')).resolves.toBeUndefined();

    expect(requestJsonMock).toHaveBeenNthCalledWith(
      1,
      '/api/v1/workspaces/workspace-1/projects',
      expect.any(Object),
      {
        method: 'POST',
        body: JSON.stringify({
          name: 'VF Assistant Regression',
          description: 'Regression suite',
        }),
      },
    );
    expect(requestJsonMock).toHaveBeenNthCalledWith(
      2,
      '/api/v1/projects/project-1',
      expect.any(Object),
      {
        method: 'PATCH',
        body: JSON.stringify({
          name: 'VF Assistant Regression',
          description: 'Updated scope',
        }),
      },
    );
    expect(requestJsonMock).toHaveBeenNthCalledWith(
      3,
      '/api/v1/projects/project-1/archive',
      expect.any(Object),
      {
        method: 'POST',
      },
    );
    expect(requestVoidMock).toHaveBeenCalledWith('/api/v1/projects/project-1', {
      method: 'DELETE',
    });
  });

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
