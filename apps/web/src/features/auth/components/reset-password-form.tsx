import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Link, useSearchParams } from 'react-router';
import { Loader2 } from 'lucide-react';
import { PasswordInput } from '@/shared/components/ui/password-input';
import { Label } from '@/shared/components/ui/label';
import { Button } from '@/shared/components/ui/button';
import { useResetPasswordMutation } from '@/features/auth/hooks/use-auth-queries';
import { resetPasswordFormSchema } from '@/features/auth/schemas/auth.schemas';
import type { ResetPasswordFormValues } from '@/features/auth/types/auth.types';
import { cn } from '@/shared/lib/utils';
import { ApiError } from '@/shared/api/api-error';

interface ResetPasswordFormProps {
  className?: string;
}

export function ResetPasswordForm({ className }: ResetPasswordFormProps) {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const resetPasswordMutation = useResetPasswordMutation();
  const [success, setSuccess] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
    setError,
  } = useForm<ResetPasswordFormValues>({
    resolver: zodResolver(resetPasswordFormSchema),
    defaultValues: { token: token || '', password: '', confirmPassword: '' },
  });

  const onSubmit = async (data: ResetPasswordFormValues) => {
    try {
      await resetPasswordMutation.mutateAsync({
        token: data.token,
        password: data.password,
      });
      setSuccess(true);
    } catch (error) {
      if (error instanceof ApiError && error.status === 400) {
        setError('root', { message: 'Đường dẫn đặt lại mật khẩu không hợp lệ hoặc đã hết hạn.' });
      } else if (error instanceof ApiError && error.fieldErrors?.length) {
        error.fieldErrors.forEach((fe) => {
          if (fe.field === 'password') {
            setError(fe.field, { message: fe.message });
          } else {
            setError('root', { message: fe.message });
          }
        });
      } else {
        setError('root', { message: error instanceof Error ? error.message : 'Đặt lại mật khẩu thất bại' });
      }
    }
  };

  if (!token) {
    return (
      <div className={cn('flex flex-col items-center justify-center gap-4 text-center', className)}>
        <h3 className="text-lg font-medium text-danger-fg">Lỗi</h3>
        <p className="text-sm text-muted-foreground">
          Thiếu token xác nhận. Vui lòng kiểm tra lại đường dẫn trong email của bạn.
        </p>
        <Button asChild className="mt-4 w-full">
          <Link to="/login">Trở về trang Đăng nhập</Link>
        </Button>
      </div>
    );
  }

  if (success) {
    return (
      <div className={cn('flex flex-col items-center justify-center gap-4 text-center', className)}>
        <h3 className="text-lg font-medium text-foreground">Đặt lại mật khẩu thành công</h3>
        <p className="text-sm text-muted-foreground">
          Mật khẩu của bạn đã được thay đổi. Bạn có thể đăng nhập bằng mật khẩu mới.
        </p>
        <Button asChild className="mt-4 w-full">
          <Link to="/login">Trở về trang Đăng nhập</Link>
        </Button>
      </div>
    );
  }

  return (
    <div className={cn('flex flex-col gap-4', className)}>
      <form
        noValidate
        onSubmit={(e) => {
          void handleSubmit(onSubmit)(e);
        }}
        className="flex flex-col gap-4"
      >
        {errors.root && (
          <div className="rounded-md bg-danger-bg px-3 py-2 text-sm text-danger-fg border border-danger-border">
            {errors.root.message}
          </div>
        )}
        <div className="space-y-2">
          <Label htmlFor="password" className={errors.password ? "text-danger-fg" : ""}>
            Mật khẩu mới
          </Label>
          <PasswordInput
            {...register('password')}
            id="password"
            placeholder="••••••••"
            autoComplete="new-password"
            disabled={isSubmitting}
            className={errors.password ? 'border-danger-border focus-visible:ring-danger-border' : ''}
          />
          {errors.password && <p className="text-sm text-danger-fg">{errors.password.message}</p>}
        </div>
        <div className="space-y-2">
          <Label htmlFor="confirmPassword" className={errors.confirmPassword ? "text-danger-fg" : ""}>
            Xác nhận mật khẩu mới
          </Label>
          <PasswordInput
            {...register('confirmPassword')}
            id="confirmPassword"
            placeholder="••••••••"
            autoComplete="new-password"
            disabled={isSubmitting}
            className={errors.confirmPassword ? 'border-danger-border focus-visible:ring-danger-border' : ''}
          />
          {errors.confirmPassword && <p className="text-sm text-danger-fg">{errors.confirmPassword.message}</p>}
        </div>
        {/* Hidden token field for React Hook Form validation */}
        <input type="hidden" {...register('token')} />
        
        <Button
          type="submit"
          disabled={isSubmitting}
          className="mt-2 w-full"
        >
          {isSubmitting ? (
            <>
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              Đang xử lý...
            </>
          ) : (
            'Đặt lại mật khẩu'
          )}
        </Button>
      </form>
    </div>
  );
}
