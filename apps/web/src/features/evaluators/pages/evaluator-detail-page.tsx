import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useOutletContext, useParams } from 'react-router';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import { Label } from '@/shared/components/ui/label';
import { Skeleton } from '@/shared/components/ui/skeleton';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/components/ui/table';
import { PermissionGuard } from '@/shared/components/permission-guard';
import { notifyError, notifySuccess, notifyWarning } from '@/shared/lib/notifications';
import { EvaluatorRuleBuilder } from '@/features/evaluators/components/evaluator-rule-builder';
import {
  buildCreateVersionRequest,
  buildValidateEvaluatorRequest,
  fieldSourceOptions,
  ruleDraftsFromVersion,
  ruleSummary,
  type RuleDraft,
} from '@/features/evaluators/components/evaluator-rule-utils';
import {
  useCreateEvaluatorVersion,
  useEvaluator,
  useEvaluatorFieldSources,
  useEvaluatorOperators,
  useEvaluatorVersions,
  useUpdateEvaluator,
  useValidateEvaluator,
} from '@/features/evaluators/hooks/use-evaluators';
import {
  createEvaluatorVersionRequestSchema,
  updateEvaluatorRequestSchema,
} from '@/features/evaluators/types/evaluator';
import type { WorkspaceContextType } from '@/app/layouts/workspace-context-layout';
import type { ProjectResponse } from '@/features/projects/types/project';
import type { EvaluatorVersionResponse } from '@/features/evaluators/types/evaluator';

interface ProjectContextType extends WorkspaceContextType {
  project: ProjectResponse;
}

export function EvaluatorDetailPage() {
  const { evaluatorId = '', versionId } = useParams<{
    evaluatorId: string;
    versionId?: string;
  }>();
  const { workspace, project } = useOutletContext<ProjectContextType>();
  const navigate = useNavigate();
  const evaluatorQuery = useEvaluator(evaluatorId);
  const versionsQuery = useEvaluatorVersions(evaluatorId);
  const fieldSourcesQuery = useEvaluatorFieldSources(project.id);
  const operatorsQuery = useEvaluatorOperators();
  const updateMutation = useUpdateEvaluator(project.id);
  const createVersionMutation = useCreateEvaluatorVersion(evaluatorId);
  const validateMutation = useValidateEvaluator(project.id);
  const selectedVersion =
    versionsQuery.data?.find((version) => version.id === versionId) ?? versionsQuery.data?.[0];
  const fieldSources = useMemo(
    () => fieldSourceOptions(fieldSourcesQuery.data),
    [fieldSourcesQuery.data],
  );
  const [name, setName] = useState('');
  const [draftRules, setDraftRules] = useState<RuleDraft[]>([]);

  useEffect(() => {
    if (evaluatorQuery.data) {
      setName(evaluatorQuery.data.name);
    }
  }, [evaluatorQuery.data]);

  useEffect(() => {
    if (selectedVersion) {
      setDraftRules(ruleDraftsFromVersion(selectedVersion));
    }
  }, [selectedVersion]);

  if (evaluatorQuery.isLoading || versionsQuery.isLoading) {
    return (
      <div className="mx-auto w-full max-w-6xl px-4 py-8 sm:px-6 lg:px-8">
        <Skeleton className="h-10 w-1/3" />
        <Skeleton className="mt-6 h-[420px] w-full" />
      </div>
    );
  }

  if (
    evaluatorQuery.isError ||
    versionsQuery.isError ||
    !evaluatorQuery.data ||
    evaluatorQuery.data.projectId !== project.id
  ) {
    return (
      <div className="mx-auto w-full max-w-5xl px-4 py-8 text-destructive sm:px-6 lg:px-8">
        Không thể tải Evaluator.
      </div>
    );
  }

  const isPending =
    updateMutation.isPending || createVersionMutation.isPending || validateMutation.isPending;

  const navigateToVersion = async (targetVersionId: string) => {
    await navigate(
      `/workspaces/${workspace.id}/projects/${project.id}/evaluators/${evaluatorId}/versions/${targetVersionId}`,
    );
  };

  const handleRename = async () => {
    try {
      const request = updateEvaluatorRequestSchema.parse({ name });
      await updateMutation.mutateAsync({ evaluatorId, data: request });
      notifySuccess('Đã cập nhật Evaluator');
    } catch {
      notifyError('Không thể cập nhật Evaluator');
    }
  };

  const handleValidate = async () => {
    try {
      await validateMutation.mutateAsync(buildValidateEvaluatorRequest(draftRules));
      notifySuccess('Version mới hợp lệ');
    } catch {
      notifyWarning('Version mới chưa hợp lệ. Vui lòng kiểm tra rules.');
    }
  };

  const handleCreateVersion = async () => {
    try {
      const request = createEvaluatorVersionRequestSchema.parse(
        buildCreateVersionRequest(draftRules),
      );
      await validateMutation.mutateAsync(buildValidateEvaluatorRequest(draftRules));
      const version = await createVersionMutation.mutateAsync(request);
      notifySuccess('Đã tạo Evaluator version mới');
      await navigateToVersion(version.id);
    } catch {
      notifyError('Không thể tạo Evaluator version');
    }
  };

  return (
    <div className="mx-auto w-full max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <div className="mb-6 flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
        <div>
          <p className="text-sm text-muted-foreground">{project.name}</p>
          <h1 className="text-2xl font-semibold tracking-tight">{evaluatorQuery.data.name}</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Rule-based Evaluator · existing versions are read-only.
          </p>
        </div>
        <Button asChild variant="outline">
          <Link to={`/workspaces/${workspace.id}/projects/${project.id}/evaluators`}>Quay lại</Link>
        </Button>
      </div>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_360px]">
        <main className="space-y-6">
          <section className="rounded-lg border bg-surface p-5">
            <div className="mb-4 flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
              <div>
                <h2 className="text-lg font-semibold">
                  {selectedVersion ? `Version ${selectedVersion.version}` : 'Version'}
                </h2>
                <p className="text-sm text-muted-foreground">
                  Version đã tạo là immutable và chỉ được xem.
                </p>
              </div>
              <span className="rounded-full bg-muted px-2 py-1 text-xs text-muted-foreground">
                Read-only
              </span>
            </div>
            {selectedVersion ? <RuleReadOnlyTable version={selectedVersion} /> : null}
          </section>

          <PermissionGuard capability="EDIT" workspaceRole={workspace.currentUserRole}>
            {(canEdit) => (
              <section className="rounded-lg border bg-surface p-5">
                <div className="mb-4">
                  <h2 className="text-lg font-semibold">Tạo version mới</h2>
                  <p className="text-sm text-muted-foreground">
                    Chỉnh rules bên dưới sẽ tạo EvaluatorVersion mới, không sửa version hiện tại.
                  </p>
                </div>
                <EvaluatorRuleBuilder
                  rules={draftRules}
                  fieldSources={fieldSources}
                  operators={operatorsQuery.data ?? []}
                  disabled={
                    !canEdit ||
                    isPending ||
                    fieldSourcesQuery.isLoading ||
                    operatorsQuery.isLoading ||
                    fieldSources.length === 0
                  }
                  onChange={setDraftRules}
                />
                <div className="mt-4 flex justify-end gap-2">
                  <Button
                    type="button"
                    variant="outline"
                    disabled={!canEdit || isPending}
                    onClick={() => void handleValidate()}
                  >
                    Validate
                  </Button>
                  <Button
                    type="button"
                    disabled={!canEdit || isPending}
                    onClick={() => void handleCreateVersion()}
                  >
                    Tạo version mới
                  </Button>
                </div>
              </section>
            )}
          </PermissionGuard>
        </main>

        <aside className="space-y-6">
          <PermissionGuard capability="EDIT" workspaceRole={workspace.currentUserRole}>
            {(canEdit) => (
              <section className="rounded-lg border bg-surface p-5">
                <h2 className="text-sm font-semibold">Metadata</h2>
                <div className="mt-4 space-y-2">
                  <Label htmlFor="evaluator-name">Tên Evaluator</Label>
                  <Input
                    id="evaluator-name"
                    value={name}
                    disabled={!canEdit || isPending}
                    onChange={(event) => setName(event.target.value)}
                  />
                </div>
                <Button
                  type="button"
                  className="mt-4 w-full"
                  variant="outline"
                  disabled={!canEdit || isPending}
                  onClick={() => void handleRename()}
                >
                  Lưu metadata
                </Button>
              </section>
            )}
          </PermissionGuard>

          <section className="rounded-lg border bg-surface p-5">
            <h2 className="text-sm font-semibold">Versions</h2>
            <div className="mt-4 space-y-2">
              {(versionsQuery.data ?? []).map((version) => {
                const selected = version.id === selectedVersion?.id;
                return (
                  <Link
                    key={version.id}
                    to={`/workspaces/${workspace.id}/projects/${project.id}/evaluators/${evaluatorId}/versions/${version.id}`}
                    className={[
                      'block rounded-md border px-3 py-2 text-sm transition-colors',
                      selected ? 'border-primary bg-primary/5' : 'hover:bg-muted',
                    ].join(' ')}
                  >
                    <div className="font-medium">Version {version.version}</div>
                    <div className="mt-1 text-xs text-muted-foreground">
                      {version.rules.length} rules ·{' '}
                      {new Date(version.createdAt).toLocaleString('vi-VN')}
                    </div>
                  </Link>
                );
              })}
            </div>
          </section>
        </aside>
      </div>
    </div>
  );
}

function RuleReadOnlyTable({ version }: { version: EvaluatorVersionResponse }) {
  return (
    <div className="overflow-x-auto rounded-md border">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="w-[70px]">#</TableHead>
            <TableHead>Rule summary</TableHead>
            <TableHead>Operator</TableHead>
            <TableHead>Required</TableHead>
            <TableHead>Weight</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {version.rules
            .slice()
            .sort((left, right) => left.orderIndex - right.orderIndex)
            .map((rule) => (
              <TableRow key={rule.id}>
                <TableCell className="text-muted-foreground">{rule.orderIndex}</TableCell>
                <TableCell>{ruleSummary(rule)}</TableCell>
                <TableCell className="text-sm text-muted-foreground">{rule.operator}</TableCell>
                <TableCell className="text-sm text-muted-foreground">
                  {rule.required ? 'Bắt buộc' : 'Tùy chọn'}
                </TableCell>
                <TableCell className="text-sm text-muted-foreground">{rule.weight ?? 1}</TableCell>
              </TableRow>
            ))}
        </TableBody>
      </Table>
    </div>
  );
}
