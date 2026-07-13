import { useState } from 'react';
import { Plus } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { notifyError, notifySuccess } from '@/shared/lib/notifications';
import {
  usePersonalSecretsQuery,
  useCreatePersonalSecretMutation,
  useUpdateSecretMutation,
  useRotateSecretMutation,
  useDisableSecretMutation,
  useDeleteSecretMutation,
} from '@/features/secrets/hooks/use-secrets';
import { SecretList } from '@/features/secrets/components/secret-list';
import { CreateSecretDialog } from '@/features/secrets/components/create-secret-dialog';
import { RenameSecretDialog } from '@/features/secrets/components/rename-secret-dialog';
import { RotateSecretDialog } from '@/features/secrets/components/rotate-secret-dialog';
import type { SecretResponse } from '@/features/secrets/types/secret';

export function PersonalSecretsPage() {
  const { data: secretsData, isLoading } = usePersonalSecretsQuery();

  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [secretToRename, setSecretToRename] = useState<SecretResponse | null>(null);
  const [secretToRotate, setSecretToRotate] = useState<SecretResponse | null>(null);

  const createMutation = useCreatePersonalSecretMutation();
  const updateMutation = useUpdateSecretMutation();
  const rotateMutation = useRotateSecretMutation();
  const disableMutation = useDisableSecretMutation();
  const deleteMutation = useDeleteSecretMutation();

  const isAnyActionPending =
    createMutation.isPending ||
    updateMutation.isPending ||
    rotateMutation.isPending ||
    disableMutation.isPending ||
    deleteMutation.isPending;

  const handleDisable = (secret: SecretResponse) => {
    disableMutation.mutate(secret.id, {
      onSuccess: () => notifySuccess('Đã vô hiệu hóa secret'),
      onError: () => notifyError('Không thể vô hiệu hóa secret'),
    });
  };

  const handleDelete = (secret: SecretResponse) => {
    deleteMutation.mutate(secret.id, {
      onSuccess: () => notifySuccess('Đã xóa secret'),
      onError: () => notifyError('Không thể xóa secret'),
    });
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h3 className="text-lg font-medium">Personal Secrets</h3>
          <p className="text-sm text-muted-foreground">
            Quản lý các secret cá nhân của bạn (không chia sẻ với ai).
          </p>
        </div>
        <Button onClick={() => setIsCreateOpen(true)}>
          <Plus className="mr-2 h-4 w-4" />
          Thêm secret
        </Button>
      </div>

      <SecretList
        secrets={secretsData?.items}
        isLoading={isLoading}
        onRename={setSecretToRename}
        onRotate={setSecretToRotate}
        onDisable={handleDisable}
        onDelete={handleDelete}
        isActionPending={isAnyActionPending}
      />

      <CreateSecretDialog
        open={isCreateOpen}
        onOpenChange={setIsCreateOpen}
        onSubmit={async (data) => {
          await createMutation.mutateAsync(data);
        }}
        isPending={createMutation.isPending}
        scopeLabel="cá nhân"
      />

      {secretToRename && (
        <RenameSecretDialog
          open={!!secretToRename}
          onOpenChange={(open) => !open && setSecretToRename(null)}
          onSubmit={async (data) => {
            await updateMutation.mutateAsync({ secretId: secretToRename.id, data });
          }}
          isPending={updateMutation.isPending}
          secretName={secretToRename.name}
        />
      )}

      {secretToRotate && (
        <RotateSecretDialog
          open={!!secretToRotate}
          onOpenChange={(open) => !open && setSecretToRotate(null)}
          onSubmit={async (data) => {
            await rotateMutation.mutateAsync({ secretId: secretToRotate.id, data });
          }}
          isPending={rotateMutation.isPending}
          secretName={secretToRotate.name}
        />
      )}
    </div>
  );
}
