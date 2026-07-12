import { Outlet, useParams, useOutletContext } from 'react-router';
import { Skeleton } from '@/shared/components/ui/skeleton';
import { useProject } from '@/features/projects/hooks/use-projects';
import { NotFoundPage } from '@/app/router/pages/not-found-page';
import type { WorkspaceResponse } from '@/features/workspaces/types/workspace';
import type { ProjectResponse } from '@/features/projects/types/project';

export interface WorkspaceContextType {
  workspace: WorkspaceResponse;
}

export interface ProjectContextType {
  project: ProjectResponse;
}

export function ProjectContextLayout() {
  const { workspaceId, projectId } = useParams<{ workspaceId: string; projectId: string }>();
  const { workspace } = useOutletContext<WorkspaceContextType>();
  const { data: project, isLoading, error } = useProject(projectId);

  if (isLoading) {
    return (
      <div className="flex w-full flex-col space-y-4 p-8">
        <Skeleton className="h-10 w-1/3" />
        <Skeleton className="h-[200px] w-full" />
        <Skeleton className="h-[200px] w-full" />
      </div>
    );
  }

  // Handle 404 or missing project context
  if (error || !project) {
    return <NotFoundPage />;
  }

  // Security & integrity check: does the project belong to the currently loaded workspace?
  if (project.workspaceId !== workspaceId) {
    return <NotFoundPage />;
  }

  return <Outlet context={{ workspace, project }} />;
}
