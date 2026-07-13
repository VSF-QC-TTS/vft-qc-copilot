import { describe, expect, it } from 'vitest';

import {
  reviewFormError,
  parseOverrideStatusOption,
  parseReviewStatusOption,
  toReviewRequest,
  type ReviewFormState,
} from '@/features/test-runs/components/test-case-result-review-utils';

describe('test case result review utils', () => {
  it('builds a compact backend review request', () => {
    expect(
      toReviewRequest({
        reviewStatus: 'OVERRIDDEN',
        overrideStatus: 'PASSED',
        overrideScore: '0.95',
        note: '  Acceptable answer  ',
      }),
    ).toEqual({
      reviewStatus: 'OVERRIDDEN',
      overrideStatus: 'PASSED',
      overrideScore: 0.95,
      note: 'Acceptable answer',
    });
  });

  it('omits empty optional override fields', () => {
    expect(toReviewRequest(state({ overrideStatus: 'NONE', overrideScore: '', note: '' }))).toEqual(
      {
        reviewStatus: 'APPROVED',
      },
    );
  });

  it('validates note length and score before submit', () => {
    expect(reviewFormError(state({ overrideScore: '-1' }))).toBe(
      'Override score phải là số không âm.',
    );
    expect(reviewFormError(state({ note: 'a'.repeat(4001) }))).toBe('Ghi chú tối đa 4000 ký tự.');
    expect(reviewFormError(state({ overrideScore: '0.5' }))).toBeUndefined();
  });

  it('parses native select values through enum schemas', () => {
    expect(parseReviewStatusOption('OVERRIDDEN')).toBe('OVERRIDDEN');
    expect(parseReviewStatusOption('INVALID')).toBeUndefined();
    expect(parseOverrideStatusOption('NONE')).toBe('NONE');
    expect(parseOverrideStatusOption('PASSED')).toBe('PASSED');
    expect(parseOverrideStatusOption('INVALID')).toBeUndefined();
  });
});

function state(patch: Partial<ReviewFormState>): ReviewFormState {
  return {
    reviewStatus: 'APPROVED',
    overrideStatus: 'NONE',
    overrideScore: '',
    note: '',
    ...patch,
  };
}
