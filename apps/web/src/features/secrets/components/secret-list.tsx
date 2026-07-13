import { SecretActionsMenu } from './secret-actions-menu';
import { WorkspaceRole } from '@/features/workspaces/types/workspace';
import type { SecretResponse } from '@/features/secrets/types/secret';

export interface SecretListProps {
  secrets: SecretResponse[] | undefined;
  isLoading: boolean;
  workspaceRole?: WorkspaceRole | null;
  onRename: (secret: SecretResponse) => void;
  onRotate: (secret: SecretResponse) => void;
  onDisable: (secret: SecretResponse) => void;
  onDelete: (secret: SecretResponse) => void;
  isActionPending: boolean;
}

export function SecretList({
  secrets,
  isLoading,
  workspaceRole,
  onRename,
  onRotate,
  onDisable,
  onDelete,
  isActionPending,
}: SecretListProps) {
  if (isLoading) {
    return (
      <div className="rounded-md border">
        <div className="p-8 text-center text-muted-foreground text-sm">Đang tải...</div>
      </div>
    );
  }

  if (!secrets?.length) {
    return (
      <div className="rounded-md border">
        <div className="p-8 text-center text-muted-foreground text-sm">Chưa có secret nào.</div>
      </div>
    );
  }

  return (
    <div className="rounded-md border">
      <div className="w-full overflow-auto">
        <table className="w-full text-sm">
          <thead className="border-b bg-muted/50 text-left">
            <tr>
              <th className="px-4 py-3 font-medium text-muted-foreground">Tên hiển thị</th>
              <th className="px-4 py-3 font-medium text-muted-foreground">Trạng thái</th>
              <th className="px-4 py-3 font-medium text-muted-foreground">Preview</th>
              <th className="px-4 py-3 font-medium text-muted-foreground">Ngày tạo</th>
              <th className="px-4 py-3 font-medium text-muted-foreground text-right">Thao tác</th>
            </tr>
          </thead>
          <tbody className="divide-y">
            {secrets.map((secret) => (
              <tr key={secret.id} className="hover:bg-muted/30">
                <td className="px-4 py-3 font-medium">{secret.name}</td>
                <td className="px-4 py-3">
                  <span
                    className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${
                      secret.status === 'ACTIVE'
                        ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'
                        : 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400'
                    }`}
                  >
                    {secret.status === 'ACTIVE' ? 'Hoạt động' : 'Vô hiệu hóa'}
                  </span>
                </td>
                <td className="px-4 py-3 font-mono text-muted-foreground">
                  {secret.maskedPreview}
                </td>
                <td className="px-4 py-3 text-muted-foreground">
                  {new Date(secret.createdAt).toLocaleDateString('vi-VN')}
                </td>
                <td className="px-4 py-3 text-right">
                  <SecretActionsMenu
                    secret={secret}
                    workspaceRole={workspaceRole}
                    onRename={onRename}
                    onRotate={onRotate}
                    onDisable={onDisable}
                    onDelete={onDelete}
                    isPending={isActionPending}
                  />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
