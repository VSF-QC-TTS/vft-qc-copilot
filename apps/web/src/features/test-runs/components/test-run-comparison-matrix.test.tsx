import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { TestRunComparisonMatrix } from '@/features/test-runs/components/test-run-comparison-matrix';
import type { ComparisonMatrixResponse, ResultMode } from '@/features/test-runs/types/test-run';

interface MatrixQueryState {
  data: ComparisonMatrixResponse | undefined;
  isLoading: boolean;
  isError: boolean;
  isFetching: boolean;
}

const useTestRunComparisonMatrixMock = vi.hoisted(() =>
  vi.fn<(testRunId: string, mode: ResultMode, page: number, size: number) => MatrixQueryState>(),
);

vi.mock('@/features/test-runs/hooks/use-test-runs', () => ({
  useTestRunComparisonMatrix: useTestRunComparisonMatrixMock,
}));

const TEST_RUN_ID = '11111111-1111-4111-8111-111111111111';

describe('TestRunComparisonMatrix', () => {
  beforeEach(() => {
    useTestRunComparisonMatrixMock.mockReset();
  });

  it('renders server-driven column order, row order, missing cells and reviewed cells', () => {
    useTestRunComparisonMatrixMock.mockReturnValue({
      data: matrix({ hasMore: true }),
      isLoading: false,
      isError: false,
      isFetching: false,
    });
    const onModeChange = vi.fn<(mode: ResultMode) => void>();
    const onPageChange = vi.fn<(page: number) => void>();
    const onOpenResult = vi.fn<(resultId: number, mode: ResultMode) => void>();

    render(
      <TestRunComparisonMatrix
        testRunId={TEST_RUN_ID}
        mode="FINAL"
        page={2}
        onModeChange={onModeChange}
        onPageChange={onPageChange}
        onOpenResult={onOpenResult}
      />,
    );

    expect(useTestRunComparisonMatrixMock).toHaveBeenCalledWith(TEST_RUN_ID, 'FINAL', 2, 20);
    expect(screen.getByTestId('comparison-matrix-scroll').className).toContain('overflow-x-auto');
    expect(screen.getAllByTestId('matrix-column-name').map((item) => item.textContent)).toEqual([
      'Baseline modelThứ tự 0',
      'Internal ChatbotThứ tự 1',
    ]);
    expect(screen.getAllByTestId('matrix-row-id').map((item) => item.textContent)).toEqual([
      '18002',
      '18001',
    ]);
    expect(screen.getByText('Chưa có result')).toBeInTheDocument();
    expect(screen.getAllByTestId('matrix-reviewed-text')[0]).toHaveTextContent('Đã review');

    fireEvent.click(screen.getByRole('button', { name: 'MACHINE' }));
    expect(onModeChange).toHaveBeenCalledWith('MACHINE');

    fireEvent.click(screen.getByRole('button', { name: 'Trang trước' }));
    expect(onPageChange).toHaveBeenCalledWith(1);

    fireEvent.click(screen.getByRole('button', { name: 'Trang sau' }));
    expect(onPageChange).toHaveBeenCalledWith(3);

    const detailButton = screen.getAllByRole('button', { name: 'Xem detail' })[0];
    if (!detailButton) {
      throw new Error('Expected at least one matrix detail button');
    }
    fireEvent.click(detailButton);
    expect(onOpenResult).toHaveBeenCalledWith(9122, 'FINAL');
  });

  it('disables pagination from backend PageInfo without total pages', () => {
    useTestRunComparisonMatrixMock.mockReturnValue({
      data: matrix({ hasMore: false }),
      isLoading: false,
      isError: false,
      isFetching: false,
    });

    render(
      <TestRunComparisonMatrix
        testRunId={TEST_RUN_ID}
        mode="MACHINE"
        page={0}
        onModeChange={vi.fn<(mode: ResultMode) => void>()}
        onPageChange={vi.fn<(page: number) => void>()}
        onOpenResult={vi.fn<(resultId: number, mode: ResultMode) => void>()}
      />,
    );

    expect(screen.getByRole('button', { name: 'Trang trước' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Trang sau' })).toBeDisabled();
    expect(screen.getByText('Trang 1 · 2 Dataset row')).toBeInTheDocument();
  });
});

function matrix({ hasMore }: { hasMore: boolean }): ComparisonMatrixResponse {
  return {
    columns: [
      {
        testRunTargetId: '22222222-2222-4222-8222-222222222222',
        name: 'Baseline model',
        orderIndex: 0,
      },
      {
        testRunTargetId: '44444444-4444-4444-8444-444444444444',
        name: 'Internal Chatbot',
        orderIndex: 1,
      },
    ],
    rows: [
      {
        datasetRowId: 18002,
        rowIndex: 0,
        cells: [
          {
            testRunTargetId: '22222222-2222-4222-8222-222222222222',
            resultId: 9122,
            finalStatus: 'PASSED',
            finalScore: 0.95,
            finalPassed: true,
            errorCategory: null,
            reviewed: true,
          },
          {
            testRunTargetId: '44444444-4444-4444-8444-444444444444',
            resultId: null,
            finalStatus: null,
            finalScore: null,
            finalPassed: null,
            errorCategory: null,
            reviewed: false,
          },
        ],
      },
      {
        datasetRowId: 18001,
        rowIndex: 1,
        cells: [
          {
            testRunTargetId: '22222222-2222-4222-8222-222222222222',
            resultId: 9123,
            finalStatus: 'FAILED',
            finalScore: 0.1,
            finalPassed: false,
            errorCategory: null,
            reviewed: false,
          },
          {
            testRunTargetId: '44444444-4444-4444-8444-444444444444',
            resultId: 9124,
            finalStatus: 'ERROR',
            finalScore: null,
            finalPassed: false,
            errorCategory: 'TARGET_HTTP_ERROR',
            reviewed: false,
          },
        ],
      },
    ],
    page: { limit: 20, nextCursor: null, hasMore },
  };
}
