import { WorkspaceSwitcher } from '@/app/layouts/workspace-switcher';
import { UserMenuPlaceholder } from '@/app/layouts/user-menu-placeholder';
import { Logo } from '@/shared/components/logo';
import { cn } from '@/shared/lib/utils';
import { NavLink } from 'react-router';
import { ChevronLeft, ChevronRight, FolderKanban, KeyRound, ServerCog } from 'lucide-react';
import { useState } from 'react';

interface GlobalSidebarProps {
  className?: string;
  id?: string;
  onNavigate?: () => void;
}

const navItems = [
  { label: 'Project', href: '/workspaces', icon: FolderKanban },
  { label: 'Personal Secret', href: '/settings/secrets', icon: KeyRound },
  { label: 'Personal LLM Provider', href: '/settings/providers', icon: ServerCog },
];

export function GlobalSidebar({ className, id, onNavigate }: GlobalSidebarProps) {
  const [isCollapsed, setIsCollapsed] = useState(false);

  return (
    <aside
      id={id}
      className={cn(
        'group flex flex-col border-r border-border bg-sidebar transition-[width] duration-200 ease-out',
        isCollapsed ? 'w-16' : 'w-64',
        className,
      )}
      aria-label="Navigation chính"
    >
      <div className="flex h-12 items-center justify-between px-4">
        {!isCollapsed && <Logo />}
        {isCollapsed && (
          <img src="/favicon.png" alt="VinFast" className="mx-auto h-6 w-6 object-contain" />
        )}
      </div>

      <div className="border-b border-border p-2">
        <WorkspaceSwitcher isCollapsed={isCollapsed} className="w-full" />
      </div>

      <nav className="flex-1 space-y-1 px-2 py-4" aria-label="Menu chính">
        {navItems.map((item) => (
          <NavLink
            key={item.href}
            to={item.href}
            onClick={onNavigate}
            title={isCollapsed ? item.label : undefined}
            className={({ isActive }) =>
              cn(
                'relative flex h-[34px] items-center gap-2 rounded-md px-2 text-sm font-medium transition-colors duration-150 active:bg-surface-pressed hover:bg-surface-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring',
                isActive
                  ? 'bg-surface-selected text-primary before:absolute before:left-0 before:top-1/2 before:h-full before:max-h-[24px] before:-translate-y-1/2 before:w-[2px] before:bg-accent before:content-[""]'
                  : 'text-foreground-secondary hover:text-foreground',
                isCollapsed && 'justify-center',
              )
            }
            aria-label={item.label}
          >
            <item.icon
              className={cn('shrink-0', isCollapsed ? 'h-5 w-5' : 'h-4 w-4')}
              aria-hidden="true"
            />
            {!isCollapsed && <span className="truncate">{item.label}</span>}
          </NavLink>
        ))}
      </nav>

      <div className="flex flex-col gap-2 border-t border-border p-2">
        <div
          className={cn(
            'flex items-center px-2 py-2',
            isCollapsed ? 'justify-center' : 'justify-start',
          )}
        >
          <UserMenuPlaceholder />
          {!isCollapsed && <span className="ml-3 text-sm font-medium">Tài khoản</span>}
        </div>
        <button
          type="button"
          onClick={() => setIsCollapsed(!isCollapsed)}
          className="flex w-full items-center justify-center rounded-md p-2 text-foreground-muted transition-colors duration-150 active:bg-surface-pressed hover:bg-surface-hover hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring"
          aria-label={isCollapsed ? 'Mở rộng sidebar' : 'Thu gọn sidebar'}
          aria-expanded={!isCollapsed}
        >
          {isCollapsed ? (
            <ChevronRight aria-hidden="true" className="h-5 w-5" />
          ) : (
            <ChevronLeft aria-hidden="true" className="h-5 w-5" />
          )}
        </button>
      </div>
    </aside>
  );
}
