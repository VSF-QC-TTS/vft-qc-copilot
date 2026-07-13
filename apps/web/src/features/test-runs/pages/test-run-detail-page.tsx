import { useState } from 'react';
import { Link, useOutletContext, useParams } from 'react-router';
import { AlertTriangle, CheckCircle2, CirclePause, Clock3, XCircle } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Skeleton } from '@/shared/components/ui/skeleton';
import { PermissionGuard } from '@/shared/components/permission-guard';
import { notifyError, notifySuccess } from '@/shared/lib/notifications';
import {
  canCancelTestRun,
  isTerminalTestRunStatus,
  metricText,
  runFailureExplanation,
  targetDisplayName,
  testRunProgressPercent,
  testRunStatusLabel,
  testRunStatusTone,
} from '@/features/test-runs/components/test-run-detail-utils';
import { useCancelTestRun, useTestRun } from '@/features/test-runs/hooks/use-test-runs';
import {
  testRunSseConnectionLabel,
  useTestRunSse,
} from '@/features/test-runs/sse/use-test-run-sse';
import type { WorkspaceContextType } from '@/app/layouts/workspace-context-layout';
import type { ProjectResponse } from '@/features/projects/types/project';
import type { TestRunDetailResponse } from '@/features/test-runs/types/test-run';

interface ProjectContextType extends WorkspaceContextType {
  project: ProjectResponse;
}

type DetailTab = 'overview' | 'targets' | 'evaluators' | 'snapshots' | 'results';

const tabs: { value: DetailTab; label: string }[] = [
  { value: 'overview', label: 'Tổng quan' },
  { value: 'targets', label: 'Targets' },
  { value: 'evaluators', label: 'Evaluators' },
  { value: 'snapshots', label: 'Snapshots' },
  { value: 'results', label: 'Machine / Final' },
];

const summaryMetricItems = [
  { key: 'totalTestcases', label: 'Tổng testcase' },
  { key: 'completedTestcases', label: 'Đã chạy' },
  { key: 'passedCount', label: 'Passed' },
  { key: 'failedCount', label: 'Failed' },
  { key: 'errorCount', label: 'Error' },
  { key: 'passRate', label: 'Pass rate' },
  { key: 'averageScore', label: 'Điểm TB' },
  { key: 'averageLatencyMs', label: 'Latency TB' },
];

export function TestRunDetailPage() {
  const { testRunId = '' } = useParams<{ testRunId: string }>();
  const { workspace, project } = useOutletContext<ProjectContextType>();
  const testRunQuery = useTestRun(testRunId);
  const cancelMutation = useCancelTestRun(project.id);
  const [activeTab, setActiveTab] = useState<DetailTab>('overview');
  const testRun = testRunQuery.data;
  const realtime = useTestRunSse({
    testRunId,
    projectId: project.id,
    enabled: Boolean(
      testRunId &&
      testRun &&
      testRun.projectId === project.id &&
      !isTerminalTestRunStatus(testRun.status),
    ),
  });

  if (testRunQuery.isLoading) {
    return (
      <div className="mx-auto w-full max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
        <Skeleton className="h-10 w-1/3" />
        <Skeleton className="mt-6 h-[520px] w-full" />
      </div>
    );
  }

  if (testRunQuery.isError || !testRun || testRun.projectId !== project.id) {
    return (
      <div className="mx-auto w-full max-w-5xl px-4 py-8 text-destructive sm:px-6 lg:px-8">
        Không thể tải TestRun.
      </div>
    );
  }

  const explanation = runFailureExplanation(testRun.status);
  const progressPercent = testRunProgressPercent(testRun);
  const canCancel = canCancelTestRun(testRun.status) && !cancelMutation.isPending;
  const realtimeClosed = isTerminalTestRunStatus(testRun.status);

  const handleCancel = async () => {
    try {
      await cancelMutation.mutateAsync({
        testRunId: testRun.id,
        reason: 'Cancelled from frontend detail page',
      });
      notifySuccess('Đã gửi yêu cầu hủy TestRun');
    } catch {
      notifyError('Không thể hủy TestRun');
    }
  };

  return (
    <div className="mx-auto w-full max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <div className="mb-6 flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
        <div>
          <p className="text-sm text-muted-foreground">{project.name}</p>
          <div className="mt-1 flex flex-wrap items-center gap-3">
            <h1 className="text-2xl font-semibold tracking-tight">TestRun</h1>
            <StatusBadge status={testRun.status} />
            <RealtimeBadge
              label={
                realtimeClosed
                  ? 'Realtime đã đóng'
                  : testRunSseConnectionLabel(realtime.connectionState, realtime.stopReason)
              }
              state={realtimeClosed ? 'closed' : realtime.connectionState}
            />
          </div>
          <p className="mt-1 break-all font-mono text-xs text-muted-foreground">
            {testRun.id}
            {realtime.lastSequenceNo > 0 ? ` · SSE #${String(realtime.lastSequenceNo)}` : ''}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button asChild variant="outline">
            <Link to={`/workspaces/${workspace.id}/projects/${project.id}`}>Quay lại Project</Link>
          </Button>
          <PermissionGuard
            capability="EDIT"
            workspaceRole={workspace.currentUserRole}
            fallback={
              <Button type="button" variant="outline" disabled>
                VIEWER chỉ đọc
              </Button>
            }
          >
            <Button
              type="button"
              variant="outline"
              disabled={!canCancel}
              onClick={() => void handleCancel()}
            >
              Hủy TestRun
            </Button>
          </PermissionGuard>
        </div>
      </div>

      {explanation ? (
        <div className="mb-6 rounded-lg border border-warning-soft bg-warning-soft p-4 text-sm text-warning">
          {explanation}
        </div>
      ) : null}

      <div className="mb-6 grid gap-4 md:grid-cols-4">
        <SummaryCard label="Run type" value={runTypeLabel(testRun.runType)} />
        <SummaryCard label="Progress" value={`${String(progressPercent)}%`} />
        <SummaryCard label="Targets" value={String(testRun.targets.length)} />
        <SummaryCard label="Evaluators" value={String(testRun.evaluators.length)} />
      </div>

      <div className="mb-6 rounded-lg border bg-surface p-4">
        <div className="mb-2 flex items-center justify-between gap-4 text-sm">
          <span className="font-medium">Tiến độ</span>
          <span className="text-muted-foreground">
            {testRun.progressCompleted}/{testRun.progressTotal}
          </span>
        </div>
        <div className="h-2 overflow-hidden rounded-full bg-muted">
          <div
            className="h-full rounded-full bg-primary transition-[width]"
            style={{ width: `${String(progressPercent)}%` }}
          />
        </div>
      </div>

      <div className="mb-6 flex flex-wrap gap-2 border-b">
        {tabs.map((tab) => (
          <button
            key={tab.value}
            type="button"
            className={[
              'border-b-2 px-3 py-2 text-sm transition-colors',
              activeTab === tab.value
                ? 'border-primary text-primary'
                : 'border-transparent text-muted-foreground hover:text-foreground',
            ].join(' ')}
            onClick={() => setActiveTab(tab.value)}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {activeTab === 'overview' ? <OverviewTab testRun={testRun} /> : null}
      {activeTab === 'targets' ? <TargetsTab testRun={testRun} /> : null}
      {activeTab === 'evaluators' ? <EvaluatorsTab testRun={testRun} /> : null}
      {activeTab === 'snapshots' ? <SnapshotsTab testRun={testRun} /> : null}
      {activeTab === 'results' ? <ResultsEntryTab testRun={testRun} /> : null}
    </div>
  );
}

function OverviewTab({ testRun }: { testRun: TestRunDetailResponse }) {
  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_360px]">
      <section className="rounded-lg border bg-surface p-5">
        <h2 className="text-lg font-semibold">Summary metrics</h2>
        <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {summaryMetricItems.map((item) => (
            <SummaryCard
              key={item.key}
              label={item.label}
              value={metricText(testRun.summaryMetrics, item.key)}
            />
          ))}
        </div>
      </section>

      <aside className="space-y-6">
        <section className="rounded-lg border bg-surface p-5">
          <h2 className="text-sm font-semibold">Timeline</h2>
          <dl className="mt-4 space-y-3 text-sm">
            <MetaRow label="Created" value={formatDate(testRun.createdAt)} />
            <MetaRow label="Started" value={formatDate(testRun.startedAt)} />
            <MetaRow label="Finished" value={formatDate(testRun.finishedAt)} />
            <MetaRow label="Cancel requested" value={formatDate(testRun.cancelRequestedAt)} />
            <MetaRow label="Updated" value={formatDate(testRun.updatedAt)} />
          </dl>
        </section>

        <section className="rounded-lg border bg-surface p-5">
          <h2 className="text-sm font-semibold">Locked dataset</h2>
          <dl className="mt-4 space-y-3 text-sm">
            <MetaRow label="Dataset ID" value={testRun.datasetId} mono />
            <MetaRow label="Version ID" value={testRun.datasetVersionId} mono />
            <MetaRow label="Schema ID" value={testRun.datasetSchemaId} mono />
            <MetaRow label="Schema version" value={String(testRun.datasetSchemaVersion)} />
          </dl>
        </section>
      </aside>
    </div>
  );
}

function TargetsTab({ testRun }: { testRun: TestRunDetailResponse }) {
  return (
    <section className="rounded-lg border bg-surface p-5">
      <h2 className="text-lg font-semibold">Locked targets</h2>
      <div className="mt-4 space-y-3">
        {testRun.targets.map((target) => (
          <div key={target.id} className="rounded-md border p-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <div className="font-medium">{targetDisplayName(target)}</div>
                <div className="mt-1 text-sm text-muted-foreground">
                  {target.targetType} · order {target.orderIndex}
                </div>
              </div>
              <span className="rounded-full bg-muted px-2 py-1 text-xs text-muted-foreground">
                Locked
              </span>
            </div>
            <dl className="mt-4 grid gap-3 text-sm md:grid-cols-2">
              <MetaRow
                label="Target ID"
                value={target.chatbotTargetId ?? target.providerId ?? '—'}
                mono
              />
              <MetaRow label="Run target ID" value={target.id} mono />
            </dl>
          </div>
        ))}
      </div>
    </section>
  );
}

function EvaluatorsTab({ testRun }: { testRun: TestRunDetailResponse }) {
  return (
    <section className="rounded-lg border bg-surface p-5">
      <h2 className="text-lg font-semibold">Locked evaluator versions</h2>
      <div className="mt-4 space-y-3">
        {testRun.evaluators.map((evaluator) => (
          <div key={evaluator.id} className="rounded-md border p-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <div className="font-medium">{evaluator.evaluatorType}</div>
                <div className="mt-1 text-sm text-muted-foreground">
                  Version {evaluator.version} · {evaluator.rules.length} rule · order{' '}
                  {evaluator.orderIndex}
                </div>
              </div>
              <span className="rounded-full bg-muted px-2 py-1 text-xs text-muted-foreground">
                Immutable
              </span>
            </div>
            <dl className="mt-4 grid gap-3 text-sm md:grid-cols-2">
              <MetaRow label="Evaluator ID" value={evaluator.evaluatorId} mono />
              <MetaRow label="Version ID" value={evaluator.evaluatorVersionId} mono />
            </dl>
          </div>
        ))}
      </div>
    </section>
  );
}

function SnapshotsTab({ testRun }: { testRun: TestRunDetailResponse }) {
  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <section className="rounded-lg border bg-surface p-5">
        <h2 className="text-lg font-semibold">Domain snapshot</h2>
        <p className="mt-1 break-all font-mono text-xs text-muted-foreground">
          {testRun.domainConfigHash}
        </p>
        <pre className="mt-4 max-h-[420px] overflow-auto rounded-md bg-muted p-3 text-xs">
          {JSON.stringify(testRun.domainConfigSnapshot, null, 2)}
        </pre>
      </section>
      <section className="rounded-lg border bg-surface p-5">
        <h2 className="text-lg font-semibold">Promptfoo snapshot</h2>
        <p className="mt-1 break-all font-mono text-xs text-muted-foreground">
          {testRun.promptfooConfigHash}
        </p>
        <pre className="mt-4 max-h-[420px] overflow-auto rounded-md bg-muted p-3 text-xs">
          {JSON.stringify(testRun.promptfooConfigSnapshot, null, 2)}
        </pre>
      </section>
    </div>
  );
}

function ResultsEntryTab({ testRun }: { testRun: TestRunDetailResponse }) {
  return (
    <div className="grid gap-6 md:grid-cols-2">
      <section className="rounded-lg border bg-surface p-5">
        <h2 className="text-lg font-semibold">Machine results</h2>
        <p className="mt-2 text-sm text-muted-foreground">
          Entry point cho kết quả máy chấm theo rule-based / LLM Judge. Slice kết quả sẽ nối vào
          REST results endpoint.
        </p>
        <Button type="button" className="mt-4" variant="outline" disabled>
          Mở Machine results
        </Button>
      </section>
      <section className="rounded-lg border bg-surface p-5">
        <h2 className="text-lg font-semibold">Final QC results</h2>
        <p className="mt-2 text-sm text-muted-foreground">
          Entry point cho kết quả cuối cùng sau QC review/manual override. Machine result không bị
          ghi đè.
        </p>
        <Button type="button" className="mt-4" variant="outline" disabled>
          Mở Final results
        </Button>
      </section>
      {isTerminalTestRunStatus(testRun.status) ? null : (
        <div className="rounded-lg border bg-muted p-4 text-sm text-muted-foreground md:col-span-2">
          TestRun chưa terminal; results có thể chưa đầy đủ.
        </div>
      )}
    </div>
  );
}

function StatusBadge({ status }: { status: TestRunDetailResponse['status'] }) {
  const tone = testRunStatusTone(status);
  const Icon =
    tone === 'success'
      ? CheckCircle2
      : tone === 'danger'
        ? XCircle
        : tone === 'warning'
          ? CirclePause
          : tone === 'info'
            ? Clock3
            : AlertTriangle;
  const className = [
    'inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-medium',
    tone === 'success' ? 'bg-success-soft text-success' : '',
    tone === 'danger' ? 'bg-danger-soft text-danger' : '',
    tone === 'warning' ? 'bg-warning-soft text-warning' : '',
    tone === 'info' ? 'bg-accent-soft text-accent' : '',
    tone === 'neutral' ? 'bg-muted text-muted-foreground' : '',
  ]
    .filter(Boolean)
    .join(' ');
  return (
    <span className={className}>
      <Icon className="h-3.5 w-3.5" aria-hidden="true" />
      {testRunStatusLabel(status)}
    </span>
  );
}

function RealtimeBadge({
  label,
  state,
}: {
  label: string;
  state: ReturnType<typeof useTestRunSse>['connectionState'];
}) {
  const className = [
    'inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-medium',
    state === 'open' ? 'bg-success-soft text-success' : '',
    state === 'connecting' || state === 'reconnecting' ? 'bg-accent-soft text-accent' : '',
    state === 'error' ? 'bg-warning-soft text-warning' : '',
    state === 'idle' || state === 'closed' ? 'bg-muted text-muted-foreground' : '',
  ]
    .filter(Boolean)
    .join(' ');

  return <span className={className}>{label}</span>;
}

function SummaryCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border bg-surface p-4">
      <div className="text-xs text-muted-foreground">{label}</div>
      <div className="mt-2 text-xl font-semibold">{value}</div>
    </div>
  );
}

function MetaRow({ label, value, mono = false }: { label: string; value: string; mono?: boolean }) {
  return (
    <div>
      <dt className="text-muted-foreground">{label}</dt>
      <dd className={['mt-1 break-all', mono ? 'font-mono text-xs' : 'font-medium'].join(' ')}>
        {value || '—'}
      </dd>
    </div>
  );
}

function runTypeLabel(runType: TestRunDetailResponse['runType']) {
  return runType === 'COMPARISON' ? 'Comparison' : 'Single target';
}

function formatDate(value: string | null | undefined) {
  if (!value) return '—';
  return new Date(value).toLocaleString('vi-VN');
}
