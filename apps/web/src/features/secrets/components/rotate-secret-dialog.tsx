import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { notifyError, notifySuccess } from '@/shared/lib/notifications';
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
  rotateSecretRequestSchema,
  type RotateSecretRequest,
} from '@/features/secrets/types/secret';

export interface RotateSecretDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSubmit: (data: RotateSecretRequest) => Promise<void>;
  isPending: boolean;
  secretName: string;
}

export function RotateSecretDialog({
  open,
  onOpenChange,
  onSubmit,
  isPending,
  secretName,
}: RotateSecretDialogProps) {
  const form = useForm<RotateSecretRequest>({
    resolver: zodResolver(rotateSecretRequestSchema),
    defaultValues: {
      value: '',
    },
  });

  const handleSubmit = async (data: RotateSecretRequest) => {
    try {
      await onSubmit(data);
      notifySuccess('Đã cập nhật giá trị secret');
      form.reset();
      onOpenChange(false);
    } catch {
      notifyError('Không thể cập nhật giá trị secret');
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
          <DialogTitle>Xoay vòng giá trị</DialogTitle>
          <DialogDescription>
            Nhập giá trị mới cho secret{' '}
            <span className="font-medium text-foreground">{secretName}</span>. Hành động này sẽ thay
            thế giá trị cũ.
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
              name="value"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Giá trị mới</FormLabel>
                  <FormControl>
                    <Input type="password" placeholder="Nhập giá trị mới" {...field} />
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
