import { useMutation } from '@tanstack/react-query';
import { createAdminUser } from '@/features/admin-users/api/admin-users.api';

export function useCreateAdminUser() {
  return useMutation({
    mutationFn: createAdminUser,
  });
}
