import { useWorkspaces } from '@/features/workspaces/hooks/use-workspaces';
import { Plus, Building2, User } from 'lucide-react';
import { Link } from 'react-router';
import { Skeleton } from '@/shared/components/ui/skeleton';

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
    <div className="mx-auto max-w-[1200px] p-8">
      {/* Header */}
      <div className="mb-12 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight text-[#111111]">Workspace</h1>
          <p className="mt-1 text-sm text-[#787774]">Quản lý không gian làm việc của bạn</p>
        </div>
        <button className="flex h-9 items-center gap-2 rounded-md bg-[#111111] px-4 text-sm font-medium text-white transition-colors hover:bg-[#2C2C2C]">
          <Plus className="h-4 w-4" />
          Tạo Workspace
        </button>
      </div>

      {isLoading ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {workspaceSkeletonKeys.map((key) => (
            <Skeleton key={key} className="h-32 w-full rounded-lg" />
          ))}
        </div>
      ) : error ? (
        <div className="flex min-h-[400px] w-full flex-col items-center justify-center text-center">
          <p className="text-[#9F2F2D]">Đã có lỗi xảy ra khi tải Workspace.</p>
        </div>
      ) : workspaces.length === 0 ? (
        <div className="flex min-h-[200px] flex-col items-center justify-center rounded-xl border border-[#EAEAEA] bg-[#FBFBFA] p-8 text-center">
          <p className="text-[#787774]">Chưa có Workspace nào.</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {workspaces.map((workspace, index) => (
            <Link
              key={workspace.id}
              to={`/workspaces/${workspace.id}`}
              className="group flex flex-col justify-between rounded-xl border border-[#EAEAEA] bg-white p-6 transition-all duration-200 hover:shadow-[0_2px_8px_rgba(0,0,0,0.04)] hover:-translate-y-1 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              style={{ animationDelay: `calc(${index} * 80ms)` }}
            >
              <div className="flex items-start justify-between">
                <div className="flex h-10 w-10 items-center justify-center rounded-lg border border-[#EAEAEA] bg-[#F7F6F3]">
                  {workspace.type === 'TEAM' ? (
                    <Building2 className="h-5 w-5 text-[#111111]" />
                  ) : (
                    <User className="h-5 w-5 text-[#111111]" />
                  )}
                </div>
                <div className="flex items-center rounded-full bg-[#E1F3FE] px-2.5 py-0.5 text-xs font-medium uppercase tracking-wide text-[#1F6C9F]">
                  {workspace.type}
                </div>
              </div>
              <div className="mt-8">
                <h3 className="text-lg font-medium text-[#111111]">{workspace.name}</h3>
                <p className="mt-1 text-sm text-[#787774]">
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
