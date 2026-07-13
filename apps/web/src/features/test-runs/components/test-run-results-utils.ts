import {
  resultModeSchema,
  type ResultMode,
  type TestCaseResultDetailResponse,
  type TestCaseResultStatus,
} from '@/features/test-runs/types/test-run';

export const TEST_RUN_RESULTS_PAGE_SIZE = 20;

export function parseResultMode(value: string | null): ResultMode {
  const result = resultModeSchema.safeParse(value);
  return result.success ? result.data : 'FINAL';
}

export function parseResultPage(value: string | null): number {
  const page = Number(value);
  return Number.isInteger(page) && page >= 0 ? page : 0;
}

export function parseResultId(value: string | null): number | undefined {
  const resultId = Number(value);
  return Number.isInteger(resultId) && resultId > 0 ? resultId : undefined;
}

export function resultStatusLabel(status: TestCaseResultStatus): string {
  switch (status) {
    case 'PASSED':
      return 'Đạt';
    case 'FAILED':
      return 'Không đạt';
    case 'ERROR':
      return 'Lỗi';
    case 'JUDGE_ERROR':
      return 'Lỗi judge';
    case 'TIMEOUT':
      return 'Timeout';
    default: {
      const exhaustive: never = status;
      return exhaustive;
    }
  }
}

export function resultStatusTone(status: TestCaseResultStatus): 'success' | 'danger' | 'warning' {
  switch (status) {
    case 'PASSED':
      return 'success';
    case 'FAILED':
      return 'danger';
    case 'ERROR':
    case 'JUDGE_ERROR':
    case 'TIMEOUT':
      return 'warning';
    default: {
      const exhaustive: never = status;
      return exhaustive;
    }
  }
}

export function scoreText(value: number | null | undefined): string {
  return typeof value === 'number' && Number.isFinite(value) ? value.toFixed(2) : '—';
}

export function latencyText(value: number | null | undefined): string {
  return typeof value === 'number' && Number.isFinite(value) ? `${String(value)} ms` : '—';
}

export function reviewedText(result: TestCaseResultDetailResponse): string {
  if (!result.review) {
    return 'Chưa review';
  }
  if (result.review.overrideStatus || result.review.overrideScore != null) {
    return 'Có override';
  }
  return reviewStatusLabel(result.review.reviewStatus);
}

export function reviewStatusLabel(
  status: NonNullable<TestCaseResultDetailResponse['review']>['reviewStatus'],
): string {
  switch (status) {
    case 'APPROVED':
      return 'Đã duyệt';
    case 'REJECTED':
      return 'Từ chối';
    case 'NEEDS_FIX':
      return 'Cần sửa';
    case 'OVERRIDDEN':
      return 'Đã override';
    default: {
      const exhaustive: never = status;
      return exhaustive;
    }
  }
}

export function jsonPreview(value: unknown): string {
  if (value === null || value === undefined) {
    return '—';
  }
  return JSON.stringify(value, null, 2);
}

export function machineFinalChanged(result: TestCaseResultDetailResponse): boolean {
  return (
    result.machineStatus !== result.finalStatus ||
    result.machinePassed !== result.finalPassed ||
    result.machineScore !== result.finalScore
  );
}
