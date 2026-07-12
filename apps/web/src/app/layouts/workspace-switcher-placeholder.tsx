import { cn } from '@/shared/lib/utils';
import type { ComponentProps } from 'react';
import { ChevronDown } from 'lucide-react';

interface WorkspaceSwitcherPlaceholderProps extends ComponentProps<'button'> {
  isCollapsed?: boolean;
}

export function WorkspaceSwitcherPlaceholder({
  className,
  isCollapsed,
  ...props
}: WorkspaceSwitcherPlaceholderProps) {
  return (
    <button
      type="button"
      className={cn(
        'flex h-9 items-center rounded-md border border-transparent bg-transparent px-2 text-sm font-medium transition-colors duration-150 active:bg-surface-pressed hover:border-border hover:bg-surface-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring',
        isCollapsed ? 'justify-center' : 'gap-2',
        className,
      )}
      aria-label="Chuyển Workspace"
      {...props}
    >
      <div className="h-5 w-5 shrink-0 rounded bg-surface-subtle border border-border" />
      {!isCollapsed && (
        <>
          <span className="truncate max-w-[120px]">Workspace</span>
          <ChevronDown aria-hidden="true" className="h-4 w-4 shrink-0 text-foreground-muted" />
        </>
      )}
    </button>
  );
}
