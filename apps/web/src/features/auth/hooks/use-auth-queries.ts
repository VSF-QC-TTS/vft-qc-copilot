import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  forgotPassword,
  getCurrentUser,
  login,
  logout,
  register,
  resetPassword,
  verifyEmail,
} from '@/features/auth/api/auth.api';
import { authKeys } from '@/features/auth/hooks/auth.keys';
import { accessTokenStore } from '@/shared/api/api';

export function useCurrentUserQuery(enabled = true) {
  return useQuery({
    queryKey: authKeys.currentUser(),
    queryFn: ({ signal }) => getCurrentUser(signal),
    enabled,
    staleTime: 5 * 60 * 1000,
    retry: false, // Do not retry on 401
  });
}

export function useLoginMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: login,
    onSuccess: (response) => {
      accessTokenStore.setAccessToken(response.data.accessToken);
      queryClient.setQueryData(authKeys.currentUser(), { data: response.data.user });
    },
  });
}

export function useLogoutMutation() {
  const queryClient = useQueryClient();

  const clearSession = () => {
    accessTokenStore.clear();
    queryClient.setQueryData(authKeys.currentUser(), { data: null });
    queryClient.removeQueries({ queryKey: authKeys.all, type: 'inactive' });
  };

  return useMutation({
    mutationFn: logout,
    onMutate: clearSession,
    onSettled: () => {
      clearSession();
    },
  });
}

export function useRegisterMutation() {
  return useMutation({
    mutationFn: register,
  });
}

export function useVerifyEmailMutation() {
  return useMutation({
    mutationFn: verifyEmail,
  });
}

export function useForgotPasswordMutation() {
  return useMutation({
    mutationFn: forgotPassword,
  });
}

export function useResetPasswordMutation() {
  return useMutation({
    mutationFn: resetPassword,
  });
}
