import { QueryClient } from '@tanstack/react-query';
import { describe, expect, it, vi } from 'vitest';

import {
  invalidateReviewedTestCaseResult,
  testRunKeys,
} from '@/features/test-runs/hooks/use-test-runs';

const TEST_RUN_ID = '11111111-1111-4111-8111-111111111111';

describe('test run hooks helpers', () => {
  it('invalidates result list, both detail modes, and matrix after review save', () => {
    const queryClient = new QueryClient();
    const invalidateQueries = vi.spyOn(queryClient, 'invalidateQueries');

    invalidateReviewedTestCaseResult(queryClient, TEST_RUN_ID, 9122);

    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: testRunKeys.results(TEST_RUN_ID),
    });
    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: testRunKeys.resultDetail(9122, 'MACHINE'),
    });
    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: testRunKeys.resultDetail(9122, 'FINAL'),
    });
    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: testRunKeys.matrix(TEST_RUN_ID),
    });
  });
});
