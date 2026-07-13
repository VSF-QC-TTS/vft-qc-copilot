import { Link, useOutletContext } from 'react-router';
import { Plus } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { PermissionGuard } from '@/shared/components/permission-guard';
import { notifyError, notifySuccess } from '@/shared/lib/notifications';
import { TargetList } from '@/features/chatbot-targets/components/target-list';
import {
  useDeleteChatbotTarget,
  useProjectChatbotTargets,
} from '@/features/chatbot-targets/hooks/use-chatbot-targets';
import type { WorkspaceContextType } from '@/app/layouts/workspace-context-layout';
import type { ProjectResponse } from '@/features/projects/types/project';
import type { ChatbotTargetResponse } from '@/features/chatbot-targets/types/chatbot-target';

interface ProjectContextType extends WorkspaceContextType {
  project: ProjectResponse;
}

export function ChatbotTargetListPage() {
  const { workspace, project } = useOutletContext<ProjectContextType>();
  const targetsQuery = useProjectChatbotTargets(project.id);
  const deleteMutation = useDeleteChatbotTarget(project.id);

  const handleDelete = async (target: ChatbotTargetResponse) => {
    try {
      await deleteMutation.mutateAsync(target.id);
      notifySuccess('Đã xóa chatbot target');
    } catch {
      notifyError('Không thể xóa chatbot target');
    }
  };

  return (
    <div className="mx-auto w-full max-w-6xl px-4 py-8 sm:px-6 lg:px-8">
      <div className="mb-6 flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
        <div>
          <p className="text-sm text-muted-foreground">{project.name}</p>
          <h1 className="text-2xl font-semibold tracking-tight">Chatbot targets</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Cấu hình endpoint chatbot, binding secret, mapping dataset và response extraction.
          </p>
        </div>
        <PermissionGuard capability="EDIT" workspaceRole={workspace.currentUserRole}>
          {(isAllowed) => (
            <Button asChild disabled={!isAllowed}>
              <Link to={`/workspaces/${workspace.id}/projects/${project.id}/targets/new`}>
                <Plus className="mr-2 h-4 w-4" />
                Thêm target
              </Link>
            </Button>
          )}
        </PermissionGuard>
      </div>

      <PermissionGuard capability="EDIT" workspaceRole={workspace.currentUserRole}>
        {(canEdit) => (
          <TargetList
            targets={targetsQuery.data}
            workspaceId={workspace.id}
            projectId={project.id}
            isLoading={targetsQuery.isLoading}
            isError={targetsQuery.isError}
            canEdit={canEdit}
            isPending={deleteMutation.isPending}
            onDelete={(target) => void handleDelete(target)}
          />
        )}
      </PermissionGuard>
    </div>
  );
}
