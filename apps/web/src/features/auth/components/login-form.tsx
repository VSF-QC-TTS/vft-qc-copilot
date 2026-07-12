import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useNavigate, Link } from 'react-router';
import { Loader2 } from 'lucide-react';
import { Input } from '@/shared/components/ui/input';
import { PasswordInput } from '@/shared/components/ui/password-input';
import { Label } from '@/shared/components/ui/label';
import { Button } from '@/shared/components/ui/button';
import { useLoginMutation } from '@/features/auth/hooks/use-auth-queries';
import { loginRequestSchema } from '@/features/auth/schemas/auth.schemas';
import type { LoginRequest } from '@/features/auth/types/auth.types';
import { cn } from '@/shared/lib/utils';
import { ApiError } from '@/shared/api/api-error';

interface LoginFormProps {
  className?: string;
}

export function LoginForm({ className }: LoginFormProps) {
  const navigate = useNavigate();
  const loginMutation = useLoginMutation();

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
    setError,
  } = useForm<LoginRequest>({
    resolver: zodResolver(loginRequestSchema),
    defaultValues: { email: '', password: '' },
  });

  const onSubmit = async (data: LoginRequest) => {
    try {
      await loginMutation.mutateAsync(data);
      void navigate('/workspaces');
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        setError('root', { message: 'Email hoặc mật khẩu không đúng' });
      } else if (error instanceof ApiError && error.fieldErrors?.length) {
        error.fieldErrors.forEach((fe) => {
          if (fe.field === 'email' || fe.field === 'password') {
            setError(fe.field, { message: fe.message });
          } else {
            setError('root', { message: fe.message });
          }
        });
      } else {
        setError('root', { message: error instanceof Error ? error.message : 'Đăng nhập thất bại' });
      }
    }
  };

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
          <Label htmlFor="email" className={errors.email ? "text-danger-fg" : ""}>
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
          <div className="flex items-center justify-between">
            <Label htmlFor="password" className={errors.password ? "text-danger-fg" : ""}>
              Mật khẩu
            </Label>
            <Link
              to="/forgot-password"
              className="text-sm font-medium text-primary hover:underline"
            >
              Quên mật khẩu?
            </Link>
          </div>
          <PasswordInput
            {...register('password')}
            id="password"
            placeholder="••••••••"
            autoComplete="current-password"
            disabled={isSubmitting}
            className={errors.password ? 'border-danger-border focus-visible:ring-danger-border' : ''}
          />
          {errors.password && <p className="text-sm text-danger-fg">{errors.password.message}</p>}
        </div>
        <Button
          type="submit"
          disabled={isSubmitting}
          className="mt-2 w-full"
        >
          {isSubmitting ? (
            <>
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              Đang đăng nhập...
            </>
          ) : (
            'Đăng nhập'
          )}
        </Button>
      </form>
      
      <div className="text-center text-sm">
        Chưa có tài khoản?{' '}
        <Link to="/register" className="font-medium text-primary hover:underline">
          Đăng ký
        </Link>
      </div>
    </div>
  );
}
