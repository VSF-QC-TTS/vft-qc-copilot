package vn.vinfast.vfqc.api.secret.service.impl;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vinfast.vfqc.api.common.error.ErrorCode;
import vn.vinfast.vfqc.api.common.permission.PermissionAction;
import vn.vinfast.vfqc.api.common.permission.WorkspacePermissionService;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.secret.entity.Secret;
import vn.vinfast.vfqc.api.secret.enums.SecretScope;
import vn.vinfast.vfqc.api.secret.enums.SecretStatus;
import vn.vinfast.vfqc.api.secret.repository.SecretRepository;
import vn.vinfast.vfqc.api.secret.service.SecretLookupService;

@Service
@RequiredArgsConstructor
public class SecretLookupServiceImpl implements SecretLookupService {

  private final SecretRepository secretRepository;
  private final WorkspacePermissionService workspacePermissionService;

  @Override
  @Transactional(readOnly = true)
  public Secret requireActiveForUse(CurrentUser user, UUID secretId, UUID workspaceId) {
    var secret =
        secretRepository
            .findByIdAndStatusAndDeletedAtIsNull(secretId, SecretStatus.ACTIVE)
            .orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    if (secret.getScope() == SecretScope.WORKSPACE) {
      if (workspaceId == null || !workspaceId.equals(secret.getWorkspaceId())) {
        throw ErrorCode.SECRET_SCOPE_MISMATCH.throwIt();
      }
      workspacePermissionService.require(user, workspaceId, PermissionAction.VIEW);
      return secret;
    }
    if (!user.platformAdmin() && !user.id().equals(secret.getOwnerUserId())) {
      throw ErrorCode.RESOURCE_NOT_FOUND.throwIt();
    }
    if (workspaceId != null) {
      throw ErrorCode.SECRET_SCOPE_MISMATCH.throwIt();
    }
    return secret;
  }
}
