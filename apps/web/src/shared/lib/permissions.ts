import type { WorkspaceRole } from '@/features/workspaces/types/workspace';
import type { CurrentUserResponse } from '@/features/auth/types/auth.types';

export type WorkspaceCapability = 'VIEW' | 'EDIT' | 'MANAGE';

/**
 * Semantic capability check for workspace operations.
 */
export function can(
  capability: WorkspaceCapability,
  role: WorkspaceRole | null | undefined,
  user?: CurrentUserResponse | null,
): boolean {
  if (user?.systemRole === 'PLATFORM_ADMIN') {
    return true;
  }

  if (!role) {
    return false;
  }

  switch (capability) {
    case 'VIEW':
      return true;
    case 'EDIT':
      return role === 'OWNER' || role === 'ADMIN' || role === 'MEMBER';
    case 'MANAGE':
      return role === 'OWNER' || role === 'ADMIN';
    default:
      return false;
  }
}

export function canView(
  role: WorkspaceRole | null | undefined,
  user?: CurrentUserResponse | null,
): boolean {
  return can('VIEW', role, user);
}

export function canEdit(
  role: WorkspaceRole | null | undefined,
  user?: CurrentUserResponse | null,
): boolean {
  return can('EDIT', role, user);
}

export function canManage(
  role: WorkspaceRole | null | undefined,
  user?: CurrentUserResponse | null,
): boolean {
  return can('MANAGE', role, user);
}
