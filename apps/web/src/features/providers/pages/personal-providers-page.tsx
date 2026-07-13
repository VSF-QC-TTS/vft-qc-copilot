import { useState } from 'react';
import { Plus } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { ProviderList } from '@/features/providers/components/provider-list';
import { CreateProviderDialog } from '@/features/providers/components/create-provider-dialog';
import { EditProviderDialog } from '@/features/providers/components/edit-provider-dialog';
import { TestProviderDialog } from '@/features/providers/components/test-provider-dialog';
import {
  usePersonalProviders,
  useCreatePersonalProvider,
  useUpdateProvider,
  useDeleteProvider,
  useTestProvider,
} from '@/features/providers/hooks/use-providers';
import { usePersonalSecretsQuery } from '@/features/secrets/hooks/use-secrets';
import type {
  ProviderResponse,
  CreateProviderRequest,
  UpdateProviderRequest,
  TestProviderRequest,
} from '@/features/providers/types/provider';
import {
  createProviderRequestSchema,
  updateProviderRequestSchema,
  testProviderRequestSchema,
} from '@/features/providers/types/provider';
import { notifySuccess, notifyError } from '@/shared/lib/notifications';

export function PersonalProvidersPage() {
  const { data: providers, isLoading, isError } = usePersonalProviders();
  const { data: secrets, isLoading: isLoadingSecrets } = usePersonalSecretsQuery();

  const createMutation = useCreatePersonalProvider();
  const updateMutation = useUpdateProvider();
  const deleteMutation = useDeleteProvider();
  const testMutation = useTestProvider();

  const [createOpen, setCreateOpen] = useState(false);

  const [editingProvider, setEditingProvider] = useState<ProviderResponse | null>(null);
  const [testingProvider, setTestingProvider] = useState<ProviderResponse | null>(null);

  const handleCreateSubmit = async (data: CreateProviderRequest) => {
    try {
      const parsedData = createProviderRequestSchema.parse(data);
      await createMutation.mutateAsync(parsedData);
      notifySuccess('Thêm provider thành công');
      setCreateOpen(false);
    } catch (err) {
      notifyError('Không thể thêm provider', undefined);
      throw err; // Let form handle 409
    }
  };

  const handleEditSubmit = async (data: UpdateProviderRequest) => {
    if (!editingProvider) return;
    try {
      const parsedData = updateProviderRequestSchema.parse(data);
      await updateMutation.mutateAsync({ providerId: editingProvider.id, data: parsedData });
      notifySuccess('Cập nhật provider thành công');
      setEditingProvider(null);
    } catch (err) {
      notifyError('Không thể cập nhật provider', undefined);
      throw err;
    }
  };

  const handleDelete = async (provider: ProviderResponse) => {
    try {
      await deleteMutation.mutateAsync(provider.id);
      notifySuccess('Xóa provider thành công');
    } catch {
      notifyError('Không thể xóa provider', undefined);
    }
  };

  const handleTestSubmit = async (data: TestProviderRequest) => {
    if (!testingProvider) throw new Error('No provider selected');
    const parsedData = testProviderRequestSchema.parse(data);
    return testMutation.mutateAsync({ providerId: testingProvider.id, data: parsedData });
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-lg font-medium">Personal LLM Providers</h2>
          <p className="text-sm text-muted-foreground">Quản lý các kết nối LLM cá nhân.</p>
        </div>
        <Button onClick={() => setCreateOpen(true)}>
          <Plus className="mr-2 h-4 w-4" />
          Thêm provider
        </Button>
      </div>

      <ProviderList
        providers={providers}
        isLoading={isLoading || isLoadingSecrets}
        isError={isError}
        workspaceRole={null}
        onEdit={setEditingProvider}
        onTest={setTestingProvider}
        onDelete={(provider) => void handleDelete(provider)}
        isPending={deleteMutation.isPending}
      />

      <CreateProviderDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
        onSubmit={handleCreateSubmit}
        isPending={createMutation.isPending}
        secrets={secrets?.items}
      />

      <EditProviderDialog
        open={!!editingProvider}
        onOpenChange={(open) => !open && setEditingProvider(null)}
        provider={editingProvider}
        onSubmit={handleEditSubmit}
        isPending={updateMutation.isPending}
        secrets={secrets?.items}
      />

      <TestProviderDialog
        open={!!testingProvider}
        onOpenChange={(open) => !open && setTestingProvider(null)}
        provider={testingProvider}
        onSubmit={handleTestSubmit}
        isPending={testMutation.isPending}
      />
    </div>
  );
}
