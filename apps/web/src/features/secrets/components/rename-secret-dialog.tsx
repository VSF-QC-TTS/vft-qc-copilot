import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { useEffect } from 'react';
import { notifyError, notifySuccess } from '@/shared/lib/notifications';
import { isApiError } from '@/shared/api/api-error';
import { Button } from '@/shared/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/shared/components/ui/dialog';
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/shared/components/ui/form';
import { Input } from '@/shared/components/ui/input';
import {
  updateSecretRequestSchema,
  type UpdateSecretRequest,
} from '@/features/secrets/types/secret';

export interface RenameSecretDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSubmit: (data: UpdateSecretRequest) => Promise<void>;
  isPending: boolean;
  secretName: string;
}

export function RenameSecretDialog({
  open,
  onOpenChange,
  onSubmit,
  isPending,
  secretName,
}: RenameSecretDialogProps) {
  const form = useForm<UpdateSecretRequest>({
    resolver: zodResolver(updateSecretRequestSchema),
    defaultValues: {
      name: secretName,
    },
  });

  useEffect(() => {
    if (open) {
      form.reset({ name: secretName });
    }
  }, [open, secretName, form]);

  const handleSubmit = async (data: UpdateSecretRequest) => {
    try {
      await onSubmit(data);
      notifySuccess('Đã đổi tên secret');
      onOpenChange(false);
    } catch (err) {
      if (isApiError(err)) {
        if (err.status === 409) {
          form.setError('name', { message: 'Tên secret đã tồn tại' });
        } else {
          notifyError('Không thể đổi tên secret');
        }
      } else {
        notifyError('Lỗi hệ thống khi đổi tên secret');
      }
    }
  };

  return (
    <Dialog
      open={open}
      onOpenChange={(newOpen: boolean) => {
        if (!newOpen) {
          form.reset();
        }
        onOpenChange(newOpen);
      }}
    >
      <DialogContent className="sm:max-w-[425px]">
        <DialogHeader>
          <DialogTitle>Đổi tên secret</DialogTitle>
          <DialogDescription>
            Nhập tên mới cho secret{' '}
            <span className="font-medium text-foreground">{secretName}</span>.
          </DialogDescription>
        </DialogHeader>

        <Form {...form}>
          <form
            onSubmit={(e) => {
              void form.handleSubmit(handleSubmit)(e);
            }}
            className="space-y-4"
          >
            <FormField
              control={form.control}
              name="name"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Tên hiển thị</FormLabel>
                  <FormControl>
                    <Input placeholder="Tên secret" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => onOpenChange(false)}
                disabled={isPending}
              >
                Hủy
              </Button>
              <Button type="submit" disabled={isPending}>
                {isPending ? 'Đang lưu...' : 'Lưu thay đổi'}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
