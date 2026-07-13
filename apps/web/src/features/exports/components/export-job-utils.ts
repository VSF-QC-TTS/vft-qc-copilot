import type {
  ExportJobResponse,
  ExportJobStatus,
  ExportResultMode,
} from '@/features/exports/types/export-job';

export const EXPORT_JOB_POLL_INTERVAL_MS = 2000;

export function isTerminalExportJobStatus(status: ExportJobStatus): boolean {
  switch (status) {
    case 'COMPLETED':
    case 'FAILED':
    case 'CANCELLED':
    case 'EXPIRED':
      return true;
    case 'PENDING':
    case 'RUNNING':
      return false;
    default: {
      const exhaustive: never = status;
      return exhaustive;
    }
  }
}

export function exportJobRefetchInterval(job: ExportJobResponse | undefined): false | number {
  if (!job || isTerminalExportJobStatus(job.status)) {
    return false;
  }
  return EXPORT_JOB_POLL_INTERVAL_MS;
}

export function exportJobStatusLabel(status: ExportJobStatus): string {
  switch (status) {
    case 'PENDING':
      return 'Đang chờ';
    case 'RUNNING':
      return 'Đang xử lý';
    case 'COMPLETED':
      return 'Hoàn tất';
    case 'FAILED':
      return 'Thất bại';
    case 'CANCELLED':
      return 'Đã hủy';
    case 'EXPIRED':
      return 'Đã hết hạn';
    default: {
      const exhaustive: never = status;
      return exhaustive;
    }
  }
}

export function exportJobStatusTone(
  status: ExportJobStatus,
): 'success' | 'danger' | 'warning' | 'neutral' | 'info' {
  switch (status) {
    case 'COMPLETED':
      return 'success';
    case 'FAILED':
      return 'danger';
    case 'CANCELLED':
    case 'EXPIRED':
      return 'warning';
    case 'RUNNING':
      return 'info';
    case 'PENDING':
      return 'neutral';
    default: {
      const exhaustive: never = status;
      return exhaustive;
    }
  }
}

export function exportJobTerminalMessage(job: ExportJobResponse): string | undefined {
  switch (job.status) {
    case 'COMPLETED':
      return 'File export đã sẵn sàng để tải xuống.';
    case 'FAILED':
      return job.errorMessage || 'Export thất bại. Vui lòng tạo job mới.';
    case 'CANCELLED':
      return 'Export đã bị hủy. Vui lòng tạo job mới nếu vẫn cần file.';
    case 'EXPIRED':
      return 'File export đã hết hạn. Vui lòng tạo job mới.';
    case 'PENDING':
    case 'RUNNING':
      return undefined;
    default: {
      const exhaustive: never = job.status;
      return exhaustive;
    }
  }
}

export function exportJobFileName(job: ExportJobResponse | undefined, fallbackId: string): string {
  return job?.fileName?.trim() || `export-${fallbackId}.xlsx`;
}

export function exportResultModeLabel(mode: ExportResultMode): string {
  return mode;
}

export function fileSizeText(value: number | null | undefined): string {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    return '—';
  }
  if (value < 1024) {
    return `${String(value)} B`;
  }
  if (value < 1024 * 1024) {
    return `${(value / 1024).toFixed(1)} KB`;
  }
  return `${(value / 1024 / 1024).toFixed(1)} MB`;
}
