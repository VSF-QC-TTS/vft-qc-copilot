import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { isApiError } from '@/shared/api/api-error';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/shared/components/ui/dialog';
import { Button } from '@/shared/components/ui/button';
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
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/shared/components/ui/select';
import {
  updateProviderRequestSchema,
  type UpdateProviderRequest,
  type ProviderResponse,
} from '@/features/providers/types/provider';
import type { SecretResponse } from '@/features/secrets/types/secret';

export interface EditProviderDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSubmit: (data: UpdateProviderRequest) => Promise<void>;
  isPending: boolean;
  provider: ProviderResponse | null;
  secrets?: SecretResponse[] | undefined;
}

export function EditProviderDialog({
  open,
  onOpenChange,
  onSubmit,
  isPending,
  provider,
  secrets,
}: EditProviderDialogProps) {
  const form = useForm<UpdateProviderRequest>({
    resolver: zodResolver(updateProviderRequestSchema),
    defaultValues: {
      name: '',
      secretId: '',
      baseUrl: '',
      defaultModel: '',
    },
  });

  useEffect(() => {
    if (open && provider) {
      form.reset({
        name: provider.name,
        secretId: provider.secret.id,
        baseUrl: provider.baseUrl || '',
        defaultModel: provider.defaultModel,
      });
    }
  }, [open, provider, form]);

  const handleOpenChange = (newOpen: boolean) => {
    if (!newOpen) {
      form.reset();
    }
    onOpenChange(newOpen);
  };

  const handleSubmit = async (data: UpdateProviderRequest) => {
    try {
      await onSubmit(data);
      form.reset();
    } catch (error) {
      if (isApiError(error) && error.status === 409) {
        form.setError('name', {
          type: 'manual',
          message: 'Tên provider đã tồn tại',
        });
      }
    }
  };

  if (!provider) return null;

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-[500px]">
        <DialogHeader>
          <DialogTitle>Chỉnh sửa provider</DialogTitle>
          <DialogDescription>
            Cập nhật cấu hình cho provider{' '}
            <span className="font-medium text-foreground">{provider.name}</span>.
          </DialogDescription>
        </DialogHeader>

        <Form {...form}>
          <form onSubmit={(e) => void form.handleSubmit(handleSubmit)(e)} className="space-y-4">
            <FormField
              control={form.control}
              name="name"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Tên hiển thị</FormLabel>
                  <FormControl>
                    <Input placeholder="Ví dụ: Gemini API" {...field} disabled={isPending} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="secretId"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Secret</FormLabel>
                  <Select
                    onValueChange={field.onChange}
                    {...(field.value ? { value: field.value } : {})}
                    disabled={
                      isPending || (secrets || []).filter((s) => s.status === 'ACTIVE').length === 0
                    }
                  >
                    <FormControl>
                      <SelectTrigger>
                        <SelectValue placeholder="Chọn secret khả dụng" />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      {(secrets || [])
                        .filter((s) => s.status === 'ACTIVE')
                        .map((secret) => (
                          <SelectItem key={secret.id} value={secret.id}>
                            {secret.name}
                          </SelectItem>
                        ))}
                    </SelectContent>
                  </Select>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="baseUrl"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Base URL (Tùy chọn)</FormLabel>
                  <FormControl>
                    <Input
                      placeholder="https://api.openai.com/v1"
                      {...field}
                      disabled={isPending}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="defaultModel"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Default Model</FormLabel>
                  <FormControl>
                    <Input
                      placeholder="Ví dụ: gpt-4o, gemini-1.5-pro"
                      {...field}
                      disabled={isPending}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => handleOpenChange(false)}
                disabled={isPending}
              >
                Hủy
              </Button>
              <Button
                type="submit"
                disabled={
                  isPending || (secrets || []).filter((s) => s.status === 'ACTIVE').length === 0
                }
              >
                Lưu thay đổi
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
