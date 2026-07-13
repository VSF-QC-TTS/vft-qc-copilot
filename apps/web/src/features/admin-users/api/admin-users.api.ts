import { apiClient } from '@/shared/api/api';
import {
  adminCreateUserResponseSchema,
  type AdminCreateUserRequest,
} from '@/features/admin-users/types/admin-user';

export async function createAdminUser(data: AdminCreateUserRequest) {
  const response = await apiClient.requestJson(
    '/api/v1/admin/users',
    adminCreateUserResponseSchema,
    {
      method: 'POST',
      body: JSON.stringify(data),
    },
  );
  return response.data;
}
