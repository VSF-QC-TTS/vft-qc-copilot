package vn.vinfast.vfqc.api.provider.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.provider.dto.CreateLlmProviderRequest;
import vn.vinfast.vfqc.api.provider.dto.LlmProviderResponse;
import vn.vinfast.vfqc.api.provider.enums.ProviderScope;
import vn.vinfast.vfqc.api.provider.enums.ProviderStatus;
import vn.vinfast.vfqc.api.provider.enums.ProviderType;
import vn.vinfast.vfqc.api.provider.service.LlmProviderService;
import vn.vinfast.vfqc.api.user.enums.SystemRole;
import vn.vinfast.vfqc.api.user.enums.UserStatus;

@ExtendWith(MockitoExtension.class)
class LlmProviderControllerTest {

  @Mock private LlmProviderService providerService;

  @Test
  void listPersonal_ServiceReturnsItems_WrapsCollectionResponse() {
    // Arrange
    var user = user();
    var provider = providerResponse(UUID.randomUUID());
    var controller = new LlmProviderController(providerService);
    when(providerService.listPersonal(user)).thenReturn(List.of(provider));

    // Act
    var response = controller.listPersonal(user);

    // Assert
    assertThat(response.items()).containsExactly(provider);
    assertThat(response.page().limit()).isEqualTo(1);
    verify(providerService).listPersonal(user);
  }

  @Test
  void createPersonal_Request_DelegatesToService() {
    // Arrange
    var user = user();
    var request =
        new CreateLlmProviderRequest(
            "Provider",
            ProviderType.OPENAI_COMPATIBLE,
            UUID.randomUUID(),
            "https://example.test/v1",
            "gpt-test",
            Map.of());
    var provider = providerResponse(UUID.randomUUID());
    var controller = new LlmProviderController(providerService);
    when(providerService.createPersonal(user, request)).thenReturn(provider);

    // Act
    var response = controller.createPersonal(user, request);

    // Assert
    assertThat(response).isEqualTo(provider);
    verify(providerService).createPersonal(user, request);
  }

  @Test
  void delete_ServiceCompletes_ReturnsNoContent() {
    // Arrange
    var user = user();
    var providerId = UUID.randomUUID();
    var controller = new LlmProviderController(providerService);

    // Act
    var response = controller.delete(user, providerId);

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    verify(providerService).delete(user, providerId);
  }

  private CurrentUser user() {
    return new CurrentUser(
        UUID.randomUUID(), "user@vinfast.vn", "User", SystemRole.USER, UserStatus.ACTIVE);
  }

  private LlmProviderResponse providerResponse(UUID providerId) {
    return new LlmProviderResponse(
        providerId,
        ProviderScope.PERSONAL,
        UUID.randomUUID(),
        null,
        null,
        "Provider",
        ProviderType.OPENAI_COMPATIBLE,
        null,
        "https://example.test/v1",
        "gpt-test",
        Map.of(),
        Map.of(),
        ProviderStatus.ACTIVE,
        OffsetDateTime.parse("2026-07-05T03:00:00Z"),
        OffsetDateTime.parse("2026-07-05T03:00:00Z"));
  }
}
