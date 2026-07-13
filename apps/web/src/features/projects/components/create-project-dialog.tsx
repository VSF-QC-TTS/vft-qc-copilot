import { useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router';
import { Button } from '@/shared/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/shared/components/ui/dialog';
import { Input } from '@/shared/components/ui/input';
import { Label } from '@/shared/components/ui/label';
import { Textarea } from '@/shared/components/ui/textarea';
import { PermissionGuard } from '@/shared/components/permission-guard';
import { notifyError, notifySuccess } from '@/shared/lib/notifications';
import { useCreateProjectMutation } from '@/features/projects/hooks/use-projects';
import { createProjectRequestSchema } from '@/features/projects/types/project';
import type { WorkspaceResponse } from '@/features/workspaces/types/workspace';

interface CreateProjectDialogProps {
  workspace: WorkspaceResponse;
  trigger: ReactNode;
}

export function CreateProjectDialog({ workspace, trigger }: CreateProjectDialogProps) {
  const navigate = useNavigate();
  const createMutation = useCreateProjectMutation(workspace.id);
  const [open, setOpen] = useState(false);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [inlineError, setInlineError] = useState<string>();

  const handleSubmit = async () => {
    const parsed = createProjectRequestSchema.safeParse({
      name: name.trim(),
      description: description.trim(),
    });
    if (!parsed.success) {
      setInlineError(parsed.error.issues[0]?.message ?? 'Thông tin Project chưa hợp lệ');
      return;
    }

    setInlineError(undefined);
    try {
      const project = await createMutation.mutateAsync(parsed.data);
      notifySuccess('Đã tạo Project');
      setOpen(false);
      setName('');
      setDescription('');
      await navigate(`/workspaces/${workspace.id}/projects/${project.id}`);
    } catch {
      notifyError('Không thể tạo Project');
    }
  };

  return (
    <PermissionGuard capability="EDIT" workspaceRole={workspace.currentUserRole}>
      {(canCreate) => (
        <Dialog open={open} onOpenChange={setOpen}>
          <DialogTrigger asChild>{trigger}</DialogTrigger>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Tạo Project</DialogTitle>
              <DialogDescription>
                Project mới sẽ có Dataset schema mặc định để bắt đầu setup luồng QC.
              </DialogDescription>
            </DialogHeader>

            {!canCreate ? (
              <div className="rounded-md border bg-muted p-4 text-sm text-muted-foreground">
                Bạn không có quyền tạo Project trong Workspace này.
              </div>
            ) : (
              <div className="space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="project-create-name">Tên Project</Label>
                  <Input
                    id="project-create-name"
                    value={name}
                    onChange={(event) => {
                      setName(event.target.value);
                      setInlineError(undefined);
                    }}
                    placeholder="VF Assistant Regression"
                    disabled={createMutation.isPending}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="project-create-description">Mô tả</Label>
                  <Textarea
                    id="project-create-description"
                    value={description}
                    onChange={(event) => {
                      setDescription(event.target.value);
                      setInlineError(undefined);
                    }}
                    placeholder="Phạm vi kiểm thử, chatbot hoặc module cần đánh giá"
                    disabled={createMutation.isPending}
                  />
                </div>
                {inlineError ? <p className="text-sm text-destructive">{inlineError}</p> : null}
              </div>
            )}

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setOpen(false)}>
                Hủy
              </Button>
              <Button
                type="button"
                disabled={!canCreate || createMutation.isPending}
                onClick={() => void handleSubmit()}
              >
                {createMutation.isPending ? 'Đang tạo...' : 'Tạo Project'}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      )}
    </PermissionGuard>
  );
}
