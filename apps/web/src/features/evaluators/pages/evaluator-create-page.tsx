import { useMemo, useState } from 'react';
import { Link, useNavigate, useOutletContext } from 'react-router';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import { Label } from '@/shared/components/ui/label';
import { PermissionGuard } from '@/shared/components/permission-guard';
import { notifyError, notifySuccess, notifyWarning } from '@/shared/lib/notifications';
import { EvaluatorRuleBuilder } from '@/features/evaluators/components/evaluator-rule-builder';
import {
  buildCreateEvaluatorRequest,
  buildValidateEvaluatorRequest,
  emptyRuleDraft,
  fieldSourceOptions,
  type RuleDraft,
} from '@/features/evaluators/components/evaluator-rule-utils';
import {
  useCreateEvaluator,
  useEvaluatorFieldSources,
  useEvaluatorOperators,
  useValidateEvaluator,
} from '@/features/evaluators/hooks/use-evaluators';
import { createEvaluatorRequestSchema } from '@/features/evaluators/types/evaluator';
import type { WorkspaceContextType } from '@/app/layouts/workspace-context-layout';
import type { ProjectResponse } from '@/features/projects/types/project';

interface ProjectContextType extends WorkspaceContextType {
  project: ProjectResponse;
}

export function EvaluatorCreatePage() {
  const { workspace, project } = useOutletContext<ProjectContextType>();
  const navigate = useNavigate();
  const [name, setName] = useState('');
  const [rules, setRules] = useState<RuleDraft[]>([emptyRuleDraft()]);
  const fieldSourcesQuery = useEvaluatorFieldSources(project.id);
  const operatorsQuery = useEvaluatorOperators();
  const createMutation = useCreateEvaluator(project.id);
  const validateMutation = useValidateEvaluator(project.id);
  const fieldSources = useMemo(
    () => fieldSourceOptions(fieldSourcesQuery.data),
    [fieldSourcesQuery.data],
  );
  const isPending = createMutation.isPending || validateMutation.isPending;

  const handleCreate = async () => {
    try {
      const request = createEvaluatorRequestSchema.parse(buildCreateEvaluatorRequest(name, rules));
      await validateMutation.mutateAsync(buildValidateEvaluatorRequest(rules));
      const evaluator = await createMutation.mutateAsync(request);
      notifySuccess('Đã tạo Evaluator');
      await navigate(
        `/workspaces/${workspace.id}/projects/${project.id}/evaluators/${evaluator.id}`,
      );
    } catch {
      notifyError('Không thể tạo Evaluator');
    }
  };

  const handleValidate = async () => {
    try {
      await validateMutation.mutateAsync(buildValidateEvaluatorRequest(rules));
      notifySuccess('Evaluator hợp lệ');
    } catch {
      notifyWarning('Evaluator chưa hợp lệ. Vui lòng kiểm tra rules.');
    }
  };

  return (
    <div className="mx-auto w-full max-w-6xl px-4 py-8 sm:px-6 lg:px-8">
      <div className="mb-6 flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
        <div>
          <p className="text-sm text-muted-foreground">{project.name}</p>
          <h1 className="text-2xl font-semibold tracking-tight">Tạo Rule-based Evaluator</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Cấu hình rules an toàn từ field sources và operators do backend cung cấp.
          </p>
        </div>
        <Button asChild variant="outline">
          <Link to={`/workspaces/${workspace.id}/projects/${project.id}/evaluators`}>Quay lại</Link>
        </Button>
      </div>

      <PermissionGuard
        capability="EDIT"
        workspaceRole={workspace.currentUserRole}
        fallback={
          <div className="rounded-lg border bg-muted p-6 text-sm text-muted-foreground">
            Bạn không có quyền tạo Evaluator trong Project này.
          </div>
        }
      >
        <div className="space-y-6 rounded-lg border bg-surface p-5">
          <div className="grid gap-4 md:grid-cols-[minmax(0,1fr)_220px]">
            <div className="space-y-2">
              <Label htmlFor="evaluator-name">Tên Evaluator</Label>
              <Input
                id="evaluator-name"
                value={name}
                onChange={(event) => setName(event.target.value)}
                placeholder="Status code phải thành công"
                disabled={isPending}
              />
            </div>
            <div className="space-y-2">
              <Label>Type</Label>
              <div className="flex h-9 items-center rounded-md border bg-muted px-3 text-sm">
                Rule-based Evaluator
              </div>
            </div>
          </div>

          <EvaluatorRuleBuilder
            rules={rules}
            fieldSources={fieldSources}
            operators={operatorsQuery.data ?? []}
            disabled={
              isPending ||
              fieldSourcesQuery.isLoading ||
              operatorsQuery.isLoading ||
              fieldSources.length === 0
            }
            onChange={setRules}
          />

          <div className="flex justify-end gap-2">
            <Button
              type="button"
              variant="outline"
              disabled={isPending}
              onClick={() => void handleValidate()}
            >
              Validate
            </Button>
            <Button type="button" disabled={isPending} onClick={() => void handleCreate()}>
              Tạo Evaluator
            </Button>
          </div>
        </div>
      </PermissionGuard>
    </div>
  );
}
