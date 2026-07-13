import { Link, useOutletContext } from 'react-router';
import { Plus, Scale, Trash2 } from 'lucide-react';
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
import {
  useDeleteEvaluator,
  useProjectEvaluators,
} from '@/features/evaluators/hooks/use-evaluators';
import type { WorkspaceContextType } from '@/app/layouts/workspace-context-layout';
import type { ProjectResponse } from '@/features/projects/types/project';
import type { EvaluatorResponse } from '@/features/evaluators/types/evaluator';

interface ProjectContextType extends WorkspaceContextType {
  project: ProjectResponse;
}

export function EvaluatorListPage() {
  const { workspace, project } = useOutletContext<ProjectContextType>();
  const evaluatorsQuery = useProjectEvaluators(project.id);
  const deleteMutation = useDeleteEvaluator(project.id);

  const handleDelete = async (evaluator: EvaluatorResponse) => {
    try {
      await deleteMutation.mutateAsync(evaluator.id);
      notifySuccess('Đã xóa Evaluator');
    } catch {
      notifyError('Không thể xóa Evaluator');
    }
  };

  return (
    <div className="mx-auto w-full max-w-6xl px-4 py-8 sm:px-6 lg:px-8">
      <div className="mb-6 flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
        <div>
          <p className="text-sm text-muted-foreground">{project.name}</p>
          <h1 className="text-2xl font-semibold tracking-tight">Evaluators</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Quản lý Rule-based Evaluator và immutable versions.
          </p>
        </div>
        <PermissionGuard capability="EDIT" workspaceRole={workspace.currentUserRole}>
          {(isAllowed) => (
            <Button asChild disabled={!isAllowed}>
              <Link to={`/workspaces/${workspace.id}/projects/${project.id}/evaluators/new`}>
                <Plus className="mr-2 h-4 w-4" />
                Tạo Evaluator
              </Link>
            </Button>
          )}
        </PermissionGuard>
      </div>

      {evaluatorsQuery.isLoading ? (
        <div className="space-y-3">
          <Skeleton className="h-12 w-full" />
          <Skeleton className="h-12 w-full" />
          <Skeleton className="h-12 w-full" />
        </div>
      ) : evaluatorsQuery.isError ? (
        <div className="rounded-lg border border-destructive/30 bg-destructive/10 p-6 text-sm text-destructive">
          Không thể tải danh sách Evaluator.
        </div>
      ) : (evaluatorsQuery.data ?? []).length === 0 ? (
        <div className="rounded-lg border border-dashed bg-surface p-8 text-center">
          <Scale className="mx-auto h-8 w-8 text-muted-foreground" aria-hidden="true" />
          <h2 className="mt-4 text-lg font-semibold">Chưa có Evaluator</h2>
          <p className="mt-2 text-sm text-muted-foreground">
            Tạo Rule-based Evaluator để xác định điều kiện đạt và không đạt.
          </p>
        </div>
      ) : (
        <div className="overflow-hidden rounded-lg border bg-surface">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Tên</TableHead>
                <TableHead>Type</TableHead>
                <TableHead>Latest version</TableHead>
                <TableHead>Cập nhật</TableHead>
                <TableHead className="w-[170px]"></TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(evaluatorsQuery.data ?? []).map((evaluator) => (
                <TableRow key={evaluator.id}>
                  <TableCell>
                    <Link
                      to={`/workspaces/${workspace.id}/projects/${project.id}/evaluators/${evaluator.id}`}
                      className="font-medium hover:underline"
                    >
                      {evaluator.name}
                    </Link>
                  </TableCell>
                  <TableCell className="text-sm text-muted-foreground">
                    {evaluatorTypeLabel(evaluator.type)}
                  </TableCell>
                  <TableCell className="text-sm text-muted-foreground">
                    {evaluator.latestVersion
                      ? `Version ${evaluator.latestVersion.version}`
                      : 'Chưa có version'}
                  </TableCell>
                  <TableCell className="text-sm text-muted-foreground">
                    {new Date(evaluator.updatedAt).toLocaleString('vi-VN')}
                  </TableCell>
                  <TableCell>
                    <div className="flex justify-end gap-2">
                      <Button asChild variant="outline" size="sm">
                        <Link
                          to={`/workspaces/${workspace.id}/projects/${project.id}/evaluators/${evaluator.id}`}
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
                            onClick={() => void handleDelete(evaluator)}
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

function evaluatorTypeLabel(type: string) {
  if (type === 'RULE_BASED') return 'Rule-based Evaluator';
  if (type === 'LLM_JUDGE') return 'LLM Judge';
  return 'Mixed Evaluator';
}
