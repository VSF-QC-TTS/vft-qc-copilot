import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Link } from 'react-router';
import { Loader2 } from 'lucide-react';
import { Input } from '@/shared/components/ui/input';
import { PasswordInput } from '@/shared/components/ui/password-input';
import { Label } from '@/shared/components/ui/label';
import { Button } from '@/shared/components/ui/button';
import { useRegisterMutation } from '@/features/auth/hooks/use-auth-queries';
import { registerFormSchema } from '@/features/auth/schemas/auth.schemas';
import type { RegisterFormValues } from '@/features/auth/types/auth.types';
import { cn } from '@/shared/lib/utils';
import { ApiError } from '@/shared/api/api-error';

interface RegisterFormProps {
  className?: string;
}

export function RegisterForm({ className }: RegisterFormProps) {
  const registerMutation = useRegisterMutation();
  const [success, setSuccess] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
    setError,
  } = useForm<RegisterFormValues>({
    resolver: zodResolver(registerFormSchema),
    defaultValues: { email: '', displayName: '', password: '', confirmPassword: '' },
  });

  const onSubmit = async (data: RegisterFormValues) => {
    try {
      await registerMutation.mutateAsync({
        email: data.email,
        displayName: data.displayName,
        password: data.password,
      });
      setSuccess(true);
    } catch (error) {
      if (error instanceof ApiError && error.status === 403) {
        setError('root', { message: 'Đăng ký tự do hiện đang bị tắt bởi quản trị viên.' });
      } else if (error instanceof ApiError && error.status === 409) {
        setError('email', { message: 'Email đã tồn tại' });
      } else if (error instanceof ApiError && error.fieldErrors?.length) {
        error.fieldErrors.forEach((fe) => {
          if (fe.field === 'email' || fe.field === 'displayName' || fe.field === 'password') {
            setError(fe.field, { message: fe.message });
          } else {
            setError('root', { message: fe.message });
          }
        });
      } else {
        setError('root', { message: error instanceof Error ? error.message : 'Đăng ký thất bại' });
      }
    }
  };

  if (success) {
    return (
      <div className={cn('flex flex-col items-center justify-center gap-4 text-center', className)}>
        <h3 className="text-lg font-medium text-foreground">Đăng ký thành công</h3>
        <p className="text-sm text-muted-foreground">
          Một email xác nhận đã được gửi đến địa chỉ email của bạn. Vui lòng kiểm tra hộp thư để
          kích hoạt tài khoản.
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
          <Label htmlFor="email" className={errors.email ? 'text-danger-fg' : ''}>
            Email
          </Label>
          <Input
            {...register('email')}
            id="email"
            type="email"
            placeholder="name@vinfast.vn"
            autoComplete="email"
            disabled={isSubmitting}
            className={errors.email ? 'border-danger-border focus-visible:ring-danger-border' : ''}
          />
          {errors.email && <p className="text-sm text-danger-fg">{errors.email.message}</p>}
        </div>
        <div className="space-y-2">
          <Label htmlFor="displayName" className={errors.displayName ? 'text-danger-fg' : ''}>
            Tên hiển thị
          </Label>
          <Input
            {...register('displayName')}
            id="displayName"
            type="text"
            placeholder="Nguyen Van A"
            autoComplete="name"
            disabled={isSubmitting}
            className={
              errors.displayName ? 'border-danger-border focus-visible:ring-danger-border' : ''
            }
          />
          {errors.displayName && (
            <p className="text-sm text-danger-fg">{errors.displayName.message}</p>
          )}
        </div>
        <div className="space-y-2">
          <Label htmlFor="password" className={errors.password ? 'text-danger-fg' : ''}>
            Mật khẩu
          </Label>
          <PasswordInput
            {...register('password')}
            id="password"
            placeholder="••••••••"
            autoComplete="new-password"
            disabled={isSubmitting}
            className={
              errors.password ? 'border-danger-border focus-visible:ring-danger-border' : ''
            }
          />
          {errors.password && <p className="text-sm text-danger-fg">{errors.password.message}</p>}
        </div>
        <div className="space-y-2">
          <Label
            htmlFor="confirmPassword"
            className={errors.confirmPassword ? 'text-danger-fg' : ''}
          >
            Xác nhận mật khẩu
          </Label>
          <PasswordInput
            {...register('confirmPassword')}
            id="confirmPassword"
            placeholder="••••••••"
            autoComplete="new-password"
            disabled={isSubmitting}
            className={
              errors.confirmPassword ? 'border-danger-border focus-visible:ring-danger-border' : ''
            }
          />
          {errors.confirmPassword && (
            <p className="text-sm text-danger-fg">{errors.confirmPassword.message}</p>
          )}
        </div>
        <Button type="submit" disabled={isSubmitting} className="mt-2 w-full">
          {isSubmitting ? (
            <>
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              Đang xử lý...
            </>
          ) : (
            'Đăng ký'
          )}
        </Button>
      </form>

      <div className="text-center text-sm">
        Đã có tài khoản?{' '}
        <Link to="/login" className="font-medium text-primary hover:underline">
          Đăng nhập
        </Link>
      </div>
    </div>
  );
}
