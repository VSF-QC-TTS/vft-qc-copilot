import { describe, expect, it } from 'vitest';

import {
  isMissingMatrixCell,
  matrixCellForColumn,
  matrixResultIdText,
  matrixReviewedText,
} from '@/features/test-runs/components/test-run-comparison-matrix-utils';
import type {
  ComparisonMatrixCell,
  ComparisonMatrixColumn,
  ComparisonMatrixRow,
} from '@/features/test-runs/types/test-run';

describe('test run comparison matrix utils', () => {
  it('keeps backend column and row order without client-side result grouping', () => {
    const firstColumn = column('target-b', 0);
    const secondColumn = column('target-a', 1);
    const columns = [firstColumn, secondColumn];
    const firstRow = row(200, 0);
    const secondRow = row(100, 1);
    const rows = [firstRow, secondRow];

    expect(columns.map((item) => item.testRunTargetId)).toEqual(['target-b', 'target-a']);
    expect(rows.map((item) => item.datasetRowId)).toEqual([200, 100]);
    expect(matrixCellForColumn(firstRow, firstColumn)?.resultId).toBe(1000);
  });

  it('labels missing result cells safely', () => {
    const missing = cell('target-a', null, null, false);

    expect(isMissingMatrixCell(undefined)).toBe(true);
    expect(isMissingMatrixCell(missing)).toBe(true);
    expect(matrixResultIdText(missing)).toBe('—');
    expect(matrixReviewedText(missing)).toBe('—');
  });

  it('labels reviewed cells distinctly from unreviewed cells', () => {
    expect(matrixReviewedText(cell('target-a', 1000, 'PASSED', true))).toBe('Đã review');
    expect(matrixReviewedText(cell('target-a', 1001, 'FAILED', false))).toBe('Chưa review');
    expect(matrixResultIdText(cell('target-a', 1001, 'FAILED', false))).toBe('#1001');
  });
});

function column(testRunTargetId: string, orderIndex: number): ComparisonMatrixColumn {
  return { testRunTargetId, name: `Target ${String(orderIndex)}`, orderIndex };
}

function row(datasetRowId: number, rowIndex: number): ComparisonMatrixRow {
  return {
    datasetRowId,
    rowIndex,
    cells: [cell('target-b', 1000, 'PASSED', true), cell('target-a', 1001, 'FAILED', false)],
  };
}

function cell(
  testRunTargetId: string,
  resultId: number | null,
  finalStatus: ComparisonMatrixCell['finalStatus'],
  reviewed: boolean,
): ComparisonMatrixCell {
  return {
    testRunTargetId,
    resultId,
    finalStatus,
    finalScore: finalStatus ? 0.9 : null,
    finalPassed: finalStatus === null ? null : finalStatus === 'PASSED',
    errorCategory: null,
    reviewed,
  };
}
