import { describe, expect, it } from 'vitest';

import {
  MAX_IMPORT_PREVIEW_ROWS,
  buildConfirmImportRequest,
  importMappingFromJob,
  previewRowCount,
  validateImportDraft,
} from '@/features/datasets/components/dataset-import-utils';
import type {
  DatasetImportJobResponse,
  DatasetSchemaColumnResponse,
} from '@/features/datasets/types/dataset';

const projectId = '22222222-2222-4222-8222-222222222222';
const userId = '11111111-1111-4111-8111-111111111111';

const schemaColumns: DatasetSchemaColumnResponse[] = [
  {
    id: 1,
    name: 'question',
    displayName: 'Question',
    dataType: 'TEXT',
    required: true,
    description: null,
    orderIndex: 0,
    variable: 'question',
  },
  {
    id: 2,
    name: 'expected',
    displayName: 'Expected',
    dataType: 'TEXT',
    required: false,
    description: null,
    orderIndex: 1,
    variable: 'expected',
  },
];

function importJob(columns: string[], totalRows = 2): DatasetImportJobResponse {
  return {
    id: '99999999-9999-4999-8999-999999999999',
    targetProjectId: projectId,
    targetDatasetId: null,
    sourceType: 'EXCEL',
    sourceFileName: 'dataset.xlsx',
    status: 'PREVIEWED',
    schemaCompatibility: {
      status: columns.includes('context') ? 'REQUIRES_MAPPING' : 'COMPATIBLE',
      requiresMapping: columns.includes('context'),
      sourceColumnCount: columns.length,
      targetColumnCount: 2,
      missingRequiredColumns: [],
      extraSourceColumns: columns.filter((column) => !['question', 'expected'].includes(column)),
    },
    preview: {
      sheetName: 'Regression',
      availableSheets: ['Regression'],
      columns,
      rows: [
        {
          rowIndex: 1,
          data: { question: 'VF 8?', expected: 'Answer', context: 'Sales' },
        },
      ],
      totalRows,
      previewRowCount: 1,
    },
    mapping: {
      items: columns.map((column) =>
        ['question', 'expected'].includes(column)
          ? { sourceColumn: column, action: 'MAP_TO_EXISTING_COLUMN', targetColumn: column }
          : { sourceColumn: column, action: 'IGNORE' },
      ),
    },
    errorMessage: null,
    createdBy: userId,
    createdByUser: null,
    createdAt: '2026-01-01T00:00:00Z',
    finishedAt: null,
  };
}

describe('dataset import utils', () => {
  it('clamps preview rows to the backend maximum', () => {
    expect(previewRowCount(Number.NaN)).toBe(10);
    expect(previewRowCount(0)).toBe(1);
    expect(previewRowCount(999)).toBe(MAX_IMPORT_PREVIEW_ROWS);
  });

  it('uses backend suggested mapping for compatible imports', () => {
    expect(importMappingFromJob(importJob(['question', 'expected']), schemaColumns)).toEqual([
      { sourceColumn: 'question', action: 'MAP_TO_EXISTING_COLUMN', targetColumn: 'question' },
      { sourceColumn: 'expected', action: 'MAP_TO_EXISTING_COLUMN', targetColumn: 'expected' },
    ]);
  });

  it('requires every source column to be mapped or ignored', () => {
    const job = importJob(['question', 'expected', 'context']);
    const validation = validateImportDraft(job, {
      datasetName: 'Imported',
      datasetDescription: '',
      targetSchemaMode: 'USE_CURRENT_SCHEMA',
      mapping: [
        { sourceColumn: 'question', action: 'MAP_TO_EXISTING_COLUMN', targetColumn: 'question' },
        { sourceColumn: 'expected', action: 'MAP_TO_EXISTING_COLUMN', targetColumn: 'expected' },
      ],
    });

    expect(validation.canConfirm).toBe(false);
    expect(validation.messages).toContain('Chưa mapping source column: context');
  });

  it('flags row-limit errors before confirm', () => {
    const validation = validateImportDraft(importJob(['question'], 501), {
      datasetName: 'Too Large',
      datasetDescription: '',
      targetSchemaMode: 'USE_CURRENT_SCHEMA',
      mapping: [
        { sourceColumn: 'question', action: 'MAP_TO_EXISTING_COLUMN', targetColumn: 'question' },
      ],
    });

    expect(validation.canConfirm).toBe(false);
    expect(validation.messages).toContain('File vượt quá giới hạn 500 dòng import.');
  });

  it('requires schema extension when a mapping adds a new column', () => {
    const validation = validateImportDraft(importJob(['question', 'context']), {
      datasetName: 'Imported',
      datasetDescription: '',
      targetSchemaMode: 'USE_CURRENT_SCHEMA',
      mapping: [
        { sourceColumn: 'question', action: 'MAP_TO_EXISTING_COLUMN', targetColumn: 'question' },
        {
          sourceColumn: 'context',
          action: 'ADD_AS_NEW_COLUMN',
          targetColumn: 'context',
          displayName: 'Context',
          dataType: 'TEXT',
          required: false,
        },
      ],
    });

    expect(validation.canConfirm).toBe(false);
    expect(validation.messages).toContain('Cột mới cần chế độ tạo schema version mới.');
  });

  it('builds the backend confirm request for new columns', () => {
    expect(
      buildConfirmImportRequest({
        datasetName: ' Imported ',
        datasetDescription: ' From Excel ',
        targetSchemaMode: 'EXTEND_CURRENT_SCHEMA',
        mapping: [
          {
            sourceColumn: 'context',
            action: 'ADD_AS_NEW_COLUMN',
            targetColumn: 'context',
            displayName: '',
            dataType: 'TEXT',
            required: false,
          },
        ],
      }),
    ).toEqual({
      datasetName: 'Imported',
      datasetDescription: 'From Excel',
      targetSchemaMode: 'EXTEND_CURRENT_SCHEMA',
      mapping: [
        {
          sourceColumn: 'context',
          action: 'ADD_AS_NEW_COLUMN',
          targetColumn: 'context',
          displayName: 'context',
          dataType: 'TEXT',
          required: false,
        },
      ],
    });
  });
});
