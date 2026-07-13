import { Link, useOutletContext, useParams } from 'react-router';
import { Button } from '@/shared/components/ui/button';
import { Skeleton } from '@/shared/components/ui/skeleton';
import { useTestRun } from '@/features/test-runs/hooks/use-test-runs';
import type { WorkspaceContextType } from '@/app/layouts/workspace-context-layout';
import type { ProjectResponse } from '@/features/projects/types/project';

interface ProjectContextType extends WorkspaceContextType {
  project: ProjectResponse;
}

export function TestRunDetailPage() {
  const { testRunId = '' } = useParams<{ testRunId: string }>();
  const { workspace, project } = useOutletContext<ProjectContextType>();
  const testRunQuery = useTestRun(testRunId);

  if (testRunQuery.isLoading) {
    return (
      <div className="mx-auto w-full max-w-6xl px-4 py-8 sm:px-6 lg:px-8">
        <Skeleton className="h-10 w-1/3" />
        <Skeleton className="mt-6 h-[360px] w-full" />
      </div>
    );
  }

  if (testRunQuery.isError || !testRunQuery.data || testRunQuery.data.projectId !== project.id) {
    return (
      <div className="mx-auto w-full max-w-5xl px-4 py-8 text-destructive sm:px-6 lg:px-8">
        Không thể tải TestRun.
      </div>
    );
  }

  const testRun = testRunQuery.data;

  return (
    <div className="mx-auto w-full max-w-6xl px-4 py-8 sm:px-6 lg:px-8">
      <div className="mb-6 flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
        <div>
          <p className="text-sm text-muted-foreground">{project.name}</p>
          <h1 className="text-2xl font-semibold tracking-tight">TestRun</h1>
          <p className="mt-1 break-all font-mono text-xs text-muted-foreground">{testRun.id}</p>
        </div>
        <Button asChild variant="outline">
          <Link to={`/workspaces/${workspace.id}/projects/${project.id}`}>Quay lại Project</Link>
        </Button>
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <section className="rounded-lg border bg-surface p-5">
          <h2 className="text-sm font-semibold">Trạng thái</h2>
          <p className="mt-3 text-2xl font-semibold">{testRun.status}</p>
          <p className="mt-2 text-sm text-muted-foreground">{testRun.runType}</p>
        </section>
        <section className="rounded-lg border bg-surface p-5">
          <h2 className="text-sm font-semibold">Progress</h2>
          <p className="mt-3 text-2xl font-semibold">
            {testRun.progressCompleted}/{testRun.progressTotal}
          </p>
          <p className="mt-2 text-sm text-muted-foreground">REST detail tối thiểu cho Slice 14.</p>
        </section>
        <section className="rounded-lg border bg-surface p-5">
          <h2 className="text-sm font-semibold">Locked config</h2>
          <p className="mt-3 text-sm text-muted-foreground">
            {testRun.targets.length} target · {testRun.evaluators.length} evaluator
          </p>
          <p className="mt-2 break-all font-mono text-xs text-muted-foreground">
            Dataset version: {testRun.datasetVersionId}
          </p>
        </section>
      </div>
    </div>
  );
}
