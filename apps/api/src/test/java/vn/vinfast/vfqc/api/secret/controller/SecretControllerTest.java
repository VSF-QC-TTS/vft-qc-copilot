package vn.vinfast.vfqc.api.secret.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.secret.dto.RotateSecretRequest;
import vn.vinfast.vfqc.api.secret.dto.SecretResponse;
import vn.vinfast.vfqc.api.secret.enums.SecretScope;
import vn.vinfast.vfqc.api.secret.enums.SecretStatus;
import vn.vinfast.vfqc.api.secret.service.SecretService;
import vn.vinfast.vfqc.api.user.enums.SystemRole;
import vn.vinfast.vfqc.api.user.enums.UserStatus;

@ExtendWith(MockitoExtension.class)
class SecretControllerTest {

  @Mock private SecretService secretService;

  @Test
  void listWorkspace_ServiceReturnsItems_WrapsCollectionResponse() {
    // Arrange
    var user = user();
    var workspaceId = UUID.randomUUID();
    var secret = secretResponse(UUID.randomUUID(), workspaceId);
    var controller = new SecretController(secretService);
    when(secretService.listWorkspace(user, workspaceId)).thenReturn(List.of(secret));

    // Act
    var response = controller.listWorkspace(user, workspaceId);

    // Assert
    assertThat(response.items()).containsExactly(secret);
    assertThat(response.page().limit()).isEqualTo(1);
    verify(secretService).listWorkspace(user, workspaceId);
  }

  @Test
  void rotate_Request_DelegatesToService() {
    // Arrange
    var user = user();
    var secretId = UUID.randomUUID();
    var request = new RotateSecretRequest("new-secret");
    var secret = secretResponse(secretId, null);
    var controller = new SecretController(secretService);
    when(secretService.rotate(user, secretId, request)).thenReturn(secret);

    // Act
    var response = controller.rotate(user, secretId, request);

    // Assert
    assertThat(response).isEqualTo(secret);
    verify(secretService).rotate(user, secretId, request);
  }

  @Test
  void delete_ServiceCompletes_ReturnsNoContent() {
    // Arrange
    var user = user();
    var secretId = UUID.randomUUID();
    var controller = new SecretController(secretService);

    // Act
    var response = controller.delete(user, secretId);

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    verify(secretService).delete(user, secretId);
  }

  private CurrentUser user() {
    return new CurrentUser(
        UUID.randomUUID(), "user@vinfast.vn", "User", SystemRole.USER, UserStatus.ACTIVE);
  }

  private SecretResponse secretResponse(UUID secretId, UUID workspaceId) {
    return new SecretResponse(
        secretId,
        workspaceId == null ? SecretScope.PERSONAL : SecretScope.WORKSPACE,
        workspaceId == null ? UUID.randomUUID() : null,
        null,
        workspaceId,
        "Secret",
        "****",
        SecretStatus.ACTIVE,
        OffsetDateTime.parse("2026-07-05T03:00:00Z"),
        OffsetDateTime.parse("2026-07-05T03:00:00Z"));
  }
}
