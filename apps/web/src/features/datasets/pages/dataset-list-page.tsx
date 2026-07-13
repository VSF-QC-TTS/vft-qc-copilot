import { Link, useOutletContext } from 'react-router';
import { Database, Plus, Trash2 } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Skeleton } from '@/shared/components/ui/skeleton';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/components/ui/table';
import { PermissionGuard } from '@/shared/components/permission-guard';
import { notifyError, notifySuccess } from '@/shared/lib/notifications';
import { useDeleteDataset, useProjectDatasets } from '@/features/datasets/hooks/use-datasets';
import type { WorkspaceContextType } from '@/app/layouts/workspace-context-layout';
import type { ProjectResponse } from '@/features/projects/types/project';
import type { DatasetResponse } from '@/features/datasets/types/dataset';

interface ProjectContextType extends WorkspaceContextType {
  project: ProjectResponse;
}

export function DatasetListPage() {
  const { workspace, project } = useOutletContext<ProjectContextType>();
  const datasetsQuery = useProjectDatasets(project.id);
  const deleteMutation = useDeleteDataset(project.id);

  const handleDelete = async (dataset: DatasetResponse) => {
    try {
      await deleteMutation.mutateAsync(dataset.id);
      notifySuccess('Đã xóa dataset');
    } catch {
      notifyError('Không thể xóa dataset');
    }
  };

  return (
    <div className="mx-auto w-full max-w-6xl px-4 py-8 sm:px-6 lg:px-8">
      <div className="mb-6 flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
        <div>
          <p className="text-sm text-muted-foreground">{project.name}</p>
          <h1 className="text-2xl font-semibold tracking-tight">Datasets</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Quản lý dữ liệu kiểm thử theo version. Bản đã publish chỉ được xem.
          </p>
        </div>
        <PermissionGuard capability="EDIT" workspaceRole={workspace.currentUserRole}>
          {(isAllowed) => (
            <div className="flex flex-wrap gap-2">
              <Button asChild variant="outline" disabled={!isAllowed}>
                <Link to={`/workspaces/${workspace.id}/projects/${project.id}/datasets/import`}>
                  Import Excel
                </Link>
              </Button>
              <Button asChild disabled={!isAllowed}>
                <Link to={`/workspaces/${workspace.id}/projects/${project.id}/datasets/new`}>
                  <Plus className="mr-2 h-4 w-4" />
                  Tạo dataset
                </Link>
              </Button>
            </div>
          )}
        </PermissionGuard>
      </div>

      {datasetsQuery.isLoading ? (
        <div className="space-y-3">
          <Skeleton className="h-12 w-full" />
          <Skeleton className="h-12 w-full" />
          <Skeleton className="h-12 w-full" />
        </div>
      ) : datasetsQuery.isError ? (
        <div className="rounded-lg border border-destructive/30 bg-destructive/10 p-6 text-sm text-destructive">
          Không thể tải danh sách dataset.
        </div>
      ) : (datasetsQuery.data ?? []).length === 0 ? (
        <div className="rounded-lg border border-dashed bg-surface p-8 text-center">
          <Database className="mx-auto h-8 w-8 text-muted-foreground" aria-hidden="true" />
          <h2 className="mt-4 text-lg font-semibold">Chưa có dataset</h2>
          <p className="mt-2 text-sm text-muted-foreground">
            Tạo dataset đầu tiên để nhập câu hỏi và expected response cho project.
          </p>
        </div>
      ) : (
        <div className="overflow-hidden rounded-lg border bg-surface">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Tên</TableHead>
                <TableHead>Current version</TableHead>
                <TableHead>Cập nhật</TableHead>
                <TableHead className="w-[170px]"></TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(datasetsQuery.data ?? []).map((dataset) => (
                <TableRow key={dataset.id}>
                  <TableCell>
                    <Link
                      to={`/workspaces/${workspace.id}/projects/${project.id}/datasets/${dataset.id}`}
                      className="font-medium hover:underline"
                    >
                      {dataset.name}
                    </Link>
                    {dataset.description && (
                      <p className="mt-1 line-clamp-2 text-sm text-muted-foreground">
                        {dataset.description}
                      </p>
                    )}
                  </TableCell>
                  <TableCell className="font-mono text-xs text-muted-foreground">
                    {dataset.currentVersionId ?? 'Chưa publish'}
                  </TableCell>
                  <TableCell className="text-sm text-muted-foreground">
                    {new Date(dataset.updatedAt).toLocaleString('vi-VN')}
                  </TableCell>
                  <TableCell>
                    <div className="flex justify-end gap-2">
                      <Button asChild variant="outline" size="sm">
                        <Link
                          to={`/workspaces/${workspace.id}/projects/${project.id}/datasets/${dataset.id}`}
                        >
                          Mở
                        </Link>
                      </Button>
                      <PermissionGuard capability="EDIT" workspaceRole={workspace.currentUserRole}>
                        {(canEdit) => (
                          <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            disabled={!canEdit || deleteMutation.isPending}
                            onClick={() => void handleDelete(dataset)}
                          >
                            <Trash2 className="h-4 w-4" />
                            Xóa
                          </Button>
                        )}
                      </PermissionGuard>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
    </div>
  );
}
