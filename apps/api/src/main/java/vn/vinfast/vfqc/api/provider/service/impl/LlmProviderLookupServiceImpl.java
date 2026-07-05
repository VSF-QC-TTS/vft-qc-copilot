package vn.vinfast.vfqc.api.provider.service.impl;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vinfast.vfqc.api.common.error.ErrorCode;
import vn.vinfast.vfqc.api.common.permission.PermissionAction;
import vn.vinfast.vfqc.api.common.permission.WorkspacePermissionService;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.provider.entity.LlmProvider;
import vn.vinfast.vfqc.api.provider.enums.ProviderScope;
import vn.vinfast.vfqc.api.provider.repository.LlmProviderRepository;
import vn.vinfast.vfqc.api.provider.service.LlmProviderLookupService;

@Service
@RequiredArgsConstructor
public class LlmProviderLookupServiceImpl implements LlmProviderLookupService {

  private final LlmProviderRepository providerRepository;
  private final WorkspacePermissionService workspacePermissionService;

  @Override
  @Transactional(readOnly = true)
  public LlmProvider requireVisible(CurrentUser user, UUID providerId) {
    var provider =
        providerRepository
            .findByIdAndDeletedAtIsNull(providerId)
            .orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    if (provider.getScope() == ProviderScope.WORKSPACE) {
      workspacePermissionService.require(user, provider.getWorkspaceId(), PermissionAction.VIEW);
      return provider;
    }
    if (user.platformAdmin() || user.id().equals(provider.getOwnerUserId())) {
      return provider;
    }
    throw ErrorCode.RESOURCE_NOT_FOUND.throwIt();
  }
}
