import { useState } from 'react';
import { useNavigate, useOutletContext } from 'react-router';
import { TargetEditor } from '@/features/chatbot-targets/components/target-editor';
import {
  buildCreateRequest,
  emptyTargetFormState,
} from '@/features/chatbot-targets/components/target-form-utils';
import {
  createChatbotTargetRequestSchema,
  inspectCurlRequestSchema,
} from '@/features/chatbot-targets/types/chatbot-target';
import {
  useCreateChatbotTarget,
  useInspectChatbotTargetCurl,
} from '@/features/chatbot-targets/hooks/use-chatbot-targets';
import { useWorkspaceSecretsQuery } from '@/features/secrets/hooks/use-secrets';
import { PermissionGuard } from '@/shared/components/permission-guard';
import { notifyError, notifySuccess } from '@/shared/lib/notifications';
import type { WorkspaceContextType } from '@/app/layouts/workspace-context-layout';
import type { ProjectResponse } from '@/features/projects/types/project';

interface ProjectContextType extends WorkspaceContextType {
  project: ProjectResponse;
}

export function ChatbotTargetCreatePage() {
  const { workspace, project } = useOutletContext<ProjectContextType>();
  const navigate = useNavigate();
  const [formState, setFormState] = useState(emptyTargetFormState);
  const secretsQuery = useWorkspaceSecretsQuery(workspace.id);
  const createMutation = useCreateChatbotTarget(project.id);
  const inspectMutation = useInspectChatbotTargetCurl(project.id);

  const activeSecrets = (secretsQuery.data?.items ?? []).filter(
    (secret) => secret.status === 'ACTIVE',
  );

  const handleSubmit = async () => {
    try {
      const request = createChatbotTargetRequestSchema.parse(buildCreateRequest(formState));
      const created = await createMutation.mutateAsync(request);
      notifySuccess('Đã tạo chatbot target');
      await navigate(`/workspaces/${workspace.id}/projects/${project.id}/targets/${created.id}`);
    } catch {
      notifyError('Không thể lưu chatbot target');
    }
  };

  return (
    <div className="mx-auto w-full max-w-5xl px-4 py-8 sm:px-6 lg:px-8">
      <div className="mb-6">
        <p className="text-sm text-muted-foreground">{project.name}</p>
        <h1 className="text-2xl font-semibold tracking-tight">Tạo chatbot target</h1>
      </div>
      <PermissionGuard capability="EDIT" workspaceRole={workspace.currentUserRole}>
        {(canEdit) => (
          <TargetEditor
            value={formState}
            onChange={setFormState}
            activeSecrets={activeSecrets}
            canEdit={canEdit}
            isPending={createMutation.isPending}
            isInspecting={inspectMutation.isPending}
            onInspectCurl={(rawCurl) =>
              inspectMutation.mutateAsync(inspectCurlRequestSchema.parse({ rawCurl }))
            }
            onSubmit={handleSubmit}
          />
        )}
      </PermissionGuard>
    </div>
  );
}
