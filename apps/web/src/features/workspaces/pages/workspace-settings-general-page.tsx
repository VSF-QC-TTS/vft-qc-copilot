import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useOutletContext } from 'react-router';
import { WorkspaceContextType } from '@/app/layouts/workspace-context-layout';
import { useUpdateWorkspaceMutation } from '@/features/workspaces/hooks/use-workspaces';
import {
  updateWorkspaceRequestSchema,
  UpdateWorkspaceRequest,
} from '@/features/workspaces/types/workspace';
import { PermissionGuard } from '@/shared/components/permission-guard';
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
export function WorkspaceSettingsGeneralPage() {
  const { workspace } = useOutletContext<WorkspaceContextType>();
  const updateWorkspaceMutation = useUpdateWorkspaceMutation(workspace.id);

  const form = useForm<UpdateWorkspaceRequest>({
    resolver: zodResolver(updateWorkspaceRequestSchema),
    defaultValues: {
      name: workspace.name,
    },
  });

  const onSubmit = (data: UpdateWorkspaceRequest) => {
    updateWorkspaceMutation.mutate(data, {
      onSuccess: () => {
        notifySuccess('Đã cập nhật cài đặt workspace');
      },
      onError: (error) => {
        if (isApiError(error) && error.status === 403) {
          notifyError('Bạn không có quyền thực hiện hành động này');
        } else {
          notifyError('Không thể cập nhật workspace');
        }
      },
    });
  };

  return (
    <div className="space-y-6">
      <div>
        <h3 className="text-lg font-medium">Thông tin chung</h3>
        <p className="text-sm text-muted-foreground">Cập nhật thông tin cơ bản của workspace.</p>
      </div>
      <div className="rounded-md border p-6">
        <Form {...form}>
          <form
            onSubmit={(e) => {
              void form.handleSubmit(onSubmit)(e);
            }}
            className="space-y-4 max-w-sm"
          >
            <PermissionGuard capability="MANAGE" workspaceRole={workspace.currentUserRole}>
              {(isAllowed) => (
                <>
                  <FormField
                    control={form.control}
                    name="name"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Tên Workspace</FormLabel>
                        <FormControl>
                          <Input placeholder="Tên Workspace" disabled={!isAllowed} {...field} />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  {isAllowed && (
                    <Button type="submit" disabled={updateWorkspaceMutation.isPending}>
                      {updateWorkspaceMutation.isPending ? 'Đang lưu...' : 'Lưu thay đổi'}
                    </Button>
                  )}
                </>
              )}
            </PermissionGuard>
          </form>
        </Form>
      </div>
    </div>
  );
}
