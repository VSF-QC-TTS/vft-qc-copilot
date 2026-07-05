package vn.vinfast.vfqc.api.secret.service;

import java.util.List;
import java.util.UUID;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.secret.dto.CreateSecretRequest;
import vn.vinfast.vfqc.api.secret.dto.RotateSecretRequest;
import vn.vinfast.vfqc.api.secret.dto.SecretResponse;
import vn.vinfast.vfqc.api.secret.dto.UpdateSecretRequest;

public interface SecretService {

  /**
   * Lists workspace-scoped secrets visible to the current user.
   *
   * @param user current authenticated user
   * @param workspaceId workspace identifier
   * @return sanitized secret responses without encrypted or plaintext values
   */
  List<SecretResponse> listWorkspace(CurrentUser user, UUID workspaceId);

  /**
   * Lists personal secrets owned by the current user.
   *
   * @param user current authenticated user
   * @return sanitized secret responses without encrypted or plaintext values
   */
  List<SecretResponse> listPersonal(CurrentUser user);

  /**
   * Creates a workspace-scoped secret after encrypting the plaintext value.
   *
   * @param user current authenticated user
   * @param workspaceId workspace identifier
   * @param request create request containing plaintext value
   * @return sanitized created secret response
   */
  SecretResponse createWorkspace(CurrentUser user, UUID workspaceId, CreateSecretRequest request);

  /**
   * Creates a personal secret owned by the current user after encrypting the plaintext value.
   *
   * @param user current authenticated user
   * @param request create request containing plaintext value
   * @return sanitized created secret response
   */
  SecretResponse createPersonal(CurrentUser user, CreateSecretRequest request);

  /**
   * Returns one secret if visible to the current user.
   *
   * @param user current authenticated user
   * @param secretId secret identifier
   * @return sanitized secret response
   */
  SecretResponse get(CurrentUser user, UUID secretId);

  /**
   * Updates non-sensitive secret metadata only.
   *
   * @param user current authenticated user
   * @param secretId secret identifier
   * @param request update request
   * @return sanitized updated secret response
   */
  SecretResponse update(CurrentUser user, UUID secretId, UpdateSecretRequest request);

  /**
   * Rotates secret material by replacing the encrypted value and masked preview.
   *
   * @param user current authenticated user
   * @param secretId secret identifier
   * @param request rotation request containing plaintext value
   * @return sanitized rotated secret response
   */
  SecretResponse rotate(CurrentUser user, UUID secretId, RotateSecretRequest request);

  /**
   * Disables a secret so it cannot be selected for new provider or target configuration.
   *
   * @param user current authenticated user
   * @param secretId secret identifier
   * @return sanitized disabled secret response
   */
  SecretResponse disable(CurrentUser user, UUID secretId);

  /**
   * Soft-deletes a secret after permission checks.
   *
   * @param user current authenticated user
   * @param secretId secret identifier
   */
  void delete(CurrentUser user, UUID secretId);
}
