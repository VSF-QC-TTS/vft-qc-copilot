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
  FormDescription,
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
  createProviderRequestSchema,
  type CreateProviderRequest,
} from '@/features/providers/types/provider';
import type { SecretResponse } from '@/features/secrets/types/secret';

export interface CreateProviderDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSubmit: (data: CreateProviderRequest) => Promise<void>;
  isPending: boolean;
  secrets?: SecretResponse[] | undefined;
}

export function CreateProviderDialog({
  open,
  onOpenChange,
  onSubmit,
  isPending,
  secrets,
}: CreateProviderDialogProps) {
  const form = useForm<CreateProviderRequest>({
    resolver: zodResolver(createProviderRequestSchema),
    defaultValues: {
      name: '',
      providerType: 'OPENAI_COMPATIBLE',
      secretId: '',
      baseUrl: '',
      defaultModel: '',
    },
  });

  const handleOpenChange = (newOpen: boolean) => {
    if (!newOpen) {
      form.reset();
    }
    onOpenChange(newOpen);
  };

  const handleSubmit = async (data: CreateProviderRequest) => {
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

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-[500px]">
        <DialogHeader>
          <DialogTitle>Thêm provider</DialogTitle>
          <DialogDescription>
            Tạo cấu hình provider LLM mới. Secret sử dụng phải đang ở trạng thái hoạt động.
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
              name="providerType"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Loại Provider</FormLabel>
                  <Select
                    onValueChange={field.onChange}
                    defaultValue={field.value}
                    disabled={isPending}
                  >
                    <FormControl>
                      <SelectTrigger>
                        <SelectValue placeholder="Chọn loại provider" />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      <SelectItem value="OPENAI_COMPATIBLE">OpenAI Compatible</SelectItem>
                      <SelectItem value="GOOGLE_GENAI">Google GenAI</SelectItem>
                      <SelectItem value="CUSTOM_HTTP">Custom HTTP</SelectItem>
                    </SelectContent>
                  </Select>
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
                  {(secrets || []).filter((s) => s.status === 'ACTIVE').length === 0 && (
                    <FormDescription className="text-destructive">
                      Không có secret nào đang hoạt động. Vui lòng tạo secret trước.
                    </FormDescription>
                  )}
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
                Thêm provider
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
