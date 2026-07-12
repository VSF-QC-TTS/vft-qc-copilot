import { cn } from '@/shared/lib/utils';
import type { ComponentProps } from 'react';
import { ChevronDown } from 'lucide-react';
import { useWorkspaces } from '@/features/workspaces/hooks/use-workspaces';
import { useNavigate, useParams } from 'react-router';

import { Skeleton } from '@/shared/components/ui/skeleton';

// Fallback simple dropdown if shadcn dropdown is not ready. 
// We will use standard select-like UI but styled minimally.
export function WorkspaceSwitcher({ className, isCollapsed, ...props }: ComponentProps<'button'> & { isCollapsed?: boolean }) {
  const { data: workspaceCollection, isLoading } = useWorkspaces();
  const workspaces = workspaceCollection?.items || [];
  
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const navigate = useNavigate();

  const currentWorkspace = workspaces.find((w) => w.id === workspaceId);

  // Note: Following minimalist-ui rules: flat architecture, subtle hovers.
  // We use a clean select element overlayed, or just a simple button if we had a popover.
  // For MVP, a minimalist select is accessible and flat.
  
  if (isLoading) {
    return <Skeleton className={cn("h-9 w-full rounded-md", className)} />;
  }

  return (
    <div className={cn('relative flex items-center', className)}>
      <button
        type="button"
        className={cn(
          'flex h-9 w-full items-center rounded border border-[#EAEAEA] bg-[#F9F9F8] px-2 text-sm font-medium transition-transform duration-200 hover:scale-[0.98] active:scale-[0.96] focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring',
          isCollapsed ? 'justify-center' : 'justify-between gap-2'
        )}
        aria-label="Chuyển Workspace"
      >
        <div className="flex items-center gap-2 overflow-hidden">
          <div className="h-5 w-5 shrink-0 rounded-sm bg-[#EAEAEA]" />
          {!isCollapsed && (
            <span className="truncate max-w-[120px] text-[#111111]">
              {currentWorkspace ? currentWorkspace.name : 'Chọn Workspace'}
            </span>
          )}
        </div>
        {!isCollapsed && (
          <ChevronDown aria-hidden="true" className="h-4 w-4 shrink-0 text-[#787774]" />
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
        <option value="" disabled>Chọn Workspace</option>
        {workspaces.map((w) => (
          <option key={w.id} value={w.id}>
            {w.name}
          </option>
        ))}
      </select>
    </div>
  );
}
