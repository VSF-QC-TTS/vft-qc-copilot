import { Skeleton } from '@/shared/components/ui/skeleton';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/components/ui/table';
import { ProviderActionsMenu } from './provider-actions-menu';
import type { ProviderResponse } from '@/features/providers/types/provider';
import type { WorkspaceRole } from '@/features/workspaces/types/workspace';

export interface ProviderListProps {
  providers?: ProviderResponse[] | undefined;
  isLoading: boolean;
  isError: boolean;
  workspaceRole?: WorkspaceRole | null | undefined;
  onEdit: (provider: ProviderResponse) => void;
  onTest: (provider: ProviderResponse) => void;
  onDelete: (provider: ProviderResponse) => void;
  isPending: boolean;
}

export function ProviderList({
  providers,
  isLoading,
  isError,
  workspaceRole,
  onEdit,
  onTest,
  onDelete,
  isPending,
}: ProviderListProps) {
  if (isLoading) {
    return (
      <div className="space-y-4">
        {Array.from({ length: 3 }).map((_, i) => (
          <div key={`skeleton-${String(i)}`} className="flex items-center space-x-4">
            <Skeleton className="h-12 w-full" />
          </div>
        ))}
      </div>
    );
  }

  if (isError) {
    return (
      <div className="flex flex-col items-center justify-center p-8 text-center text-muted-foreground">
        <p>Không thể tải danh sách providers. Vui lòng thử lại sau.</p>
      </div>
    );
  }

  if (!providers || providers.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center p-8 text-center border rounded-lg border-dashed">
        <p className="text-muted-foreground">Không có provider nào</p>
      </div>
    );
  }

  return (
    <div className="rounded-md border">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Tên hiển thị</TableHead>
            <TableHead>Loại</TableHead>
            <TableHead>Trạng thái</TableHead>
            <TableHead>Default Model</TableHead>
            <TableHead>Secret sử dụng</TableHead>
            <TableHead>Ngày tạo</TableHead>
            <TableHead className="w-[50px]"></TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {providers.map((provider) => (
            <TableRow key={provider.id}>
              <TableCell className="font-medium">{provider.name}</TableCell>
              <TableCell>
                <span className="inline-flex items-center rounded-full bg-secondary px-2.5 py-0.5 text-xs font-semibold text-secondary-foreground">
                  {provider.providerType}
                </span>
              </TableCell>
              <TableCell>
                <span
                  className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${
                    provider.status === 'ACTIVE'
                      ? 'bg-primary/10 text-primary'
                      : provider.status === 'TEST_FAILED'
                        ? 'bg-destructive/10 text-destructive'
                        : 'bg-muted text-muted-foreground'
                  }`}
                >
                  {provider.status === 'ACTIVE'
                    ? 'Hoạt động'
                    : provider.status === 'TEST_FAILED'
                      ? 'Kiểm tra lỗi'
                      : 'Vô hiệu hóa'}
                </span>
              </TableCell>
              <TableCell className="text-muted-foreground">{provider.defaultModel}</TableCell>
              <TableCell className="text-muted-foreground">{provider.secret.name}</TableCell>
              <TableCell className="text-muted-foreground">
                {new Date(provider.createdAt).toLocaleDateString('vi-VN')}
              </TableCell>
              <TableCell>
                <ProviderActionsMenu
                  provider={provider}
                  workspaceRole={workspaceRole}
                  onEdit={onEdit}
                  onTest={onTest}
                  onDelete={onDelete}
                  isPending={isPending}
                />
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}
