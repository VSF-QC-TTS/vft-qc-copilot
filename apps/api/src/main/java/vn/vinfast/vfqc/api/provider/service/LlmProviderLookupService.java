package vn.vinfast.vfqc.api.provider.service;

import java.util.UUID;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.provider.entity.LlmProvider;

public interface LlmProviderLookupService {

  /**
   * Loads a provider visible to the current user for runtime configuration use.
   *
   * @param user current authenticated user
   * @param providerId provider identifier
   * @return provider entity after permission checks
   */
  LlmProvider requireVisible(CurrentUser user, UUID providerId);
}
