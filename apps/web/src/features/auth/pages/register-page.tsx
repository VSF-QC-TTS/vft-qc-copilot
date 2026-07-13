import { RegisterForm } from '@/features/auth/components/register-form';
import { Logo } from '@/shared/components/logo';

export function RegisterPage() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-sidebar px-4">
      <div className="w-full max-w-sm rounded-lg border border-border bg-surface p-6 sm:p-8">
        <div className="mb-6 flex flex-col items-center space-y-4 text-center">
          <Logo className="h-8 justify-center" />
          <div className="space-y-1">
            <h1 className="text-xl font-semibold tracking-tight text-foreground">
              Đăng ký tài khoản
            </h1>
            <p className="text-sm text-foreground-muted">Tạo tài khoản VinFast QC Copilot mới</p>
          </div>
        </div>
        <RegisterForm />
      </div>
    </div>
  );
}
