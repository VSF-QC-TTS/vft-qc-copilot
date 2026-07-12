import { cn } from '@/shared/lib/utils';
import { WorkspaceSwitcher } from '@/app/layouts/workspace-switcher';
import { UserMenuPlaceholder } from '@/app/layouts/user-menu-placeholder';
import { ThemeSwitcher } from '@/app/layouts/theme-switcher';
import { Menu } from 'lucide-react';

interface AppHeaderProps {
  isMenuOpen: boolean;
  onMenuClick: () => void;
  className?: string;
}

export function AppHeader({ isMenuOpen, onMenuClick, className }: AppHeaderProps) {
  return (
    <header
      className={cn(
        'sticky top-0 z-30 flex h-12 w-full items-center justify-between border-b border-border bg-topbar px-4 backdrop-blur-md sm:px-6',
        className,
      )}
    >
      <div className="flex items-center gap-2">
        <button
          type="button"
          onClick={onMenuClick}
          className="mr-2 inline-flex items-center justify-center rounded-md p-2 text-foreground-muted transition-all active:scale-[0.98] hover:bg-surface-hover hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring lg:hidden"
          aria-label="Mở menu"
          aria-controls="mobile-navigation"
          aria-expanded={isMenuOpen}
        >
          <Menu aria-hidden="true" className="h-5 w-5" />
        </button>
        <WorkspaceSwitcher className="lg:hidden" />
      </div>
      <div className="flex items-center gap-2">
        <ThemeSwitcher />
        <UserMenuPlaceholder />
      </div>
    </header>
  );
}
