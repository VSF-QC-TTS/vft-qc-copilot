import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useOutletContext } from 'react-router';
import { Upload } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import { Label } from '@/shared/components/ui/label';
import { Textarea } from '@/shared/components/ui/textarea';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/components/ui/table';
import { PermissionGuard } from '@/shared/components/permission-guard';
import { notifyError, notifySuccess, notifyWarning } from '@/shared/lib/notifications';
import {
  MAX_IMPORT_PREVIEW_ROWS,
  buildConfirmImportRequest,
  hasNewSchemaColumns,
  importMappingFromJob,
  previewRowCount,
  validateImportDraft,
  type DatasetImportDraft,
} from '@/features/datasets/components/dataset-import-utils';
import {
  useConfirmDatasetImportJob,
  useCreateDatasetImportJob,
  useProjectDatasetSchema,
} from '@/features/datasets/hooks/use-datasets';
import type { WorkspaceContextType } from '@/app/layouts/workspace-context-layout';
import type { ProjectResponse } from '@/features/projects/types/project';
import {
  datasetColumnDataTypeSchema,
  datasetImportMappingActionSchema,
  datasetImportTargetSchemaModeSchema,
} from '@/features/datasets/types/dataset';
import type {
  DatasetColumnDataType,
  DatasetImportJobResponse,
  DatasetImportMappingAction,
  DatasetImportMappingItem,
  DatasetImportTargetSchemaMode,
  DatasetSchemaColumnResponse,
} from '@/features/datasets/types/dataset';

interface ProjectContextType extends WorkspaceContextType {
  project: ProjectResponse;
}

const dataTypes: DatasetColumnDataType[] = [
  'TEXT',
  'NUMBER',
  'BOOLEAN',
  'JSON',
  'DATE',
  'DATETIME',
];
const mappingActions: DatasetImportMappingAction[] = [
  'MAP_TO_EXISTING_COLUMN',
  'ADD_AS_NEW_COLUMN',
  'IGNORE',
];
const schemaModes: DatasetImportTargetSchemaMode[] = [
  'USE_CURRENT_SCHEMA',
  'EXTEND_CURRENT_SCHEMA',
  'CREATE_NEW_SCHEMA_VERSION',
];

export function DatasetImportPage() {
  const { workspace, project } = useOutletContext<ProjectContextType>();
  const navigate = useNavigate();
  const schemaQuery = useProjectDatasetSchema(project.id);
  const createImportMutation = useCreateDatasetImportJob(project.id);
  const confirmImportMutation = useConfirmDatasetImportJob(project.id);
  const [file, setFile] = useState<File | null>(null);
  const [sheetName, setSheetName] = useState('');
  const [previewRows, setPreviewRows] = useState(10);
  const [draft, setDraft] = useState<DatasetImportDraft>({
    datasetName: '',
    datasetDescription: '',
    targetSchemaMode: 'USE_CURRENT_SCHEMA',
    mapping: [],
  });
  const importJob = createImportMutation.data;
  const schemaColumns = useMemo(() => schemaQuery.data?.columns ?? [], [schemaQuery.data]);
  const validation = useMemo(
    () => (importJob ? validateImportDraft(importJob, draft) : { canConfirm: false, messages: [] }),
    [draft, importJob],
  );
  const hasNewColumns = hasNewSchemaColumns(draft.mapping);
  const hasUnsavedImportDraft = Boolean(importJob) && !confirmImportMutation.isSuccess;

  useEffect(() => {
    if (!importJob || schemaColumns.length === 0) return;
    setDraft((current) => ({
      ...current,
      targetSchemaMode: importJob.schemaCompatibility.requiresMapping
        ? 'EXTEND_CURRENT_SCHEMA'
        : 'USE_CURRENT_SCHEMA',
      mapping: importMappingFromJob(importJob, schemaColumns),
    }));
  }, [importJob, schemaColumns]);

  useEffect(() => {
    if (!hasUnsavedImportDraft) return undefined;

    const handleBeforeUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault();
    };

    window.addEventListener('beforeunload', handleBeforeUnload);
    return () => window.removeEventListener('beforeunload', handleBeforeUnload);
  }, [hasUnsavedImportDraft]);

  const handleBack = async () => {
    if (
      hasUnsavedImportDraft &&
      !window.confirm('Preview import chưa được confirm. Bạn có chắc muốn rời trang?')
    ) {
      return;
    }
    await navigate(`/workspaces/${workspace.id}/projects/${project.id}/datasets`);
  };

  const handlePreview = async () => {
    if (!file) {
      notifyError('Vui lòng chọn file Excel');
      return;
    }
    try {
      const request = {
        file,
        previewRowCount: previewRowCount(previewRows),
        ...(sheetName.trim() ? { sheetName: sheetName.trim() } : {}),
      };
      await createImportMutation.mutateAsync(request);
      notifySuccess('Đã tạo Preview import');
    } catch {
      notifyError('Không thể tạo Preview từ file Excel');
    }
  };

  const handleConfirm = async () => {
    if (!importJob) return;
    const request = buildConfirmImportRequest(draft);
    if (!validation.canConfirm) {
      notifyWarning(validation.messages[0] ?? 'Mapping chưa hợp lệ');
      return;
    }
    try {
      const dataset = await confirmImportMutation.mutateAsync({
        importJobId: importJob.id,
        data: request,
      });
      notifySuccess('Đã import Dataset');
      await navigate(`/workspaces/${workspace.id}/projects/${project.id}/datasets/${dataset.id}`);
    } catch {
      notifyError('Không thể import Dataset');
    }
  };

  const updateMapping = (sourceColumn: string, patch: Partial<DatasetImportMappingItem>) => {
    setDraft((current) => ({
      ...current,
      mapping: current.mapping.map((item) =>
        item.sourceColumn === sourceColumn ? normalizeMappingPatch(item, patch) : item,
      ),
    }));
  };

  return (
    <div className="mx-auto w-full max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <div className="mb-6 flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
        <div>
          <p className="text-sm text-muted-foreground">{project.name}</p>
          <h1 className="text-2xl font-semibold tracking-tight">Import Dataset từ Excel</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Upload file XLSX, kiểm tra Preview, mapping cột rồi confirm để tạo Dataset ACTIVE.
          </p>
        </div>
        <Button type="button" variant="outline" onClick={() => void handleBack()}>
          Quay lại
        </Button>
      </div>

      <PermissionGuard
        capability="EDIT"
        workspaceRole={workspace.currentUserRole}
        fallback={
          <div className="rounded-lg border bg-muted p-6 text-sm text-muted-foreground">
            Bạn không có quyền import Dataset trong Project này.
          </div>
        }
      >
        <div className="grid gap-6 xl:grid-cols-[380px_minmax(0,1fr)]">
          <section className="rounded-lg border bg-surface p-5">
            <div className="mb-4 flex items-center gap-2">
              <Upload className="h-5 w-5 text-muted-foreground" aria-hidden="true" />
              <h2 className="text-lg font-semibold">File và Dataset</h2>
            </div>
            <div className="space-y-5">
              <div className="space-y-2">
                <Label htmlFor="dataset-import-file">File Excel</Label>
                <Input
                  id="dataset-import-file"
                  type="file"
                  accept=".xlsx,.xls,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/vnd.ms-excel"
                  disabled={createImportMutation.isPending || confirmImportMutation.isPending}
                  onChange={(event) => setFile(event.target.files?.[0] ?? null)}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="dataset-import-sheet">Sheet name</Label>
                <Input
                  id="dataset-import-sheet"
                  value={sheetName}
                  onChange={(event) => setSheetName(event.target.value)}
                  placeholder="Để trống để dùng sheet đầu tiên"
                  disabled={createImportMutation.isPending || confirmImportMutation.isPending}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="dataset-import-preview-rows">Số dòng Preview</Label>
                <Input
                  id="dataset-import-preview-rows"
                  type="number"
                  min={1}
                  max={MAX_IMPORT_PREVIEW_ROWS}
                  value={previewRows}
                  onChange={(event) => setPreviewRows(Number(event.target.value))}
                  disabled={createImportMutation.isPending || confirmImportMutation.isPending}
                />
                <p className="text-xs text-muted-foreground">
                  Backend mặc định 10 dòng và giới hạn tối đa {MAX_IMPORT_PREVIEW_ROWS} dòng.
                </p>
              </div>
              <Button
                type="button"
                className="w-full"
                disabled={createImportMutation.isPending || schemaQuery.isLoading}
                onClick={() => void handlePreview()}
              >
                Tạo Preview
              </Button>

              {importJob && (
                <div className="space-y-5 border-t pt-5">
                  <div className="space-y-2">
                    <Label htmlFor="dataset-import-name">Tên Dataset</Label>
                    <Input
                      id="dataset-import-name"
                      value={draft.datasetName}
                      onChange={(event) => setDraft({ ...draft, datasetName: event.target.value })}
                      placeholder="Regression import từ Excel"
                      disabled={confirmImportMutation.isPending}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="dataset-import-description">Mô tả</Label>
                    <Textarea
                      id="dataset-import-description"
                      value={draft.datasetDescription}
                      onChange={(event) =>
                        setDraft({ ...draft, datasetDescription: event.target.value })
                      }
                      placeholder="Nguồn file, phạm vi hoặc ghi chú import"
                      disabled={confirmImportMutation.isPending}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="dataset-import-schema-mode">Schema mode</Label>
                    <select
                      id="dataset-import-schema-mode"
                      className="h-9 w-full rounded-md border border-input bg-background px-3 text-sm"
                      value={draft.targetSchemaMode}
                      onChange={(event) =>
                        setDraft({
                          ...draft,
                          targetSchemaMode: parseSchemaMode(event.target.value),
                        })
                      }
                      disabled={confirmImportMutation.isPending}
                    >
                      {schemaModes.map((mode) => (
                        <option key={mode} value={mode}>
                          {schemaModeLabel(mode)}
                        </option>
                      ))}
                    </select>
                  </div>
                  {hasNewColumns && (
                    <div className="rounded-md border border-warning-soft bg-warning-soft p-3 text-sm text-warning">
                      Mapping có cột mới. Backend sẽ tạo Dataset schema version mới khi confirm.
                    </div>
                  )}
                  {validation.messages.length > 0 && (
                    <div className="rounded-md border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive">
                      <ul className="list-disc space-y-1 pl-4">
                        {validation.messages.map((message) => (
                          <li key={message}>{message}</li>
                        ))}
                      </ul>
                    </div>
                  )}
                  <Button
                    type="button"
                    className="w-full"
                    disabled={!validation.canConfirm || confirmImportMutation.isPending}
                    onClick={() => void handleConfirm()}
                  >
                    Confirm import
                  </Button>
                </div>
              )}
            </div>
          </section>

          <main className="space-y-6">
            {importJob ? (
              <>
                <PreviewSummary importJob={importJob} />
                <MappingTable
                  mapping={draft.mapping}
                  schemaColumns={schemaColumns}
                  isPending={confirmImportMutation.isPending}
                  onChange={updateMapping}
                />
                <PreviewTable importJob={importJob} />
              </>
            ) : (
              <div className="rounded-lg border border-dashed bg-surface p-8 text-center text-sm text-muted-foreground">
                Chọn file Excel và tạo Preview để xem dữ liệu trước khi import.
              </div>
            )}
          </main>
        </div>
      </PermissionGuard>
    </div>
  );
}

function PreviewSummary({ importJob }: { importJob: DatasetImportJobResponse }) {
  return (
    <section className="rounded-lg border bg-surface p-5">
      <h2 className="text-lg font-semibold">Preview</h2>
      <dl className="mt-4 grid gap-4 text-sm md:grid-cols-4">
        <div>
          <dt className="text-muted-foreground">File</dt>
          <dd className="mt-1 font-medium">{importJob.sourceFileName ?? 'upload.xlsx'}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Sheet</dt>
          <dd className="mt-1 font-medium">{importJob.preview.sheetName}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Tổng dòng</dt>
          <dd className="mt-1 font-medium">{importJob.preview.totalRows}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Trạng thái schema</dt>
          <dd className="mt-1 font-medium">
            {compatibilityLabel(importJob.schemaCompatibility.status)}
          </dd>
        </div>
      </dl>
      {(importJob.schemaCompatibility.extraSourceColumns?.length ?? 0) > 0 && (
        <p className="mt-4 text-sm text-muted-foreground">
          Cột ngoài schema: {importJob.schemaCompatibility.extraSourceColumns?.join(', ') ?? ''}
        </p>
      )}
    </section>
  );
}

function MappingTable({
  mapping,
  schemaColumns,
  isPending,
  onChange,
}: {
  mapping: DatasetImportMappingItem[];
  schemaColumns: DatasetSchemaColumnResponse[];
  isPending: boolean;
  onChange: (sourceColumn: string, patch: Partial<DatasetImportMappingItem>) => void;
}) {
  return (
    <section className="rounded-lg border bg-surface p-5">
      <h2 className="text-lg font-semibold">Mapping cột</h2>
      <p className="mt-1 text-sm text-muted-foreground">
        Mỗi source column phải được map, thêm thành cột mới hoặc ignore.
      </p>
      <div className="mt-4 overflow-x-auto rounded-md border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Source column</TableHead>
              <TableHead>Action</TableHead>
              <TableHead>Target column</TableHead>
              <TableHead>Display name</TableHead>
              <TableHead>Data type</TableHead>
              <TableHead>Required</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {mapping.map((item) => (
              <TableRow key={item.sourceColumn}>
                <TableCell className="font-mono text-xs">{item.sourceColumn}</TableCell>
                <TableCell>
                  <select
                    aria-label={`Action cho ${item.sourceColumn}`}
                    className="h-9 min-w-[190px] rounded-md border border-input bg-background px-3 text-sm"
                    value={item.action}
                    disabled={isPending}
                    onChange={(event) =>
                      onChange(item.sourceColumn, {
                        action: parseMappingAction(event.target.value),
                      })
                    }
                  >
                    {mappingActions.map((action) => (
                      <option key={action} value={action}>
                        {mappingActionLabel(action)}
                      </option>
                    ))}
                  </select>
                </TableCell>
                <TableCell>
                  {item.action === 'MAP_TO_EXISTING_COLUMN' ? (
                    <select
                      aria-label={`Target column cho ${item.sourceColumn}`}
                      className="h-9 min-w-[170px] rounded-md border border-input bg-background px-3 text-sm"
                      value={item.targetColumn ?? ''}
                      disabled={isPending}
                      onChange={(event) =>
                        onChange(item.sourceColumn, { targetColumn: event.target.value })
                      }
                    >
                      <option value="">Chọn cột</option>
                      {schemaColumns.map((column) => (
                        <option key={column.id} value={column.name}>
                          {column.name}
                        </option>
                      ))}
                    </select>
                  ) : (
                    <Input
                      aria-label={`Target column mới cho ${item.sourceColumn}`}
                      value={item.targetColumn ?? ''}
                      disabled={isPending || item.action === 'IGNORE'}
                      onChange={(event) =>
                        onChange(item.sourceColumn, { targetColumn: event.target.value })
                      }
                    />
                  )}
                </TableCell>
                <TableCell>
                  <Input
                    aria-label={`Display name cho ${item.sourceColumn}`}
                    value={item.displayName ?? ''}
                    disabled={isPending || item.action !== 'ADD_AS_NEW_COLUMN'}
                    onChange={(event) =>
                      onChange(item.sourceColumn, { displayName: event.target.value })
                    }
                  />
                </TableCell>
                <TableCell>
                  <select
                    aria-label={`Data type cho ${item.sourceColumn}`}
                    className="h-9 min-w-[130px] rounded-md border border-input bg-background px-3 text-sm disabled:opacity-50"
                    value={item.dataType ?? 'TEXT'}
                    disabled={isPending || item.action !== 'ADD_AS_NEW_COLUMN'}
                    onChange={(event) =>
                      onChange(item.sourceColumn, {
                        dataType: parseColumnDataType(event.target.value),
                      })
                    }
                  >
                    {dataTypes.map((dataType) => (
                      <option key={dataType} value={dataType}>
                        {dataType}
                      </option>
                    ))}
                  </select>
                </TableCell>
                <TableCell>
                  <input
                    aria-label={`Required cho ${item.sourceColumn}`}
                    type="checkbox"
                    checked={item.required ?? false}
                    disabled={isPending || item.action !== 'ADD_AS_NEW_COLUMN'}
                    onChange={(event) =>
                      onChange(item.sourceColumn, { required: event.target.checked })
                    }
                  />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    </section>
  );
}

function PreviewTable({ importJob }: { importJob: DatasetImportJobResponse }) {
  return (
    <section className="rounded-lg border bg-surface p-5">
      <h2 className="text-lg font-semibold">Dữ liệu Preview</h2>
      <div className="mt-4 overflow-x-auto rounded-md border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-[90px]">Row</TableHead>
              {importJob.preview.columns.map((column) => (
                <TableHead key={column}>{column}</TableHead>
              ))}
            </TableRow>
          </TableHeader>
          <TableBody>
            {importJob.preview.rows.map((row) => (
              <TableRow key={row.rowIndex}>
                <TableCell className="font-mono text-xs text-muted-foreground">
                  {row.rowIndex}
                </TableCell>
                {importJob.preview.columns.map((column) => (
                  <TableCell key={column} className="min-w-[160px] text-sm">
                    {cellToPreview(row.data[column])}
                  </TableCell>
                ))}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    </section>
  );
}

function normalizeMappingPatch(
  item: DatasetImportMappingItem,
  patch: Partial<DatasetImportMappingItem>,
): DatasetImportMappingItem {
  const next = { ...item, ...patch };
  if (patch.action === 'IGNORE') {
    return { sourceColumn: item.sourceColumn, action: 'IGNORE' };
  }
  if (patch.action === 'ADD_AS_NEW_COLUMN') {
    const targetColumn = item.targetColumn || item.sourceColumn;
    return {
      sourceColumn: item.sourceColumn,
      action: 'ADD_AS_NEW_COLUMN',
      targetColumn,
      displayName: item.displayName || targetColumn,
      dataType: item.dataType ?? 'TEXT',
      required: item.required ?? false,
    };
  }
  if (patch.action === 'MAP_TO_EXISTING_COLUMN') {
    return {
      sourceColumn: item.sourceColumn,
      action: 'MAP_TO_EXISTING_COLUMN',
      targetColumn: item.targetColumn ?? item.sourceColumn,
    };
  }
  return next;
}

function cellToPreview(value: unknown) {
  if (value === null || value === undefined) return '';
  if (typeof value === 'string') return value;
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  return JSON.stringify(value);
}

function mappingActionLabel(action: DatasetImportMappingAction) {
  if (action === 'ADD_AS_NEW_COLUMN') return 'Thêm cột mới';
  if (action === 'IGNORE') return 'Ignore';
  return 'Map vào cột hiện có';
}

function schemaModeLabel(mode: DatasetImportTargetSchemaMode) {
  if (mode === 'EXTEND_CURRENT_SCHEMA') return 'Extend current schema';
  if (mode === 'CREATE_NEW_SCHEMA_VERSION') return 'Create new schema version';
  return 'Use current schema';
}

function compatibilityLabel(status: string) {
  if (status === 'COMPATIBLE') return 'Tương thích';
  if (status === 'INCOMPATIBLE') return 'Không tương thích';
  return 'Cần mapping';
}

function parseSchemaMode(value: string): DatasetImportTargetSchemaMode {
  return datasetImportTargetSchemaModeSchema.parse(value);
}

function parseMappingAction(value: string): DatasetImportMappingAction {
  return datasetImportMappingActionSchema.parse(value);
}

function parseColumnDataType(value: string): DatasetColumnDataType {
  return datasetColumnDataTypeSchema.parse(value);
}
