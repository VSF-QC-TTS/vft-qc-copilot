import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { CheckCircle2, Loader2, XCircle } from 'lucide-react';
import { Logo } from '@/shared/components/logo';
import { Button } from '@/shared/components/ui/button';
import { useVerifyEmailMutation } from '@/features/auth/hooks/use-auth-queries';
import { ApiError } from '@/shared/api/api-error';

export function VerifyEmailPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const verifyEmailMutation = useVerifyEmailMutation();
  const [status, setStatus] = useState<'loading' | 'success' | 'error'>('loading');
  const [errorMessage, setErrorMessage] = useState('');
  const hasAttempted = useRef(false);

  useEffect(() => {
    if (!token) {
      setStatus('error');
      setErrorMessage('Thiếu token xác nhận. Vui lòng kiểm tra lại đường dẫn trong email của bạn.');
      return;
    }

    if (hasAttempted.current) return;
    hasAttempted.current = true;

    verifyEmailMutation.mutate(
      { token },
      {
        onSuccess: () => {
          setStatus('success');
        },
        onError: (error) => {
          setStatus('error');
          if (error instanceof ApiError && error.status === 400) {
            setErrorMessage('Đường dẫn xác nhận không hợp lệ hoặc đã hết hạn.');
          } else {
            setErrorMessage(error instanceof Error ? error.message : 'Xác nhận email thất bại');
          }
        },
      }
    );
  }, [token, verifyEmailMutation]);

  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-sidebar px-4">
      <div className="w-full max-w-sm rounded-lg border border-border bg-surface p-6 sm:p-8">
        <div className="mb-6 flex flex-col items-center space-y-4 text-center">
          <Logo className="h-8 justify-center" />
          <h1 className="text-xl font-semibold tracking-tight text-foreground">
            Xác nhận Email
          </h1>
        </div>

        <div className="flex flex-col items-center justify-center gap-4 text-center">
          {status === 'loading' && (
            <>
              <Loader2 className="h-12 w-12 animate-spin text-primary" />
              <p className="text-sm text-muted-foreground">
                Đang xác nhận email của bạn, vui lòng đợi...
              </p>
            </>
          )}

          {status === 'success' && (
            <>
              <CheckCircle2 className="h-12 w-12 text-success-fg" />
              <p className="text-sm text-muted-foreground">
                Email của bạn đã được xác nhận thành công. Bạn có thể đăng nhập ngay bây giờ.
              </p>
              <Button asChild className="mt-4 w-full">
                <Link to="/login">Tiếp tục Đăng nhập</Link>
              </Button>
            </>
          )}

          {status === 'error' && (
            <>
              <XCircle className="h-12 w-12 text-danger-fg" />
              <p className="text-sm text-danger-fg">
                {errorMessage}
              </p>
              <Button asChild className="mt-4 w-full">
                <Link to="/login">Trở về trang Đăng nhập</Link>
              </Button>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
