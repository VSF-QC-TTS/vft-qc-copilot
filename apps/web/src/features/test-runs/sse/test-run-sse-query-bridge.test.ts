import { QueryClient } from '@tanstack/react-query';
import { describe, expect, it, vi } from 'vitest';

import { testRunKeys } from '@/features/test-runs/hooks/use-test-runs';
import { createTestRunSseQueryBridge } from '@/features/test-runs/sse/test-run-sse-query-bridge';
import {
  parseTestRunSseEvent,
  type ParsedSseEvent,
} from '@/features/test-runs/sse/test-run-sse.types';
import type { TestRunDetailResponse } from '@/features/test-runs/types/test-run';

const TEST_RUN_ID = '11111111-1111-4111-8111-111111111111';
const PROJECT_ID = '22222222-2222-4222-8222-222222222222';

describe('TestRun SSE query bridge', () => {
  it('refetches authoritative detail when RUN_STARTED arrives', () => {
    const { bridge, invalidateQueries } = setupBridge();

    bridge.handleEvent(event(1, 'RUN_STARTED', { status: 'RUNNING' }));

    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: testRunKeys.detail(TEST_RUN_ID) });
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: testRunKeys.project(PROJECT_ID) });
  });

  it('updates RUN_PROGRESS in the cached detail without refetching expensive results', () => {
    const { bridge, queryClient, invalidateQueries } = setupBridge();
    queryClient.setQueryData(testRunKeys.detail(TEST_RUN_ID), testRun());

    bridge.handleEvent(
      event(2, 'RUN_PROGRESS', {
        status: 'RUNNING',
        completed: 7,
        total: 10,
        passRate: 0.7,
      }),
    );

    expect(
      queryClient.getQueryData<TestRunDetailResponse>(testRunKeys.detail(TEST_RUN_ID)),
    ).toMatchObject({
      progressCompleted: 7,
      progressTotal: 10,
      summaryMetrics: { passRate: 0.7 },
      updatedAt: '2026-07-03T10:00:00+07:00',
    });
    expect(invalidateQueries).not.toHaveBeenCalledWith({
      queryKey: testRunKeys.results(TEST_RUN_ID),
    });
  });

  it('debounces high-volume testcase result invalidation', () => {
    vi.useFakeTimers();
    const { bridge, invalidateQueries } = setupBridge();

    for (let sequenceNo = 1; sequenceNo <= 100; sequenceNo += 1) {
      bridge.handleEvent(event(sequenceNo, 'TESTCASE_COMPLETED', { status: 'PASSED' }));
    }

    expect(invalidateQueries).not.toHaveBeenCalledWith({
      queryKey: testRunKeys.results(TEST_RUN_ID),
    });

    vi.runOnlyPendingTimers();

    expect(invalidateQueries).toHaveBeenCalledTimes(1);
    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: testRunKeys.results(TEST_RUN_ID),
    });
    vi.useRealTimers();
  });

  it('terminal events update visible status and refetch final REST data', () => {
    const { bridge, queryClient, invalidateQueries } = setupBridge();
    queryClient.setQueryData(testRunKeys.detail(TEST_RUN_ID), testRun());

    bridge.handleEvent(
      event(9, 'RUN_COMPLETED', {
        status: 'COMPLETED',
        completed: 10,
        total: 10,
      }),
    );

    expect(
      queryClient.getQueryData<TestRunDetailResponse>(testRunKeys.detail(TEST_RUN_ID)),
    ).toMatchObject({
      status: 'COMPLETED',
      progressCompleted: 10,
      progressTotal: 10,
    });
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: testRunKeys.detail(TEST_RUN_ID) });
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: testRunKeys.project(PROJECT_ID) });
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: testRunKeys.results(TEST_RUN_ID) });
  });

  it('can invalidate snapshot on reconnect recovery or visibility regain', () => {
    const { bridge, invalidateQueries } = setupBridge();

    bridge.invalidateSnapshot();

    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: testRunKeys.detail(TEST_RUN_ID) });
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: testRunKeys.project(PROJECT_ID) });
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: testRunKeys.results(TEST_RUN_ID) });
  });
});

function setupBridge() {
  const queryClient = new QueryClient();
  const invalidateQueries = vi.spyOn(queryClient, 'invalidateQueries');
  const bridge = createTestRunSseQueryBridge({
    queryClient,
    projectId: PROJECT_ID,
    testRunId: TEST_RUN_ID,
  });

  return { bridge, queryClient, invalidateQueries };
}

function event(
  sequenceNo: number,
  eventType: ParsedSseEvent['event'],
  payload: Record<string, unknown>,
) {
  const parsed = parseTestRunSseEvent({
    id: String(sequenceNo),
    event: eventType,
    data: JSON.stringify({
      eventId: `event-${sequenceNo}`,
      sequenceNo,
      testRunId: TEST_RUN_ID,
      eventType,
      createdAt: '2026-07-03T10:00:00+07:00',
      payload,
    }),
  });

  if (!parsed.ok) {
    throw new Error(parsed.invalidEvent.reason);
  }

  return parsed.event;
}

function testRun(): TestRunDetailResponse {
  return {
    id: TEST_RUN_ID,
    projectId: PROJECT_ID,
    runType: 'SINGLE_TARGET',
    datasetId: '33333333-3333-4333-8333-333333333333',
    datasetVersionId: '44444444-4444-4444-8444-444444444444',
    datasetSchemaId: '55555555-5555-4555-8555-555555555555',
    datasetSchemaVersion: 1,
    judgeProviderId: null,
    judgeModel: null,
    status: 'RUNNING',
    progressCompleted: 0,
    progressTotal: 10,
    summaryMetrics: {},
    domainConfigSnapshot: {},
    promptfooConfigSnapshot: {},
    domainConfigHash: 'domain',
    promptfooConfigHash: 'promptfoo',
    targets: [],
    evaluators: [],
    startedBy: '66666666-6666-4666-8666-666666666666',
    startedAt: null,
    finishedAt: null,
    cancelRequestedAt: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  };
}
