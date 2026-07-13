import { useState } from 'react';
import { useOutletContext } from 'react-router';
import { WorkspaceContextType } from '@/app/layouts/workspace-context-layout';
import {
  useWorkspaceMembers,
  useUpdateWorkspaceMemberRoleMutation,
} from '@/features/workspaces/hooks/use-workspaces';
import { PermissionGuard } from '@/shared/components/permission-guard';
import { notifyError, notifySuccess } from '@/shared/lib/notifications';
import { isApiError } from '@/shared/api/api-error';
import { WorkspaceRole, workspaceRoleSchema } from '@/features/workspaces/types/workspace';
import { Button } from '@/shared/components/ui/button';
import { AddWorkspaceMemberDialog } from '@/features/workspaces/components/add-workspace-member-dialog';
import { Skeleton } from '@/shared/components/ui/skeleton';
import { canManage } from '@/shared/lib/permissions';
import { useCurrentUserQuery } from '@/features/auth/hooks/use-auth-queries';

export function WorkspaceSettingsMembersPage() {
  const { workspace } = useOutletContext<WorkspaceContextType>();
  const { data: members, isLoading, error } = useWorkspaceMembers(workspace.id);
  const updateRoleMutation = useUpdateWorkspaceMemberRoleMutation(workspace.id);
  const { data: userResponse } = useCurrentUserQuery();

  const [isAddDialogOpen, setIsAddDialogOpen] = useState(false);

  const handleRoleChange = (memberId: number, newRole: WorkspaceRole) => {
    updateRoleMutation.mutate(
      { memberId, data: { role: newRole } },
      {
        onSuccess: () => {
          notifySuccess('Đã cập nhật vai trò thành viên');
        },
        onError: (err) => {
          if (isApiError(err) && err.status === 403) {
            notifyError('Bạn không có quyền thực hiện hành động này');
          } else {
            notifyError('Không thể cập nhật vai trò');
          }
        },
      },
    );
  };

  const handleRoleSelectChange = (memberId: number, value: string) => {
    const parsedRole = workspaceRoleSchema.safeParse(value);
    if (parsedRole.success) {
      handleRoleChange(memberId, parsedRole.data);
    }
  };

  const isManager = canManage(workspace.currentUserRole, userResponse?.data);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h3 className="text-lg font-medium">Thành viên</h3>
          <p className="text-sm text-muted-foreground">
            Quản lý những ai có quyền truy cập vào workspace này.
          </p>
        </div>
        <PermissionGuard capability="MANAGE" workspaceRole={workspace.currentUserRole} hide>
          <Button onClick={() => setIsAddDialogOpen(true)}>Thêm thành viên</Button>
        </PermissionGuard>
      </div>

      <div className="rounded-md border">
        {isLoading ? (
          <div className="p-8 flex flex-col space-y-4">
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-10 w-full" />
          </div>
        ) : error ? (
          <div className="p-8 text-center text-muted-foreground text-sm">
            Không thể tải danh sách thành viên.
          </div>
        ) : !members?.items.length ? (
          <div className="p-8 text-center text-muted-foreground text-sm">
            Chưa có thành viên nào.
          </div>
        ) : (
          <div className="w-full overflow-x-auto">
            <table className="w-full text-sm text-left border-collapse">
              <thead className="bg-muted/50 border-b">
                <tr>
                  <th className="px-4 py-3 font-medium">Tên</th>
                  <th className="px-4 py-3 font-medium">Email</th>
                  <th className="px-4 py-3 font-medium">Trạng thái</th>
                  <th className="px-4 py-3 font-medium">Vai trò</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {members.items.map((member) => (
                  <tr key={member.id} className="hover:bg-muted/30">
                    <td className="px-4 py-3">{member.user.name}</td>
                    <td className="px-4 py-3">{member.user.email}</td>
                    <td className="px-4 py-3">
                      {member.status === 'ACTIVE'
                        ? 'Đang hoạt động'
                        : member.status === 'INVITED'
                          ? 'Đã mời'
                          : 'Vô hiệu hóa'}
                    </td>
                    <td className="px-4 py-3">
                      {isManager && member.user.id !== userResponse?.data.id ? (
                        <select
                          className="flex h-8 w-full max-w-[140px] items-center justify-between rounded-md border border-input bg-transparent px-2 py-1 text-xs shadow-sm focus:outline-none focus:ring-1 focus:ring-ring"
                          value={member.role}
                          onChange={(e) => handleRoleSelectChange(member.id, e.target.value)}
                          disabled={updateRoleMutation.isPending}
                        >
                          <option value="OWNER">Chủ sở hữu</option>
                          <option value="ADMIN">Quản trị viên</option>
                          <option value="MEMBER">Thành viên</option>
                          <option value="VIEWER">Người xem</option>
                        </select>
                      ) : (
                        <span className="px-2 py-1 text-xs font-medium">
                          {member.role === 'OWNER'
                            ? 'Chủ sở hữu'
                            : member.role === 'ADMIN'
                              ? 'Quản trị viên'
                              : member.role === 'MEMBER'
                                ? 'Thành viên'
                                : 'Người xem'}
                        </span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <AddWorkspaceMemberDialog
        workspaceId={workspace.id}
        open={isAddDialogOpen}
        onOpenChange={setIsAddDialogOpen}
      />
    </div>
  );
}
