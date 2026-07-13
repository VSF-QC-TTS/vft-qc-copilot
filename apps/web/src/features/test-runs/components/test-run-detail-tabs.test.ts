import { describe, expect, it } from 'vitest';

import {
  detailTabFromSearchParams,
  parseDetailTab,
} from '@/features/test-runs/components/test-run-detail-tabs';

describe('test run detail tabs', () => {
  it('parses only supported tab values', () => {
    expect(parseDetailTab('results')).toBe('results');
    expect(parseDetailTab('matrix')).toBe('matrix');
    expect(parseDetailTab('unknown')).toBeUndefined();
    expect(parseDetailTab(null)).toBeUndefined();
  });

  it('derives deep-link tab from result and matrix params', () => {
    expect(detailTabFromSearchParams(new URLSearchParams('resultId=123'))).toBe('results');
    expect(detailTabFromSearchParams(new URLSearchParams('resultMode=FINAL'))).toBe('results');
    expect(detailTabFromSearchParams(new URLSearchParams('matrixPage=1'))).toBe('matrix');
    expect(detailTabFromSearchParams(new URLSearchParams())).toBe('overview');
  });

  it('uses an explicit tab over inferred params', () => {
    expect(detailTabFromSearchParams(new URLSearchParams('tab=matrix&resultId=123'))).toBe(
      'matrix',
    );
  });
});
