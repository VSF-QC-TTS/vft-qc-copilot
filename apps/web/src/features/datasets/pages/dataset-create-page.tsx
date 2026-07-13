import { useState } from 'react';
import { Link, useNavigate, useOutletContext } from 'react-router';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import { Label } from '@/shared/components/ui/label';
import { Textarea } from '@/shared/components/ui/textarea';
import { PermissionGuard } from '@/shared/components/permission-guard';
import { notifyError, notifySuccess } from '@/shared/lib/notifications';
import { useCreateDataset } from '@/features/datasets/hooks/use-datasets';
import { createDatasetRequestSchema } from '@/features/datasets/types/dataset';
import type { WorkspaceContextType } from '@/app/layouts/workspace-context-layout';
import type { ProjectResponse } from '@/features/projects/types/project';

interface ProjectContextType extends WorkspaceContextType {
  project: ProjectResponse;
}

export function DatasetCreatePage() {
  const { workspace, project } = useOutletContext<ProjectContextType>();
  const navigate = useNavigate();
  const createMutation = useCreateDataset(project.id);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');

  const handleSubmit = async () => {
    const parsed = createDatasetRequestSchema.safeParse({ name, description });
    if (!parsed.success) {
      notifyError(parsed.error.issues[0]?.message ?? 'Dữ liệu dataset chưa hợp lệ');
      return;
    }

    try {
      const created = await createMutation.mutateAsync(parsed.data);
      notifySuccess('Đã tạo dataset');
      await navigate(`/workspaces/${workspace.id}/projects/${project.id}/datasets/${created.id}`);
    } catch {
      notifyError('Không thể tạo dataset');
    }
  };

  return (
    <div className="mx-auto w-full max-w-3xl px-4 py-8 sm:px-6 lg:px-8">
      <div className="mb-6">
        <p className="text-sm text-muted-foreground">{project.name}</p>
        <h1 className="text-2xl font-semibold tracking-tight">Tạo dataset</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Dataset mới sẽ bắt đầu bằng một bản nháp để bạn nhập dòng kiểm thử.
        </p>
      </div>

      <PermissionGuard
        capability="EDIT"
        workspaceRole={workspace.currentUserRole}
        fallback={
          <div className="rounded-lg border bg-muted p-6 text-sm text-muted-foreground">
            Bạn không có quyền tạo dataset trong workspace này.
          </div>
        }
      >
        <div className="rounded-lg border bg-surface p-6">
          <div className="space-y-5">
            <div className="space-y-2">
              <Label htmlFor="dataset-name">Tên dataset</Label>
              <Input
                id="dataset-name"
                value={name}
                onChange={(event) => setName(event.target.value)}
                placeholder="Regression tiếng Việt"
                disabled={createMutation.isPending}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="dataset-description">Mô tả</Label>
              <Textarea
                id="dataset-description"
                value={description}
                onChange={(event) => setDescription(event.target.value)}
                placeholder="Mục đích, phạm vi hoặc nguồn dữ liệu"
                disabled={createMutation.isPending}
              />
            </div>
            <div className="flex justify-end gap-2">
              <Button asChild variant="outline">
                <Link to={`/workspaces/${workspace.id}/projects/${project.id}/datasets`}>Hủy</Link>
              </Button>
              <Button
                type="button"
                onClick={() => void handleSubmit()}
                disabled={createMutation.isPending}
              >
                Tạo dataset
              </Button>
            </div>
          </div>
        </div>
      </PermissionGuard>
    </div>
  );
}
