import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ExportJobPanel } from '@/features/exports/components/export-job-panel';
import type {
  CreateDatasetExportRequest,
  CreateTestRunExportRequest,
  ExportJobResponse,
  ExportJobStatus,
} from '@/features/exports/types/export-job';

const createDatasetMutateAsyncMock = vi.hoisted(() =>
  vi.fn<(data?: CreateDatasetExportRequest) => Promise<ExportJobResponse>>(),
);
const createTestRunMutateAsyncMock = vi.hoisted(() =>
  vi.fn<(data?: CreateTestRunExportRequest) => Promise<ExportJobResponse>>(),
);
const downloadMutateAsyncMock = vi.hoisted(() => vi.fn<(jobId: string) => Promise<Blob>>());
const useExportJobMock = vi.hoisted(() =>
  vi.fn<
    (jobId: string | undefined) => {
      data: ExportJobResponse | undefined;
      isLoading: boolean;
      isError: boolean;
    }
  >(),
);
const useRecentExportJobsMock = vi.hoisted(() =>
  vi.fn<
    () => {
      data:
        | {
            items: ExportJobResponse[];
            page: { limit: number; nextCursor: string | null; hasMore: boolean };
          }
        | undefined;
      isLoading: boolean;
      isError: boolean;
    }
  >(),
);
const downloadBlobMock = vi.hoisted(() => vi.fn<(blob: Blob, filename: string) => void>());

vi.mock('@/features/exports/hooks/use-export-jobs', () => ({
  useCreateDatasetExportJob: () => ({
    mutateAsync: createDatasetMutateAsyncMock,
    isPending: false,
  }),
  useCreateTestRunExportJob: () => ({
    mutateAsync: createTestRunMutateAsyncMock,
    isPending: false,
  }),
  useDownloadExportJobFile: () => ({
    mutateAsync: downloadMutateAsyncMock,
    isPending: false,
  }),
  useExportJob: useExportJobMock,
  useRecentExportJobs: useRecentExportJobsMock,
}));

vi.mock('@/shared/api/download', () => ({
  downloadBlob: downloadBlobMock,
}));

vi.mock('@/shared/lib/notifications', () => ({
  notifyError: vi.fn<(message: string) => void>(),
  notifySuccess: vi.fn<(message: string) => void>(),
}));

const TEST_RUN_ID = '22222222-2222-4222-8222-222222222222';
const DATASET_VERSION_ID = '11111111-1111-4111-8111-111111111111';
const JOB_ID = '33333333-3333-4333-8333-333333333333';

describe('ExportJobPanel', () => {
  beforeEach(() => {
    createDatasetMutateAsyncMock.mockReset();
    createTestRunMutateAsyncMock.mockReset();
    downloadMutateAsyncMock.mockReset();
    downloadBlobMock.mockReset();
    useExportJobMock.mockReset();
    useExportJobMock.mockReturnValue({ data: undefined, isLoading: false, isError: false });
    useRecentExportJobsMock.mockReset();
    useRecentExportJobsMock.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: false,
    });
  });

  it('allows a read-only user flow to create a dataset export job', async () => {
    const createdJob = exportJob('PENDING', {
      jobType: 'DATASET_VERSION',
      datasetVersionId: DATASET_VERSION_ID,
      testRunId: null,
      resultMode: null,
    });
    createDatasetMutateAsyncMock.mockResolvedValueOnce(createdJob);

    render(
      <ExportJobPanel
        title="Export Dataset"
        description="Tạo file XLSX"
        source={{ sourceType: 'dataset-version', sourceId: DATASET_VERSION_ID }}
      />,
    );

    const createButton = screen.getByRole('button', { name: 'Tạo Export job' });
    expect(createButton).toBeEnabled();
    fireEvent.click(createButton);

    await waitFor(() =>
      expect(createDatasetMutateAsyncMock).toHaveBeenCalledWith({ format: 'XLSX' }),
    );
  });

  it('creates TestRun exports with FINAL as the default mode and supports MACHINE switch', async () => {
    createTestRunMutateAsyncMock.mockResolvedValue(exportJob('PENDING'));

    render(
      <ExportJobPanel
        title="Export TestRun"
        description="Tạo file XLSX"
        source={{ sourceType: 'test-run', sourceId: TEST_RUN_ID }}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Tạo Export job' }));
    await waitFor(() =>
      expect(createTestRunMutateAsyncMock).toHaveBeenLastCalledWith({
        format: 'XLSX',
        resultMode: 'FINAL',
      }),
    );

    fireEvent.click(screen.getByRole('button', { name: 'MACHINE' }));
    fireEvent.click(screen.getByRole('button', { name: 'Tạo Export job' }));
    await waitFor(() =>
      expect(createTestRunMutateAsyncMock).toHaveBeenLastCalledWith({
        format: 'XLSX',
        resultMode: 'MACHINE',
      }),
    );
  });

  it('downloads completed jobs with the backend filename', async () => {
    const blob = new Blob(['xlsx']);
    useExportJobMock.mockReturnValue({
      data: exportJob('COMPLETED', {
        fileName: 'ket-qua.xlsx',
        fileSizeBytes: 2048,
      }),
      isLoading: false,
      isError: false,
    });
    downloadMutateAsyncMock.mockResolvedValueOnce(blob);

    render(
      <ExportJobPanel
        title="Export TestRun"
        description="Tạo file XLSX"
        source={{ sourceType: 'test-run', sourceId: TEST_RUN_ID }}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Tải XLSX' }));

    await waitFor(() => expect(downloadMutateAsyncMock).toHaveBeenCalledWith(JOB_ID));
    expect(downloadBlobMock).toHaveBeenCalledWith(blob, 'ket-qua.xlsx');
    expect(screen.getByText('2.0 KB')).toBeInTheDocument();
  });

  it('resumes the latest source export job after a reload', () => {
    const recentJob = exportJob('COMPLETED', { fileName: 'gan-nhat.xlsx' });
    useRecentExportJobsMock.mockReturnValue({
      data: {
        items: [recentJob],
        page: { limit: 5, nextCursor: null, hasMore: false },
      },
      isLoading: false,
      isError: false,
    });
    useExportJobMock.mockImplementation((jobId) => ({
      data: jobId === recentJob.id ? recentJob : undefined,
      isLoading: false,
      isError: false,
    }));

    render(panel());

    expect(useExportJobMock).toHaveBeenCalledWith(recentJob.id);
    expect(screen.getByText('gan-nhat.xlsx')).toBeInTheDocument();
    expect(screen.getByText('Export jobs gần đây')).toBeInTheDocument();
  });

  it('renders failed, cancelled, and expired terminal messages', () => {
    const { rerender } = renderPanelWithJob(exportJob('FAILED', { errorMessage: 'Worker failed' }));
    expect(screen.getByText('Worker failed')).toBeInTheDocument();

    useExportJobMock.mockReturnValue({
      data: exportJob('CANCELLED'),
      isLoading: false,
      isError: false,
    });
    rerender(panel());
    expect(screen.getByText(/đã bị hủy/i)).toBeInTheDocument();

    useExportJobMock.mockReturnValue({
      data: exportJob('EXPIRED'),
      isLoading: false,
      isError: false,
    });
    rerender(panel());
    expect(screen.getByText('File export đã hết hạn. Vui lòng tạo job mới.')).toBeInTheDocument();
  });
});

function renderPanelWithJob(job: ExportJobResponse) {
  useExportJobMock.mockReturnValue({ data: job, isLoading: false, isError: false });
  return render(panel());
}

function panel() {
  return (
    <ExportJobPanel
      title="Export TestRun"
      description="Tạo file XLSX"
      source={{ sourceType: 'test-run', sourceId: TEST_RUN_ID }}
    />
  );
}

function exportJob(
  status: ExportJobStatus,
  patch: Partial<ExportJobResponse> = {},
): ExportJobResponse {
  return {
    id: JOB_ID,
    jobType: 'TEST_RUN_RESULTS',
    format: 'XLSX',
    status,
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
    ...patch,
  };
}
