import { Link, useOutletContext } from 'react-router';
import { WorkspaceContextType } from '@/app/layouts/workspace-context-layout';
import { useWorkspaceProjects } from '@/features/workspaces/hooks/use-workspaces';
import { Plus, ArrowRight } from 'lucide-react';
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
    <div className="mx-auto w-full max-w-5xl py-24 px-4 sm:px-6 lg:px-8">
      <div className="mb-12 flex flex-col items-start justify-between sm:flex-row sm:items-center">
        <div className="space-y-1">
          <div className="mb-2 flex items-center gap-2">
            <span className="flex items-center rounded-full bg-[#E1F3FE] px-2.5 py-0.5 text-xs font-medium uppercase tracking-wide text-[#1F6C9F]">
              {workspace.type}
            </span>
            {workspace.currentUserRole && (
              <span className="flex items-center rounded-full bg-[#EDF3EC] px-2.5 py-0.5 text-xs font-medium uppercase tracking-wide text-[#346538]">
                {workspace.currentUserRole}
              </span>
            )}
          </div>
          <h1
            className="text-4xl font-semibold tracking-tight text-[#111111]"
            style={{ fontFamily: "'Lyon Text', 'Newsreader', 'Playfair Display', serif" }}
          >
            {workspace.name}
          </h1>
          <p className="text-base text-[#787774]">
            ID:{' '}
            <kbd className="rounded border border-[#EAEAEA] bg-[#F7F6F3] px-1 font-mono text-xs">
              {workspace.id}
            </kbd>
          </p>
        </div>
        <div className="mt-4 flex gap-3 sm:mt-0">
          <Link
            to={`/workspaces/${workspace.id}/projects`}
            className="flex h-9 items-center justify-center rounded-md border border-[#EAEAEA] bg-white px-4 text-sm font-medium text-[#111111] transition-transform hover:bg-[#F9F9F8] active:scale-[0.98]"
          >
            Tất cả Project
          </Link>
          <CreateProjectDialog
            workspace={workspace}
            trigger={
              <button
                type="button"
                className="flex h-9 items-center justify-center rounded-md bg-[#111111] px-4 text-sm font-medium text-white transition-transform hover:bg-[#333333] active:scale-[0.98]"
              >
                <Plus className="mr-2 h-4 w-4" />
                Tạo Project
              </button>
            }
          />
        </div>
      </div>

      <div className="mt-16">
        <h2 className="mb-6 text-xl font-medium tracking-tight text-[#111111]">Project gần đây</h2>

        {isLoading ? (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {recentProjectSkeletonKeys.map((key) => (
              <Skeleton key={key} className="h-32 w-full rounded-lg" />
            ))}
          </div>
        ) : error ? (
          <div className="flex min-h-[200px] w-full items-center justify-center rounded-xl border border-[#EAEAEA] bg-[#FBFBFA]">
            <p className="text-[#9F2F2D]">Không thể tải project.</p>
          </div>
        ) : projects.length === 0 ? (
          <div className="flex min-h-[200px] flex-col items-center justify-center rounded-xl border border-[#EAEAEA] bg-[#FBFBFA] p-8 text-center">
            <p className="text-[#787774]">Chưa có project nào trong workspace này.</p>
            <CreateProjectDialog
              workspace={workspace}
              trigger={
                <button
                  type="button"
                  className="mt-4 flex h-9 items-center justify-center rounded-md border border-[#EAEAEA] bg-white px-4 text-sm font-medium text-[#111111] transition-transform hover:bg-[#F9F9F8] active:scale-[0.98]"
                >
                  Tạo Project đầu tiên
                </button>
              }
            />
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {projects.slice(0, 6).map((project, index) => (
              <Link
                key={project.id}
                to={`/workspaces/${workspace.id}/projects/${project.id}`}
                className="group flex flex-col justify-between rounded-xl border border-[#EAEAEA] bg-white p-5 transition-all duration-200 hover:-translate-y-1 hover:shadow-[0_2px_8px_rgba(0,0,0,0.04)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                style={{ animationDelay: `calc(${index} * 80ms)` }}
              >
                <div>
                  <h3 className="text-lg font-medium text-[#111111]">{project.name}</h3>
                  {project.description && (
                    <p className="mt-1 line-clamp-2 text-sm text-[#787774]">
                      {project.description}
                    </p>
                  )}
                </div>
                <div className="mt-6 flex items-center text-sm font-medium text-[#787774] transition-colors group-hover:text-[#111111]">
                  Đi đến project
                  <ArrowRight className="ml-1 h-4 w-4 transition-transform group-hover:translate-x-1" />
                </div>
              </Link>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
