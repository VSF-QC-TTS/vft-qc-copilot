import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import { Label } from '@/shared/components/ui/label';
import { isApiError } from '@/shared/api/api-error';
import { notifyError, notifySuccess } from '@/shared/lib/notifications';
import { useCreateAdminUser } from '@/features/admin-users/hooks/use-admin-users';
import {
  adminCreateUserRequestSchema,
  type AdminCreateUserRequest,
  type AdminCreateUserResponse,
} from '@/features/admin-users/types/admin-user';

export function AdminUsersPage() {
  const createUserMutation = useCreateAdminUser();
  const [createdUser, setCreatedUser] = useState<AdminCreateUserResponse>();
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<AdminCreateUserRequest>({
    resolver: zodResolver(adminCreateUserRequestSchema),
    defaultValues: {
      email: '',
      displayName: '',
    },
  });

  const onSubmit = async (data: AdminCreateUserRequest) => {
    try {
      const response = await createUserMutation.mutateAsync({
        email: data.email.trim(),
        displayName: data.displayName.trim(),
      });
      setCreatedUser(response);
      reset();
      notifySuccess('Đã gửi email invite');
    } catch (error) {
      if (isApiError(error) && error.status === 403) {
        setError('root', {
          message: 'Tài khoản hiện tại không có quyền PLATFORM_ADMIN.',
        });
        notifyError('Không có quyền tạo user');
        return;
      }
      if (isApiError(error) && error.status === 409) {
        setError('email', { message: 'Email đã tồn tại' });
        return;
      }
      if (isApiError(error) && error.fieldErrors?.length) {
        error.fieldErrors.forEach((fieldError) => {
          if (fieldError.field === 'email' || fieldError.field === 'displayName') {
            setError(fieldError.field, { message: fieldError.message });
          } else {
            setError('root', { message: fieldError.message });
          }
        });
        return;
      }
      setError('root', { message: 'Không thể tạo user invite' });
      notifyError('Không thể tạo user invite');
    }
  };

  const isPending = isSubmitting || createUserMutation.isPending;

  return (
    <div className="mx-auto w-full max-w-5xl px-4 py-8 sm:px-6 lg:px-8">
      <div className="mb-6">
        <p className="text-sm text-muted-foreground">Platform Admin</p>
        <h1 className="mt-1 text-2xl font-semibold tracking-tight">Quản lý user invite</h1>
        <p className="mt-2 max-w-3xl text-sm text-muted-foreground">
          Tạo tài khoản nội bộ và gửi invite email. Người dùng hoàn tất onboarding qua route
          reset-password bằng token một lần do backend gửi trong email.
        </p>
      </div>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_340px]">
        <section className="rounded-lg border bg-surface p-5">
          <h2 className="text-lg font-semibold">Tạo user invite</h2>
          <form
            noValidate
            className="mt-5 space-y-4"
            onSubmit={(event) => void handleSubmit(onSubmit)(event)}
          >
            {errors.root ? (
              <div className="rounded-md border border-danger-soft bg-danger-soft p-3 text-sm text-danger">
                {errors.root.message}
              </div>
            ) : null}

            <div className="space-y-2">
              <Label htmlFor="admin-user-email">Email</Label>
              <Input
                id="admin-user-email"
                type="email"
                placeholder="user@vinfast.vn"
                autoComplete="email"
                disabled={isPending}
                {...register('email')}
              />
              {errors.email ? <p className="text-sm text-danger">{errors.email.message}</p> : null}
            </div>

            <div className="space-y-2">
              <Label htmlFor="admin-user-display-name">Tên hiển thị</Label>
              <Input
                id="admin-user-display-name"
                placeholder="Nguyen Van A"
                autoComplete="name"
                disabled={isPending}
                {...register('displayName')}
              />
              {errors.displayName ? (
                <p className="text-sm text-danger">{errors.displayName.message}</p>
              ) : null}
            </div>

            <div className="flex items-center justify-between gap-3">
              <p className="text-xs text-muted-foreground">
                Backend tạo USER ở trạng thái PENDING_EMAIL_VERIFICATION.
              </p>
              <Button type="submit" disabled={isPending}>
                Gửi invite
              </Button>
            </div>
          </form>
        </section>

        <aside className="space-y-6">
          <section className="rounded-lg border bg-surface p-5">
            <h2 className="text-sm font-semibold">Luồng invite</h2>
            <ol className="mt-4 space-y-3 text-sm text-muted-foreground">
              <li>1. Platform Admin tạo user bằng email nội bộ.</li>
              <li>2. Backend gửi invite email chứa token một lần.</li>
              <li>3. User đặt mật khẩu tại route reset-password.</li>
            </ol>
          </section>

          <section className="rounded-lg border bg-surface p-5">
            <h2 className="text-sm font-semibold">Invite gần nhất</h2>
            {createdUser ? (
              <dl className="mt-4 space-y-3 text-sm">
                <MetaRow label="Email" value={createdUser.email} />
                <MetaRow label="Tên" value={createdUser.user.name} />
                <MetaRow label="Trạng thái" value={createdUser.status} />
                <MetaRow label="User ID" value={createdUser.id} mono />
              </dl>
            ) : (
              <p className="mt-3 text-sm text-muted-foreground">
                Chưa có user invite nào được tạo trong phiên này.
              </p>
            )}
          </section>
        </aside>
      </div>
    </div>
  );
}

function MetaRow({ label, value, mono = false }: { label: string; value: string; mono?: boolean }) {
  return (
    <div>
      <dt className="text-muted-foreground">{label}</dt>
      <dd className={['mt-1 break-all font-medium', mono ? 'font-mono text-xs' : ''].join(' ')}>
        {value}
      </dd>
    </div>
  );
}
