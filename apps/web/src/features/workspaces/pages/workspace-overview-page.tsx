import { Link, useOutletContext } from 'react-router';
import { WorkspaceContextType } from '@/app/layouts/workspace-context-layout';
import { useWorkspaceProjects } from '@/features/workspaces/hooks/use-workspaces';
import { Plus, ArrowRight, KeyRound, ServerCog, UsersRound } from 'lucide-react';
import { Skeleton } from '@/shared/components/ui/skeleton';
import { CreateProjectDialog } from '@/features/projects/components/create-project-dialog';

const recentProjectSkeletonKeys = [
  'recent-project-skeleton-1',
  'recent-project-skeleton-2',
  'recent-project-skeleton-3',
];

export function WorkspaceOverviewPage() {
  const { workspace } = useOutletContext<WorkspaceContextType>();
  const { data: projectCollection, isLoading, error } = useWorkspaceProjects(workspace.id);

  const projects = projectCollection?.items ?? [];

  return (
    <div className="mx-auto w-full max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div className="space-y-1">
          <div className="mb-2 flex items-center gap-2">
            <span className="flex items-center rounded-full border border-info-border bg-info-bg px-2.5 py-0.5 text-xs font-medium text-info-fg">
              {workspace.type}
            </span>
            {workspace.currentUserRole && (
              <span className="flex items-center rounded-full border border-success-border bg-success-bg px-2.5 py-0.5 text-xs font-medium text-success-fg">
                {workspace.currentUserRole}
              </span>
            )}
          </div>
          <h1 className="text-3xl font-semibold tracking-tight text-foreground">
            {workspace.name}
          </h1>
          <p className="text-sm text-muted-foreground">
            ID:{' '}
            <kbd className="rounded border border-border bg-muted px-1 font-mono text-xs">
              {workspace.id}
            </kbd>
          </p>
        </div>
        <div className="flex flex-wrap gap-3">
          <Link
            to={`/workspaces/${workspace.id}/projects`}
            className="inline-flex h-9 items-center justify-center rounded-md border border-border bg-surface px-4 text-sm font-medium text-foreground transition-colors hover:bg-surface-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring"
          >
            Tất cả Project
          </Link>
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

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_320px]">
        <section>
          <h2 className="mb-4 text-xl font-medium tracking-tight text-foreground">
            Project gần đây
          </h2>

          {isLoading ? (
            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
              {recentProjectSkeletonKeys.map((key) => (
                <Skeleton key={key} className="h-32 w-full rounded-lg" />
              ))}
            </div>
          ) : error ? (
            <div className="flex min-h-[200px] w-full items-center justify-center rounded-lg border border-border bg-surface-subtle">
              <p className="text-danger">Không thể tải project.</p>
            </div>
          ) : projects.length === 0 ? (
            <div className="flex min-h-[220px] flex-col items-center justify-center rounded-lg border border-border bg-surface-subtle p-8 text-center">
              <p className="text-muted-foreground">Chưa có project nào trong Workspace này.</p>
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
          ) : (
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
              {projects.slice(0, 6).map((project, index) => (
                <Link
                  key={project.id}
                  to={`/workspaces/${workspace.id}/projects/${project.id}`}
                  className="group flex flex-col justify-between rounded-lg border border-border bg-surface p-5 transition-colors duration-150 hover:border-border-strong hover:bg-surface-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring"
                  style={{ animationDelay: `calc(${index} * 80ms)` }}
                >
                  <div>
                    <h3 className="text-lg font-medium text-foreground">{project.name}</h3>
                    {project.description && (
                      <p className="mt-1 line-clamp-2 text-sm text-muted-foreground">
                        {project.description}
                      </p>
                    )}
                  </div>
                  <div className="mt-6 flex items-center text-sm font-medium text-muted-foreground transition-colors group-hover:text-foreground">
                    Đi đến Project
                    <ArrowRight
                      className="ml-1 h-4 w-4 transition-transform group-hover:translate-x-1"
                      aria-hidden="true"
                    />
                  </div>
                </Link>
              ))}
            </div>
          )}
        </section>

        <aside className="rounded-lg border border-border bg-surface p-5">
          <h2 className="text-sm font-semibold text-foreground">Thiết lập Workspace</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Chuẩn bị Secret, Provider và thành viên trước khi chạy TestRun.
          </p>
          <div className="mt-4 space-y-2">
            <SetupLink
              to={`/workspaces/${workspace.id}/settings/secrets`}
              icon={KeyRound}
              label="Workspace Secret"
            />
            <SetupLink
              to={`/workspaces/${workspace.id}/settings/providers`}
              icon={ServerCog}
              label="Workspace Provider"
            />
            <SetupLink
              to={`/workspaces/${workspace.id}/settings/members`}
              icon={UsersRound}
              label="Thành viên"
            />
          </div>
        </aside>
      </div>
    </div>
  );
}

function SetupLink({
  to,
  icon: Icon,
  label,
}: {
  to: string;
  icon: typeof KeyRound;
  label: string;
}) {
  return (
    <Link
      to={to}
      className="flex items-center justify-between rounded-md border border-border bg-surface px-3 py-2 text-sm text-foreground transition-colors hover:bg-surface-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring"
    >
      <span className="flex items-center gap-2">
        <Icon className="h-4 w-4 text-muted-foreground" aria-hidden="true" />
        {label}
      </span>
      <ArrowRight className="h-4 w-4 text-muted-foreground" aria-hidden="true" />
    </Link>
  );
}
