import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';

import { ThemeSwitcher } from '@/app/layouts/theme-switcher';
import { ThemeProvider } from '@/app/providers/theme-provider';

describe('ThemeSwitcher', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('lets the user switch theme without reloading', async () => {
    const user = userEvent.setup();
    render(
      <ThemeProvider>
        <ThemeSwitcher />
      </ThemeProvider>,
    );

    await user.selectOptions(screen.getByRole('combobox', { name: 'Giao diện' }), 'dark');

    expect(document.documentElement).toHaveAttribute('data-theme', 'dark');
    expect(localStorage.getItem('vfqc.theme')).toBe('dark');
  });
});
