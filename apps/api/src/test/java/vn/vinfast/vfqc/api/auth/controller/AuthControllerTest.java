package vn.vinfast.vfqc.api.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import vn.vinfast.vfqc.api.auth.dto.AuthMessageResponse;
import vn.vinfast.vfqc.api.auth.dto.ForgotPasswordRequest;
import vn.vinfast.vfqc.api.auth.dto.RegisterRequest;
import vn.vinfast.vfqc.api.auth.dto.RegisterResponse;
import vn.vinfast.vfqc.api.auth.dto.ResetPasswordRequest;
import vn.vinfast.vfqc.api.auth.service.AuthService;
import vn.vinfast.vfqc.api.auth.service.RefreshTokenService;
import vn.vinfast.vfqc.api.common.config.SecurityProperties;
import vn.vinfast.vfqc.api.user.enums.UserStatus;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

  @Mock private AuthService authService;
  @Mock private RefreshTokenService refreshTokenService;

  @Test
  void register_Request_DelegatesToServiceWithAuditMetadata() {
    var request = new RegisterRequest("user@vinfast.vn", "User", "ChangeMe@123");
    var servletRequest = servletRequest();
    var expected =
        new RegisterResponse(
            UUID.randomUUID(),
            "user@vinfast.vn",
            UserStatus.PENDING_EMAIL_VERIFICATION,
            "Registration accepted.");
    var controller = controller();
    when(authService.register(request, "127.0.0.1", "JUnit")).thenReturn(expected);

    var response = controller.register(request, servletRequest);

    assertThat(response).isEqualTo(expected);
    verify(authService).register(request, "127.0.0.1", "JUnit");
  }

  @Test
  void forgotPassword_Request_DelegatesToServiceWithAuditMetadata() {
    var request = new ForgotPasswordRequest("user@vinfast.vn");
    var servletRequest = servletRequest();
    var expected =
        new AuthMessageResponse("If the email exists, reset instructions have been sent.");
    var controller = controller();
    when(authService.forgotPassword(request, "127.0.0.1", "JUnit")).thenReturn(expected);

    var response = controller.forgotPassword(request, servletRequest);

    assertThat(response).isEqualTo(expected);
    verify(authService).forgotPassword(request, "127.0.0.1", "JUnit");
  }

  @Test
  void resetPassword_Request_DelegatesToService() {
    var request = new ResetPasswordRequest("token", "NewPassword@123");
    var expected = new AuthMessageResponse("Password reset successfully.");
    var controller = controller();
    when(authService.resetPassword(request)).thenReturn(expected);

    var response = controller.resetPassword(request);

    assertThat(response).isEqualTo(expected);
    verify(authService).resetPassword(request);
  }

  private AuthController controller() {
    return new AuthController(
        authService,
        refreshTokenService,
        new SecurityProperties(
            15,
            10080,
            new SecurityProperties.Jwt("dev-only-change-me-dev-only-change-me"),
            "",
            "",
            new SecurityProperties.Cookie(false, "lax"),
            "runner-token"));
  }

  private MockHttpServletRequest servletRequest() {
    var request = new MockHttpServletRequest();
    request.setRemoteAddr("127.0.0.1");
    request.addHeader("User-Agent", "JUnit");
    return request;
  }
}
