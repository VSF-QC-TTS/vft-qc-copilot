import { describe, expect, it } from 'vitest';

import { testRunSseConnectionLabel } from '@/features/test-runs/sse/use-test-run-sse';

describe('TestRun SSE hook helpers', () => {
  it('keeps realtime labels separate from TestRun terminal status text', () => {
    expect(testRunSseConnectionLabel('open')).toBe('Realtime hoạt động');
    expect(testRunSseConnectionLabel('reconnecting')).toBe('Đang kết nối lại realtime');
    expect(testRunSseConnectionLabel('error')).toBe('Realtime tạm ngắt');
    expect(testRunSseConnectionLabel('closed', 'terminal')).toBe('Realtime đã đóng');
  });
});
