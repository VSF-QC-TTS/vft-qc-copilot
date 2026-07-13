import { useState } from 'react';
import { Button } from '@/shared/components/ui/button';
import { Skeleton } from '@/shared/components/ui/skeleton';
import { downloadBlob } from '@/shared/api/download';
import { notifyError, notifySuccess } from '@/shared/lib/notifications';
import {
  exportJobFileName,
  exportJobStatusLabel,
  exportJobStatusTone,
  exportJobTerminalMessage,
  exportResultModeLabel,
  fileSizeText,
  isTerminalExportJobStatus,
} from '@/features/exports/components/export-job-utils';
import {
  useCreateDatasetExportJob,
  useCreateTestRunExportJob,
  useDownloadExportJobFile,
  useExportJob,
} from '@/features/exports/hooks/use-export-jobs';
import type {
  ExportJobResponse,
  ExportJobStatus,
  ExportResultMode,
} from '@/features/exports/types/export-job';

type ExportSource =
  | {
      sourceType: 'dataset-version';
      sourceId: string | undefined;
    }
  | {
      sourceType: 'test-run';
      sourceId: string;
    };

interface ExportJobPanelProps {
  title: string;
  description: string;
  source: ExportSource;
}

export function ExportJobPanel({ title, description, source }: ExportJobPanelProps) {
  const [activeJobId, setActiveJobId] = useState<string>();
  const [resultMode, setResultMode] = useState<ExportResultMode>('FINAL');
  const datasetExportMutation = useCreateDatasetExportJob(
    source.sourceType === 'dataset-version' ? source.sourceId : undefined,
  );
  const testRunExportMutation = useCreateTestRunExportJob(
    source.sourceType === 'test-run' ? source.sourceId : '',
  );
  const jobQuery = useExportJob(activeJobId);
  const downloadMutation = useDownloadExportJobFile();
  const job = jobQuery.data;
  const isCreatePending = datasetExportMutation.isPending || testRunExportMutation.isPending;
  const sourceReady = Boolean(source.sourceId);

  const handleCreateJob = async () => {
    if (!sourceReady) {
      notifyError('Chưa có nguồn Export hợp lệ');
      return;
    }

    try {
      const createdJob =
        source.sourceType === 'dataset-version'
          ? await datasetExportMutation.mutateAsync({ format: 'XLSX' })
          : await testRunExportMutation.mutateAsync({ format: 'XLSX', resultMode });
      setActiveJobId(createdJob.id);
      notifySuccess('Đã tạo Export job');
    } catch {
      notifyError('Không thể tạo Export job');
    }
  };

  const handleDownload = async () => {
    if (!job || job.status !== 'COMPLETED') {
      return;
    }

    try {
      const blob = await downloadMutation.mutateAsync(job.id);
      downloadBlob(blob, exportJobFileName(job, job.id));
      notifySuccess('Đã tải file Export');
    } catch {
      notifyError('Không thể tải file Export');
    }
  };

  return (
    <section className="rounded-lg border bg-surface p-5">
      <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
        <div>
          <h2 className="text-sm font-semibold">{title}</h2>
          <p className="mt-1 text-sm text-muted-foreground">{description}</p>
        </div>
        <span className="rounded-full bg-muted px-2 py-1 text-xs text-muted-foreground">XLSX</span>
      </div>

      {source.sourceType === 'test-run' ? (
        <div className="mt-4">
          <div className="text-xs text-muted-foreground">Result mode</div>
          <div className="mt-2 flex rounded-md border bg-background p-1 text-sm">
            {(['FINAL', 'MACHINE'] satisfies ExportResultMode[]).map((mode) => (
              <button
                key={mode}
                type="button"
                className={[
                  'rounded px-3 py-1.5 transition-colors',
                  resultMode === mode
                    ? 'bg-foreground text-background'
                    : 'text-muted-foreground hover:text-foreground',
                ].join(' ')}
                disabled={isCreatePending}
                onClick={() => setResultMode(mode)}
              >
                {exportResultModeLabel(mode)}
              </button>
            ))}
          </div>
          <p className="mt-2 text-xs text-muted-foreground">Mặc định dùng FINAL.</p>
        </div>
      ) : null}

      <div className="mt-4 flex flex-wrap gap-2">
        <Button
          type="button"
          variant="outline"
          disabled={!sourceReady || isCreatePending}
          onClick={() => void handleCreateJob()}
        >
          Tạo Export job
        </Button>
        <Button
          type="button"
          disabled={!job || job.status !== 'COMPLETED' || downloadMutation.isPending}
          onClick={() => void handleDownload()}
        >
          Tải XLSX
        </Button>
      </div>

      <ExportJobState job={job} isLoading={jobQuery.isLoading} isError={jobQuery.isError} />
    </section>
  );
}

function ExportJobState({
  job,
  isLoading,
  isError,
}: {
  job: ExportJobResponse | undefined;
  isLoading: boolean;
  isError: boolean;
}) {
  if (isLoading) {
    return (
      <div className="mt-4">
        <Skeleton className="h-20 w-full" />
      </div>
    );
  }

  if (isError) {
    return (
      <div className="mt-4 rounded-lg border border-danger-soft bg-danger-soft p-3 text-sm text-danger">
        Không thể tải trạng thái Export job.
      </div>
    );
  }

  if (!job) {
    return (
      <div className="mt-4 rounded-lg border bg-muted p-3 text-sm text-muted-foreground">
        Chưa có Export job trong phiên này.
      </div>
    );
  }

  const message = exportJobTerminalMessage(job);

  return (
    <div className="mt-4 rounded-lg border bg-background p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="text-xs text-muted-foreground">Export job</div>
          <div className="mt-1 break-all font-mono text-xs">{job.id}</div>
        </div>
        <ExportStatusBadge status={job.status} />
      </div>
      <dl className="mt-4 grid gap-3 text-sm sm:grid-cols-2">
        <ExportMeta label="File" value={job.fileName ?? '—'} />
        <ExportMeta label="Dung lượng" value={fileSizeText(job.fileSizeBytes)} />
        <ExportMeta label="Bắt đầu" value={formatDate(job.startedAt)} />
        <ExportMeta label="Hết hạn" value={formatDate(job.expiresAt)} />
      </dl>
      {message ? (
        <p
          className={[
            'mt-4 rounded-md border p-3 text-sm',
            job.status === 'COMPLETED' ? 'bg-success-soft text-success' : '',
            job.status === 'FAILED' ? 'bg-danger-soft text-danger' : '',
            job.status === 'CANCELLED' || job.status === 'EXPIRED'
              ? 'bg-warning-soft text-warning'
              : '',
          ]
            .filter(Boolean)
            .join(' ')}
        >
          {message}
        </p>
      ) : (
        <p className="mt-4 rounded-md border bg-muted p-3 text-sm text-muted-foreground">
          Job chưa terminal; UI sẽ poll trạng thái và tự dừng khi hoàn tất.
        </p>
      )}
    </div>
  );
}

function ExportStatusBadge({ status }: { status: ExportJobStatus }) {
  const tone = exportJobStatusTone(status);
  const className = [
    'inline-flex items-center rounded-full px-2.5 py-1 text-xs font-medium',
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
      {exportJobStatusLabel(status)}
      {isTerminalExportJobStatus(status) ? '' : '…'}
    </span>
  );
}

function ExportMeta({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="mt-1 break-all font-medium">{value}</dd>
    </div>
  );
}

function formatDate(value: string | null | undefined): string {
  if (!value) {
    return '—';
  }
  return new Date(value).toLocaleString('vi-VN');
}
