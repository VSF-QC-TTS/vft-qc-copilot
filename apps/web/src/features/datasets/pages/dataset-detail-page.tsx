import { useEffect, useState } from 'react';
import { Link, useNavigate, useOutletContext, useParams } from 'react-router';
import { GitBranch, RefreshCw } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Skeleton } from '@/shared/components/ui/skeleton';
import { PermissionGuard } from '@/shared/components/permission-guard';
import { notifyError, notifySuccess } from '@/shared/lib/notifications';
import { isApiError } from '@/shared/api/api-error';
import { DatasetRowEditor } from '@/features/datasets/components/dataset-row-editor';
import {
  isDraftVersion,
  preferredVersion,
  versionStatusLabel,
} from '@/features/datasets/components/dataset-version-utils';
import { ExportJobPanel } from '@/features/exports/components/export-job-panel';
import {
  useAddDatasetRow,
  useCreateDatasetDraft,
  useDataset,
  useDatasetRows,
  useDatasetVersions,
  useDiscardDatasetVersion,
  usePatchDatasetRow,
  useProjectDatasetSchema,
  usePublishDatasetVersion,
} from '@/features/datasets/hooks/use-datasets';
import type { WorkspaceContextType } from '@/app/layouts/workspace-context-layout';
import type { ProjectResponse } from '@/features/projects/types/project';
import type { PatchDatasetRowCellsRequest } from '@/features/datasets/types/dataset';

interface ProjectContextType extends WorkspaceContextType {
  project: ProjectResponse;
}

const DATASET_ROWS_PAGE_SIZE = 20;

export function DatasetDetailPage() {
  const { datasetId = '', versionId } = useParams<{ datasetId: string; versionId?: string }>();
  const { workspace, project } = useOutletContext<ProjectContextType>();
  const navigate = useNavigate();
  const [rowsPage, setRowsPage] = useState(0);
  const datasetQuery = useDataset(datasetId);
  const versionsQuery = useDatasetVersions(datasetId);
  const schemaQuery = useProjectDatasetSchema(project.id);
  const selectedVersion =
    versionsQuery.data?.find((version) => version.id === versionId) ??
    preferredVersion(versionsQuery.data);
  const rowsQuery = useDatasetRows(selectedVersion?.id ?? '', rowsPage, DATASET_ROWS_PAGE_SIZE);
  const createDraftMutation = useCreateDatasetDraft(datasetId);
  const publishMutation = usePublishDatasetVersion(datasetId);
  const discardMutation = useDiscardDatasetVersion(datasetId);
  const addRowMutation = useAddDatasetRow(selectedVersion?.id ?? '');
  const patchRowMutation = usePatchDatasetRow(selectedVersion?.id ?? '');
  const isLoading = datasetQuery.isLoading || versionsQuery.isLoading || schemaQuery.isLoading;

  useEffect(() => {
    setRowsPage(0);
  }, [selectedVersion?.id]);

  if (isLoading) {
    return (
      <div className="mx-auto w-full max-w-6xl px-4 py-8 sm:px-6 lg:px-8">
        <Skeleton className="h-10 w-1/3" />
        <Skeleton className="mt-6 h-[420px] w-full" />
      </div>
    );
  }

  if (
    datasetQuery.isError ||
    versionsQuery.isError ||
    schemaQuery.isError ||
    !datasetQuery.data ||
    !schemaQuery.data ||
    datasetQuery.data.projectId !== project.id
  ) {
    return (
      <div className="mx-auto w-full max-w-5xl px-4 py-8 text-destructive sm:px-6 lg:px-8">
        Không thể tải dataset.
      </div>
    );
  }

  const versions = versionsQuery.data ?? [];
  const hasDraft = versions.some((version) => version.status === 'DRAFT');
  const isSelectedDraft = isDraftVersion(selectedVersion);
  const canMutate =
    createDraftMutation.isPending ||
    publishMutation.isPending ||
    discardMutation.isPending ||
    addRowMutation.isPending ||
    patchRowMutation.isPending;

  const navigateToVersion = async (targetVersionId: string) => {
    await navigate(
      `/workspaces/${workspace.id}/projects/${project.id}/datasets/${datasetId}/versions/${targetVersionId}`,
    );
  };

  const handleCreateDraft = async () => {
    try {
      const draft = await createDraftMutation.mutateAsync();
      notifySuccess('Đã tạo bản nháp');
      await navigateToVersion(draft.id);
    } catch {
      notifyError('Không thể tạo bản nháp');
    }
  };

  const handlePublish = async () => {
    if (!selectedVersion) return;
    try {
      const published = await publishMutation.mutateAsync(selectedVersion.id);
      notifySuccess('Đã publish dataset');
      await navigateToVersion(published.id);
    } catch {
      notifyError('Không thể publish dataset');
    }
  };

  const handleDiscard = async () => {
    if (!selectedVersion) return;
    try {
      const discarded = await discardMutation.mutateAsync(selectedVersion.id);
      notifySuccess('Đã hủy bản nháp');
      await navigateToVersion(discarded.id);
    } catch {
      notifyError('Không thể hủy bản nháp');
    }
  };

  const handleAddRow = async (cells: Record<string, string>) => {
    if (!selectedVersion) return;
    try {
      await addRowMutation.mutateAsync({ cells });
      notifySuccess('Đã thêm dòng');
    } catch {
      notifyError('Không thể thêm dòng');
    }
  };

  const handlePatchRow = async (rowId: number, request: PatchDatasetRowCellsRequest) => {
    try {
      await patchRowMutation.mutateAsync({ rowId, data: request });
      notifySuccess('Đã lưu dòng');
    } catch (error) {
      if (isApiError(error) && error.status === 409) {
        notifyError('Dòng đã được cập nhật ở nơi khác. Vui lòng tải lại dữ liệu.');
        return;
      }
      notifyError('Không thể lưu dòng');
    }
  };

  return (
    <div className="mx-auto w-full max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <div className="mb-6 flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
        <div>
          <p className="text-sm text-muted-foreground">{project.name}</p>
          <h1 className="text-2xl font-semibold tracking-tight">{datasetQuery.data.name}</h1>
          {datasetQuery.data.description && (
            <p className="mt-1 max-w-3xl text-sm text-muted-foreground">
              {datasetQuery.data.description}
            </p>
          )}
        </div>
        <div className="flex flex-wrap justify-end gap-2">
          <Button asChild variant="outline">
            <Link to={`/workspaces/${workspace.id}/projects/${project.id}/datasets`}>Quay lại</Link>
          </Button>
          <Button
            type="button"
            variant="outline"
            onClick={() => void rowsQuery.refetch()}
            disabled={!selectedVersion || rowsQuery.isFetching}
          >
            <RefreshCw className="h-4 w-4" />
            Tải lại dòng
          </Button>
        </div>
      </div>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_360px]">
        <main className="space-y-6">
          <section className="rounded-lg border bg-surface p-5">
            <div className="mb-4 flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
              <div>
                <h2 className="text-lg font-semibold">Dòng kiểm thử</h2>
                <p className="text-sm text-muted-foreground">
                  {selectedVersion
                    ? `Version ${selectedVersion.version} · ${versionStatusLabel(selectedVersion.status)}`
                    : 'Chưa có version'}
                </p>
              </div>
              <PermissionGuard capability="EDIT" workspaceRole={workspace.currentUserRole}>
                {(canEdit) => (
                  <div className="flex flex-wrap gap-2">
                    <Button
                      type="button"
                      variant="outline"
                      disabled={!canEdit || hasDraft || canMutate}
                      onClick={() => void handleCreateDraft()}
                    >
                      Tạo bản nháp
                    </Button>
                    <Button
                      type="button"
                      disabled={!canEdit || !isSelectedDraft || canMutate}
                      onClick={() => void handlePublish()}
                    >
                      Publish
                    </Button>
                    <Button
                      type="button"
                      variant="outline"
                      disabled={!canEdit || !isSelectedDraft || canMutate}
                      onClick={() => void handleDiscard()}
                    >
                      Hủy nháp
                    </Button>
                  </div>
                )}
              </PermissionGuard>
            </div>

            {selectedVersion ? (
              <PermissionGuard capability="EDIT" workspaceRole={workspace.currentUserRole}>
                {(canEdit) => (
                  <DatasetRowEditor
                    rows={rowsQuery.data?.items ?? []}
                    columns={schemaQuery.data.columns}
                    isEditable={canEdit && isSelectedDraft}
                    isPending={canMutate}
                    onAddRow={handleAddRow}
                    onPatchRow={handlePatchRow}
                  />
                )}
              </PermissionGuard>
            ) : (
              <div className="rounded-md border bg-muted p-4 text-sm text-muted-foreground">
                Dataset chưa có version để hiển thị.
              </div>
            )}

            {selectedVersion ? (
              <div className="mt-4 flex flex-col gap-3 border-t pt-4 sm:flex-row sm:items-center sm:justify-between">
                <div className="text-sm text-muted-foreground">
                  Trang {String(rowsPage + 1)} · {String(rowsQuery.data?.items.length ?? 0)} dòng
                </div>
                <div className="flex gap-2">
                  <Button
                    type="button"
                    variant="outline"
                    disabled={rowsPage === 0 || rowsQuery.isFetching}
                    onClick={() => setRowsPage((current) => Math.max(0, current - 1))}
                  >
                    Trang trước
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    disabled={!rowsQuery.data?.page.hasMore || rowsQuery.isFetching}
                    onClick={() => setRowsPage((current) => current + 1)}
                  >
                    Trang sau
                  </Button>
                </div>
              </div>
            ) : null}
          </section>
        </main>

        <aside className="space-y-6">
          <section className="rounded-lg border bg-surface p-5">
            <div className="mb-4 flex items-center gap-2">
              <GitBranch className="h-5 w-5 text-muted-foreground" aria-hidden="true" />
              <h2 className="text-sm font-semibold">Versions</h2>
            </div>
            <div className="space-y-2">
              {versions.map((version) => {
                const isSelected = version.id === selectedVersion?.id;
                return (
                  <Link
                    key={version.id}
                    to={`/workspaces/${workspace.id}/projects/${project.id}/datasets/${datasetId}/versions/${version.id}`}
                    className={[
                      'block rounded-md border px-3 py-2 text-sm transition-colors',
                      isSelected ? 'border-primary bg-primary/5' : 'hover:bg-muted',
                    ].join(' ')}
                  >
                    <div className="flex items-center justify-between gap-3">
                      <span className="font-medium">Version {version.version}</span>
                      <span className="rounded-full bg-muted px-2 py-0.5 text-xs text-muted-foreground">
                        {versionStatusLabel(version.status)}
                      </span>
                    </div>
                    <div className="mt-1 text-xs text-muted-foreground">
                      {version.rowCount} dòng · Schema v{version.schemaVersion}
                    </div>
                  </Link>
                );
              })}
            </div>
          </section>

          <ExportJobPanel
            title="Export Dataset"
            description="Tạo file XLSX cho Dataset version đang chọn. VIEWER cũng có thể tạo Export."
            source={{ sourceType: 'dataset-version', sourceId: selectedVersion?.id }}
          />

          <section className="rounded-lg border bg-surface p-5">
            <h2 className="text-sm font-semibold">Schema đang dùng</h2>
            <p className="mt-1 text-sm text-muted-foreground">
              Schema lấy từ backend project. UI slice này chỉ hiển thị, không tạo schema thủ công.
            </p>
            <div className="mt-4 space-y-3">
              {schemaQuery.data.columns.map((column) => (
                <div key={column.id} className="rounded-md border p-3">
                  <div className="flex items-center justify-between gap-3">
                    <div>
                      <p className="text-sm font-medium">{column.displayName || column.name}</p>
                      <p className="font-mono text-xs text-muted-foreground">{column.name}</p>
                    </div>
                    <span className="rounded-full bg-muted px-2 py-0.5 text-xs text-muted-foreground">
                      {column.dataType}
                    </span>
                  </div>
                  <p className="mt-2 text-xs text-muted-foreground">
                    {column.required ? 'Bắt buộc' : 'Tùy chọn'} · {column.variable}
                  </p>
                  {column.description && (
                    <p className="mt-1 text-xs text-muted-foreground">{column.description}</p>
                  )}
                </div>
              ))}
            </div>
          </section>
        </aside>
      </div>
    </div>
  );
}
