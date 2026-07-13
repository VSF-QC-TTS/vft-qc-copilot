import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
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
  createSecretRequestSchema,
  type CreateSecretRequest,
} from '@/features/secrets/types/secret';

export interface CreateSecretDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSubmit: (data: CreateSecretRequest) => Promise<void>;
  isPending: boolean;
  scopeLabel: string;
}

export function CreateSecretDialog({
  open,
  onOpenChange,
  onSubmit,
  isPending,
  scopeLabel,
}: CreateSecretDialogProps) {
  const form = useForm<CreateSecretRequest>({
    resolver: zodResolver(createSecretRequestSchema),
    defaultValues: {
      name: '',
      value: '',
    },
  });

  const handleSubmit = async (data: CreateSecretRequest) => {
    try {
      await onSubmit(data);
      notifySuccess('Đã thêm secret');
      form.reset();
      onOpenChange(false);
    } catch (err) {
      if (isApiError(err)) {
        if (err.status === 409) {
          form.setError('name', { message: 'Tên secret đã tồn tại' });
        } else {
          notifyError('Không thể thêm secret');
        }
      } else {
        notifyError('Lỗi hệ thống khi thêm secret');
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
          <DialogTitle>Thêm secret</DialogTitle>
          <DialogDescription>
            Secret mới sẽ được lưu trữ an toàn trong phạm vi {scopeLabel}.
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
                    <Input placeholder="Ví dụ: Gemini API Key" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="value"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Giá trị Secret</FormLabel>
                  <FormControl>
                    <Input type="password" placeholder="Nhập giá trị secret" {...field} />
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
                {isPending ? 'Đang thêm...' : 'Thêm secret'}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
