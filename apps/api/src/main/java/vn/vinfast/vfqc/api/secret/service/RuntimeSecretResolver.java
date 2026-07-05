package vn.vinfast.vfqc.api.secret.service;

import java.util.UUID;
import vn.vinfast.vfqc.api.common.security.CurrentUser;

public interface RuntimeSecretResolver {

  /**
   * Resolves a plaintext secret for runtime-only use. Callers must never log or persist the result.
   *
   * @param user current authenticated user or authenticated runner principal owner
   * @param secretId secret identifier
   * @param workspaceId expected workspace for workspace-scoped use, or {@code null} for personal
   * @return plaintext secret value
   */
  String resolve(CurrentUser user, UUID secretId, UUID workspaceId);
}
