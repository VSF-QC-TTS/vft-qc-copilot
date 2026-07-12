import { useTheme } from '@/app/providers/theme-provider';
import type { Theme } from '@/app/providers/theme-provider';

function isTheme(value: string): value is Theme {
  return value === 'light' || value === 'dark' || value === 'system';
}

export function ThemeSwitcher() {
  const { setTheme, theme } = useTheme();

  return (
    <select
      aria-label="Giao diện"
      className="h-8 rounded-md border border-border bg-surface px-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring"
      onChange={(event) => {
        if (isTheme(event.target.value)) {
          setTheme(event.target.value);
        }
      }}
      value={theme}
    >
      <option value="light">Sáng</option>
      <option value="dark">Tối</option>
      <option value="system">Hệ thống</option>
    </select>
  );
}
