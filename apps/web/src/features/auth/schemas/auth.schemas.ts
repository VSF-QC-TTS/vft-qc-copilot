import { z } from 'zod';

export const loginRequestSchema = z.object({
  email: z.string().email('Email không hợp lệ'),
  password: z.string().min(1, 'Vui lòng nhập mật khẩu'),
});

export const loginResponseSchema = z.object({
  accessToken: z.string().min(1),
  tokenType: z.string(),
  expiresIn: z.number().int().positive(),
  user: z.object({
    id: z.string(),
    email: z.string(),
    displayName: z.string(),
    avatarUrl: z.string().nullable().optional(),
    systemRole: z.string(),
    status: z.string(),
  }),
});

export const currentUserResponseSchema = z.object({
  id: z.string(),
  email: z.string(),
  displayName: z.string(),
  avatarUrl: z.string().nullable().optional(),
  systemRole: z.string(),
  status: z.string(),
});

export const authMessageResponseSchema = z.object({
  message: z.string(),
});

export const registerRequestSchema = z.object({
  email: z.string().min(1, 'Vui lòng nhập email').email('Email không hợp lệ'),
  displayName: z
    .string()
    .min(1, 'Vui lòng nhập tên hiển thị')
    .max(120, 'Tên hiển thị tối đa 120 ký tự'),
  password: z
    .string()
    .min(8, 'Mật khẩu phải từ 8 đến 128 ký tự')
    .max(128, 'Mật khẩu phải từ 8 đến 128 ký tự'),
});

export const registerFormSchema = registerRequestSchema
  .extend({
    confirmPassword: z.string().min(1, 'Vui lòng xác nhận mật khẩu'),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: 'Mật khẩu không khớp',
    path: ['confirmPassword'],
  });

export const verifyEmailRequestSchema = z.object({
  token: z.string().min(1),
});

export const forgotPasswordRequestSchema = z.object({
  email: z.string().min(1, 'Vui lòng nhập email').email('Email không hợp lệ'),
});

export const resetPasswordRequestSchema = z.object({
  token: z.string().min(1, 'Thiếu token xác nhận'),
  password: z
    .string()
    .min(8, 'Mật khẩu phải từ 8 đến 128 ký tự')
    .max(128, 'Mật khẩu phải từ 8 đến 128 ký tự'),
});

export const resetPasswordFormSchema = resetPasswordRequestSchema
  .extend({
    confirmPassword: z.string().min(1, 'Vui lòng xác nhận mật khẩu'),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: 'Mật khẩu không khớp',
    path: ['confirmPassword'],
  });
