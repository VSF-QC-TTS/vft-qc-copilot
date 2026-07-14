import { Link, useOutletContext } from 'react-router';
import { WorkspaceContextType } from '@/app/layouts/workspace-context-layout';
import { useWorkspaceProjects } from '@/features/workspaces/hooks/use-workspaces';
import { Plus, LayoutGrid, List } from 'lucide-react';
import { cn } from '@/shared/lib/utils';
import { useState } from 'react';
import { Skeleton } from '@/shared/components/ui/skeleton';
import { CreateProjectDialog } from '@/features/projects/components/create-project-dialog';

const projectSkeletonKeys = [
  'project-skeleton-1',
  'project-skeleton-2',
  'project-skeleton-3',
  'project-skeleton-4',
  'project-skeleton-5',
  'project-skeleton-6',
];

export function ProjectListPage() {
  const { workspace } = useOutletContext<WorkspaceContextType>();
  const { data: projectCollection, isLoading, error } = useWorkspaceProjects(workspace.id);
  const [view, setView] = useState<'grid' | 'list'>('grid');

  if (isLoading) {
    return (
      <div className="mx-auto w-full max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
        <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {projectSkeletonKeys.map((key) => (
            <Skeleton key={key} className="h-32 w-full rounded-xl" />
          ))}
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex min-h-[400px] w-full flex-col items-center justify-center text-center">
        <p className="text-danger">Đã có lỗi xảy ra khi tải danh sách Project.</p>
      </div>
    );
  }

  const projects = projectCollection?.items ?? [];

  return (
    <div className="mx-auto w-full max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-3xl font-semibold tracking-tight text-foreground">Projects</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Quản lý các project trong workspace{' '}
            <span className="font-medium text-foreground">{workspace.name}</span>.
          </p>
        </div>
        <div className="flex items-center gap-4">
          <div className="flex items-center rounded-md border border-border bg-surface-subtle p-0.5">
            <button
              type="button"
              aria-label="Xem dạng lưới"
              onClick={() => setView('grid')}
              className={cn(
                'flex h-8 w-8 items-center justify-center rounded-[4px] transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring',
                view === 'grid'
                  ? 'bg-surface text-foreground'
                  : 'text-muted-foreground hover:bg-surface-hover hover:text-foreground',
              )}
            >
              <LayoutGrid className="h-4 w-4" aria-hidden="true" />
            </button>
            <button
              type="button"
              aria-label="Xem dạng danh sách"
              onClick={() => setView('list')}
              className={cn(
                'flex h-8 w-8 items-center justify-center rounded-[4px] transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring',
                view === 'list'
                  ? 'bg-surface text-foreground'
                  : 'text-muted-foreground hover:bg-surface-hover hover:text-foreground',
              )}
            >
              <List className="h-4 w-4" aria-hidden="true" />
            </button>
          </div>
          <CreateProjectDialog
            workspace={workspace}
            trigger={
              <button
                type="button"
                className="inline-flex h-9 items-center justify-center rounded-md bg-primary px-4 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring"
              >
                <Plus className="mr-2 h-4 w-4" aria-hidden="true" />
                Tạo Project
              </button>
            }
          />
        </div>
      </div>

      {projects.length === 0 ? (
        <div className="flex min-h-[300px] flex-col items-center justify-center rounded-lg border border-border bg-surface-subtle p-8 text-center">
          <p className="text-muted-foreground">Chưa có Project nào.</p>
          <CreateProjectDialog
            workspace={workspace}
            trigger={
              <button
                type="button"
                className="mt-4 inline-flex h-9 items-center justify-center rounded-md border border-border bg-surface px-4 text-sm font-medium text-foreground transition-colors hover:bg-surface-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring"
              >
                Tạo Project đầu tiên
              </button>
            }
          />
        </div>
      ) : view === 'grid' ? (
        <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {projects.map((project, index) => (
            <Link
              key={project.id}
              to={`/workspaces/${workspace.id}/projects/${project.id}`}
              className="group flex flex-col justify-between rounded-lg border border-border bg-surface p-6 transition-colors duration-150 hover:border-border-strong hover:bg-surface-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring"
              style={{ animationDelay: `calc(${index} * 40ms)` }}
            >
              <div>
                <h3 className="text-lg font-medium text-foreground">{project.name}</h3>
                <p className="mt-2 line-clamp-2 text-sm text-muted-foreground">
                  {project.description || 'Không có mô tả.'}
                </p>
              </div>
              <div className="mt-6 flex items-center justify-between text-xs text-muted-foreground">
                <span>
                  Cập nhật {new Date(project.updatedAt || project.createdAt).toLocaleDateString()}
                </span>
                <span className="rounded-full border border-neutral-border bg-neutral-bg px-2 py-0.5 text-neutral-fg">
                  Hoạt động
                </span>
              </div>
            </Link>
          ))}
        </div>
      ) : (
        <div className="overflow-hidden rounded-lg border border-border bg-surface">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-border bg-muted">
              <tr>
                <th className="px-6 py-3 font-medium text-muted-foreground">Tên Project</th>
                <th className="px-6 py-3 font-medium text-muted-foreground">Mô tả</th>
                <th className="px-6 py-3 font-medium text-muted-foreground">Trạng thái</th>
                <th className="px-6 py-3 text-right font-medium text-muted-foreground">Cập nhật</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {projects.map((project) => (
                <tr key={project.id} className="transition-colors hover:bg-surface-hover">
                  <td className="px-6 py-4">
                    <Link
                      to={`/workspaces/${workspace.id}/projects/${project.id}`}
                      className="font-medium text-foreground hover:underline"
                    >
                      {project.name}
                    </Link>
                  </td>
                  <td className="max-w-[300px] truncate px-6 py-4 text-muted-foreground">
                    {project.description || '-'}
                  </td>
                  <td className="px-6 py-4">
                    <span className="inline-flex items-center rounded-full border border-success-border bg-success-bg px-2.5 py-0.5 text-xs font-medium text-success-fg">
                      Hoạt động
                    </span>
                  </td>
                  <td className="px-6 py-4 text-right text-muted-foreground">
                    {new Date(project.updatedAt || project.createdAt).toLocaleDateString()}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
