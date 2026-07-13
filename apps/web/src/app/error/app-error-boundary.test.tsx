import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { AppErrorBoundary } from '@/app/error/app-error-boundary';

function ThrowingView(): never {
  throw new Error('render failed with sensitive request payload');
}

function HealthyView() {
  return <div>Nội dung đã khôi phục</div>;
}

describe('AppErrorBoundary', () => {
  it('shows a generic fallback without rendering the thrown message', () => {
    render(
      <AppErrorBoundary>
        <ThrowingView />
      </AppErrorBoundary>,
    );

    expect(screen.getByRole('heading', { name: 'Không thể hiển thị trang' })).toBeInTheDocument();
    expect(screen.queryByText(/sensitive request payload/i)).not.toBeInTheDocument();
  });

  it('allows users to retry rendering after a failure', async () => {
    const user = userEvent.setup();
    const { rerender } = render(
      <AppErrorBoundary>
        <ThrowingView />
      </AppErrorBoundary>,
    );

    rerender(
      <AppErrorBoundary>
        <HealthyView />
      </AppErrorBoundary>,
    );
    await user.click(screen.getByRole('button', { name: 'Thử lại' }));

    expect(screen.getByText('Nội dung đã khôi phục')).toBeInTheDocument();
  });
});
