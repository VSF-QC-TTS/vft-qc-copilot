package vn.vinfast.vfqc.api.provider.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.provider.dto.LlmProviderTestLogResponse;
import vn.vinfast.vfqc.api.provider.dto.TestLlmProviderRequest;
import vn.vinfast.vfqc.api.provider.enums.ProviderTestStatus;
import vn.vinfast.vfqc.api.provider.service.LlmProviderTestService;
import vn.vinfast.vfqc.api.user.enums.SystemRole;
import vn.vinfast.vfqc.api.user.enums.UserStatus;

@ExtendWith(MockitoExtension.class)
class LlmProviderTestHistoryControllerTest {

  @Mock private LlmProviderTestService testService;

  @Test
  void test_Request_DelegatesToService() {
    // Arrange
    var user = user();
    var providerId = UUID.randomUUID();
    var request = new TestLlmProviderRequest("gpt-test", "Return ok", Map.of());
    var controller = new LlmProviderTestHistoryController(testService);

    // Act
    controller.test(user, providerId, request);

    // Assert
    verify(testService).test(user, providerId, request);
  }

  @Test
  void history_ServiceReturnsItems_WrapsCollectionResponseAndPassesLimit() {
    // Arrange
    var user = user();
    var providerId = UUID.randomUUID();
    var log =
        new LlmProviderTestLogResponse(
            1L,
            ProviderTestStatus.SUCCESS,
            "gpt-test",
            Map.of("model", "gpt-test"),
            "ok",
            null,
            null,
            15,
            Map.of(),
            null);
    var controller = new LlmProviderTestHistoryController(testService);
    when(testService.history(user, providerId, 50)).thenReturn(List.of(log));

    // Act
    var response = controller.history(user, providerId, 50);

    // Assert
    assertThat(response.items()).containsExactly(log);
    assertThat(response.page().limit()).isEqualTo(1);
    verify(testService).history(user, providerId, 50);
  }

  private CurrentUser user() {
    return new CurrentUser(
        UUID.randomUUID(), "user@vinfast.vn", "User", SystemRole.USER, UserStatus.ACTIVE);
  }
}
