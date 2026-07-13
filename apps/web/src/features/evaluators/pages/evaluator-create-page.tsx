import { useMemo, useState } from 'react';
import { Link, useNavigate, useOutletContext } from 'react-router';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import { Label } from '@/shared/components/ui/label';
import { PermissionGuard } from '@/shared/components/permission-guard';
import { notifyError, notifySuccess, notifyWarning } from '@/shared/lib/notifications';
import { EvaluatorLlmJudgeEditor } from '@/features/evaluators/components/evaluator-llm-judge-editor';
import { EvaluatorRuleBuilder } from '@/features/evaluators/components/evaluator-rule-builder';
import {
  buildCreateEvaluatorRequest,
  buildValidateEvaluatorRequest,
  emptyLlmJudgeDraft,
  emptyRuleDraft,
  fieldSourceOptions,
  type LlmJudgeDraft,
  type RuleDraft,
} from '@/features/evaluators/components/evaluator-rule-utils';
import {
  useCreateEvaluator,
  useEvaluatorFieldSources,
  useEvaluatorOperators,
  useValidateEvaluator,
} from '@/features/evaluators/hooks/use-evaluators';
import {
  createEvaluatorRequestSchema,
  evaluatorTypeSchema,
  type EvaluatorType,
} from '@/features/evaluators/types/evaluator';
import { useWorkspaceProviders } from '@/features/providers/hooks/use-providers';
import type { WorkspaceContextType } from '@/app/layouts/workspace-context-layout';
import type { ProjectResponse } from '@/features/projects/types/project';

interface ProjectContextType extends WorkspaceContextType {
  project: ProjectResponse;
}

export function EvaluatorCreatePage() {
  const { workspace, project } = useOutletContext<ProjectContextType>();
  const navigate = useNavigate();
  const [name, setName] = useState('');
  const [type, setType] = useState<EvaluatorType>('RULE_BASED');
  const [rules, setRules] = useState<RuleDraft[]>([emptyRuleDraft()]);
  const [llmJudge, setLlmJudge] = useState<LlmJudgeDraft>(emptyLlmJudgeDraft());
  const fieldSourcesQuery = useEvaluatorFieldSources(project.id);
  const operatorsQuery = useEvaluatorOperators();
  const providersQuery = useWorkspaceProviders(workspace.id);
  const createMutation = useCreateEvaluator(project.id);
  const validateMutation = useValidateEvaluator(project.id);
  const fieldSources = useMemo(
    () => fieldSourceOptions(fieldSourcesQuery.data),
    [fieldSourcesQuery.data],
  );
  const activeProviders = useMemo(
    () => (providersQuery.data ?? []).filter((provider) => provider.status === 'ACTIVE'),
    [providersQuery.data],
  );
  const usesRules = type !== 'LLM_JUDGE';
  const usesLlmJudge = type !== 'RULE_BASED';
  const isPending =
    createMutation.isPending || validateMutation.isPending || providersQuery.isLoading;

  const handleCreate = async () => {
    try {
      const request = createEvaluatorRequestSchema.parse(
        buildCreateEvaluatorRequest(name, rules, type, llmJudge),
      );
      await validateMutation.mutateAsync(buildValidateEvaluatorRequest(rules, type, llmJudge));
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
      await validateMutation.mutateAsync(buildValidateEvaluatorRequest(rules, type, llmJudge));
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
          <h1 className="text-2xl font-semibold tracking-tight">Tạo Evaluator</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Cấu hình Rule-based, LLM Judge hoặc Mixed Evaluator theo contract backend.
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
              <Label htmlFor="evaluator-type">Loại Evaluator</Label>
              <select
                id="evaluator-type"
                className="h-9 w-full rounded-md border border-input bg-background px-3 text-sm disabled:cursor-not-allowed disabled:opacity-50"
                value={type}
                disabled={isPending}
                onChange={(event) => setType(evaluatorTypeSchema.parse(event.target.value))}
              >
                <option value="RULE_BASED">Rule-based Evaluator</option>
                <option value="LLM_JUDGE">LLM Judge</option>
                <option value="MIXED">Mixed Evaluator</option>
              </select>
            </div>
          </div>

          {usesRules ? (
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
          ) : null}

          {usesLlmJudge ? (
            <EvaluatorLlmJudgeEditor
              draft={llmJudge}
              providers={activeProviders}
              disabled={isPending}
              showRuleThreshold={type === 'MIXED'}
              onChange={setLlmJudge}
            />
          ) : null}

          <div className="flex justify-end gap-2">
            <Button
              type="button"
              variant="outline"
              disabled={isPending}
              onClick={() => void handleValidate()}
            >
              Kiểm tra
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
