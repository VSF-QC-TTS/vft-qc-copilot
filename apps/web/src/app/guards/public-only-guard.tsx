import { Navigate, Outlet, useLocation } from 'react-router';
import { z } from 'zod';
import { useAuth } from '@/app/providers/auth-provider';

const locationStateSchema = z
  .object({
    from: z
      .object({
        pathname: z.string(),
      })
      .optional(),
  })
  .catch({ from: undefined });

export function PublicOnlyGuard() {
  const { isAuthenticated, isLoading } = useAuth();
  const location = useLocation();

  if (isLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-surface-subtle border-t-foreground-muted" />
      </div>
    );
  }

  if (isAuthenticated) {
    const parsedState = locationStateSchema.safeParse(location.state);
    const fromPath =
      parsedState.success && parsedState.data.from?.pathname
        ? parsedState.data.from.pathname
        : '/workspaces';

    return <Navigate to={fromPath} replace />;
  }

  return <Outlet />;
}
