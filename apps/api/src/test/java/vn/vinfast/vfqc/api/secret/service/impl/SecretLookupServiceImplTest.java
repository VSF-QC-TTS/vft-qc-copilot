package vn.vinfast.vfqc.api.secret.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.vinfast.vfqc.api.common.error.ApiException;
import vn.vinfast.vfqc.api.common.error.ErrorCode;
import vn.vinfast.vfqc.api.common.permission.PermissionAction;
import vn.vinfast.vfqc.api.common.permission.WorkspacePermissionService;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.secret.entity.Secret;
import vn.vinfast.vfqc.api.secret.enums.SecretScope;
import vn.vinfast.vfqc.api.secret.enums.SecretStatus;
import vn.vinfast.vfqc.api.secret.repository.SecretRepository;
import vn.vinfast.vfqc.api.user.enums.SystemRole;
import vn.vinfast.vfqc.api.user.enums.UserStatus;

@ExtendWith(MockitoExtension.class)
class SecretLookupServiceImplTest {

  @Mock private SecretRepository secretRepository;
  @Mock private WorkspacePermissionService workspacePermissionService;

  @Test
  void requireActiveForUse_WorkspaceSecretWithoutWorkspace_RejectsScopeMismatch() {
    // Arrange
    var user = user(UUID.randomUUID());
    var secretId = UUID.randomUUID();
    var secret =
        new Secret()
            .setId(secretId)
            .setScope(SecretScope.WORKSPACE)
            .setWorkspaceId(UUID.randomUUID())
            .setStatus(SecretStatus.ACTIVE);
    var service = new SecretLookupServiceImpl(secretRepository, workspacePermissionService);
    when(secretRepository.findByIdAndStatusAndDeletedAtIsNull(secretId, SecretStatus.ACTIVE))
        .thenReturn(Optional.of(secret));

    // Act / Assert
    assertThatThrownBy(() -> service.requireActiveForUse(user, secretId, null))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SECRET_SCOPE_MISMATCH));
  }

  @Test
  void requireActiveForUse_PersonalSecretFromOtherUser_HidesResource() {
    // Arrange
    var user = user(UUID.randomUUID());
    var secretId = UUID.randomUUID();
    var secret =
        new Secret()
            .setId(secretId)
            .setScope(SecretScope.PERSONAL)
            .setOwnerUserId(UUID.randomUUID())
            .setStatus(SecretStatus.ACTIVE);
    var service = new SecretLookupServiceImpl(secretRepository, workspacePermissionService);
    when(secretRepository.findByIdAndStatusAndDeletedAtIsNull(secretId, SecretStatus.ACTIVE))
        .thenReturn(Optional.of(secret));

    // Act / Assert
    assertThatThrownBy(() -> service.requireActiveForUse(user, secretId, null))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
  }

  @Test
  void requireActiveForUse_WorkspaceSecretWithMatchingWorkspace_RequiresViewPermission() {
    // Arrange
    var user = user(UUID.randomUUID());
    var workspaceId = UUID.randomUUID();
    var secretId = UUID.randomUUID();
    var secret =
        new Secret()
            .setId(secretId)
            .setScope(SecretScope.WORKSPACE)
            .setWorkspaceId(workspaceId)
            .setStatus(SecretStatus.ACTIVE);
    var service = new SecretLookupServiceImpl(secretRepository, workspacePermissionService);
    when(secretRepository.findByIdAndStatusAndDeletedAtIsNull(secretId, SecretStatus.ACTIVE))
        .thenReturn(Optional.of(secret));

    // Act
    service.requireActiveForUse(user, secretId, workspaceId);

    // Assert
    verify(workspacePermissionService).require(user, workspaceId, PermissionAction.VIEW);
  }

  private CurrentUser user(UUID userId) {
    return new CurrentUser(userId, "user@vinfast.vn", "User", SystemRole.USER, UserStatus.ACTIVE);
  }
}
