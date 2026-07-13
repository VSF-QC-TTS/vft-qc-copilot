import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AdminUsersPage } from '@/features/admin-users/pages/admin-users-page';
import type {
  AdminCreateUserRequest,
  AdminCreateUserResponse,
} from '@/features/admin-users/types/admin-user';
import { ApiError } from '@/shared/api/api-error';

const createUserMutateAsyncMock = vi.hoisted(() =>
  vi.fn<(data: AdminCreateUserRequest) => Promise<AdminCreateUserResponse>>(),
);

vi.mock('@/features/admin-users/hooks/use-admin-users', () => ({
  useCreateAdminUser: () => ({
    mutateAsync: createUserMutateAsyncMock,
    isPending: false,
  }),
}));

vi.mock('@/shared/lib/notifications', () => ({
  notifyError: vi.fn<(message: string) => void>(),
  notifySuccess: vi.fn<(message: string) => void>(),
}));

describe('AdminUsersPage', () => {
  beforeEach(() => {
    createUserMutateAsyncMock.mockReset();
  });

  it('creates a user invite and shows the latest invite summary', async () => {
    createUserMutateAsyncMock.mockResolvedValueOnce(adminCreateUserResponse());

    render(<AdminUsersPage />);

    fireEvent.change(screen.getByLabelText('Email'), {
      target: { value: ' user@vinfast.vn ' },
    });
    fireEvent.change(screen.getByLabelText('Tên hiển thị'), {
      target: { value: ' Nguyen Van A ' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Gửi invite' }));

    await waitFor(() =>
      expect(createUserMutateAsyncMock).toHaveBeenCalledWith({
        email: 'user@vinfast.vn',
        displayName: 'Nguyen Van A',
      }),
    );
    expect(screen.getByText('PENDING_EMAIL_VERIFICATION')).toBeInTheDocument();
    expect(screen.getAllByText('user@vinfast.vn').length).toBeGreaterThan(0);
  });

  it('handles duplicate and permission backend errors inline', async () => {
    createUserMutateAsyncMock.mockRejectedValueOnce(new ApiError({ status: 409 }));
    const { rerender } = render(<AdminUsersPage />);

    fireEvent.change(screen.getByLabelText('Email'), {
      target: { value: 'user@vinfast.vn' },
    });
    fireEvent.change(screen.getByLabelText('Tên hiển thị'), {
      target: { value: 'Nguyen Van A' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Gửi invite' }));

    expect(await screen.findByText('Email đã tồn tại')).toBeInTheDocument();

    createUserMutateAsyncMock.mockRejectedValueOnce(new ApiError({ status: 403 }));
    rerender(<AdminUsersPage />);
    fireEvent.change(screen.getByLabelText('Email'), {
      target: { value: 'other@vinfast.vn' },
    });
    fireEvent.change(screen.getByLabelText('Tên hiển thị'), {
      target: { value: 'Other User' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Gửi invite' }));

    expect(
      await screen.findByText('Tài khoản hiện tại không có quyền PLATFORM_ADMIN.'),
    ).toBeInTheDocument();
  });
});

function adminCreateUserResponse(): AdminCreateUserResponse {
  return {
    id: '11111111-1111-4111-8111-111111111111',
    email: 'user@vinfast.vn',
    user: {
      id: '11111111-1111-4111-8111-111111111111',
      email: 'user@vinfast.vn',
      name: 'Nguyen Van A',
      avatarUrl: null,
    },
    status: 'PENDING_EMAIL_VERIFICATION',
    message: 'User created. Invite email sent.',
  };
}
