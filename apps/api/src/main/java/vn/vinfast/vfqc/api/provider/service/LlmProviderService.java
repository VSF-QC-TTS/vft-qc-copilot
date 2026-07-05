package vn.vinfast.vfqc.api.provider.service;

import java.util.List;
import java.util.UUID;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.provider.dto.CreateLlmProviderRequest;
import vn.vinfast.vfqc.api.provider.dto.LlmProviderResponse;
import vn.vinfast.vfqc.api.provider.dto.UpdateLlmProviderRequest;

public interface LlmProviderService {

  /**
   * Lists workspace providers visible to the current user.
   *
   * @param user current authenticated user
   * @param workspaceId workspace identifier
   * @return provider responses with sanitized config and secret summaries
   */
  List<LlmProviderResponse> listWorkspace(CurrentUser user, UUID workspaceId);

  /**
   * Lists personal providers owned by the current user.
   *
   * @param user current authenticated user
   * @return provider responses with sanitized config and secret summaries
   */
  List<LlmProviderResponse> listPersonal(CurrentUser user);

  /**
   * Creates a workspace-scoped provider after validating scope and sanitizing config.
   *
   * @param user current authenticated user
   * @param workspaceId workspace identifier
   * @param request provider creation request
   * @return created provider response
   */
  LlmProviderResponse createWorkspace(
      CurrentUser user, UUID workspaceId, CreateLlmProviderRequest request);

  /**
   * Creates a personal provider after validating scope and sanitizing config.
   *
   * @param user current authenticated user
   * @param request provider creation request
   * @return created provider response
   */
  LlmProviderResponse createPersonal(CurrentUser user, CreateLlmProviderRequest request);

  /**
   * Returns one provider after permission checks.
   *
   * @param user current authenticated user
   * @param providerId provider identifier
   * @return provider response
   */
  LlmProviderResponse get(CurrentUser user, UUID providerId);

  /**
   * Updates provider metadata, secret reference and non-secret sanitized config.
   *
   * @param user current authenticated user
   * @param providerId provider identifier
   * @param request update request
   * @return updated provider response
   */
  LlmProviderResponse update(CurrentUser user, UUID providerId, UpdateLlmProviderRequest request);

  /**
   * Soft-deletes a provider after permission checks.
   *
   * @param user current authenticated user
   * @param providerId provider identifier
   */
  void delete(CurrentUser user, UUID providerId);
}
