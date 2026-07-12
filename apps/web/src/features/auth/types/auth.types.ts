import { z } from 'zod';
import {
  currentUserResponseSchema,
  loginRequestSchema,
  loginResponseSchema,
  registerRequestSchema,
  registerFormSchema,
  verifyEmailRequestSchema,
  forgotPasswordRequestSchema,
  resetPasswordRequestSchema,
  resetPasswordFormSchema,
  authMessageResponseSchema,
} from '@/features/auth/schemas/auth.schemas';

export type LoginRequest = z.infer<typeof loginRequestSchema>;
export type LoginResponse = z.infer<typeof loginResponseSchema>;
export type CurrentUserResponse = z.infer<typeof currentUserResponseSchema>;
export type RegisterRequest = z.infer<typeof registerRequestSchema>;
export type RegisterFormValues = z.infer<typeof registerFormSchema>;
export type VerifyEmailRequest = z.infer<typeof verifyEmailRequestSchema>;
export type ForgotPasswordRequest = z.infer<typeof forgotPasswordRequestSchema>;
export type ResetPasswordRequest = z.infer<typeof resetPasswordRequestSchema>;
export type ResetPasswordFormValues = z.infer<typeof resetPasswordFormSchema>;
export type AuthMessageResponse = z.infer<typeof authMessageResponseSchema>;
