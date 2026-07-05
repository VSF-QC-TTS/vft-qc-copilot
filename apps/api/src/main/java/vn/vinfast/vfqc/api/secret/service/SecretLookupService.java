package vn.vinfast.vfqc.api.secret.service;

import java.util.UUID;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.secret.entity.Secret;

public interface SecretLookupService {

  /**
   * Loads an active secret and verifies the current user can use it for the requested scope.
   *
   * @param user current authenticated user
   * @param secretId secret identifier
   * @param workspaceId expected workspace for workspace-scoped use, or {@code null} for personal
   * @return active secret entity
   */
  Secret requireActiveForUse(CurrentUser user, UUID secretId, UUID workspaceId);
}
