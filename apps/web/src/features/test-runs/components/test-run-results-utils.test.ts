import { describe, expect, it } from 'vitest';

import {
  jsonPreview,
  machineFinalChanged,
  parseResultId,
  parseResultMode,
  parseResultPage,
  reviewedText,
  scoreText,
} from '@/features/test-runs/components/test-run-results-utils';
import type { TestCaseResultDetailResponse } from '@/features/test-runs/types/test-run';

describe('test run results utils', () => {
  it('parses URL state defensively', () => {
    expect(parseResultMode('MACHINE')).toBe('MACHINE');
    expect(parseResultMode('invalid')).toBe('FINAL');
    expect(parseResultPage('2')).toBe(2);
    expect(parseResultPage('-1')).toBe(0);
    expect(parseResultId('42')).toBe(42);
    expect(parseResultId('0')).toBeUndefined();
  });

  it('formats missing optional fields safely', () => {
    expect(scoreText(0.875)).toBe('0.88');
    expect(scoreText(null)).toBe('—');
    expect(jsonPreview(undefined)).toBe('—');
    expect(jsonPreview({ answer: '<script>alert(1)</script>' })).toContain('<script>');
  });

  it('keeps machine and final results distinct', () => {
    expect(machineFinalChanged(result({ finalStatus: 'PASSED', finalScore: 0.95 }))).toBe(true);
    expect(machineFinalChanged(result({ finalStatus: 'FAILED', finalScore: 0.1 }))).toBe(false);
  });

  it('labels review state without mutating machine result', () => {
    expect(reviewedText(result({ review: null }))).toBe('Chưa review');
    expect(
      reviewedText(
        result({
          review: {
            id: 1,
            testCaseResultId: 100,
            reviewerId: '77777777-7777-4777-8777-777777777777',
            reviewStatus: 'OVERRIDDEN',
            overrideStatus: 'PASSED',
            overrideScore: 0.95,
            note: 'Masked note',
            createdAt: '2026-01-01T00:00:00Z',
            updatedAt: '2026-01-01T00:00:00Z',
          },
        }),
      ),
    ).toBe('Có override');
  });
});

function result(patch: Partial<TestCaseResultDetailResponse>): TestCaseResultDetailResponse {
  return {
    id: 100,
    testRunId: '11111111-1111-4111-8111-111111111111',
    testRunTargetId: '22222222-2222-4222-8222-222222222222',
    datasetRowId: 10,
    machineStatus: 'FAILED',
    machineScore: 0.1,
    machinePassed: false,
    finalStatus: 'FAILED',
    finalScore: 0.1,
    finalPassed: false,
    errorCategory: null,
    request: {},
    response: {},
    extractedOutput: {},
    gradingResult: {},
    componentResults: {},
    tokenUsage: {},
    latencyMs: null,
    machineSummary: null,
    review: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...patch,
  };
}
