import { describe, expect, it } from 'vitest';

import {
  datasetResponseSchema,
  datasetRowSchema,
  datasetSchemaResponseSchema,
  patchDatasetRowCellsRequestSchema,
} from '@/features/datasets/types/dataset';

const userId = '11111111-1111-4111-8111-111111111111';
const projectId = '22222222-2222-4222-8222-222222222222';
const datasetId = '33333333-3333-4333-8333-333333333333';
const versionId = '44444444-4444-4444-8444-444444444444';
const schemaId = '55555555-5555-4555-8555-555555555555';

describe('dataset DTO schemas', () => {
  it('parses the backend dataset response contract', () => {
    const parsed = datasetResponseSchema.parse({
      id: datasetId,
      scope: 'PROJECT',
      projectId,
      currentVersionId: versionId,
      name: 'Regression',
      description: null,
      createdBy: userId,
      createdByUser: null,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    });

    expect(parsed.currentVersionId).toBe(versionId);
  });

  it('parses project dataset schema columns without allowing invented column CRUD fields', () => {
    const parsed = datasetSchemaResponseSchema.parse({
      id: schemaId,
      scope: 'PROJECT',
      projectId,
      version: 1,
      name: 'Default project schema',
      columns: [
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
      ],
      createdBy: userId,
      createdByUser: null,
      createdAt: '2026-01-01T00:00:00Z',
    });

    expect(parsed.columns.map((column) => column.name)).toEqual(['question', 'expected']);
  });

  it('requires expectedRevision when patching dataset row cells', () => {
    expect(() =>
      patchDatasetRowCellsRequestSchema.parse({
        cells: { question: 'Xin chào', expected: 'Chào bạn' },
      }),
    ).toThrow(/expectedRevision/);

    expect(
      patchDatasetRowCellsRequestSchema.parse({
        cells: { question: 'Xin chào', expected: 'Chào bạn' },
        expectedRevision: 3,
      }).expectedRevision,
    ).toBe(3);
  });

  it('parses dataset row cells as unknown values while preserving revision', () => {
    const parsed = datasetRowSchema.parse({
      id: 10,
      datasetVersionId: versionId,
      rowIndex: 0,
      cells: { question: 'Q1', score: 1, expected: null },
      revision: 4,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    });

    expect(parsed.revision).toBe(4);
  });
});
