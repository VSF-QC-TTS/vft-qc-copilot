import { describe, expect, it } from 'vitest';
import { canView, canEdit, canManage } from './permissions';
import type { CurrentUserResponse } from '@/features/auth/types/auth.types';

describe('permissions', () => {
  const platformAdminUser: CurrentUserResponse = {
    id: '1',
    email: 'admin@example.com',
    displayName: 'Admin',
    systemRole: 'PLATFORM_ADMIN',
    status: 'ACTIVE',
  };

  const normalUser: CurrentUserResponse = {
    id: '2',
    email: 'user@example.com',
    displayName: 'User',
    systemRole: 'USER',
    status: 'ACTIVE',
  };

  it('allows PLATFORM_ADMIN to bypass with null role', () => {
    expect(canView(null, platformAdminUser)).toBe(true);
    expect(canEdit(null, platformAdminUser)).toBe(true);
    expect(canManage(null, platformAdminUser)).toBe(true);
  });

  it('allows OWNER all capabilities', () => {
    expect(canView('OWNER', normalUser)).toBe(true);
    expect(canEdit('OWNER', normalUser)).toBe(true);
    expect(canManage('OWNER', normalUser)).toBe(true);
  });

  it('allows ADMIN all capabilities', () => {
    expect(canView('ADMIN', normalUser)).toBe(true);
    expect(canEdit('ADMIN', normalUser)).toBe(true);
    expect(canManage('ADMIN', normalUser)).toBe(true);
  });

  it('allows MEMBER view and edit but not manage', () => {
    expect(canView('MEMBER', normalUser)).toBe(true);
    expect(canEdit('MEMBER', normalUser)).toBe(true);
    expect(canManage('MEMBER', normalUser)).toBe(false);
  });

  it('allows VIEWER view only', () => {
    expect(canView('VIEWER', normalUser)).toBe(true);
    expect(canEdit('VIEWER', normalUser)).toBe(false);
    expect(canManage('VIEWER', normalUser)).toBe(false);
  });

  it('denies all if role is null and user is not PLATFORM_ADMIN', () => {
    expect(canView(null, normalUser)).toBe(false);
    expect(canEdit(null, normalUser)).toBe(false);
    expect(canManage(null, normalUser)).toBe(false);
  });
});
