import { apiClient } from '@/shared/api/api';
import {
  currentUserResponseSchema,
  loginResponseSchema,
} from '@/features/auth/schemas/auth.schemas';
import type { LoginRequest } from '@/features/auth/types/auth.types';

export async function login(data: LoginRequest) {
  return apiClient.requestJson('/api/v1/auth/login', loginResponseSchema, {
    method: 'POST',
    body: JSON.stringify(data),
    headers: { 'Content-Type': 'application/json' },
    auth: 'none',
  });
}

export async function getCurrentUser(signal?: AbortSignal) {
  return apiClient.requestJson('/api/v1/auth/me', currentUserResponseSchema, {
    ...(signal ? { signal } : {}),
  });
}

export async function logout() {
  return apiClient.requestVoid('/api/v1/auth/logout', { method: 'POST' });
}
