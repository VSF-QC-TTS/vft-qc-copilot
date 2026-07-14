import { cn } from '@/shared/lib/utils';
import type { ComponentProps } from 'react';
import { ChevronDown } from 'lucide-react';
import { useWorkspaces } from '@/features/workspaces/hooks/use-workspaces';
import { useNavigate, useParams } from 'react-router';

import { Skeleton } from '@/shared/components/ui/skeleton';

export function WorkspaceSwitcher({
  className,
  isCollapsed,
}: ComponentProps<'button'> & { isCollapsed?: boolean }) {
  const { data: workspaceCollection, isLoading } = useWorkspaces();
  const workspaces = workspaceCollection?.items || [];

  const { workspaceId } = useParams<{ workspaceId: string }>();
  const navigate = useNavigate();

  const currentWorkspace = workspaces.find((w) => w.id === workspaceId);

  if (isLoading) {
    return (
      <Skeleton aria-label="Chuyển Workspace" className={cn('h-9 w-full rounded-md', className)} />
    );
  }

  return (
    <div className={cn('relative flex items-center', className)}>
      <button
        type="button"
        className={cn(
          'flex h-9 w-full items-center rounded-md border border-border bg-surface-subtle px-2 text-sm font-medium text-foreground transition-colors duration-150 hover:bg-surface-hover active:bg-surface-pressed focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring',
          isCollapsed ? 'justify-center' : 'justify-between gap-2',
        )}
        aria-label="Chuyển Workspace"
      >
        <div className="flex items-center gap-2 overflow-hidden">
          <div className="h-5 w-5 shrink-0 rounded-sm bg-muted" />
          {!isCollapsed && (
            <span className="max-w-[120px] truncate text-foreground">
              {currentWorkspace ? currentWorkspace.name : 'Chọn Workspace'}
            </span>
          )}
        </div>
        {!isCollapsed && (
          <ChevronDown aria-hidden="true" className="h-4 w-4 shrink-0 text-muted-foreground" />
        )}
      </button>

      {/* 
        Native select overlaid to handle switching natively and accessibly without 
        complex dropdown dependencies, styled to be invisible.
      */}
      <select
        className="absolute inset-0 h-full w-full cursor-pointer opacity-0"
        value={workspaceId || ''}
        onChange={(e) => {
          if (e.target.value) {
            void navigate(`/workspaces/${e.target.value}`);
          }
        }}
        aria-label="Chọn Workspace"
      >
        <option value="" disabled>
          Chọn Workspace
        </option>
        {workspaces.map((w) => (
          <option key={w.id} value={w.id}>
            {w.name}
          </option>
        ))}
      </select>
    </div>
  );
}
