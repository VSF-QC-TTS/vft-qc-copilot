import { ForgotPasswordForm } from '@/features/auth/components/forgot-password-form';
import { Logo } from '@/shared/components/logo';

export function ForgotPasswordPage() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-sidebar px-4">
      <div className="w-full max-w-sm rounded-lg border border-border bg-surface p-6 sm:p-8">
        <div className="mb-6 flex flex-col items-center space-y-4 text-center">
          <Logo className="h-8 justify-center" />
          <div className="space-y-1">
            <h1 className="text-xl font-semibold tracking-tight text-foreground">
              Quên mật khẩu
            </h1>
          </div>
        </div>
        <ForgotPasswordForm />
      </div>
    </div>
  );
}
