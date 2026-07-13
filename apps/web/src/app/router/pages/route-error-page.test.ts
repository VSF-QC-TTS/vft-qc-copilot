import { describe, expect, it } from 'vitest';

import { routeErrorCopy } from '@/app/router/pages/route-error-page';

function routeError(status: number) {
  return {
    data: null,
    internal: false,
    status,
    statusText: String(status),
  };
}

describe('routeErrorCopy', () => {
  it('maps route errors to Vietnamese safe messages', () => {
    expect(routeErrorCopy(routeError(404))).toMatchObject({
      title: 'Không tìm thấy trang',
    });
    expect(routeErrorCopy(routeError(403))).toMatchObject({
      title: 'Không có quyền truy cập',
    });
    expect(routeErrorCopy(routeError(401))).toMatchObject({
      title: 'Cần đăng nhập lại',
    });
  });

  it('does not expose raw error messages for generic route errors', () => {
    expect(routeErrorCopy(new Error('Authorization: Bearer secret-token'))).toMatchObject({
      title: 'Không thể tải trang',
      description: 'Route hiện tại gặp lỗi. Hãy thử tải lại hoặc quay về trang chính.',
    });
  });
});
