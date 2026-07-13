import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Play } from 'lucide-react';
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
import { Textarea } from '@/shared/components/ui/textarea';
import { notifyError } from '@/shared/lib/notifications';
import {
  testProviderRequestSchema,
  type TestProviderRequest,
  type ProviderResponse,
  type ProviderTestResponse,
} from '@/features/providers/types/provider';

export interface TestProviderDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSubmit: (data: TestProviderRequest) => Promise<ProviderTestResponse>;
  isPending: boolean;
  provider: ProviderResponse | null;
}

export function TestProviderDialog({
  open,
  onOpenChange,
  onSubmit,
  isPending,
  provider,
}: TestProviderDialogProps) {
  const [testResult, setTestResult] = useState<ProviderTestResponse | null>(null);

  const form = useForm<TestProviderRequest>({
    resolver: zodResolver(testProviderRequestSchema),
    defaultValues: {
      model: '',
      prompt: 'Hello, are you there?',
    },
  });

  const handleOpenChange = (newOpen: boolean) => {
    if (!newOpen) {
      form.reset();
      setTestResult(null);
    }
    onOpenChange(newOpen);
  };

  const handleSubmit = async (data: TestProviderRequest) => {
    try {
      setTestResult(null);
      const result = await onSubmit(data);
      setTestResult(result);
    } catch {
      notifyError('Không thể thực hiện bài kiểm tra');
    }
  };

  if (!provider) return null;

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-[600px] max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Kiểm tra kết nối</DialogTitle>
          <DialogDescription>
            Gửi yêu cầu thử nghiệm tới provider{' '}
            <span className="font-medium text-foreground">{provider.name}</span>.
          </DialogDescription>
        </DialogHeader>

        <Form {...form}>
          <form onSubmit={(e) => void form.handleSubmit(handleSubmit)(e)} className="space-y-4">
            <FormField
              control={form.control}
              name="model"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Model (Bỏ trống để dùng mặc định)</FormLabel>
                  <FormControl>
                    <Input placeholder={provider.defaultModel} {...field} disabled={isPending} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="prompt"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Prompt thử nghiệm</FormLabel>
                  <FormControl>
                    <Textarea
                      placeholder="Nhập prompt thử nghiệm..."
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
                Đóng
              </Button>
              <Button type="submit" disabled={isPending}>
                <Play className="mr-2 h-4 w-4" />
                Chạy thử nghiệm
              </Button>
            </DialogFooter>
          </form>
        </Form>

        {testResult && (
          <div className="mt-6 space-y-4 border-t pt-4">
            <h3 className="text-sm font-semibold">Kết quả thử nghiệm</h3>

            <div className="rounded-md border p-4 text-sm space-y-2 bg-muted/50">
              <div className="flex justify-between items-center pb-2 border-b">
                <span className="text-muted-foreground">Trạng thái:</span>
                <span
                  className={`font-semibold ${testResult.status === 'SUCCESS' ? 'text-primary' : 'text-destructive'}`}
                >
                  {testResult.status === 'SUCCESS' ? 'THÀNH CÔNG' : 'THẤT BẠI'}
                </span>
              </div>

              {testResult.latencyMs !== null && (
                <div className="flex justify-between items-center">
                  <span className="text-muted-foreground">Độ trễ:</span>
                  <span>{testResult.latencyMs} ms</span>
                </div>
              )}

              {testResult.model && (
                <div className="flex justify-between items-center">
                  <span className="text-muted-foreground">Model thực tế:</span>
                  <span className="font-mono text-xs">{testResult.model}</span>
                </div>
              )}

              {testResult.errorMessage && (
                <div className="pt-2">
                  <span className="text-muted-foreground block mb-1">Lỗi:</span>
                  <p className="text-destructive font-mono text-xs bg-destructive/10 p-2 rounded">
                    {testResult.errorCode ? `[${testResult.errorCode}] ` : ''}
                    {testResult.errorMessage}
                  </p>
                </div>
              )}

              {testResult.responsePreview && (
                <div className="pt-2">
                  <span className="text-muted-foreground block mb-1">Nội dung phản hồi:</span>
                  <p className="font-mono text-xs bg-background p-2 rounded border break-words">
                    {testResult.responsePreview}
                  </p>
                </div>
              )}
            </div>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
