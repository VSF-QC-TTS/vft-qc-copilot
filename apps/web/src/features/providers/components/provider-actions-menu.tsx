import { useState } from 'react';
import { MoreHorizontal, Pencil, TestTube, Trash2 } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/shared/components/ui/dropdown-menu';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/shared/components/ui/alert-dialog';
import { PermissionGuard } from '@/shared/components/permission-guard';
import { WorkspaceRole } from '@/features/workspaces/types/workspace';
import type { ProviderResponse } from '@/features/providers/types/provider';

export interface ProviderActionsMenuProps {
  provider: ProviderResponse;
  workspaceRole?: WorkspaceRole | null | undefined;
  onEdit: (provider: ProviderResponse) => void;
  onTest: (provider: ProviderResponse) => void;
  onDelete: (provider: ProviderResponse) => void;
  isPending: boolean;
}

export function ProviderActionsMenu({
  provider,
  workspaceRole,
  onEdit,
  onTest,
  onDelete,
  isPending,
}: ProviderActionsMenuProps) {
  const [showDeleteAlert, setShowDeleteAlert] = useState(false);

  const isWorkspace = provider.scope === 'WORKSPACE';

  const renderMenuItems = (isAllowed: boolean) => (
    <DropdownMenuContent align="end">
      <DropdownMenuItem onClick={() => onEdit(provider)} disabled={isPending || !isAllowed}>
        <Pencil className="mr-2 h-4 w-4" />
        Chỉnh sửa
      </DropdownMenuItem>
      <DropdownMenuItem onClick={() => onTest(provider)} disabled={isPending || !isAllowed}>
        <TestTube className="mr-2 h-4 w-4" />
        Kiểm tra
      </DropdownMenuItem>
      <DropdownMenuSeparator />
      <DropdownMenuItem
        onClick={() => setShowDeleteAlert(true)}
        disabled={isPending || !isAllowed}
        className="text-destructive focus:text-destructive"
      >
        <Trash2 className="mr-2 h-4 w-4" />
        Xóa
      </DropdownMenuItem>
    </DropdownMenuContent>
  );

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button variant="ghost" className="h-8 w-8 p-0">
            <span className="sr-only">Mở menu</span>
            <MoreHorizontal className="h-4 w-4" />
          </Button>
        </DropdownMenuTrigger>
        {isWorkspace ? (
          <PermissionGuard capability="EDIT" workspaceRole={workspaceRole}>
            {(isAllowed) => renderMenuItems(isAllowed)}
          </PermissionGuard>
        ) : (
          renderMenuItems(true)
        )}
      </DropdownMenu>

      <AlertDialog open={showDeleteAlert} onOpenChange={setShowDeleteAlert}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Xóa Provider?</AlertDialogTitle>
            <AlertDialogDescription>
              Bạn có chắc chắn muốn xóa provider{' '}
              <span className="font-medium text-foreground">{provider.name}</span>? Hành động này
              không thể hoàn tác.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={isPending}>Hủy</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              onClick={() => {
                setShowDeleteAlert(false);
                onDelete(provider);
              }}
              disabled={isPending}
            >
              Xóa
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
