import { Outlet } from 'react-router';
import { useAuth } from '@/app/providers/auth-provider';

export function PlatformAdminGuard() {
  const { user, isLoading } = useAuth();

  if (isLoading) {
    return (
      <div className="flex min-h-[320px] items-center justify-center bg-background">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-surface-subtle border-t-foreground-muted" />
      </div>
    );
  }

  if (user?.systemRole !== 'PLATFORM_ADMIN') {
    return (
      <div className="mx-auto w-full max-w-3xl px-4 py-10 sm:px-6 lg:px-8">
        <section className="rounded-lg border bg-surface p-6">
          <p className="text-sm font-medium text-danger">Không có quyền truy cập</p>
          <h1 className="mt-2 text-2xl font-semibold tracking-tight">Khu vực Platform Admin</h1>
          <p className="mt-3 text-sm text-muted-foreground">
            Trang này chỉ dành cho tài khoản có system role PLATFORM_ADMIN.
          </p>
        </section>
      </div>
    );
  }

  return <Outlet />;
}
