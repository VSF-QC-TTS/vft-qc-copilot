import { useEffect, useRef } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useAddWorkspaceMemberMutation } from '@/features/workspaces/hooks/use-workspaces';
import {
  AddWorkspaceMemberRequest,
  addWorkspaceMemberRequestSchema,
} from '@/features/workspaces/types/workspace';
import { notifyError, notifySuccess } from '@/shared/lib/notifications';
import { isApiError } from '@/shared/api/api-error';

import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/shared/components/ui/form';
import { Input } from '@/shared/components/ui/input';
import { Button } from '@/shared/components/ui/button';

export interface AddWorkspaceMemberDialogProps {
  workspaceId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function AddWorkspaceMemberDialog({
  workspaceId,
  open,
  onOpenChange,
}: AddWorkspaceMemberDialogProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const addMutation = useAddWorkspaceMemberMutation(workspaceId);

  const form = useForm<AddWorkspaceMemberRequest>({
    resolver: zodResolver(addWorkspaceMemberRequestSchema),
    defaultValues: {
      email: '',
      role: 'MEMBER',
    },
  });

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;

    if (open) {
      dialog.showModal();
    } else {
      dialog.close();
      form.reset();
    }
  }, [open, form]);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return undefined;

    const handleClose = () => onOpenChange(false);
    dialog.addEventListener('close', handleClose);
    return () => dialog.removeEventListener('close', handleClose);
  }, [onOpenChange]);

  const onSubmit = (data: AddWorkspaceMemberRequest) => {
    addMutation.mutate(data, {
      onSuccess: () => {
        notifySuccess('Đã thêm thành viên');
        onOpenChange(false);
      },
      onError: (error) => {
        if (isApiError(error) && error.status === 403) {
          notifyError('Bạn không có quyền thực hiện hành động này');
        } else if (isApiError(error) && error.status === 409) {
          notifyError('Thành viên này đã tồn tại trong workspace');
        } else {
          notifyError('Không thể thêm thành viên');
        }
      },
    });
  };

  return (
    <dialog
      ref={dialogRef}
      className="w-full max-w-md rounded-lg border bg-background p-6 backdrop:bg-overlay"
    >
      <div className="flex flex-col space-y-4">
        <div className="flex flex-col space-y-1.5">
          <h2 className="text-lg font-semibold leading-none tracking-tight">Thêm thành viên</h2>
          <p className="text-sm text-muted-foreground">Mời thành viên mới vào workspace.</p>
        </div>

        <Form {...form}>
          <form
            onSubmit={(e) => {
              void form.handleSubmit(onSubmit)(e);
            }}
            className="space-y-4"
          >
            <FormField
              control={form.control}
              name="email"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Email</FormLabel>
                  <FormControl>
                    <Input placeholder="name@example.com" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="role"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Vai trò</FormLabel>
                  <FormControl>
                    <select
                      className="flex h-9 w-full items-center justify-between rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm ring-offset-background placeholder:text-muted-foreground focus:outline-none focus:ring-1 focus:ring-ring disabled:cursor-not-allowed disabled:opacity-50"
                      {...field}
                    >
                      <option value="MEMBER">Thành viên</option>
                      <option value="ADMIN">Quản trị viên</option>
                      <option value="VIEWER">Người xem</option>
                    </select>
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <div className="flex justify-end space-x-2 pt-4">
              <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
                Hủy
              </Button>
              <Button type="submit" disabled={addMutation.isPending}>
                {addMutation.isPending ? 'Đang thêm...' : 'Thêm thành viên'}
              </Button>
            </div>
          </form>
        </Form>
      </div>
    </dialog>
  );
}
