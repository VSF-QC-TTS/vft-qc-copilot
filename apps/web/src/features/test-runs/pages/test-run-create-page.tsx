import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useOutletContext } from 'react-router';
import { AlertTriangle, CheckCircle2, Info } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import { Label } from '@/shared/components/ui/label';
import { Skeleton } from '@/shared/components/ui/skeleton';
import { PermissionGuard } from '@/shared/components/permission-guard';
import { notifyError, notifySuccess, notifyWarning } from '@/shared/lib/notifications';
import { useProjectChatbotTargets } from '@/features/chatbot-targets/hooks/use-chatbot-targets';
import { useProjectDatasets } from '@/features/datasets/hooks/use-datasets';
import { useProjectEvaluators } from '@/features/evaluators/hooks/use-evaluators';
import { useWorkspaceProviders } from '@/features/providers/hooks/use-providers';
import {
  activeRunDatasets,
  activeRunProviders,
  activeRunTargets,
  buildCreateTestRunRequest,
  buildRunPreflightRequest,
  validateRunSelection,
  type BaselineDraft,
  type TestRunSelection,
} from '@/features/test-runs/components/test-run-form-utils';
import {
  createTestRunRequestSchema,
  runPreflightRequestSchema,
  testRunTypeSchema,
  type RunPreflightResponse,
} from '@/features/test-runs/types/test-run';
import { useCreateTestRun, usePreflightTestRun } from '@/features/test-runs/hooks/use-test-runs';
import type { WorkspaceContextType } from '@/app/layouts/workspace-context-layout';
import type { ProjectResponse } from '@/features/projects/types/project';

interface ProjectContextType extends WorkspaceContextType {
  project: ProjectResponse;
}

export function TestRunCreatePage() {
  const { workspace, project } = useOutletContext<ProjectContextType>();
  const navigate = useNavigate();
  const datasetsQuery = useProjectDatasets(project.id);
  const targetsQuery = useProjectChatbotTargets(project.id);
  const evaluatorsQuery = useProjectEvaluators(project.id);
  const providersQuery = useWorkspaceProviders(workspace.id);
  const preflightMutation = usePreflightTestRun(project.id);
  const createMutation = useCreateTestRun(project.id);
  const [selection, setSelection] = useState<TestRunSelection>({
    datasetId: '',
    runType: 'SINGLE_TARGET',
    chatbotTargetIds: [],
    baselines: [],
    evaluatorVersionIds: [],
    maxRows: '',
  });
  const [preflightResult, setPreflightResult] = useState<RunPreflightResponse | null>(null);
  const [preflightKey, setPreflightKey] = useState('');

  const datasets = useMemo(() => activeRunDatasets(datasetsQuery.data ?? []), [datasetsQuery.data]);
  const targets = useMemo(() => activeRunTargets(targetsQuery.data ?? []), [targetsQuery.data]);
  const providers = useMemo(
    () => activeRunProviders(providersQuery.data ?? []),
    [providersQuery.data],
  );
  const evaluators = useMemo(() => evaluatorsQuery.data ?? [], [evaluatorsQuery.data]);
  const selectionKey = JSON.stringify(selection);
  const selectionErrors = validateRunSelection(
    datasetsQuery.data ?? [],
    targets,
    providers,
    evaluators,
    selection,
  );
  const canUsePreflight = selectionErrors.length === 0;
  const canCreate =
    canUsePreflight &&
    preflightResult?.runnable === true &&
    preflightKey === selectionKey &&
    !createMutation.isPending;
  const isLoading =
    datasetsQuery.isLoading ||
    targetsQuery.isLoading ||
    evaluatorsQuery.isLoading ||
    providersQuery.isLoading;
  const isPending = preflightMutation.isPending || createMutation.isPending;

  useEffect(() => {
    const firstDataset = datasets[0];
    if (!selection.datasetId && firstDataset) {
      setSelection((current) => ({ ...current, datasetId: firstDataset.id }));
    }
  }, [datasets, selection.datasetId]);

  useEffect(() => {
    const firstTarget = targets[0];
    if (selection.chatbotTargetIds.length === 0 && firstTarget) {
      setSelection((current) => ({ ...current, chatbotTargetIds: [firstTarget.id] }));
    }
  }, [targets, selection.chatbotTargetIds.length]);

  useEffect(() => {
    if (selection.evaluatorVersionIds.length === 0) {
      const firstVersionId = evaluators.find((evaluator) => evaluator.latestVersion)?.latestVersion
        ?.id;
      if (firstVersionId) {
        setSelection((current) => ({ ...current, evaluatorVersionIds: [firstVersionId] }));
      }
    }
  }, [evaluators, selection.evaluatorVersionIds.length]);

  useEffect(() => {
    setSelection((current) => ({
      ...current,
      baselines: providers.map((provider) => {
        const existing = current.baselines.find((baseline) => baseline.providerId === provider.id);
        return (
          existing ?? {
            providerId: provider.id,
            model: provider.defaultModel,
            enabled: false,
          }
        );
      }),
    }));
  }, [providers]);

  const patchSelection = (patch: Partial<TestRunSelection>) => {
    setSelection((current) => ({ ...current, ...patch }));
    setPreflightResult(null);
    setPreflightKey('');
  };

  const handlePreflight = async () => {
    try {
      const request = runPreflightRequestSchema.parse(
        buildRunPreflightRequest(targets, providers, evaluators, selection),
      );
      const result = await preflightMutation.mutateAsync(request);
      setPreflightResult(result);
      setPreflightKey(selectionKey);
      if (result.runnable) {
        notifySuccess('Preflight hợp lệ');
      } else {
        notifyWarning('Preflight có blocker. Vui lòng kiểm tra lại.');
      }
    } catch {
      notifyError('Không thể chạy preflight');
    }
  };

  const handleCreate = async () => {
    try {
      const request = createTestRunRequestSchema.parse(
        buildCreateTestRunRequest(targets, providers, evaluators, selection),
      );
      const testRun = await createMutation.mutateAsync(request);
      notifySuccess('Đã tạo TestRun');
      await navigate(`/workspaces/${workspace.id}/projects/${project.id}/test-runs/${testRun.id}`);
    } catch {
      notifyError('Không thể tạo TestRun');
    }
  };

  if (isLoading) {
    return (
      <div className="mx-auto w-full max-w-6xl px-4 py-8 sm:px-6 lg:px-8">
        <Skeleton className="h-10 w-1/3" />
        <Skeleton className="mt-6 h-[520px] w-full" />
      </div>
    );
  }

  return (
    <div className="mx-auto w-full max-w-6xl px-4 py-8 sm:px-6 lg:px-8">
      <div className="mb-6 flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
        <div>
          <p className="text-sm text-muted-foreground">{project.name}</p>
          <h1 className="text-2xl font-semibold tracking-tight">Tạo TestRun</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Chọn dataset, targets và evaluator versions trước khi chạy preflight.
          </p>
        </div>
        <Button asChild variant="outline">
          <Link to={`/workspaces/${workspace.id}/projects/${project.id}`}>Quay lại</Link>
        </Button>
      </div>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_360px]">
        <main className="space-y-6">
          <section className="rounded-lg border bg-surface p-5">
            <h2 className="text-lg font-semibold">1. Dataset và run type</h2>
            <div className="mt-4 grid gap-4 md:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="test-run-dataset">Dataset active</Label>
                <select
                  id="test-run-dataset"
                  className="h-9 w-full rounded-md border border-input bg-background px-3 text-sm"
                  value={selection.datasetId}
                  disabled={isPending}
                  onChange={(event) => patchSelection({ datasetId: event.target.value })}
                >
                  <option value="">Chọn Dataset</option>
                  {datasets.map((dataset) => (
                    <option key={dataset.id} value={dataset.id}>
                      {dataset.name}
                    </option>
                  ))}
                </select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="test-run-type">Run type</Label>
                <select
                  id="test-run-type"
                  className="h-9 w-full rounded-md border border-input bg-background px-3 text-sm"
                  value={selection.runType}
                  disabled={isPending}
                  onChange={(event) =>
                    patchSelection({ runType: testRunTypeSchema.parse(event.target.value) })
                  }
                >
                  <option value="SINGLE_TARGET">Single run</option>
                  <option value="COMPARISON">Comparison run</option>
                </select>
              </div>
            </div>
            <div className="mt-4 max-w-xs space-y-2">
              <Label htmlFor="test-run-max-rows">Max rows</Label>
              <Input
                id="test-run-max-rows"
                type="number"
                min={1}
                max={10000}
                value={selection.maxRows}
                disabled={isPending}
                placeholder="Để trống để chạy toàn bộ"
                onChange={(event) => patchSelection({ maxRows: event.target.value })}
              />
            </div>
          </section>

          <section className="rounded-lg border bg-surface p-5">
            <h2 className="text-lg font-semibold">2. Targets</h2>
            <p className="mt-1 text-sm text-muted-foreground">
              Single run cần đúng 1 target. Comparison run cần ít nhất 2 target.
            </p>
            <div className="mt-4 space-y-3">
              {targets.map((target) => (
                <div
                  key={target.id}
                  className="flex items-center justify-between gap-4 rounded-md border p-3 text-sm"
                >
                  <span>
                    <span className="font-medium">{target.name}</span>
                    <span className="ml-2 text-muted-foreground">Chatbot target</span>
                  </span>
                  <input
                    aria-label={`Chọn target ${target.name}`}
                    type="checkbox"
                    checked={selection.chatbotTargetIds.includes(target.id)}
                    disabled={isPending}
                    onChange={(event) => {
                      const next = event.target.checked
                        ? [...selection.chatbotTargetIds, target.id]
                        : selection.chatbotTargetIds.filter((targetId) => targetId !== target.id);
                      patchSelection({ chatbotTargetIds: next });
                    }}
                  />
                </div>
              ))}
              {targets.length === 0 ? (
                <p className="rounded-md border bg-muted p-3 text-sm text-muted-foreground">
                  Chưa có chatbot target ACTIVE.
                </p>
              ) : null}
            </div>

            {providers.length > 0 ? (
              <div className="mt-5 space-y-3">
                <h3 className="text-sm font-semibold">LLM baseline</h3>
                {selection.baselines.map((baseline) => {
                  const provider = providers.find((item) => item.id === baseline.providerId);
                  if (!provider) return null;
                  return (
                    <div key={provider.id} className="rounded-md border p-3">
                      <div className="flex items-center justify-between gap-4">
                        <div className="text-sm font-medium">
                          <input
                            aria-label={`Chọn baseline ${provider.name}`}
                            className="mr-2"
                            type="checkbox"
                            checked={baseline.enabled}
                            disabled={isPending}
                            onChange={(event) =>
                              updateBaseline(selection, patchSelection, provider.id, {
                                enabled: event.target.checked,
                              })
                            }
                          />
                          {provider.name}
                        </div>
                        <span className="text-xs text-muted-foreground">Provider ACTIVE</span>
                      </div>
                      <Input
                        className="mt-3"
                        value={baseline.model}
                        disabled={isPending || !baseline.enabled}
                        placeholder={provider.defaultModel}
                        onChange={(event) =>
                          updateBaseline(selection, patchSelection, provider.id, {
                            model: event.target.value,
                          })
                        }
                      />
                    </div>
                  );
                })}
              </div>
            ) : null}
          </section>

          <section className="rounded-lg border bg-surface p-5">
            <h2 className="text-lg font-semibold">3. Evaluators</h2>
            <div className="mt-4 space-y-3">
              {evaluators.map((evaluator) => {
                const versionId = evaluator.latestVersion?.id;
                const disabled = isPending || !versionId;
                return (
                  <div
                    key={evaluator.id}
                    className="flex items-center justify-between gap-4 rounded-md border p-3 text-sm"
                  >
                    <span>
                      <span className="font-medium">{evaluator.name}</span>
                      <span className="ml-2 text-muted-foreground">
                        {evaluator.type} · version {evaluator.latestVersion?.version ?? 'N/A'}
                      </span>
                    </span>
                    <input
                      aria-label={`Chọn evaluator ${evaluator.name}`}
                      type="checkbox"
                      checked={Boolean(
                        versionId && selection.evaluatorVersionIds.includes(versionId),
                      )}
                      disabled={disabled}
                      onChange={(event) => {
                        if (!versionId) return;
                        const next = event.target.checked
                          ? [...selection.evaluatorVersionIds, versionId]
                          : selection.evaluatorVersionIds.filter((id) => id !== versionId);
                        patchSelection({ evaluatorVersionIds: next });
                      }}
                    />
                  </div>
                );
              })}
              {evaluators.length === 0 ? (
                <p className="rounded-md border bg-muted p-3 text-sm text-muted-foreground">
                  Chưa có Evaluator.
                </p>
              ) : null}
            </div>
          </section>
        </main>

        <aside className="space-y-6">
          <section className="rounded-lg border bg-surface p-5">
            <h2 className="text-sm font-semibold">Preflight</h2>
            {selectionErrors.length > 0 ? (
              <ul className="mt-4 space-y-2 text-sm text-destructive">
                {selectionErrors.map((error) => (
                  <li key={error}>{error}</li>
                ))}
              </ul>
            ) : (
              <p className="mt-4 text-sm text-muted-foreground">
                Selection hợp lệ để gọi preflight.
              </p>
            )}
            <Button
              type="button"
              className="mt-4 w-full"
              variant="outline"
              disabled={!canUsePreflight || isPending}
              onClick={() => void handlePreflight()}
            >
              Chạy preflight
            </Button>
          </section>

          {preflightResult ? <PreflightResult result={preflightResult} /> : null}

          <PermissionGuard
            capability="EDIT"
            workspaceRole={workspace.currentUserRole}
            fallback={
              <section className="rounded-lg border bg-muted p-5 text-sm text-muted-foreground">
                VIEWER có thể chạy preflight nhưng không thể tạo TestRun.
              </section>
            }
          >
            <section className="rounded-lg border bg-surface p-5">
              <h2 className="text-sm font-semibold">Tạo TestRun</h2>
              <p className="mt-2 text-sm text-muted-foreground">
                Backend sẽ khóa dataset version, targets và evaluator versions, rồi đưa run vào
                trạng thái QUEUED.
              </p>
              <Button
                type="button"
                className="mt-4 w-full"
                disabled={!canCreate}
                onClick={() => void handleCreate()}
              >
                Tạo và mở TestRun
              </Button>
            </section>
          </PermissionGuard>
        </aside>
      </div>
    </div>
  );
}

function PreflightResult({ result }: { result: RunPreflightResponse }) {
  return (
    <section className="rounded-lg border bg-surface p-5">
      <div className="flex items-center gap-2">
        {result.runnable ? (
          <CheckCircle2 className="h-5 w-5 text-success" aria-hidden="true" />
        ) : (
          <AlertTriangle className="h-5 w-5 text-warning" aria-hidden="true" />
        )}
        <h2 className="text-sm font-semibold">{result.runnable ? 'Có thể chạy' : 'Có blocker'}</h2>
      </div>
      <dl className="mt-4 grid grid-cols-3 gap-3 text-sm">
        <div>
          <dt className="text-muted-foreground">Rows</dt>
          <dd className="font-medium">{result.counts.rows}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Targets</dt>
          <dd className="font-medium">{result.counts.targets}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Evaluators</dt>
          <dd className="font-medium">{result.counts.evaluators}</dd>
        </div>
      </dl>
      {result.blockers.length > 0 ? (
        <MessageList title="Blockers" messages={result.blockers} tone="warning" />
      ) : null}
      {result.warnings.length > 0 ? (
        <MessageList title="Warnings" messages={result.warnings} tone="info" />
      ) : null}
    </section>
  );
}

function MessageList({
  title,
  messages,
  tone,
}: {
  title: string;
  messages: RunPreflightResponse['blockers'];
  tone: 'warning' | 'info';
}) {
  const Icon = tone === 'warning' ? AlertTriangle : Info;
  return (
    <div className="mt-4 rounded-md border bg-muted p-3">
      <div className="flex items-center gap-2 text-sm font-medium">
        <Icon className="h-4 w-4" aria-hidden="true" />
        {title}
      </div>
      <ul className="mt-2 space-y-2 text-sm text-muted-foreground">
        {messages.map((message) => (
          <li key={`${message.code}-${message.resourceId ?? 'none'}`}>
            {message.message}
            <span className="ml-2 font-mono text-xs">{message.code}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

function updateBaseline(
  selection: TestRunSelection,
  patchSelection: (patch: Partial<TestRunSelection>) => void,
  providerId: string,
  patch: Partial<BaselineDraft>,
) {
  patchSelection({
    baselines: selection.baselines.map((baseline) =>
      baseline.providerId === providerId ? { ...baseline, ...patch } : baseline,
    ),
  });
}
