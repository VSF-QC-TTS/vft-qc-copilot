import { describe, expect, it } from 'vitest';

import {
  defaultCells,
  preferredVersion,
  rowPatchRequest,
  versionStatusLabel,
} from '@/features/datasets/components/dataset-version-utils';
import type {
  DatasetRowResponse,
  DatasetSchemaColumnResponse,
  DatasetVersionResponse,
} from '@/features/datasets/types/dataset';

const datasetId = '33333333-3333-4333-8333-333333333333';
const schemaId = '55555555-5555-4555-8555-555555555555';
const userId = '11111111-1111-4111-8111-111111111111';

function version(
  id: string,
  status: DatasetVersionResponse['status'],
  versionNumber: number,
): DatasetVersionResponse {
  return {
    id,
    datasetId,
    version: versionNumber,
    schemaId,
    schemaVersion: 1,
    rowCount: 0,
    status,
    createdBy: userId,
    createdByUser: null,
    createdAt: '2026-01-01T00:00:00Z',
    publishedAt: null,
    discardedAt: null,
  };
}

describe('dataset version utils', () => {
  it('prefers draft over active version for editing continuation', () => {
    const active = version('44444444-4444-4444-8444-444444444444', 'ACTIVE', 1);
    const draft = version('66666666-6666-4666-8666-666666666666', 'DRAFT', 2);

    expect(preferredVersion([active, draft])).toBe(draft);
  });

  it('falls back to active version when no draft exists', () => {
    const archived = version('77777777-7777-4777-8777-777777777777', 'ARCHIVED', 1);
    const active = version('44444444-4444-4444-8444-444444444444', 'ACTIVE', 2);

    expect(preferredVersion([archived, active])).toBe(active);
  });

  it('builds row patch request with the backend revision guard', () => {
    const row: DatasetRowResponse = {
      id: 15,
      datasetVersionId: '44444444-4444-4444-8444-444444444444',
      rowIndex: 0,
      cells: { question: 'old' },
      revision: 8,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    };

    expect(rowPatchRequest(row, { question: 'new' })).toEqual({
      cells: { question: 'new' },
      expectedRevision: 8,
    });
  });

  it('creates empty cells from backend schema columns', () => {
    const columns: DatasetSchemaColumnResponse[] = [
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

    expect(defaultCells(columns)).toEqual({ question: '', expected: '' });
  });

  it('renders Vietnamese status labels', () => {
    expect(versionStatusLabel('DRAFT')).toBe('Bản nháp');
    expect(versionStatusLabel('ACTIVE')).toBe('Đang hoạt động');
    expect(versionStatusLabel('ARCHIVED')).toBe('Đã lưu trữ');
    expect(versionStatusLabel('DISCARDED')).toBe('Đã hủy');
  });
});
