import { cn } from '@/shared/lib/utils';
import type { ComponentProps } from 'react';
import { useLogoutMutation } from '@/features/auth/hooks/use-auth-queries';
import { LogOut } from 'lucide-react';
import { useNavigate } from 'react-router';

export function UserMenuPlaceholder({ className, ...props }: ComponentProps<'button'>) {
  const logoutMutation = useLogoutMutation();
  const navigate = useNavigate();

  const handleLogout = async () => {
    await logoutMutation.mutateAsync();
    void navigate('/login');
  };

  return (
    <button
      type="button"
      onClick={() => {
        void handleLogout();
      }}
      className={cn(
        'flex h-8 w-8 items-center justify-center rounded-full border border-border bg-surface-subtle transition-colors duration-150 active:bg-surface-pressed hover:bg-surface-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring',
        className,
      )}
      aria-label="Đăng xuất"
      {...props}
    >
      <LogOut className="h-4 w-4 text-foreground-muted" />
    </button>
  );
}
