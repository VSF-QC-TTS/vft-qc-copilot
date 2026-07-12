import { act, render, screen, waitFor, waitForElementToBeRemoved } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { NotificationToaster } from '@/shared/components/toast/notification-toaster';
import { ThemeProvider } from '@/app/providers/theme-provider';
import { notifySuccess } from '@/shared/lib/notifications';

describe('NotificationToaster', () => {
  let narrowScreen = false;
  let screenChangeHandler: (() => void) | undefined;

  beforeEach(() => {
    narrowScreen = false;
    screenChangeHandler = undefined;
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      value: vi.fn<(query: string) => unknown>().mockImplementation((query: string) => ({
        get matches() {
          return query === '(max-width: 767px)' && narrowScreen;
        },
        media: query,
        onchange: null,
        addListener: vi.fn<(...args: unknown[]) => unknown>(),
        removeListener: vi.fn<(...args: unknown[]) => unknown>(),
        addEventListener: vi.fn<(event: string, handler: () => void) => void>(
          (_event: string, handler: () => void) => {
            if (query === '(max-width: 767px)') {
              screenChangeHandler = handler;
            }
          },
        ),
        removeEventListener: vi.fn<(...args: unknown[]) => unknown>(),
        dispatchEvent: vi.fn<(...args: unknown[]) => unknown>(),
      })),
    });
  });

  it('renders without crashing and shows toasts', async () => {
    render(
      <ThemeProvider>
        <NotificationToaster />
      </ThemeProvider>,
    );
    act(() => {
      notifySuccess('Test thông báo');
    });
    expect(await screen.findByText('Test thông báo')).toBeInTheDocument();
  });

  it('moves notifications to the top center on narrow screens', async () => {
    render(
      <ThemeProvider>
        <NotificationToaster />
      </ThemeProvider>,
    );

    act(() => {
      notifySuccess('Thông báo responsive');
    });
    await screen.findByText('Thông báo responsive');

    const toaster = document.querySelector('[data-sonner-toaster]');
    expect(toaster).not.toBeNull();
    expect(toaster).toHaveAttribute('data-x-position', 'right');
    expect(toaster).toHaveAttribute('data-y-position', 'top');

    narrowScreen = true;
    act(() => screenChangeHandler?.());

    const narrowToaster = document.querySelector('[data-sonner-toaster]');
    expect(narrowToaster).toHaveAttribute('data-x-position', 'center');
    expect(narrowToaster).toHaveAttribute('data-y-position', 'top');
  });

  it('shows at most three notifications at once', async () => {
    render(
      <ThemeProvider>
        <NotificationToaster />
      </ThemeProvider>,
    );

    act(() => {
      notifySuccess('Thông báo 1');
      notifySuccess('Thông báo 2');
      notifySuccess('Thông báo 3');
      notifySuccess('Thông báo 4');
    });
    await screen.findByText('Thông báo 4');

    await waitFor(() => {
      expect(document.querySelectorAll('[data-sonner-toast][data-visible="true"]')).toHaveLength(3);
    });
  });

  it('dismisses a notification with the accessible close button', async () => {
    const user = userEvent.setup();
    render(
      <ThemeProvider>
        <NotificationToaster />
      </ThemeProvider>,
    );

    act(() => {
      notifySuccess('Thông báo có thể đóng');
    });
    const message = await screen.findByText('Thông báo có thể đóng');

    await user.click(screen.getByRole('button', { name: 'Đóng thông báo' }));
    await waitForElementToBeRemoved(message);
    expect(screen.queryByText('Thông báo có thể đóng')).not.toBeInTheDocument();
  });
});
