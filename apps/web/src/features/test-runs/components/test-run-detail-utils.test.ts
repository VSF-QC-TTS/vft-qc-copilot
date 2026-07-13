import { describe, expect, it } from 'vitest';
import {
  canCancelTestRun,
  isTerminalTestRunStatus,
  metricText,
  runFailureExplanation,
  targetDisplayName,
  testRunProgressPercent,
  testRunStatusLabel,
  testRunStatusTone,
} from '@/features/test-runs/components/test-run-detail-utils';
import type {
  TestRunDetailResponse,
  TestRunTargetResponse,
} from '@/features/test-runs/types/test-run';

describe('test run detail utils', () => {
  it('maps terminal and non-terminal statuses for rendering', () => {
    expect(testRunStatusLabel('QUEUED')).toBe('Đang chờ chạy');
    expect(testRunStatusTone('RUNNING')).toBe('info');
    expect(testRunStatusTone('COMPLETED')).toBe('success');
    expect(testRunStatusTone('FAILED')).toBe('danger');
    expect(isTerminalTestRunStatus('COMPLETED')).toBe(true);
    expect(isTerminalTestRunStatus('RUNNING')).toBe(false);
  });

  it('allows cancel only for queued and running states', () => {
    expect(canCancelTestRun('QUEUED')).toBe(true);
    expect(canCancelTestRun('RUNNING')).toBe(true);
    expect(canCancelTestRun('CANCEL_REQUESTED')).toBe(false);
    expect(canCancelTestRun('COMPLETED')).toBe(false);
  });

  it('explains run-level failure separately from testcase failure', () => {
    expect(runFailureExplanation('FAILED')).toContain('run-level');
    expect(runFailureExplanation('CANCEL_REQUESTED')).toContain('yêu cầu hủy');
    expect(runFailureExplanation('COMPLETED')).toBeNull();
  });

  it('formats progress and summary metrics safely', () => {
    expect(testRunProgressPercent(testRun({ progressCompleted: 3, progressTotal: 10 }))).toBe(30);
    expect(testRunProgressPercent(testRun({ progressCompleted: 1, progressTotal: 0 }))).toBe(0);
    expect(metricText({ passRate: 0.875, total: 10, ready: true }, 'passRate')).toBe('0.88');
    expect(metricText({ passRate: 0.875, total: 10, ready: true }, 'total')).toBe('10');
    expect(metricText({ passRate: 0.875, total: 10, ready: true }, 'missing')).toBe('—');
  });

  it('includes baseline model in target display name', () => {
    const target: TestRunTargetResponse = {
      id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
      targetType: 'LLM_BASELINE',
      chatbotTargetId: null,
      providerId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
      model: 'gpt-4.1-mini',
      name: 'OpenAI baseline',
      orderIndex: 1,
      config: {},
    };
    expect(targetDisplayName(target)).toBe('OpenAI baseline · gpt-4.1-mini');
  });
});

function testRun(
  patch: Pick<TestRunDetailResponse, 'progressCompleted' | 'progressTotal'>,
): TestRunDetailResponse {
  return {
    id: '11111111-1111-4111-8111-111111111111',
    projectId: '22222222-2222-4222-8222-222222222222',
    runType: 'SINGLE_TARGET',
    datasetId: '33333333-3333-4333-8333-333333333333',
    datasetVersionId: '44444444-4444-4444-8444-444444444444',
    datasetSchemaId: '55555555-5555-4555-8555-555555555555',
    datasetSchemaVersion: 1,
    judgeProviderId: null,
    judgeModel: null,
    status: 'RUNNING',
    progressCompleted: patch.progressCompleted,
    progressTotal: patch.progressTotal,
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
