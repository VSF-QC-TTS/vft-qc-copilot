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
import { notifyError, notifySuccess } from '@/shared/lib/notifications';
import { useCreateWorkspaceMutation } from '@/features/workspaces/hooks/use-workspaces';
import { createWorkspaceRequestSchema } from '@/features/workspaces/types/workspace';

interface CreateWorkspaceDialogProps {
  trigger: ReactNode;
}

export function CreateWorkspaceDialog({ trigger }: CreateWorkspaceDialogProps) {
  const navigate = useNavigate();
  const createMutation = useCreateWorkspaceMutation();
  const [open, setOpen] = useState(false);
  const [name, setName] = useState('');
  const [inlineError, setInlineError] = useState<string>();

  const handleSubmit = async () => {
    const parsed = createWorkspaceRequestSchema.safeParse({ name: name.trim() });
    if (!parsed.success) {
      setInlineError(parsed.error.issues[0]?.message ?? 'Thông tin Workspace chưa hợp lệ');
      return;
    }

    setInlineError(undefined);
    try {
      const workspace = await createMutation.mutateAsync(parsed.data);
      notifySuccess('Đã tạo Workspace');
      setOpen(false);
      setName('');
      await navigate(`/workspaces/${workspace.id}`);
    } catch {
      notifyError('Không thể tạo Workspace');
    }
  };

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Tạo Workspace</DialogTitle>
          <DialogDescription>
            Workspace mới sẽ dùng scope TEAM và bạn sẽ là OWNER của Workspace này.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-2">
          <Label htmlFor="workspace-create-name">Tên Workspace</Label>
          <Input
            id="workspace-create-name"
            value={name}
            onChange={(event) => {
              setName(event.target.value);
              setInlineError(undefined);
            }}
            placeholder="VF QA Platform"
            disabled={createMutation.isPending}
          />
          {inlineError ? <p className="text-sm text-destructive">{inlineError}</p> : null}
        </div>

        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => setOpen(false)}>
            Hủy
          </Button>
          <Button
            type="button"
            disabled={createMutation.isPending}
            onClick={() => void handleSubmit()}
          >
            {createMutation.isPending ? 'Đang tạo...' : 'Tạo Workspace'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
