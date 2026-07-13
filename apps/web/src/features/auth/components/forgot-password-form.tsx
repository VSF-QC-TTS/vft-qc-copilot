import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Link } from 'react-router';
import { Loader2 } from 'lucide-react';
import { Input } from '@/shared/components/ui/input';
import { Label } from '@/shared/components/ui/label';
import { Button } from '@/shared/components/ui/button';
import { useForgotPasswordMutation } from '@/features/auth/hooks/use-auth-queries';
import { forgotPasswordRequestSchema } from '@/features/auth/schemas/auth.schemas';
import type { ForgotPasswordRequest } from '@/features/auth/types/auth.types';
import { cn } from '@/shared/lib/utils';
import { ApiError } from '@/shared/api/api-error';

interface ForgotPasswordFormProps {
  className?: string;
}

export function ForgotPasswordForm({ className }: ForgotPasswordFormProps) {
  const forgotPasswordMutation = useForgotPasswordMutation();
  const [success, setSuccess] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
    setError,
  } = useForm<ForgotPasswordRequest>({
    resolver: zodResolver(forgotPasswordRequestSchema),
    defaultValues: { email: '' },
  });

  const onSubmit = async (data: ForgotPasswordRequest) => {
    try {
      await forgotPasswordMutation.mutateAsync(data);
      setSuccess(true);
    } catch (error) {
      if (error instanceof ApiError && error.fieldErrors?.length) {
        error.fieldErrors.forEach((fe) => {
          if (fe.field === 'email') {
            setError(fe.field, { message: fe.message });
          } else {
            setError('root', { message: fe.message });
          }
        });
      } else {
        // Generic error
        setError('root', {
          message: error instanceof Error ? error.message : 'Yêu cầu đặt lại mật khẩu thất bại',
        });
      }
    }
  };

  if (success) {
    return (
      <div className={cn('flex flex-col items-center justify-center gap-4 text-center', className)}>
        <h3 className="text-lg font-medium text-foreground">Yêu cầu đã được gửi</h3>
        <p className="text-sm text-muted-foreground">
          Nếu email này được liên kết với một tài khoản, bạn sẽ nhận được một đường dẫn đặt lại mật
          khẩu.
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
        <p className="text-sm text-muted-foreground text-center mb-2">
          Nhập địa chỉ email của bạn, chúng tôi sẽ gửi một đường dẫn để đặt lại mật khẩu.
        </p>
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
        <Button type="submit" disabled={isSubmitting} className="mt-2 w-full">
          {isSubmitting ? (
            <>
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              Đang gửi yêu cầu...
            </>
          ) : (
            'Gửi yêu cầu'
          )}
        </Button>
      </form>

      <div className="text-center text-sm">
        <Link to="/login" className="font-medium text-primary hover:underline">
          Trở lại Đăng nhập
        </Link>
      </div>
    </div>
  );
}
