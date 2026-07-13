import type {
  TestRunDetailResponse,
  TestRunStatus,
  TestRunTargetResponse,
} from '@/features/test-runs/types/test-run';

export type TestRunStatusTone = 'neutral' | 'info' | 'warning' | 'success' | 'danger';

export function testRunStatusLabel(status: TestRunStatus) {
  const labels: Record<TestRunStatus, string> = {
    CREATED: 'Đã tạo',
    QUEUED: 'Đang chờ chạy',
    RUNNING: 'Đang chạy',
    CANCEL_REQUESTED: 'Đang yêu cầu hủy',
    COMPLETED: 'Hoàn tất',
    FAILED: 'Lỗi hệ thống',
    CANCELLED: 'Đã hủy',
  };
  return labels[status];
}

export function testRunStatusTone(status: TestRunStatus): TestRunStatusTone {
  if (status === 'COMPLETED') return 'success';
  if (status === 'FAILED') return 'danger';
  if (status === 'CANCEL_REQUESTED' || status === 'CANCELLED') return 'warning';
  if (status === 'RUNNING' || status === 'QUEUED') return 'info';
  return 'neutral';
}

export function isTerminalTestRunStatus(status: TestRunStatus) {
  return status === 'COMPLETED' || status === 'FAILED' || status === 'CANCELLED';
}

export function canCancelTestRun(status: TestRunStatus) {
  return status === 'QUEUED' || status === 'RUNNING';
}

export function testRunProgressPercent(testRun: TestRunDetailResponse) {
  if (testRun.progressTotal <= 0) return 0;
  return Math.min(100, Math.round((testRun.progressCompleted / testRun.progressTotal) * 100));
}

export function metricText(metrics: Record<string, unknown>, key: string) {
  const value = metrics[key];
  if (value === null || value === undefined) return '—';
  if (typeof value === 'number') return Number.isInteger(value) ? String(value) : value.toFixed(2);
  if (typeof value === 'boolean') return value ? 'Có' : 'Không';
  if (typeof value === 'string' && value.trim()) return value;
  return '—';
}

export function targetDisplayName(target: TestRunTargetResponse) {
  if (target.targetType === 'LLM_BASELINE') {
    return target.model ? `${target.name} · ${target.model}` : target.name;
  }
  return target.name;
}

export function runFailureExplanation(status: TestRunStatus) {
  if (status === 'FAILED') {
    return 'TestRun FAILED là lỗi hệ thống hoặc lỗi thực thi run-level, khác với testcase FAILED trong machine/final results.';
  }
  if (status === 'CANCEL_REQUESTED') {
    return 'Backend đã nhận yêu cầu hủy. Runner sẽ dừng an toàn và chuyển trạng thái khi xử lý xong.';
  }
  return null;
}
