import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  createDatasetExportJob,
  createTestRunExportJob,
  downloadExportJobFile,
  getExportJob,
} from '@/features/exports/api/export-jobs.api';
import {
  exportJobResponseSchema,
  type ExportJobResponse,
} from '@/features/exports/types/export-job';

const requestJsonMock = vi.hoisted(() =>
  vi.fn<(path: string, schema: unknown, init?: RequestInit) => Promise<{ data: unknown }>>(),
);
const requestBlobMock = vi.hoisted(() =>
  vi.fn<(path: string, init?: RequestInit) => Promise<{ data: Blob }>>(),
);

vi.mock('@/shared/api/api', () => ({
  apiClient: {
    requestJson: requestJsonMock,
    requestBlob: requestBlobMock,
  },
}));

const DATASET_VERSION_ID = '11111111-1111-4111-8111-111111111111';
const TEST_RUN_ID = '22222222-2222-4222-8222-222222222222';
const JOB_ID = '33333333-3333-4333-8333-333333333333';

describe('export jobs API', () => {
  beforeEach(() => {
    requestJsonMock.mockReset();
    requestBlobMock.mockReset();
  });

  it('creates a dataset export with default XLSX format', async () => {
    const job = exportJob({ jobType: 'DATASET_VERSION', datasetVersionId: DATASET_VERSION_ID });
    requestJsonMock.mockResolvedValueOnce({ data: job });

    await expect(createDatasetExportJob(DATASET_VERSION_ID)).resolves.toEqual(job);

    expect(requestJsonMock).toHaveBeenCalledWith(
      `/api/v1/dataset-versions/${DATASET_VERSION_ID}/export-jobs`,
      expect.any(Object),
      {
        method: 'POST',
        body: JSON.stringify({ format: 'XLSX' }),
      },
    );
  });

  it('creates a TestRun export with default XLSX and FINAL mode', async () => {
    const job = exportJob({ jobType: 'TEST_RUN_RESULTS', testRunId: TEST_RUN_ID });
    requestJsonMock.mockResolvedValueOnce({ data: job });

    await expect(createTestRunExportJob(TEST_RUN_ID)).resolves.toEqual(job);

    expect(requestJsonMock).toHaveBeenCalledWith(
      `/api/v1/test-runs/${TEST_RUN_ID}/export-jobs`,
      expect.any(Object),
      {
        method: 'POST',
        body: JSON.stringify({ format: 'XLSX', resultMode: 'FINAL' }),
      },
    );
  });

  it('gets job status and downloads Blob outside React Query cache', async () => {
    const signal = new AbortController().signal;
    const job = exportJob({ status: 'COMPLETED', fileName: 'testrun.xlsx' });
    const blob = new Blob(['xlsx']);
    requestJsonMock.mockResolvedValueOnce({ data: job });
    requestBlobMock.mockResolvedValueOnce({ data: blob });

    await expect(getExportJob(JOB_ID, signal)).resolves.toEqual(job);
    await expect(downloadExportJobFile(JOB_ID, signal)).resolves.toBe(blob);

    expect(requestJsonMock).toHaveBeenCalledWith(
      `/api/v1/export-jobs/${JOB_ID}`,
      expect.any(Object),
      {
        signal,
      },
    );
    expect(requestBlobMock).toHaveBeenCalledWith(`/api/v1/export-jobs/${JOB_ID}/download`, {
      signal,
    });
  });

  it('validates terminal states and nullable source fields from backend', () => {
    expect(exportJobResponseSchema.safeParse(exportJob({ status: 'FAILED' })).success).toBe(true);
    expect(exportJobResponseSchema.safeParse(exportJob({ status: 'CANCELLED' })).success).toBe(
      true,
    );
    expect(exportJobResponseSchema.safeParse(exportJob({ status: 'EXPIRED' })).success).toBe(true);
    expect(exportJobResponseSchema.safeParse({ ...baseExportJob(), status: 'DONE' }).success).toBe(
      false,
    );
  });
});

function exportJob(patch: Partial<ExportJobResponse> = {}): ExportJobResponse {
  return { ...baseExportJob(), ...patch };
}

function baseExportJob(): ExportJobResponse {
  return {
    id: JOB_ID,
    jobType: 'TEST_RUN_RESULTS',
    format: 'XLSX',
    status: 'PENDING',
    datasetVersionId: null,
    testRunId: TEST_RUN_ID,
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
  };
}
