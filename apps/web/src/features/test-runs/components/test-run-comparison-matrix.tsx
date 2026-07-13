import { Button } from '@/shared/components/ui/button';
import { Skeleton } from '@/shared/components/ui/skeleton';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/components/ui/table';
import {
  matrixCellForColumn,
  matrixResultIdText,
  matrixReviewedText,
  TEST_RUN_MATRIX_PAGE_SIZE,
} from '@/features/test-runs/components/test-run-comparison-matrix-utils';
import {
  resultStatusLabel,
  resultStatusTone,
  scoreText,
} from '@/features/test-runs/components/test-run-results-utils';
import { useTestRunComparisonMatrix } from '@/features/test-runs/hooks/use-test-runs';
import type {
  ComparisonMatrixCell,
  ResultMode,
  TestCaseResultStatus,
} from '@/features/test-runs/types/test-run';

export function TestRunComparisonMatrix({
  testRunId,
  mode,
  page,
  onModeChange,
  onPageChange,
  onOpenResult,
}: {
  testRunId: string;
  mode: ResultMode;
  page: number;
  onModeChange: (mode: ResultMode) => void;
  onPageChange: (page: number) => void;
  onOpenResult: (resultId: number, mode: ResultMode) => void;
}) {
  const matrixQuery = useTestRunComparisonMatrix(testRunId, mode, page, TEST_RUN_MATRIX_PAGE_SIZE);
  const matrix = matrixQuery.data;
  const columns = matrix?.columns ?? [];
  const rows = matrix?.rows ?? [];

  return (
    <section className="rounded-lg border bg-surface p-5">
      <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
        <div>
          <h2 className="text-lg font-semibold">Comparison matrix</h2>
          <p className="mt-2 max-w-2xl text-sm text-muted-foreground">
            Backend trả về Dataset row theo thứ tự gốc và target column theo order locked của
            TestRun. Frontend chỉ render page hiện tại, không group lại toàn bộ result list.
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

      <div
        className="mt-5 overflow-x-auto rounded-lg border"
        data-testid="comparison-matrix-scroll"
      >
        <Table className="min-w-[920px]">
          <TableHeader>
            <TableRow>
              <TableHead className="sticky left-0 z-10 w-[150px] bg-surface">Dataset row</TableHead>
              {columns.map((column) => (
                <TableHead
                  key={column.testRunTargetId}
                  className="min-w-[190px]"
                  data-testid="matrix-column-name"
                >
                  <div className="font-medium text-foreground">{column.name}</div>
                  <div className="mt-1 text-xs text-muted-foreground">
                    Thứ tự {String(column.orderIndex)}
                  </div>
                </TableHead>
              ))}
            </TableRow>
          </TableHeader>
          <TableBody>
            {matrixQuery.isLoading ? (
              <MatrixSkeletonRows columnCount={Math.max(columns.length + 1, 4)} />
            ) : rows.length > 0 ? (
              rows.map((row) => (
                <TableRow key={row.datasetRowId} data-testid="matrix-row">
                  <TableCell className="sticky left-0 z-10 bg-surface">
                    <div className="font-medium">Row {String(row.rowIndex)}</div>
                    <div
                      className="mt-1 font-mono text-xs text-muted-foreground"
                      data-testid="matrix-row-id"
                    >
                      {String(row.datasetRowId)}
                    </div>
                  </TableCell>
                  {columns.map((column) => {
                    const cell = matrixCellForColumn(row, column);
                    return (
                      <TableCell key={column.testRunTargetId} className="align-top">
                        <MatrixCell cell={cell} mode={mode} onOpenResult={onOpenResult} />
                      </TableCell>
                    );
                  })}
                </TableRow>
              ))
            ) : (
              <TableRow>
                <TableCell
                  colSpan={Math.max(columns.length + 1, 1)}
                  className="py-8 text-center text-muted-foreground"
                >
                  Chưa có Dataset row trong matrix.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>

      {matrixQuery.isError ? (
        <div className="mt-4 rounded-lg border border-danger-soft bg-danger-soft p-3 text-sm text-danger">
          Không thể tải comparison matrix.
        </div>
      ) : null}

      <div className="mt-4 flex items-center justify-between gap-3">
        <div className="text-sm text-muted-foreground">
          Trang {String(page + 1)} · {String(rows.length)} Dataset row
        </div>
        <div className="flex gap-2">
          <Button
            type="button"
            variant="outline"
            disabled={page === 0 || matrixQuery.isFetching}
            onClick={() => onPageChange(Math.max(0, page - 1))}
          >
            Trang trước
          </Button>
          <Button
            type="button"
            variant="outline"
            disabled={!matrix?.page.hasMore || matrixQuery.isFetching}
            onClick={() => onPageChange(page + 1)}
          >
            Trang sau
          </Button>
        </div>
      </div>
    </section>
  );
}

function MatrixCell({
  cell,
  mode,
  onOpenResult,
}: {
  cell: ComparisonMatrixCell | undefined;
  mode: ResultMode;
  onOpenResult: (resultId: number, mode: ResultMode) => void;
}) {
  if (!cell || cell.resultId === null || cell.finalStatus === null) {
    return (
      <div className="rounded-md border bg-muted p-3 text-sm text-muted-foreground">
        Chưa có result
      </div>
    );
  }

  const finalStatus = cell.finalStatus;
  const resultId = cell.resultId;

  return (
    <div className="rounded-md border bg-background p-3">
      <div className="flex items-start justify-between gap-3">
        <MatrixStatusBadge status={finalStatus} />
        <span className="font-mono text-xs text-muted-foreground">{matrixResultIdText(cell)}</span>
      </div>
      <dl className="mt-3 space-y-1 text-xs text-muted-foreground">
        <div className="flex justify-between gap-3">
          <dt>Score</dt>
          <dd className="font-medium text-foreground">{scoreText(cell.finalScore)}</dd>
        </div>
        <div className="flex justify-between gap-3">
          <dt>Review</dt>
          <dd data-testid="matrix-reviewed-text">{matrixReviewedText(cell)}</dd>
        </div>
        <div className="flex justify-between gap-3">
          <dt>Mode</dt>
          <dd>{mode}</dd>
        </div>
      </dl>
      {cell.errorCategory ? (
        <div className="mt-2 text-xs text-warning">{cell.errorCategory}</div>
      ) : null}
      <Button
        type="button"
        variant="outline"
        size="sm"
        className="mt-3 w-full"
        onClick={() => onOpenResult(resultId, mode)}
      >
        Xem detail
      </Button>
    </div>
  );
}

function MatrixStatusBadge({ status }: { status: TestCaseResultStatus }) {
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

function MatrixSkeletonRows({ columnCount }: { columnCount: number }) {
  return (
    <>
      {['matrix-skeleton-1', 'matrix-skeleton-2', 'matrix-skeleton-3'].map((key) => (
        <TableRow key={key}>
          <TableCell colSpan={columnCount}>
            <Skeleton className="h-20 w-full" />
          </TableCell>
        </TableRow>
      ))}
    </>
  );
}
