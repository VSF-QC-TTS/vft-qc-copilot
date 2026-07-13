import { Link, useOutletContext } from 'react-router';
import { WorkspaceContextType } from '@/app/layouts/workspace-context-layout';
import { useWorkspaceProjects } from '@/features/workspaces/hooks/use-workspaces';
import { Plus, LayoutGrid, List } from 'lucide-react';
import { cn } from '@/shared/lib/utils';
import { useState } from 'react';
import { Skeleton } from '@/shared/components/ui/skeleton';

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
      <div className="mx-auto w-full max-w-5xl py-12 px-4 sm:px-6 lg:px-8">
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
        <p className="text-[#9F2F2D]">Đã có lỗi xảy ra khi tải danh sách Project.</p>
      </div>
    );
  }

  const projects = projectCollection?.items ?? [];

  return (
    <div className="mx-auto w-full max-w-5xl py-12 px-4 sm:px-6 lg:px-8">
      <div className="mb-8 flex flex-col items-start justify-between sm:flex-row sm:items-center">
        <div>
          <h1
            className="text-3xl font-semibold tracking-tight text-[#111111]"
            style={{ fontFamily: "'Lyon Text', 'Newsreader', 'Playfair Display', serif" }}
          >
            Projects
          </h1>
          <p className="mt-1 text-sm text-[#787774]">
            Quản lý các project trong workspace{' '}
            <span className="font-medium text-[#111111]">{workspace.name}</span>.
          </p>
        </div>
        <div className="mt-4 flex items-center gap-4 sm:mt-0">
          <div className="flex items-center rounded-md border border-[#EAEAEA] bg-[#F9F9F8] p-0.5">
            <button
              type="button"
              onClick={() => setView('grid')}
              className={cn(
                'flex h-8 w-8 items-center justify-center rounded-[4px] transition-colors',
                view === 'grid'
                  ? 'bg-white shadow-[0_1px_3px_rgba(0,0,0,0.04)] text-[#111111]'
                  : 'text-[#787774] hover:text-[#111111]',
              )}
            >
              <LayoutGrid className="h-4 w-4" />
            </button>
            <button
              type="button"
              onClick={() => setView('list')}
              className={cn(
                'flex h-8 w-8 items-center justify-center rounded-[4px] transition-colors',
                view === 'list'
                  ? 'bg-white shadow-[0_1px_3px_rgba(0,0,0,0.04)] text-[#111111]'
                  : 'text-[#787774] hover:text-[#111111]',
              )}
            >
              <List className="h-4 w-4" />
            </button>
          </div>
          <button className="flex h-9 items-center justify-center rounded-md bg-[#111111] px-4 text-sm font-medium text-white transition-transform hover:bg-[#333333] active:scale-[0.98]">
            <Plus className="mr-2 h-4 w-4" />
            Tạo Project
          </button>
        </div>
      </div>

      {projects.length === 0 ? (
        <div className="flex min-h-[300px] flex-col items-center justify-center rounded-xl border border-[#EAEAEA] bg-[#FBFBFA] p-8 text-center">
          <p className="text-[#787774]">Chưa có project nào.</p>
          <button className="mt-4 flex h-9 items-center justify-center rounded-md border border-[#EAEAEA] bg-white px-4 text-sm font-medium text-[#111111] transition-transform hover:bg-[#F9F9F8] active:scale-[0.98]">
            Tạo project đầu tiên
          </button>
        </div>
      ) : view === 'grid' ? (
        <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {projects.map((project, index) => (
            <Link
              key={project.id}
              to={`/workspaces/${workspace.id}/projects/${project.id}`}
              className="group flex flex-col justify-between rounded-xl border border-[#EAEAEA] bg-white p-6 transition-all duration-200 hover:-translate-y-1 hover:shadow-[0_2px_8px_rgba(0,0,0,0.04)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              style={{ animationDelay: `calc(${index} * 40ms)` }}
            >
              <div>
                <h3 className="text-lg font-medium text-[#111111]">{project.name}</h3>
                <p className="mt-2 line-clamp-2 text-sm text-[#787774]">
                  {project.description || 'Không có mô tả.'}
                </p>
              </div>
              <div className="mt-6 flex items-center justify-between text-xs text-[#787774]">
                <span>
                  Cập nhật {new Date(project.updatedAt || project.createdAt).toLocaleDateString()}
                </span>
                <span className="rounded-full bg-[#F7F6F3] px-2 py-0.5 border border-[#EAEAEA]">
                  Hoạt động
                </span>
              </div>
            </Link>
          ))}
        </div>
      ) : (
        <div className="overflow-hidden rounded-xl border border-[#EAEAEA] bg-white">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-[#EAEAEA] bg-[#F7F6F3]">
              <tr>
                <th className="px-6 py-3 font-medium text-[#787774]">Tên Project</th>
                <th className="px-6 py-3 font-medium text-[#787774]">Mô tả</th>
                <th className="px-6 py-3 font-medium text-[#787774]">Trạng thái</th>
                <th className="px-6 py-3 text-right font-medium text-[#787774]">Cập nhật</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#EAEAEA]">
              {projects.map((project) => (
                <tr key={project.id} className="transition-colors hover:bg-[#FBFBFA]">
                  <td className="px-6 py-4">
                    <Link
                      to={`/workspaces/${workspace.id}/projects/${project.id}`}
                      className="font-medium text-[#111111] hover:underline"
                    >
                      {project.name}
                    </Link>
                  </td>
                  <td className="px-6 py-4 text-[#787774] max-w-[300px] truncate">
                    {project.description || '-'}
                  </td>
                  <td className="px-6 py-4">
                    <span className="inline-flex items-center rounded-full bg-[#EDF3EC] px-2.5 py-0.5 text-xs font-medium uppercase tracking-wide text-[#346538]">
                      Hoạt động
                    </span>
                  </td>
                  <td className="px-6 py-4 text-right text-[#787774]">
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
