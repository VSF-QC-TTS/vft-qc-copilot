import { act, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ThemeProvider, useTheme } from '@/app/providers/theme-provider';

function TestComponent() {
  const { theme, setTheme } = useTheme();
  return (
    <div>
      <span data-testid="current-theme">{theme}</span>
      <button onClick={() => setTheme('dark')}>Set Dark</button>
      <button onClick={() => setTheme('light')}>Set Light</button>
      <button onClick={() => setTheme('system')}>Set System</button>
    </div>
  );
}

describe('ThemeProvider', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove('light', 'dark');
    document.documentElement.removeAttribute('data-theme');
    document.documentElement.style.colorScheme = '';

    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      value: vi.fn<(query: string) => unknown>().mockImplementation((query: string) => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: vi.fn<(...args: unknown[]) => unknown>(),
        removeListener: vi.fn<(...args: unknown[]) => unknown>(),
        addEventListener: vi.fn<(...args: unknown[]) => unknown>(),
        removeEventListener: vi.fn<(...args: unknown[]) => unknown>(),
        dispatchEvent: vi.fn<(...args: unknown[]) => unknown>(),
      })),
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('renders children with the default light theme', () => {
    render(
      <ThemeProvider>
        <span>Nội dung ứng dụng</span>
      </ThemeProvider>,
    );

    expect(screen.getByText('Nội dung ứng dụng')).toBeVisible();
    expect(document.documentElement).toHaveAttribute('data-theme', 'light');
    expect(document.documentElement).toHaveClass('light');
  });

  it('uses persisted preference from vfqc.theme', () => {
    localStorage.setItem('vfqc.theme', 'dark');
    render(
      <ThemeProvider>
        <TestComponent />
      </ThemeProvider>,
    );

    expect(screen.getByTestId('current-theme')).toHaveTextContent('dark');
    expect(document.documentElement).toHaveAttribute('data-theme', 'dark');
    expect(document.documentElement).toHaveClass('dark');
  });

  it('changes theme dynamically and persists it', () => {
    render(
      <ThemeProvider>
        <TestComponent />
      </ThemeProvider>,
    );

    act(() => {
      screen.getByText('Set Dark').click();
    });

    expect(screen.getByTestId('current-theme')).toHaveTextContent('dark');
    expect(document.documentElement).toHaveClass('dark');
    expect(localStorage.getItem('vfqc.theme')).toBe('dark');
  });

  it('supports system preference', () => {
    let prefersDark = true;
    let changeHandler: (() => void) | undefined;

    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      value: vi.fn<(query: string) => unknown>().mockImplementation((query: string) => ({
        get matches() {
          return query === '(prefers-color-scheme: dark)' && prefersDark;
        },
        media: query,
        onchange: null,
        addListener: vi.fn<(...args: unknown[]) => unknown>(),
        removeListener: vi.fn<(...args: unknown[]) => unknown>(),
        addEventListener: vi.fn<(event: string, handler: () => void) => void>(
          (_event: string, handler: () => void) => {
            changeHandler = handler;
          },
        ),
        removeEventListener: vi.fn<(...args: unknown[]) => unknown>(),
        dispatchEvent: vi.fn<(...args: unknown[]) => unknown>(),
      })),
    });

    render(
      <ThemeProvider>
        <TestComponent />
      </ThemeProvider>,
    );

    act(() => {
      screen.getByText('Set System').click();
    });

    expect(screen.getByTestId('current-theme')).toHaveTextContent('system');
    // It should apply dark because the mock matches prefers-color-scheme: dark
    expect(document.documentElement).toHaveClass('dark');
    expect(localStorage.getItem('vfqc.theme')).toBe('system');

    prefersDark = false;
    act(() => changeHandler?.());

    expect(document.documentElement).toHaveClass('light');
    expect(document.documentElement).toHaveAttribute('data-theme', 'light');
  });

  it('rejects useTheme outside ThemeProvider', () => {
    expect(() => render(<TestComponent />)).toThrow('useTheme must be used within a ThemeProvider');
  });
});
