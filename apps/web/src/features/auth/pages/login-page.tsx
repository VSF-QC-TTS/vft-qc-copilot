import { LoginForm } from '@/features/auth/components/login-form';
import { Logo } from '@/shared/components/logo';

export function LoginPage() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-sidebar px-4">
      <div className="w-full max-w-sm rounded-lg border border-border bg-surface p-6 shadow-sm sm:p-8">
        <div className="mb-6 flex flex-col items-center space-y-4 text-center">
          <Logo className="h-8 justify-center" />
          <div className="space-y-1">
            <h1 className="text-xl font-semibold tracking-tight text-foreground">
              Đăng nhập vào hệ thống
            </h1>
            <p className="text-sm text-foreground-muted">
              Sử dụng tài khoản VinFast QC Copilot của bạn
            </p>
          </div>
        </div>
        <LoginForm />
      </div>
    </div>
  );
}
