import { useState } from 'react';
import { MoreHorizontal, Pencil, KeyRound, Ban, Trash2 } from 'lucide-react';
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
import type { SecretResponse } from '@/features/secrets/types/secret';

export interface SecretActionsMenuProps {
  secret: SecretResponse;
  workspaceRole?: WorkspaceRole | null | undefined;
  onRename: (secret: SecretResponse) => void;
  onRotate: (secret: SecretResponse) => void;
  onDisable: (secret: SecretResponse) => void;
  onDelete: (secret: SecretResponse) => void;
  isPending: boolean;
}

export function SecretActionsMenu({
  secret,
  workspaceRole,
  onRename,
  onRotate,
  onDisable,
  onDelete,
  isPending,
}: SecretActionsMenuProps) {
  const [showDisableAlert, setShowDisableAlert] = useState(false);
  const [showDeleteAlert, setShowDeleteAlert] = useState(false);

  // If it's a personal secret, the owner always has permission.
  // If it's a workspace secret, we require EDIT capability to manage it.
  const isWorkspace = secret.scope === 'WORKSPACE';

  const renderMenuItems = (isAllowed: boolean) => (
    <DropdownMenuContent align="end">
      <DropdownMenuItem onClick={() => onRename(secret)} disabled={isPending || !isAllowed}>
        <Pencil className="mr-2 h-4 w-4" />
        Đổi tên
      </DropdownMenuItem>
      <DropdownMenuItem onClick={() => onRotate(secret)} disabled={isPending || !isAllowed}>
        <KeyRound className="mr-2 h-4 w-4" />
        Xoay vòng giá trị
      </DropdownMenuItem>
      {secret.status === 'ACTIVE' && (
        <>
          <DropdownMenuSeparator />
          <DropdownMenuItem
            onClick={() => setShowDisableAlert(true)}
            disabled={isPending || !isAllowed}
            className="text-destructive focus:text-destructive"
          >
            <Ban className="mr-2 h-4 w-4" />
            Vô hiệu hóa
          </DropdownMenuItem>
        </>
      )}
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

      <AlertDialog open={showDisableAlert} onOpenChange={setShowDisableAlert}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Vô hiệu hóa secret?</AlertDialogTitle>
            <AlertDialogDescription>
              Bạn có chắc chắn muốn vô hiệu hóa secret{' '}
              <span className="font-medium text-foreground">{secret.name}</span>? Các ứng dụng đang
              sử dụng secret này sẽ không thể truy cập được nữa.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={isPending}>Hủy</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              onClick={() => {
                setShowDisableAlert(false);
                onDisable(secret);
              }}
              disabled={isPending}
            >
              Vô hiệu hóa
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <AlertDialog open={showDeleteAlert} onOpenChange={setShowDeleteAlert}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Xóa secret?</AlertDialogTitle>
            <AlertDialogDescription>
              Bạn có chắc chắn muốn xóa secret{' '}
              <span className="font-medium text-foreground">{secret.name}</span>? Hành động này
              không thể hoàn tác.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={isPending}>Hủy</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              onClick={() => {
                setShowDeleteAlert(false);
                onDelete(secret);
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
