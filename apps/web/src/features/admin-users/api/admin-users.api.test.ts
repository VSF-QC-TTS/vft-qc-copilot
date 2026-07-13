import { beforeEach, describe, expect, it, vi } from 'vitest';

import { createAdminUser } from '@/features/admin-users/api/admin-users.api';
import { adminCreateUserResponseSchema } from '@/features/admin-users/types/admin-user';

const requestJsonMock = vi.hoisted(() =>
  vi.fn<(path: string, schema: unknown, init?: RequestInit) => Promise<{ data: unknown }>>(),
);

vi.mock('@/shared/api/api', () => ({
  apiClient: {
    requestJson: requestJsonMock,
  },
}));

const USER_ID = '11111111-1111-4111-8111-111111111111';

describe('admin users API', () => {
  beforeEach(() => {
    requestJsonMock.mockReset();
  });

  it('creates an internal user invite through the admin namespace', async () => {
    const response = adminCreateUserResponse();
    requestJsonMock.mockResolvedValueOnce({ data: response });

    await expect(
      createAdminUser({
        email: 'user@vinfast.vn',
        displayName: 'Nguyen Van A',
      }),
    ).resolves.toEqual(response);

    expect(requestJsonMock).toHaveBeenCalledWith('/api/v1/admin/users', expect.any(Object), {
      method: 'POST',
      body: JSON.stringify({
        email: 'user@vinfast.vn',
        displayName: 'Nguyen Van A',
      }),
    });
  });

  it('validates backend invite response and rejects unsupported status', () => {
    expect(adminCreateUserResponseSchema.safeParse(adminCreateUserResponse()).success).toBe(true);
    expect(
      adminCreateUserResponseSchema.safeParse({
        ...adminCreateUserResponse(),
        status: 'INVITED',
      }).success,
    ).toBe(false);
  });
});

function adminCreateUserResponse() {
  return {
    id: USER_ID,
    email: 'user@vinfast.vn',
    user: {
      id: USER_ID,
      email: 'user@vinfast.vn',
      name: 'Nguyen Van A',
      avatarUrl: null,
    },
    status: 'PENDING_EMAIL_VERIFICATION',
    message: 'User created. Invite email sent.',
  };
}
