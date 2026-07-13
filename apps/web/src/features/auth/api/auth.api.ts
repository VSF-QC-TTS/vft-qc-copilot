import { apiClient } from '@/shared/api/api';
import {
  authMessageResponseSchema,
  currentUserResponseSchema,
  loginResponseSchema,
  registerResponseSchema,
} from '@/features/auth/schemas/auth.schemas';
import type {
  ForgotPasswordRequest,
  LoginRequest,
  RegisterRequest,
  ResetPasswordRequest,
  VerifyEmailRequest,
} from '@/features/auth/types/auth.types';

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

export async function register(data: RegisterRequest) {
  return apiClient.requestJson('/api/v1/auth/register', registerResponseSchema, {
    method: 'POST',
    body: JSON.stringify(data),
    headers: { 'Content-Type': 'application/json' },
    auth: 'none',
  });
}

export async function verifyEmail(data: VerifyEmailRequest) {
  return apiClient.requestJson('/api/v1/auth/verify-email', authMessageResponseSchema, {
    method: 'POST',
    body: JSON.stringify(data),
    headers: { 'Content-Type': 'application/json' },
    auth: 'none',
  });
}

export async function forgotPassword(data: ForgotPasswordRequest) {
  return apiClient.requestJson('/api/v1/auth/password/forgot', authMessageResponseSchema, {
    method: 'POST',
    body: JSON.stringify(data),
    headers: { 'Content-Type': 'application/json' },
    auth: 'none',
  });
}

export async function resetPassword(data: ResetPasswordRequest) {
  return apiClient.requestJson('/api/v1/auth/password/reset', authMessageResponseSchema, {
    method: 'POST',
    body: JSON.stringify(data),
    headers: { 'Content-Type': 'application/json' },
    auth: 'none',
  });
}
