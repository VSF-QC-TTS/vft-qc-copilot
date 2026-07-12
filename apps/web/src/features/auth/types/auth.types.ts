import { z } from 'zod';
import {
  currentUserResponseSchema,
  loginRequestSchema,
  loginResponseSchema,
} from '@/features/auth/schemas/auth.schemas';

export type LoginRequest = z.infer<typeof loginRequestSchema>;
export type LoginResponse = z.infer<typeof loginResponseSchema>;
export type CurrentUserResponse = z.infer<typeof currentUserResponseSchema>;
