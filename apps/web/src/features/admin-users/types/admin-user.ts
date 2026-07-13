import { z } from 'zod';

export const userStatusSchema = z.enum(['PENDING_EMAIL_VERIFICATION', 'ACTIVE', 'DISABLED']);
export type UserStatus = z.infer<typeof userStatusSchema>;

export const userSummarySchema = z.object({
  id: z.string().uuid(),
  email: z.string(),
  name: z.string(),
  avatarUrl: z.string().nullable().optional(),
});
export type UserSummaryResponse = z.infer<typeof userSummarySchema>;

export const adminCreateUserRequestSchema = z.object({
  email: z.string().min(1, 'Vui lòng nhập email').email('Email không hợp lệ'),
  displayName: z
    .string()
    .min(1, 'Vui lòng nhập tên hiển thị')
    .max(120, 'Tên hiển thị tối đa 120 ký tự'),
});
export type AdminCreateUserRequest = z.infer<typeof adminCreateUserRequestSchema>;

export const adminCreateUserResponseSchema = z.object({
  id: z.string().uuid(),
  email: z.string(),
  user: userSummarySchema,
  status: userStatusSchema,
  message: z.string(),
});
export type AdminCreateUserResponse = z.infer<typeof adminCreateUserResponseSchema>;
