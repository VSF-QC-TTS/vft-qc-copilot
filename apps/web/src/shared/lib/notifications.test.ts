import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as sonner from 'sonner';

import { notifyError, notifyInfo, notifySuccess, notifyWarning } from '@/shared/lib/notifications';

vi.mock('sonner', () => ({
  toast: {
    success: vi.fn<() => void>(),
    info: vi.fn<() => void>(),
    warning: vi.fn<() => void>(),
    error: vi.fn<() => void>(),
  },
}));

describe('notifications', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('calls success with default duration 4000', () => {
    notifySuccess('Đã lưu');
    expect(sonner.toast.success).toHaveBeenCalledWith('Đã lưu', {
      duration: 4000,
    });
  });

  it('calls error with default duration 8000', () => {
    notifyError('Lỗi');
    expect(sonner.toast.error).toHaveBeenCalledWith('Lỗi', {
      duration: 8000,
    });
  });

  it('calls warning with default duration 7000', () => {
    notifyWarning('Cảnh báo');
    expect(sonner.toast.warning).toHaveBeenCalledWith('Cảnh báo', {
      duration: 7000,
    });
  });

  it('calls info with default duration 5000', () => {
    notifyInfo('Thông tin');
    expect(sonner.toast.info).toHaveBeenCalledWith('Thông tin', {
      duration: 5000,
    });
  });

  it('deduplicates the same notification inside the configured window', () => {
    vi.spyOn(Date, 'now').mockReturnValue(1000);

    notifyWarning('Kết nối realtime bị gián đoạn.');
    notifyWarning('Kết nối realtime bị gián đoạn.');

    expect(sonner.toast.warning).toHaveBeenCalledTimes(1);
  });
});
