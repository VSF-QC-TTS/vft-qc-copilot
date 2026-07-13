import { Outlet, useParams } from 'react-router';
import { useWorkspace } from '@/features/workspaces/hooks/use-workspaces';
import { NotFoundPage } from '@/app/router/pages/not-found-page';

import { WorkspaceResponse } from '@/features/workspaces/types/workspace';

import { Skeleton } from '@/shared/components/ui/skeleton';

export interface WorkspaceContextType {
  workspace: WorkspaceResponse;
}

export function WorkspaceContextLayout() {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const { data: workspace, isLoading, error } = useWorkspace(workspaceId);

  if (isLoading) {
    return (
      <div className="flex w-full flex-col space-y-4 p-8">
        <Skeleton className="h-10 w-1/3" />
        <Skeleton className="h-[200px] w-full" />
        <Skeleton className="h-[200px] w-full" />
      </div>
    );
  }

  // Handle 404 or missing workspace context
  if (error || !workspace) {
    return <NotFoundPage />;
  }

  // At this point we have a valid workspace context for child routes.
  // Note: currentUserRole might be null if user is a PLATFORM_ADMIN
  // but they are allowed to be here. We do not block based on role here,
  // we just provide the workspace context to the children.

  return <Outlet context={{ workspace }} />;
}
