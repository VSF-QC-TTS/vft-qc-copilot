import { useCurrentUserQuery } from '@/features/auth/hooks/use-auth-queries';
import type { WorkspaceRole } from '@/features/workspaces/types/workspace';
import { can } from '@/shared/lib/permissions';
import type { WorkspaceCapability } from '@/shared/lib/permissions';
import type { ReactNode } from 'react';

export interface PermissionGuardProps {
  capability: WorkspaceCapability;
  workspaceRole: WorkspaceRole | null | undefined;
  children: ReactNode | ((isAllowed: boolean) => ReactNode);
  fallback?: ReactNode;
  hide?: boolean;
}

/**
 * A component that renders its children based on workspace capability.
 * Supports:
 * - Hiding content when not allowed (default or if hide=true)
 * - Rendering a fallback when not allowed
 * - Render prop pattern for disabled/read-only states: (isAllowed) => <Button disabled={!isAllowed} />
 */
export function PermissionGuard({
  capability,
  workspaceRole,
  children,
  fallback = null,
  hide = false,
}: PermissionGuardProps) {
  const { data: userResponse } = useCurrentUserQuery();
  const user = userResponse?.data;

  const isAllowed = can(capability, workspaceRole, user);

  if (typeof children === 'function') {
    return <>{children(isAllowed)}</>;
  }

  if (isAllowed) {
    return <>{children}</>;
  }

  if (hide) {
    return null;
  }

  return <>{fallback}</>;
}
