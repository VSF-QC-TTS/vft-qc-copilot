import { describe, expect, it } from 'vitest';

import {
  EXPORT_JOB_POLL_INTERVAL_MS,
  exportJobFileName,
  exportJobRefetchInterval,
  exportJobStatusLabel,
  exportJobTerminalMessage,
  fileSizeText,
  isTerminalExportJobStatus,
} from '@/features/exports/components/export-job-utils';
import type { ExportJobResponse, ExportJobStatus } from '@/features/exports/types/export-job';

describe('export job utils', () => {
  it('stops polling on terminal export jobs', () => {
    expect(exportJobRefetchInterval(undefined)).toBe(false);
    expect(exportJobRefetchInterval(job('PENDING'))).toBe(EXPORT_JOB_POLL_INTERVAL_MS);
    expect(exportJobRefetchInterval(job('RUNNING'))).toBe(EXPORT_JOB_POLL_INTERVAL_MS);
    expect(exportJobRefetchInterval(job('COMPLETED'))).toBe(false);
    expect(exportJobRefetchInterval(job('FAILED'))).toBe(false);
    expect(exportJobRefetchInterval(job('CANCELLED'))).toBe(false);
    expect(exportJobRefetchInterval(job('EXPIRED'))).toBe(false);
  });

  it('labels lifecycle and terminal states', () => {
    expect(isTerminalExportJobStatus('COMPLETED')).toBe(true);
    expect(isTerminalExportJobStatus('RUNNING')).toBe(false);
    expect(exportJobStatusLabel('EXPIRED')).toBe('Đã hết hạn');
    expect(exportJobTerminalMessage(job('COMPLETED'))).toContain('sẵn sàng');
    expect(exportJobTerminalMessage(job('FAILED', { errorMessage: 'Worker failed' }))).toBe(
      'Worker failed',
    );
    expect(exportJobTerminalMessage(job('CANCELLED'))).toContain('đã bị hủy');
    expect(exportJobTerminalMessage(job('EXPIRED'))).toContain('hết hạn');
  });

  it('formats safe filename fallback and file size text', () => {
    expect(exportJobFileName(job('COMPLETED', { fileName: 'results.xlsx' }), 'fallback')).toBe(
      'results.xlsx',
    );
    expect(exportJobFileName(job('COMPLETED', { fileName: '  ' }), 'fallback')).toBe(
      'export-fallback.xlsx',
    );
    expect(fileSizeText(null)).toBe('—');
    expect(fileSizeText(512)).toBe('512 B');
    expect(fileSizeText(2048)).toBe('2.0 KB');
  });
});

function job(status: ExportJobStatus, patch: Partial<ExportJobResponse> = {}): ExportJobResponse {
  return {
    id: '33333333-3333-4333-8333-333333333333',
    jobType: 'TEST_RUN_RESULTS',
    format: 'XLSX',
    status,
    datasetVersionId: null,
    testRunId: '22222222-2222-4222-8222-222222222222',
    resultMode: 'FINAL',
    fileName: null,
    fileSizeBytes: null,
    errorCode: null,
    errorMessage: null,
    createdBy: '44444444-4444-4444-8444-444444444444',
    createdAt: '2026-07-14T01:00:00+07:00',
    startedAt: null,
    finishedAt: null,
    expiresAt: null,
    ...patch,
  };
}
