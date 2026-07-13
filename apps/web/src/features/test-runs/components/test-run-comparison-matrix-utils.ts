import type {
  ComparisonMatrixCell,
  ComparisonMatrixColumn,
  ComparisonMatrixRow,
} from '@/features/test-runs/types/test-run';

export const TEST_RUN_MATRIX_PAGE_SIZE = 20;

export function matrixCellForColumn(
  row: ComparisonMatrixRow,
  column: ComparisonMatrixColumn,
): ComparisonMatrixCell | undefined {
  return row.cells.find((cell) => cell.testRunTargetId === column.testRunTargetId);
}

export function isMissingMatrixCell(cell: ComparisonMatrixCell | undefined): boolean {
  return !cell || cell.resultId === null || cell.finalStatus === null;
}

export function matrixReviewedText(cell: ComparisonMatrixCell | undefined): string {
  if (!cell || cell.resultId === null || cell.finalStatus === null) {
    return '—';
  }
  return cell.reviewed ? 'Đã review' : 'Chưa review';
}

export function matrixResultIdText(cell: ComparisonMatrixCell | undefined): string {
  return cell?.resultId ? `#${String(cell.resultId)}` : '—';
}
