import { useState } from 'react';
import { Link, useNavigate, useOutletContext } from 'react-router';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import { Label } from '@/shared/components/ui/label';
import { Textarea } from '@/shared/components/ui/textarea';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from '@/shared/components/ui/alert-dialog';
import { PermissionGuard } from '@/shared/components/permission-guard';
import { notifyError, notifySuccess } from '@/shared/lib/notifications';
import {
  useArchiveProjectMutation,
  useDeleteProjectMutation,
  useUpdateProjectMutation,
} from '@/features/projects/hooks/use-projects';
import { updateProjectRequestSchema } from '@/features/projects/types/project';
import type { WorkspaceContextType } from '@/app/layouts/workspace-context-layout';
import type { ProjectResponse } from '@/features/projects/types/project';

interface ProjectContextType extends WorkspaceContextType {
  project: ProjectResponse;
}

export function ProjectSettingsPage() {
  const { workspace, project } = useOutletContext<ProjectContextType>();
  const navigate = useNavigate();
  const updateMutation = useUpdateProjectMutation(project.id, workspace.id);
  const archiveMutation = useArchiveProjectMutation(project.id, workspace.id);
  const deleteMutation = useDeleteProjectMutation(project.id, workspace.id);
  const [name, setName] = useState(project.name);
  const [description, setDescription] = useState(project.description ?? '');
  const [inlineError, setInlineError] = useState<string>();
  const isArchived = Boolean(project.archivedAt);

  const handleSave = async () => {
    const parsed = updateProjectRequestSchema.safeParse({
      name: name.trim(),
      description: description.trim(),
    });
    if (!parsed.success) {
      setInlineError(parsed.error.issues[0]?.message ?? 'Thông tin Project chưa hợp lệ');
      return;
    }

    setInlineError(undefined);
    try {
      await updateMutation.mutateAsync(parsed.data);
      notifySuccess('Đã cập nhật Project');
    } catch {
      notifyError('Không thể cập nhật Project');
    }
  };

  const handleArchive = async () => {
    try {
      await archiveMutation.mutateAsync();
      notifySuccess('Đã lưu trữ Project');
    } catch {
      notifyError('Không thể lưu trữ Project');
    }
  };

  const handleDelete = async () => {
    try {
      await deleteMutation.mutateAsync();
      notifySuccess('Đã xóa Project');
      await navigate(`/workspaces/${workspace.id}/projects`);
    } catch {
      notifyError('Không thể xóa Project');
    }
  };

  return (
    <div className="mx-auto w-full max-w-4xl px-4 py-8 sm:px-6 lg:px-8">
      <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-sm text-muted-foreground">{workspace.name}</p>
          <h1 className="text-2xl font-semibold tracking-tight">Cài đặt Project</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Cập nhật thông tin và trạng thái vòng đời của Project.
          </p>
        </div>
        <Button asChild variant="outline">
          <Link to={`/workspaces/${workspace.id}/projects/${project.id}`}>Quay lại Project</Link>
        </Button>
      </div>

      <div className="space-y-6">
        <section className="rounded-lg border bg-surface p-6">
          <div className="mb-5">
            <h2 className="text-lg font-semibold">Thông tin chung</h2>
            <p className="mt-1 text-sm text-muted-foreground">
              Tên và mô tả giúp QC nhận diện phạm vi đánh giá.
            </p>
          </div>
          <PermissionGuard capability="EDIT" workspaceRole={workspace.currentUserRole}>
            {(canEdit) => (
              <div className="space-y-5">
                <div className="space-y-2">
                  <Label htmlFor="project-settings-name">Tên Project</Label>
                  <Input
                    id="project-settings-name"
                    value={name}
                    onChange={(event) => {
                      setName(event.target.value);
                      setInlineError(undefined);
                    }}
                    disabled={!canEdit || updateMutation.isPending}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="project-settings-description">Mô tả</Label>
                  <Textarea
                    id="project-settings-description"
                    value={description}
                    onChange={(event) => {
                      setDescription(event.target.value);
                      setInlineError(undefined);
                    }}
                    disabled={!canEdit || updateMutation.isPending}
                  />
                </div>
                {inlineError ? <p className="text-sm text-destructive">{inlineError}</p> : null}
                <div className="flex justify-end">
                  <Button
                    type="button"
                    disabled={!canEdit || updateMutation.isPending}
                    onClick={() => void handleSave()}
                  >
                    {updateMutation.isPending ? 'Đang lưu...' : 'Lưu thay đổi'}
                  </Button>
                </div>
                {!canEdit ? (
                  <p className="rounded-md border bg-muted p-3 text-sm text-muted-foreground">
                    Bạn chỉ có quyền xem Project này.
                  </p>
                ) : null}
              </div>
            )}
          </PermissionGuard>
        </section>

        <section className="rounded-lg border bg-surface p-6">
          <div className="mb-5">
            <h2 className="text-lg font-semibold">Vòng đời Project</h2>
            <p className="mt-1 text-sm text-muted-foreground">
              Lưu trữ Project khi không còn chạy TestRun mới. Xóa Project chỉ dành cho người có
              quyền quản lý.
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <PermissionGuard capability="EDIT" workspaceRole={workspace.currentUserRole}>
              {(canArchive) => (
                <Button
                  type="button"
                  variant="outline"
                  disabled={!canArchive || isArchived || archiveMutation.isPending}
                  onClick={() => void handleArchive()}
                >
                  {isArchived ? 'Project đã lưu trữ' : 'Lưu trữ Project'}
                </Button>
              )}
            </PermissionGuard>
            <PermissionGuard capability="MANAGE" workspaceRole={workspace.currentUserRole}>
              {(canDelete) => (
                <AlertDialog>
                  <AlertDialogTrigger asChild>
                    <Button type="button" variant="destructive" disabled={!canDelete}>
                      Xóa Project
                    </Button>
                  </AlertDialogTrigger>
                  <AlertDialogContent>
                    <AlertDialogHeader>
                      <AlertDialogTitle>Xóa Project này?</AlertDialogTitle>
                      <AlertDialogDescription>
                        Project sẽ bị soft-delete và không còn xuất hiện trong danh sách. Hành động
                        này không xóa dữ liệu bằng thao tác thủ công ở frontend.
                      </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                      <AlertDialogCancel>Hủy</AlertDialogCancel>
                      <AlertDialogAction
                        disabled={deleteMutation.isPending}
                        onClick={() => void handleDelete()}
                      >
                        Xóa Project
                      </AlertDialogAction>
                    </AlertDialogFooter>
                  </AlertDialogContent>
                </AlertDialog>
              )}
            </PermissionGuard>
          </div>
        </section>
      </div>
    </div>
  );
}
