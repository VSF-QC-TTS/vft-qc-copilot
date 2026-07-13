import { Link, useOutletContext } from 'react-router';
import { AlertTriangle, Archive, CheckCircle2, Circle, PlayCircle, Workflow } from 'lucide-react';
import { WorkspaceContextType } from '@/app/layouts/workspace-context-layout';
import { useProjectSetupStatus } from '@/features/projects/hooks/use-projects';
import type { ProjectResponse } from '@/features/projects/types/project';
import type { ProjectSetupStatusResponse } from '@/features/projects/types/project-setup-status';
import { Skeleton } from '@/shared/components/ui/skeleton';
import { cn } from '@/shared/lib/utils';

interface ProjectContextType extends WorkspaceContextType {
  project: ProjectResponse;
}

const setupStepLabels: {
  key: keyof ProjectSetupStatusResponse['steps'];
  label: string;
  description: string;
  countKey?: keyof ProjectSetupStatusResponse['counts'];
}[] = [
  {
    key: 'hasDataset',
    label: 'Dataset',
    description: 'Có dữ liệu kiểm thử trong project.',
    countKey: 'datasets',
  },
  {
    key: 'hasActiveDatasetVersion',
    label: 'Active dataset version',
    description: 'Có version dataset đã publish để chạy test.',
    countKey: 'activeDatasetVersions',
  },
  {
    key: 'hasActiveChatbotTarget',
    label: 'Chatbot target',
    description: 'Có target đang hoạt động để gửi request.',
    countKey: 'activeChatbotTargets',
  },
  {
    key: 'hasResponseExtraction',
    label: 'Response extraction',
    description: 'Target có cấu hình trích xuất response.',
    countKey: 'activeChatbotTargetsWithExtraction',
  },
  {
    key: 'hasEvaluator',
    label: 'Evaluator',
    description: 'Có evaluator đang hoạt động để chấm kết quả.',
    countKey: 'activeEvaluators',
  },
  {
    key: 'hasUsableJudgeProvider',
    label: 'Judge provider',
    description: 'Có LLM provider dùng được cho evaluator.',
    countKey: 'usableJudgeProviders',
  },
];

export function ProjectOverviewPage() {
  const { workspace, project } = useOutletContext<ProjectContextType>();
  const {
    data: setupStatus,
    isLoading: isSetupLoading,
    error: setupError,
  } = useProjectSetupStatus(project.id);

  const projectIsArchived = Boolean(project.archivedAt);
  const setupContextMismatch =
    setupStatus &&
    (setupStatus.projectId !== project.id || setupStatus.workspaceId !== project.workspaceId);
  const canRunAnything =
    Boolean(setupStatus?.canRunSingle) || Boolean(setupStatus?.canRunComparison);

  return (
    <div className="mx-auto w-full max-w-6xl px-4 py-8 sm:px-6 lg:px-8">
      <div className="mb-8 flex flex-col gap-4 border-b border-border pb-6 lg:flex-row lg:items-end lg:justify-between">
        <div className="min-w-0 space-y-3">
          <div className="flex flex-wrap items-center gap-2">
            <span className="inline-flex items-center rounded-full bg-accent-soft px-2.5 py-0.5 text-xs font-medium uppercase tracking-wide text-accent">
              Project
            </span>
            <span
              className={cn(
                'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium uppercase tracking-wide',
                projectIsArchived ? 'bg-warning-soft text-warning' : 'bg-success-soft text-success',
              )}
            >
              {projectIsArchived ? 'Đã lưu trữ' : 'Đang mở'}
            </span>
            {setupStatus && !setupContextMismatch && (
              <span
                className={cn(
                  'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium uppercase tracking-wide',
                  canRunAnything ? 'bg-success-soft text-success' : 'bg-warning-soft text-warning',
                )}
              >
                {canRunAnything ? 'Sẵn sàng chạy' : 'Chưa sẵn sàng chạy'}
              </span>
            )}
          </div>
          <div>
            <p className="text-sm text-foreground-muted">{workspace.name}</p>
            <h1 className="truncate text-3xl font-semibold tracking-tight text-foreground">
              {project.name}
            </h1>
          </div>
          <div className="flex flex-wrap gap-2">
            <Link
              to={`/workspaces/${workspace.id}/projects/${project.id}/datasets`}
              className="inline-flex h-9 items-center rounded-md border border-border px-3 text-sm font-medium text-foreground transition-colors hover:bg-surface-hover"
            >
              Datasets
            </Link>
            <Link
              to={`/workspaces/${workspace.id}/projects/${project.id}/targets`}
              className="inline-flex h-9 items-center rounded-md border border-border px-3 text-sm font-medium text-foreground transition-colors hover:bg-surface-hover"
            >
              Chatbot targets
            </Link>
            <Link
              to={`/workspaces/${workspace.id}/projects/${project.id}/evaluators`}
              className="inline-flex h-9 items-center rounded-md border border-border px-3 text-sm font-medium text-foreground transition-colors hover:bg-surface-hover"
            >
              Evaluators
            </Link>
            <Link
              to={`/workspaces/${workspace.id}/projects/${project.id}/test-runs/new`}
              className="inline-flex h-9 items-center rounded-md border border-border px-3 text-sm font-medium text-foreground transition-colors hover:bg-surface-hover"
            >
              Tạo TestRun
            </Link>
          </div>
          {project.description && (
            <p className="max-w-3xl text-sm leading-6 text-foreground-secondary">
              {project.description}
            </p>
          )}
        </div>
        <dl className="grid grid-cols-2 gap-4 text-sm lg:min-w-[280px]">
          <div>
            <dt className="text-foreground-muted">Workspace</dt>
            <dd className="mt-1 truncate font-medium text-foreground">{workspace.name}</dd>
          </div>
          <div>
            <dt className="text-foreground-muted">Ngày tạo</dt>
            <dd className="mt-1 font-medium text-foreground">
              {new Date(project.createdAt).toLocaleDateString('vi-VN')}
            </dd>
          </div>
        </dl>
      </div>

      {isSetupLoading ? (
        <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_320px]">
          <Skeleton className="h-[420px] rounded-lg" />
          <Skeleton className="h-[320px] rounded-lg" />
        </div>
      ) : setupError || setupContextMismatch ? (
        <div className="rounded-lg border border-danger-soft bg-danger-soft p-6 text-danger">
          Không thể tải trạng thái thiết lập project. Vui lòng thử lại hoặc kiểm tra quyền truy cập.
        </div>
      ) : setupStatus ? (
        <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_320px]">
          <section className="rounded-lg border border-border bg-surface p-6">
            <div className="mb-6 flex items-center justify-between gap-4">
              <div>
                <h2 className="text-lg font-semibold text-foreground">Thiết lập Project</h2>
                <p className="mt-1 text-sm text-foreground-secondary">
                  Các điều kiện tối thiểu để project có thể chạy test.
                </p>
              </div>
              <Workflow className="h-5 w-5 shrink-0 text-foreground-muted" aria-hidden="true" />
            </div>

            <div className="divide-y divide-border">
              {setupStepLabels.map((step) => {
                const isDone = setupStatus.steps[step.key];
                const count = step.countKey ? setupStatus.counts[step.countKey] : undefined;

                return (
                  <div key={step.key} className="flex gap-4 py-4 first:pt-0 last:pb-0">
                    <div className="pt-0.5">
                      {isDone ? (
                        <CheckCircle2 className="h-5 w-5 text-success" aria-hidden="true" />
                      ) : (
                        <Circle className="h-5 w-5 text-foreground-muted" aria-hidden="true" />
                      )}
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <h3 className="text-sm font-medium text-foreground">{step.label}</h3>
                        <span
                          className={cn(
                            'rounded-full px-2 py-0.5 text-xs font-medium',
                            isDone
                              ? 'bg-success-soft text-success'
                              : 'bg-warning-soft text-warning',
                          )}
                        >
                          {isDone ? 'Đã có' : 'Cần cấu hình'}
                        </span>
                        {typeof count === 'number' && (
                          <span className="text-xs text-foreground-muted">{count}</span>
                        )}
                      </div>
                      <p className="mt-1 text-sm text-foreground-secondary">{step.description}</p>
                    </div>
                  </div>
                );
              })}
            </div>
          </section>

          <aside className="space-y-6">
            <section className="rounded-lg border border-border bg-surface-subtle p-5">
              <div className="mb-4 flex items-center gap-2">
                <PlayCircle className="h-5 w-5 text-foreground-muted" aria-hidden="true" />
                <h2 className="text-sm font-semibold text-foreground">Khả năng chạy</h2>
              </div>
              <dl className="space-y-3 text-sm">
                <div className="flex items-center justify-between gap-4">
                  <dt className="text-foreground-secondary">Single run</dt>
                  <dd className="font-medium text-foreground">
                    {setupStatus.canRunSingle ? 'Có thể chạy' : 'Chưa sẵn sàng'}
                  </dd>
                </div>
                <div className="flex items-center justify-between gap-4">
                  <dt className="text-foreground-secondary">Comparison run</dt>
                  <dd className="font-medium text-foreground">
                    {setupStatus.canRunComparison ? 'Có thể chạy' : 'Chưa sẵn sàng'}
                  </dd>
                </div>
                <div className="flex items-center justify-between gap-4">
                  <dt className="text-foreground-secondary">Comparison candidates</dt>
                  <dd className="font-medium text-foreground">
                    {setupStatus.counts.comparisonCandidates}
                  </dd>
                </div>
              </dl>
            </section>

            <section className="rounded-lg border border-border bg-surface p-5">
              <div className="mb-4 flex items-center gap-2">
                <Archive className="h-5 w-5 text-foreground-muted" aria-hidden="true" />
                <h2 className="text-sm font-semibold text-foreground">Thông tin</h2>
              </div>
              <dl className="space-y-3 text-sm">
                <div>
                  <dt className="text-foreground-muted">Project ID</dt>
                  <dd className="mt-1 break-all font-mono text-xs text-foreground">{project.id}</dd>
                </div>
                <div>
                  <dt className="text-foreground-muted">Workspace ID</dt>
                  <dd className="mt-1 break-all font-mono text-xs text-foreground">
                    {project.workspaceId}
                  </dd>
                </div>
              </dl>
            </section>

            {setupStatus.blockers.length > 0 && (
              <section className="rounded-lg border border-warning-soft bg-warning-soft p-5">
                <div className="mb-3 flex items-center gap-2">
                  <AlertTriangle className="h-5 w-5 text-warning" aria-hidden="true" />
                  <h2 className="text-sm font-semibold text-warning">Cần xử lý</h2>
                </div>
                <ul className="space-y-3 text-sm text-warning">
                  {setupStatus.blockers.map((blocker) => (
                    <li key={blocker.code}>{blocker.message}</li>
                  ))}
                </ul>
              </section>
            )}
          </aside>
        </div>
      ) : (
        <div className="rounded-lg border border-border bg-surface-subtle p-6 text-foreground-secondary">
          Chưa có dữ liệu thiết lập project.
        </div>
      )}
    </div>
  );
}
