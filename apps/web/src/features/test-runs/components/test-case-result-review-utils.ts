import type {
  ReviewStatus,
  TestCaseResultStatus,
  UpsertTestCaseResultReviewRequest,
} from '@/features/test-runs/types/test-run';
import {
  reviewStatusSchema,
  testCaseResultStatusSchema,
} from '@/features/test-runs/types/test-run';

export const REVIEW_STATUS_OPTIONS: ReadonlyArray<{ value: ReviewStatus; label: string }> = [
  { value: 'APPROVED', label: 'Đã duyệt' },
  { value: 'REJECTED', label: 'Từ chối' },
  { value: 'NEEDS_FIX', label: 'Cần sửa' },
  { value: 'OVERRIDDEN', label: 'Override' },
];

export const OVERRIDE_STATUS_OPTIONS: ReadonlyArray<{
  value: TestCaseResultStatus;
  label: string;
}> = [
  { value: 'PASSED', label: 'Đạt' },
  { value: 'FAILED', label: 'Không đạt' },
  { value: 'ERROR', label: 'Lỗi' },
  { value: 'JUDGE_ERROR', label: 'Lỗi judge' },
  { value: 'TIMEOUT', label: 'Timeout' },
];

export interface ReviewFormState {
  reviewStatus: ReviewStatus;
  overrideStatus: TestCaseResultStatus | 'NONE';
  overrideScore: string;
  note: string;
}

export function toReviewRequest(state: ReviewFormState): UpsertTestCaseResultReviewRequest {
  const note = state.note.trim();
  const score = Number(state.overrideScore);

  return {
    reviewStatus: state.reviewStatus,
    ...(state.overrideStatus !== 'NONE' ? { overrideStatus: state.overrideStatus } : {}),
    ...(state.overrideScore.trim() && Number.isFinite(score) ? { overrideScore: score } : {}),
    ...(note ? { note } : {}),
  };
}

export function parseReviewStatusOption(value: string): ReviewStatus | undefined {
  const result = reviewStatusSchema.safeParse(value);
  return result.success ? result.data : undefined;
}

export function parseOverrideStatusOption(
  value: string,
): ReviewFormState['overrideStatus'] | undefined {
  if (value === 'NONE') {
    return 'NONE';
  }

  const result = testCaseResultStatusSchema.safeParse(value);
  return result.success ? result.data : undefined;
}

export function reviewFormError(state: ReviewFormState): string | undefined {
  if (state.note.length > 4000) {
    return 'Ghi chú tối đa 4000 ký tự.';
  }

  if (state.overrideScore.trim()) {
    const score = Number(state.overrideScore);
    if (!Number.isFinite(score) || score < 0) {
      return 'Override score phải là số không âm.';
    }
  }

  return undefined;
}
