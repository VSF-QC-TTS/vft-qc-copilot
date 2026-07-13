import { Link } from 'react-router';
import { MoreHorizontal, Pencil, Trash2 } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/shared/components/ui/dropdown-menu';
import { Skeleton } from '@/shared/components/ui/skeleton';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/components/ui/table';
import type { ChatbotTargetResponse } from '@/features/chatbot-targets/types/chatbot-target';
import { statusLabel } from '@/features/chatbot-targets/components/target-form-utils';

export interface TargetListProps {
  targets: ChatbotTargetResponse[] | undefined;
  workspaceId: string;
  projectId: string;
  isLoading: boolean;
  isError: boolean;
  canEdit: boolean;
  isPending: boolean;
  onDelete: (target: ChatbotTargetResponse) => void;
}

export function TargetList({
  targets,
  workspaceId,
  projectId,
  isLoading,
  isError,
  canEdit,
  isPending,
  onDelete,
}: TargetListProps) {
  if (isLoading) {
    return (
      <div className="space-y-3">
        <Skeleton className="h-12 w-full" />
        <Skeleton className="h-12 w-full" />
        <Skeleton className="h-12 w-full" />
      </div>
    );
  }

  if (isError) {
    return <div className="rounded-lg border p-6 text-destructive">Không thể tải target.</div>;
  }

  if (!targets?.length) {
    return (
      <div className="rounded-lg border border-dashed p-8 text-center text-muted-foreground">
        Chưa có chatbot target nào trong project này.
      </div>
    );
  }

  return (
    <div className="rounded-md border">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Tên target</TableHead>
            <TableHead>Method</TableHead>
            <TableHead>Trạng thái</TableHead>
            <TableHead>Extraction</TableHead>
            <TableHead>Cập nhật</TableHead>
            <TableHead className="w-[48px]"></TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {targets.map((target) => (
            <TableRow key={target.id}>
              <TableCell>
                <Link
                  to={`/workspaces/${workspaceId}/projects/${projectId}/targets/${target.id}`}
                  className="font-medium hover:underline"
                >
                  {target.name}
                </Link>
                {target.description && (
                  <p className="mt-1 line-clamp-1 text-xs text-muted-foreground">
                    {target.description}
                  </p>
                )}
              </TableCell>
              <TableCell className="font-mono text-xs">
                {target.requestConfig?.method ?? '-'}
              </TableCell>
              <TableCell>{statusLabel(target.status)}</TableCell>
              <TableCell>
                {(target.responseExtractionConfig?.fields.length ?? 0) > 0
                  ? `${target.responseExtractionConfig?.fields.length ?? 0} field`
                  : 'Chưa cấu hình'}
              </TableCell>
              <TableCell className="text-muted-foreground">
                {new Date(target.updatedAt).toLocaleDateString('vi-VN')}
              </TableCell>
              <TableCell>
                <DropdownMenu>
                  <DropdownMenuTrigger asChild>
                    <Button variant="ghost" className="h-8 w-8 p-0" disabled={isPending}>
                      <span className="sr-only">Mở menu</span>
                      <MoreHorizontal className="h-4 w-4" />
                    </Button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end">
                    <DropdownMenuItem asChild>
                      <Link
                        to={`/workspaces/${workspaceId}/projects/${projectId}/targets/${target.id}`}
                      >
                        <Pencil className="mr-2 h-4 w-4" />
                        Xem chi tiết
                      </Link>
                    </DropdownMenuItem>
                    <DropdownMenuSeparator />
                    <DropdownMenuItem
                      className="text-destructive focus:text-destructive"
                      disabled={!canEdit || isPending}
                      onClick={() => onDelete(target)}
                    >
                      <Trash2 className="mr-2 h-4 w-4" />
                      Xóa
                    </DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}
