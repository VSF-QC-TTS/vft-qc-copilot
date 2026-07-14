import { useWorkspaces } from '@/features/workspaces/hooks/use-workspaces';
import { Plus, Building2, User } from 'lucide-react';
import { Link } from 'react-router';
import { Skeleton } from '@/shared/components/ui/skeleton';
import { CreateWorkspaceDialog } from '@/features/workspaces/components/create-workspace-dialog';

const workspaceSkeletonKeys = [
  'workspace-skeleton-1',
  'workspace-skeleton-2',
  'workspace-skeleton-3',
  'workspace-skeleton-4',
  'workspace-skeleton-5',
  'workspace-skeleton-6',
];

export function WorkspaceListPage() {
  const { data: workspaceCollection, isLoading, error } = useWorkspaces();

  const workspaces = workspaceCollection?.items || [];

  return (
    <div className="mx-auto w-full max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight text-foreground">Workspace</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Chọn Workspace để mở Project, thiết lập Secret và Provider.
          </p>
        </div>
        <CreateWorkspaceDialog
          trigger={
            <button
              type="button"
              className="inline-flex h-9 items-center gap-2 rounded-md bg-primary px-4 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring"
            >
              <Plus className="h-4 w-4" aria-hidden="true" />
              Tạo Workspace
            </button>
          }
        />
      </div>

      {isLoading ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {workspaceSkeletonKeys.map((key) => (
            <Skeleton key={key} className="h-32 w-full rounded-lg" />
          ))}
        </div>
      ) : error ? (
        <div className="flex min-h-[400px] w-full flex-col items-center justify-center text-center">
          <p className="text-danger">Đã có lỗi xảy ra khi tải Workspace.</p>
        </div>
      ) : workspaces.length === 0 ? (
        <div className="flex min-h-[240px] flex-col items-center justify-center rounded-lg border border-border bg-surface-subtle p-8 text-center">
          <p className="text-muted-foreground">Chưa có Workspace nào.</p>
          <CreateWorkspaceDialog
            trigger={
              <button
                type="button"
                className="mt-4 inline-flex h-9 items-center gap-2 rounded-md border border-border bg-surface px-4 text-sm font-medium text-foreground transition-colors hover:bg-surface-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring"
              >
                <Plus className="h-4 w-4" aria-hidden="true" />
                Tạo Workspace đầu tiên
              </button>
            }
          />
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {workspaces.map((workspace, index) => (
            <Link
              key={workspace.id}
              to={`/workspaces/${workspace.id}`}
              className="group flex flex-col justify-between rounded-lg border border-border bg-surface p-6 transition-colors duration-150 hover:border-border-strong hover:bg-surface-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring"
              style={{ animationDelay: `calc(${index} * 80ms)` }}
            >
              <div className="flex items-start justify-between">
                <div className="flex h-10 w-10 items-center justify-center rounded-lg border border-border bg-muted">
                  {workspace.type === 'TEAM' ? (
                    <Building2 className="h-5 w-5 text-foreground" aria-hidden="true" />
                  ) : (
                    <User className="h-5 w-5 text-foreground" aria-hidden="true" />
                  )}
                </div>
                <div className="flex items-center rounded-full border border-info-border bg-info-bg px-2.5 py-0.5 text-xs font-medium text-info-fg">
                  {workspace.type}
                </div>
              </div>
              <div className="mt-8">
                <h3 className="text-lg font-medium text-foreground">{workspace.name}</h3>
                <p className="mt-1 text-sm text-muted-foreground">
                  Vai trò: {workspace.currentUserRole ?? 'Quản trị viên hệ thống'}
                </p>
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
