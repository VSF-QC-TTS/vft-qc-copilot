import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { ThemeProvider } from '@/app/providers/theme-provider';

describe('ThemeProvider', () => {
  it('renders children with the default light theme', () => {
    render(
      <ThemeProvider>
        <span>Nội dung ứng dụng</span>
      </ThemeProvider>,
    );

    expect(screen.getByText('Nội dung ứng dụng')).toBeVisible();
    expect(document.documentElement).toHaveAttribute('data-theme', 'light');
  });
});
