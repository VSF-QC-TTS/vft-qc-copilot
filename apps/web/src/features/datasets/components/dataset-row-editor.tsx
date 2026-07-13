import { useState } from 'react';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/components/ui/table';
import type {
  DatasetRowResponse,
  DatasetSchemaColumnResponse,
} from '@/features/datasets/types/dataset';
import { rowPatchRequest } from '@/features/datasets/components/dataset-version-utils';

export interface DatasetRowEditorProps {
  rows: DatasetRowResponse[];
  columns: DatasetSchemaColumnResponse[];
  isEditable: boolean;
  isPending: boolean;
  onAddRow: (cells: Record<string, string>) => Promise<void>;
  onPatchRow: (rowId: number, request: ReturnType<typeof rowPatchRequest>) => Promise<void>;
}

export function DatasetRowEditor({
  rows,
  columns,
  isEditable,
  isPending,
  onAddRow,
  onPatchRow,
}: DatasetRowEditorProps) {
  const [newCells, setNewCells] = useState<Record<string, string>>(() => emptyCells(columns));

  const addRow = async () => {
    await onAddRow(newCells);
    setNewCells(emptyCells(columns));
  };

  return (
    <div className="space-y-4">
      {!isEditable && (
        <div className="rounded-md border bg-muted p-3 text-sm text-muted-foreground">
          Version này không phải bản nháp nên chỉ được xem, không được chỉnh sửa.
        </div>
      )}
      <div className="overflow-x-auto rounded-md border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-[80px]">#</TableHead>
              {columns.map((column) => (
                <TableHead key={column.id}>
                  {column.displayName || column.name}
                  {column.required && <span className="ml-1 text-destructive">*</span>}
                </TableHead>
              ))}
              <TableHead className="w-[120px]">Revision</TableHead>
              <TableHead className="w-[110px]"></TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.map((row) => (
              <DatasetRow
                key={row.id}
                row={row}
                columns={columns}
                isEditable={isEditable}
                isPending={isPending}
                onSave={onPatchRow}
              />
            ))}
            {isEditable && (
              <TableRow>
                <TableCell className="text-muted-foreground">Mới</TableCell>
                {columns.map((column) => (
                  <TableCell key={column.id}>
                    <Input
                      value={newCells[column.name] ?? ''}
                      onChange={(event) =>
                        setNewCells({ ...newCells, [column.name]: event.target.value })
                      }
                      placeholder={column.required ? 'Bắt buộc' : 'Tùy chọn'}
                      disabled={isPending}
                    />
                  </TableCell>
                ))}
                <TableCell>-</TableCell>
                <TableCell>
                  <Button
                    type="button"
                    size="sm"
                    onClick={() => void addRow()}
                    disabled={isPending}
                  >
                    Thêm
                  </Button>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>
    </div>
  );
}

function DatasetRow({
  row,
  columns,
  isEditable,
  isPending,
  onSave,
}: {
  row: DatasetRowResponse;
  columns: DatasetSchemaColumnResponse[];
  isEditable: boolean;
  isPending: boolean;
  onSave: (rowId: number, request: ReturnType<typeof rowPatchRequest>) => Promise<void>;
}) {
  const [cells, setCells] = useState<Record<string, string>>(() =>
    Object.fromEntries(columns.map((column) => [column.name, cellToText(row.cells[column.name])])),
  );

  return (
    <TableRow>
      <TableCell className="text-muted-foreground">{row.rowIndex + 1}</TableCell>
      {columns.map((column) => (
        <TableCell key={column.id}>
          <Input
            value={cells[column.name] ?? ''}
            disabled={!isEditable || isPending}
            onChange={(event) => setCells({ ...cells, [column.name]: event.target.value })}
          />
        </TableCell>
      ))}
      <TableCell className="text-muted-foreground">{row.revision}</TableCell>
      <TableCell>
        {isEditable && (
          <Button
            type="button"
            size="sm"
            variant="outline"
            disabled={isPending}
            onClick={() => void onSave(row.id, rowPatchRequest(row, cells))}
          >
            Lưu
          </Button>
        )}
      </TableCell>
    </TableRow>
  );
}

function emptyCells(columns: DatasetSchemaColumnResponse[]) {
  return Object.fromEntries(columns.map((column) => [column.name, '']));
}

function cellToText(value: unknown) {
  if (value === null || value === undefined) return '';
  if (typeof value === 'string') return value;
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  return JSON.stringify(value);
}
