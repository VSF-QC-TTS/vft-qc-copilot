import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { SecretActionsMenu } from './secret-actions-menu';
import type { SecretResponse } from '@/features/secrets/types/secret';
import type { CurrentUserResponse } from '@/features/auth/types/auth.types';

type CurrentUserQueryResult = {
  data?: {
    data: CurrentUserResponse;
  };
};

const useCurrentUserQueryMock = vi.hoisted(() => vi.fn<() => CurrentUserQueryResult>());

vi.mock('@/features/auth/hooks/use-auth-queries', () => ({
  useCurrentUserQuery: useCurrentUserQueryMock,
}));

describe('SecretActionsMenu', () => {
  const mockOnRename = vi.fn<(secret: SecretResponse) => void>();
  const mockOnRotate = vi.fn<(secret: SecretResponse) => void>();
  const mockOnDisable = vi.fn<(secret: SecretResponse) => void>();
  const mockOnDelete = vi.fn<(secret: SecretResponse) => void>();

  const mockSecret: SecretResponse = {
    id: 'sec-1',
    scope: 'WORKSPACE',
    ownerUserId: 'user-1',
    ownerUser: { id: 'user-1', email: 'test@example.com', name: 'Test' },
    workspaceId: 'ws-1',
    name: 'Test Secret',
    maskedPreview: 'abc***123',
    status: 'ACTIVE',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  };

  const normalUser: CurrentUserResponse = {
    id: 'user-2',
    email: 'user2@example.com',
    displayName: 'User 2',
    systemRole: 'USER',
    status: 'ACTIVE',
  };

  const mockCurrentUser = () => {
    useCurrentUserQueryMock.mockReturnValue({ data: { data: normalUser } });
  };

  it('renders actions for MEMBER (has EDIT capability)', async () => {
    mockCurrentUser();

    render(
      <SecretActionsMenu
        secret={mockSecret}
        workspaceRole="MEMBER"
        onRename={mockOnRename}
        onRotate={mockOnRotate}
        onDisable={mockOnDisable}
        onDelete={mockOnDelete}
        isPending={false}
      />,
    );

    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Mở menu' }));

    expect(await screen.findByRole('menuitem', { name: 'Đổi tên' })).not.toBeDisabled();
    expect(await screen.findByRole('menuitem', { name: 'Xoay vòng giá trị' })).not.toBeDisabled();
  });

  it('disables actions for VIEWER', async () => {
    mockCurrentUser();

    render(
      <SecretActionsMenu
        secret={mockSecret}
        workspaceRole="VIEWER"
        onRename={mockOnRename}
        onRotate={mockOnRotate}
        onDisable={mockOnDisable}
        onDelete={mockOnDelete}
        isPending={false}
      />,
    );

    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Mở menu' }));

    expect(await screen.findByRole('menuitem', { name: 'Đổi tên' })).toHaveAttribute(
      'aria-disabled',
      'true',
    );
    expect(await screen.findByRole('menuitem', { name: 'Xoay vòng giá trị' })).toHaveAttribute(
      'aria-disabled',
      'true',
    );
  });

  it('allows actions unconditionally for PERSONAL secrets', async () => {
    mockCurrentUser();

    const personalSecret = { ...mockSecret, scope: 'PERSONAL' as const };

    render(
      <SecretActionsMenu
        secret={personalSecret}
        workspaceRole={null}
        onRename={mockOnRename}
        onRotate={mockOnRotate}
        onDisable={mockOnDisable}
        onDelete={mockOnDelete}
        isPending={false}
      />,
    );

    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Mở menu' }));
    expect(await screen.findByRole('menuitem', { name: 'Đổi tên' })).not.toBeDisabled();
  });
});
