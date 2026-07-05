package vn.vinfast.vfqc.api.provider.service;

import java.util.List;
import java.util.UUID;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.provider.dto.LlmProviderTestLogResponse;
import vn.vinfast.vfqc.api.provider.dto.LlmProviderTestResponse;
import vn.vinfast.vfqc.api.provider.dto.TestLlmProviderRequest;

public interface LlmProviderTestService {

  /**
   * Executes a real provider connectivity test and persists a sanitized test log.
   *
   * @param user current authenticated user
   * @param providerId provider identifier
   * @param request test request
   * @return sanitized test result
   */
  LlmProviderTestResponse test(CurrentUser user, UUID providerId, TestLlmProviderRequest request);

  /**
   * Lists recent sanitized provider test logs.
   *
   * @param user current authenticated user
   * @param providerId provider identifier
   * @param limit maximum number of logs to return
   * @return sanitized test log responses
   */
  List<LlmProviderTestLogResponse> history(CurrentUser user, UUID providerId, int limit);
}
