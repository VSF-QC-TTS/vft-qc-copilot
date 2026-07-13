import { useEffect, useState } from 'react';
import { useNavigate, useOutletContext, useParams } from 'react-router';
import { Skeleton } from '@/shared/components/ui/skeleton';
import { PermissionGuard } from '@/shared/components/permission-guard';
import { notifyError, notifySuccess } from '@/shared/lib/notifications';
import { TargetEditor } from '@/features/chatbot-targets/components/target-editor';
import { TargetTestPanel } from '@/features/chatbot-targets/components/target-test-panel';
import {
  buildUpdateRequest,
  emptyTargetFormState,
  formStateFromTarget,
} from '@/features/chatbot-targets/components/target-form-utils';
import {
  inspectCurlRequestSchema,
  updateChatbotTargetRequestSchema,
} from '@/features/chatbot-targets/types/chatbot-target';
import {
  useChatbotTarget,
  useChatbotTargetExamples,
  useChatbotTargetReadiness,
  useInspectChatbotTargetCurl,
  useTestChatbotTarget,
  useUpdateChatbotTarget,
} from '@/features/chatbot-targets/hooks/use-chatbot-targets';
import { useWorkspaceSecretsQuery } from '@/features/secrets/hooks/use-secrets';
import type { WorkspaceContextType } from '@/app/layouts/workspace-context-layout';
import type { ProjectResponse } from '@/features/projects/types/project';

interface ProjectContextType extends WorkspaceContextType {
  project: ProjectResponse;
}

export function ChatbotTargetDetailPage() {
  const { targetId = '' } = useParams<{ targetId: string }>();
  const { workspace, project } = useOutletContext<ProjectContextType>();
  const navigate = useNavigate();
  const targetQuery = useChatbotTarget(targetId);
  const readinessQuery = useChatbotTargetReadiness(targetId);
  const examplesQuery = useChatbotTargetExamples(targetId);
  const secretsQuery = useWorkspaceSecretsQuery(workspace.id);
  const inspectMutation = useInspectChatbotTargetCurl(project.id);
  const updateMutation = useUpdateChatbotTarget();
  const testMutation = useTestChatbotTarget();
  const [formState, setFormState] = useState(emptyTargetFormState);

  useEffect(() => {
    if (targetQuery.data) {
      setFormState(formStateFromTarget(targetQuery.data));
    }
  }, [targetQuery.data]);

  if (targetQuery.isLoading) {
    return (
      <div className="mx-auto w-full max-w-5xl px-4 py-8 sm:px-6 lg:px-8">
        <Skeleton className="h-10 w-1/3" />
        <Skeleton className="mt-6 h-[420px] w-full" />
      </div>
    );
  }

  if (targetQuery.isError || !targetQuery.data || targetQuery.data.projectId !== project.id) {
    return (
      <div className="mx-auto w-full max-w-5xl px-4 py-8 text-destructive sm:px-6 lg:px-8">
        Không thể tải chatbot target.
      </div>
    );
  }

  const activeSecrets = (secretsQuery.data?.items ?? []).filter(
    (secret) => secret.status === 'ACTIVE',
  );

  const handleSubmit = async () => {
    try {
      const request = updateChatbotTargetRequestSchema.parse(buildUpdateRequest(formState));
      await updateMutation.mutateAsync({ targetId, data: request });
      notifySuccess('Đã lưu chatbot target');
    } catch {
      notifyError('Không thể lưu chatbot target');
    }
  };

  return (
    <div className="mx-auto w-full max-w-6xl px-4 py-8 sm:px-6 lg:px-8">
      <div className="mb-6 flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
        <div>
          <p className="text-sm text-muted-foreground">{project.name}</p>
          <h1 className="text-2xl font-semibold tracking-tight">{targetQuery.data.name}</h1>
        </div>
        <button
          type="button"
          onClick={() =>
            void navigate(`/workspaces/${workspace.id}/projects/${project.id}/targets`)
          }
          className="text-sm text-muted-foreground hover:underline"
        >
          Quay lại danh sách
        </button>
      </div>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_360px]">
        <PermissionGuard capability="EDIT" workspaceRole={workspace.currentUserRole}>
          {(canEdit) => (
            <TargetEditor
              value={formState}
              onChange={setFormState}
              activeSecrets={activeSecrets}
              canEdit={canEdit}
              isPending={updateMutation.isPending}
              isInspecting={inspectMutation.isPending}
              onInspectCurl={(rawCurl) =>
                inspectMutation.mutateAsync(inspectCurlRequestSchema.parse({ rawCurl }))
              }
              onSubmit={handleSubmit}
            />
          )}
        </PermissionGuard>
        <PermissionGuard capability="EDIT" workspaceRole={workspace.currentUserRole}>
          {(canEdit) => (
            <TargetTestPanel
              readiness={readinessQuery.data}
              examples={examplesQuery.data}
              canEdit={canEdit}
              isTesting={testMutation.isPending}
              onTest={(datasetValues) =>
                testMutation
                  .mutateAsync({ targetId, data: { datasetValues } })
                  .then(() => undefined)
              }
            />
          )}
        </PermissionGuard>
      </div>
    </div>
  );
}
