import { useEffect, useState } from 'react';
import { Link, useOutletContext, useParams, useSearchParams } from 'react-router';
import { AlertTriangle, CheckCircle2, CirclePause, Clock3, XCircle } from 'lucide-react';
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
import { Textarea } from '@/shared/components/ui/textarea';
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
import {
  OVERRIDE_STATUS_OPTIONS,
  REVIEW_STATUS_OPTIONS,
  parseOverrideStatusOption,
  parseReviewStatusOption,
  reviewFormError,
  toReviewRequest,
  type ReviewFormState,
} from '@/features/test-runs/components/test-case-result-review-utils';
import {
  jsonPreview,
  latencyText,
  machineFinalChanged,
  parseResultId,
  parseResultMode,
  parseResultPage,
  resultStatusLabel,
  resultStatusTone,
  reviewedText,
  scoreText,
  TEST_RUN_RESULTS_PAGE_SIZE,
} from '@/features/test-runs/components/test-run-results-utils';
import {
  useCancelTestRun,
  useReviewTestCaseResult,
  useTestCaseResult,
  useTestRun,
  useTestRunResults,
} from '@/features/test-runs/hooks/use-test-runs';
import {
  testRunSseConnectionLabel,
  useTestRunSse,
} from '@/features/test-runs/sse/use-test-run-sse';
import type { WorkspaceContextType } from '@/app/layouts/workspace-context-layout';
import type { ProjectResponse } from '@/features/projects/types/project';
import type {
  ResultMode,
  TestCaseResultDetailResponse,
  TestRunDetailResponse,
} from '@/features/test-runs/types/test-run';

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
  const [searchParams, setSearchParams] = useSearchParams();
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
  const resultMode = parseResultMode(searchParams.get('resultMode'));
  const resultPage = parseResultPage(searchParams.get('resultPage'));
  const selectedResultId = parseResultId(searchParams.get('resultId'));

  const updateResultParams = (patch: {
    mode?: ResultMode;
    page?: number;
    resultId?: number | null;
  }) => {
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      if (patch.mode) {
        next.set('resultMode', patch.mode);
      }
      if (patch.page !== undefined) {
        next.set('resultPage', String(patch.page));
      }
      if (patch.resultId === null) {
        next.delete('resultId');
      } else if (patch.resultId !== undefined) {
        next.set('resultId', String(patch.resultId));
      }
      return next;
    });
  };

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
      {activeTab === 'results' ? (
        <ResultsEntryTab
          testRun={testRun}
          mode={resultMode}
          page={resultPage}
          selectedResultId={selectedResultId}
          workspaceRole={workspace.currentUserRole}
          onModeChange={(mode) => updateResultParams({ mode, page: 0, resultId: null })}
          onPageChange={(page) => updateResultParams({ page, resultId: null })}
          onSelectResult={(resultId) => updateResultParams({ resultId })}
        />
      ) : null}
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

function ResultsEntryTab({
  testRun,
  mode,
  page,
  selectedResultId,
  workspaceRole,
  onModeChange,
  onPageChange,
  onSelectResult,
}: {
  testRun: TestRunDetailResponse;
  mode: ResultMode;
  page: number;
  selectedResultId: number | undefined;
  workspaceRole: ProjectContextType['workspace']['currentUserRole'];
  onModeChange: (mode: ResultMode) => void;
  onPageChange: (page: number) => void;
  onSelectResult: (resultId: number) => void;
}) {
  const resultsQuery = useTestRunResults(testRun.id, mode, page, TEST_RUN_RESULTS_PAGE_SIZE);
  const detailQuery = useTestCaseResult(selectedResultId, mode);
  const results = resultsQuery.data?.items ?? [];

  return (
    <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_420px]">
      <section className="rounded-lg border bg-surface p-5">
        <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
          <div>
            <h2 className="text-lg font-semibold">Kết quả TestRun</h2>
            <p className="mt-2 max-w-2xl text-sm text-muted-foreground">
              MACHINE giữ nguyên kết quả máy chấm. FINAL áp dụng QC override nếu có nhưng vẫn giữ
              cột Machine để đối chiếu.
            </p>
          </div>
          <div className="flex rounded-md border bg-background p-1 text-sm">
            {(['MACHINE', 'FINAL'] satisfies ResultMode[]).map((item) => (
              <button
                key={item}
                type="button"
                className={[
                  'rounded px-3 py-1.5 transition-colors',
                  mode === item
                    ? 'bg-foreground text-background'
                    : 'text-muted-foreground hover:text-foreground',
                ].join(' ')}
                onClick={() => onModeChange(item)}
              >
                {item}
              </button>
            ))}
          </div>
        </div>

        <div className="mt-5 rounded-lg border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Result</TableHead>
                <TableHead>Machine</TableHead>
                <TableHead>Final</TableHead>
                <TableHead>Review</TableHead>
                <TableHead>Latency</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {resultsQuery.isLoading ? (
                <ResultsSkeletonRows />
              ) : results.length > 0 ? (
                results.map((result) => (
                  <TableRow
                    key={result.id}
                    className={selectedResultId === result.id ? 'bg-muted/60' : 'cursor-pointer'}
                    onClick={() => onSelectResult(result.id)}
                  >
                    <TableCell>
                      <div className="font-medium">#{String(result.id)}</div>
                      <div className="mt-1 text-xs text-muted-foreground">
                        Row {String(result.datasetRowId)}
                      </div>
                      <div className="mt-1 max-w-[220px] truncate font-mono text-xs text-muted-foreground">
                        {result.testRunTargetId}
                      </div>
                    </TableCell>
                    <TableCell>
                      <ResultStatusBadge status={result.machineStatus} />
                      <div className="mt-1 text-xs text-muted-foreground">
                        Score {scoreText(result.machineScore)}
                      </div>
                    </TableCell>
                    <TableCell>
                      <ResultStatusBadge status={result.finalStatus} />
                      <div className="mt-1 text-xs text-muted-foreground">
                        Score {scoreText(result.finalScore)}
                      </div>
                      {machineFinalChanged(result) ? (
                        <div className="mt-1 text-xs text-warning">Khác Machine</div>
                      ) : null}
                    </TableCell>
                    <TableCell className="text-sm">{reviewedText(result)}</TableCell>
                    <TableCell className="text-sm">{latencyText(result.latencyMs)}</TableCell>
                  </TableRow>
                ))
              ) : (
                <TableRow>
                  <TableCell colSpan={5} className="py-8 text-center text-muted-foreground">
                    Chưa có result nào.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </div>

        {resultsQuery.isError ? (
          <div className="mt-4 rounded-lg border border-danger-soft bg-danger-soft p-3 text-sm text-danger">
            Không thể tải result list.
          </div>
        ) : null}

        <div className="mt-4 flex items-center justify-between gap-3">
          <div className="text-sm text-muted-foreground">
            Trang {String(page + 1)} · {String(results.length)} result
          </div>
          <div className="flex gap-2">
            <Button
              type="button"
              variant="outline"
              disabled={page === 0 || resultsQuery.isFetching}
              onClick={() => onPageChange(Math.max(0, page - 1))}
            >
              Trang trước
            </Button>
            <Button
              type="button"
              variant="outline"
              disabled={!resultsQuery.data?.page.hasMore || resultsQuery.isFetching}
              onClick={() => onPageChange(page + 1)}
            >
              Trang sau
            </Button>
          </div>
        </div>

        {isTerminalTestRunStatus(testRun.status) ? null : (
          <div className="mt-4 rounded-lg border bg-muted p-4 text-sm text-muted-foreground">
            TestRun chưa terminal; results có thể chưa đầy đủ.
          </div>
        )}
      </section>

      <ResultDetailPanel
        resultId={selectedResultId}
        result={detailQuery.data}
        isLoading={detailQuery.isLoading}
        isError={detailQuery.isError}
        mode={mode}
        workspaceRole={workspaceRole}
      />
    </div>
  );
}

function ResultsSkeletonRows() {
  return (
    <>
      {['result-skeleton-1', 'result-skeleton-2', 'result-skeleton-3', 'result-skeleton-4'].map(
        (key) => (
          <TableRow key={key}>
            <TableCell colSpan={5}>
              <Skeleton className="h-12 w-full" />
            </TableCell>
          </TableRow>
        ),
      )}
    </>
  );
}

function ResultDetailPanel({
  resultId,
  result,
  isLoading,
  isError,
  mode,
  workspaceRole,
}: {
  resultId: number | undefined;
  result: TestCaseResultDetailResponse | undefined;
  isLoading: boolean;
  isError: boolean;
  mode: ResultMode;
  workspaceRole: ProjectContextType['workspace']['currentUserRole'];
}) {
  const reviewMutation = useReviewTestCaseResult(result?.testRunId ?? '');
  const [reviewForm, setReviewForm] = useState<ReviewFormState>(emptyReviewForm());
  const formError = reviewFormError(reviewForm);

  useEffect(() => {
    setReviewForm(reviewFormFromResult(result));
  }, [result]);

  const handleSaveReview = async () => {
    if (!result || formError) {
      return;
    }

    try {
      await reviewMutation.mutateAsync({
        resultId: result.id,
        data: toReviewRequest(reviewForm),
      });
      notifySuccess('Đã lưu QC review');
    } catch {
      notifyError('Không thể lưu QC review');
    }
  };

  if (resultId === undefined) {
    return (
      <aside className="rounded-lg border bg-surface p-5 text-sm text-muted-foreground">
        Chọn một result để xem request, response, grading và token usage đã được backend sanitize.
      </aside>
    );
  }

  if (isLoading) {
    return (
      <aside className="rounded-lg border bg-surface p-5">
        <Skeleton className="h-6 w-1/2" />
        <Skeleton className="mt-4 h-[520px] w-full" />
      </aside>
    );
  }

  if (isError || !result) {
    return (
      <aside className="rounded-lg border border-danger-soft bg-danger-soft p-5 text-sm text-danger">
        Không thể tải result detail.
      </aside>
    );
  }

  return (
    <aside className="space-y-4 rounded-lg border bg-surface p-5">
      <div>
        <div className="text-xs text-muted-foreground">Result detail · {mode}</div>
        <h2 className="mt-1 text-lg font-semibold">#{String(result.id)}</h2>
        <p className="mt-1 break-all font-mono text-xs text-muted-foreground">
          Target {result.testRunTargetId}
        </p>
      </div>

      <div className="grid grid-cols-2 gap-3">
        <SummaryCard label="Machine" value={resultStatusLabel(result.machineStatus)} />
        <SummaryCard label="Final" value={resultStatusLabel(result.finalStatus)} />
        <SummaryCard label="Score máy" value={scoreText(result.machineScore)} />
        <SummaryCard label="Score final" value={scoreText(result.finalScore)} />
      </div>

      {result.machineSummary ? (
        <section className="rounded-lg border bg-background p-4">
          <h3 className="text-sm font-semibold">Machine summary</h3>
          <p className="mt-2 whitespace-pre-wrap text-sm text-muted-foreground">
            {result.machineSummary}
          </p>
        </section>
      ) : null}

      <section className="rounded-lg border bg-background p-4">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h3 className="text-sm font-semibold">QC review</h3>
            <p className="mt-1 text-sm text-muted-foreground">
              Review chỉ thay đổi FINAL; machine result vẫn được giữ nguyên.
            </p>
          </div>
          {result.review ? (
            <span className="rounded-full bg-muted px-2 py-1 text-xs text-muted-foreground">
              {reviewedText(result)}
            </span>
          ) : null}
        </div>

        <PermissionGuard capability="EDIT" workspaceRole={workspaceRole}>
          {(canReview) => (
            <div className="mt-4 space-y-4">
              <div className="grid gap-3 sm:grid-cols-2">
                <div>
                  <Label htmlFor="review-status">Trạng thái review</Label>
                  <select
                    id="review-status"
                    className="mt-2 flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                    value={reviewForm.reviewStatus}
                    disabled={!canReview || reviewMutation.isPending}
                    onChange={(event) => {
                      const reviewStatus = parseReviewStatusOption(event.target.value);
                      if (reviewStatus) {
                        setReviewForm((current) => ({ ...current, reviewStatus }));
                      }
                    }}
                  >
                    {REVIEW_STATUS_OPTIONS.map((option) => (
                      <option key={option.value} value={option.value}>
                        {option.label}
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <Label htmlFor="override-status">Override status</Label>
                  <select
                    id="override-status"
                    className="mt-2 flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                    value={reviewForm.overrideStatus}
                    disabled={!canReview || reviewMutation.isPending}
                    onChange={(event) => {
                      const overrideStatus = parseOverrideStatusOption(event.target.value);
                      if (overrideStatus) {
                        setReviewForm((current) => ({ ...current, overrideStatus }));
                      }
                    }}
                  >
                    <option value="NONE">Không override</option>
                    {OVERRIDE_STATUS_OPTIONS.map((option) => (
                      <option key={option.value} value={option.value}>
                        {option.label}
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              <div>
                <Label htmlFor="override-score">Override score</Label>
                <Input
                  id="override-score"
                  inputMode="decimal"
                  placeholder="Ví dụ: 0.95"
                  value={reviewForm.overrideScore}
                  disabled={!canReview || reviewMutation.isPending}
                  onChange={(event) =>
                    setReviewForm((current) => ({
                      ...current,
                      overrideScore: event.target.value,
                    }))
                  }
                />
              </div>

              <div>
                <Label htmlFor="review-note">Ghi chú</Label>
                <Textarea
                  id="review-note"
                  placeholder="Ghi chú lý do review hoặc override"
                  value={reviewForm.note}
                  disabled={!canReview || reviewMutation.isPending}
                  onChange={(event) =>
                    setReviewForm((current) => ({ ...current, note: event.target.value }))
                  }
                />
              </div>

              {formError ? <p className="text-sm text-danger">{formError}</p> : null}

              <div className="flex items-center justify-between gap-3">
                <p className="text-xs text-muted-foreground">
                  Backend sanitize ghi chú trước khi lưu.
                </p>
                <Button
                  type="button"
                  disabled={!canReview || reviewMutation.isPending || Boolean(formError)}
                  onClick={() => void handleSaveReview()}
                >
                  Lưu review
                </Button>
              </div>

              {!canReview ? (
                <p className="rounded-lg border bg-muted p-3 text-sm text-muted-foreground">
                  VIEWER chỉ được xem result, không được lưu QC review.
                </p>
              ) : null}
            </div>
          )}
        </PermissionGuard>
      </section>

      <JsonBlock title="Request" value={result.request} />
      <JsonBlock title="Response" value={result.response} />
      <JsonBlock title="Extracted output" value={result.extractedOutput} />
      <JsonBlock title="Grading result" value={result.gradingResult} />
      <JsonBlock title="Component results" value={result.componentResults} />
      <JsonBlock title="Token usage" value={result.tokenUsage} />
    </aside>
  );
}

function JsonBlock({ title, value }: { title: string; value: unknown }) {
  return (
    <section className="rounded-lg border bg-background p-4">
      <h3 className="text-sm font-semibold">{title}</h3>
      <pre className="mt-3 max-h-72 overflow-auto rounded-md bg-muted p-3 text-xs leading-relaxed text-muted-foreground">
        {jsonPreview(value)}
      </pre>
    </section>
  );
}

function ResultStatusBadge({ status }: { status: TestCaseResultDetailResponse['machineStatus'] }) {
  const tone = resultStatusTone(status);
  const className = [
    'inline-flex items-center rounded-full px-2.5 py-1 text-xs font-medium',
    tone === 'success' ? 'bg-success-soft text-success' : '',
    tone === 'danger' ? 'bg-danger-soft text-danger' : '',
    tone === 'warning' ? 'bg-warning-soft text-warning' : '',
  ]
    .filter(Boolean)
    .join(' ');

  return <span className={className}>{resultStatusLabel(status)}</span>;
}

function emptyReviewForm(): ReviewFormState {
  return {
    reviewStatus: 'APPROVED',
    overrideStatus: 'NONE',
    overrideScore: '',
    note: '',
  };
}

function reviewFormFromResult(result: TestCaseResultDetailResponse | undefined): ReviewFormState {
  if (!result?.review) {
    return emptyReviewForm();
  }

  return {
    reviewStatus: result.review.reviewStatus,
    overrideStatus: result.review.overrideStatus ?? 'NONE',
    overrideScore:
      typeof result.review.overrideScore === 'number' ? String(result.review.overrideScore) : '',
    note: result.review.note ?? '',
  };
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
