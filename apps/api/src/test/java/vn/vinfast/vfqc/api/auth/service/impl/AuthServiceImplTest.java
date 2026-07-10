package vn.vinfast.vfqc.api.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.vinfast.vfqc.api.auth.dto.ForgotPasswordRequest;
import vn.vinfast.vfqc.api.auth.dto.LoginRequest;
import vn.vinfast.vfqc.api.auth.dto.RegisterRequest;
import vn.vinfast.vfqc.api.auth.dto.ResetPasswordRequest;
import vn.vinfast.vfqc.api.auth.dto.VerifyEmailRequest;
import vn.vinfast.vfqc.api.auth.enums.AuthEmailTokenPurpose;
import vn.vinfast.vfqc.api.auth.mapper.AuthMapper;
import vn.vinfast.vfqc.api.auth.service.AuthEmailTokenConsumption;
import vn.vinfast.vfqc.api.auth.service.AuthEmailTokenIssue;
import vn.vinfast.vfqc.api.auth.service.AuthEmailTokenService;
import vn.vinfast.vfqc.api.auth.service.JwtTokenService;
import vn.vinfast.vfqc.api.auth.service.RefreshTokenService;
import vn.vinfast.vfqc.api.common.config.AuthProperties;
import vn.vinfast.vfqc.api.common.config.AuthProperties.RegistrationMode;
import vn.vinfast.vfqc.api.common.config.ClientProperties;
import vn.vinfast.vfqc.api.common.config.UserProperties;
import vn.vinfast.vfqc.api.common.error.ApiException;
import vn.vinfast.vfqc.api.common.error.ErrorCode;
import vn.vinfast.vfqc.api.common.mail.event.MailSendEvent;
import vn.vinfast.vfqc.api.common.mail.model.EmailVerificationMailModel;
import vn.vinfast.vfqc.api.common.util.AuthLinkBuilder;
import vn.vinfast.vfqc.api.user.entity.AppUser;
import vn.vinfast.vfqc.api.user.enums.SystemRole;
import vn.vinfast.vfqc.api.user.enums.UserStatus;
import vn.vinfast.vfqc.api.user.repository.UserRepository;
import vn.vinfast.vfqc.api.user.service.UserOnboardingService;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-05T10:00:00Z"), ZoneOffset.UTC);

  @Mock private UserRepository userRepository;
  @Mock private JwtTokenService jwtTokenService;
  @Mock private RefreshTokenService refreshTokenService;
  @Mock private AuthEmailTokenService authEmailTokenService;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private AuthMapper authMapper;
  @Mock private UserOnboardingService userOnboardingService;

  private final PasswordEncoder passwordEncoder =
      PasswordEncoderFactories.createDelegatingPasswordEncoder();
  private AuthServiceImpl service;

  @BeforeEach
  void setUp() {
    var linkBuilder = new AuthLinkBuilder(new ClientProperties("http://localhost:3000"));
    service =
        new AuthServiceImpl(
            userRepository,
            passwordEncoder,
            jwtTokenService,
            refreshTokenService,
            authEmailTokenService,
            eventPublisher,
            new AuthProperties(
                new AuthProperties.Registration(RegistrationMode.SELF_VERIFY),
                new AuthProperties.EmailToken(1440, 15)),
            new UserProperties("https://example.com/avatar.png"),
            userOnboardingService,
            linkBuilder,
            authMapper,
            CLOCK);
  }

  @Test
  void register_SelfRegistrationEnabled_CreatesPendingUserAndSendsVerificationMail() {
    var userId = UUID.randomUUID();
    when(userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull("user@vinfast.vn"))
        .thenReturn(false);
    when(userRepository.save(any(AppUser.class)))
        .thenAnswer(invocation -> {
          var u = invocation.<AppUser>getArgument(0);
          u.setId(userId);
          return u;
        });
    when(authEmailTokenService.issue(
            any(),
            eq(AuthEmailTokenPurpose.EMAIL_VERIFICATION),
            any(),
            eq("127.0.0.1"),
            eq("JUnit")))
        .thenReturn(new AuthEmailTokenIssue("raw-token", OffsetDateTime.now(CLOCK).plusDays(1)));
    var eventCaptor = ArgumentCaptor.forClass(MailSendEvent.class);

    var response =
        service.register(
            new RegisterRequest("USER@vinfast.vn", "Test User", "ChangeMe@123"),
            "127.0.0.1",
            "JUnit");

    assertThat(response.id()).isEqualTo(userId);
    assertThat(response.email()).isEqualTo("user@vinfast.vn");
    assertThat(response.status()).isEqualTo(UserStatus.PENDING_EMAIL_VERIFICATION);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().model()).isInstanceOf(EmailVerificationMailModel.class);
    assertThat(((EmailVerificationMailModel) eventCaptor.getValue().model()).verificationLink())
        .contains("/verify-email?token=");
  }

  @Test
  void login_PendingVerificationUser_ThrowsEmailNotVerified() {
    var user =
        user("user@vinfast.vn");
    user.setPasswordHash(passwordEncoder.encode("ChangeMe@123"));
    user.setStatus(UserStatus.PENDING_EMAIL_VERIFICATION);
    when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("user@vinfast.vn"))
        .thenReturn(Optional.of(user));

    assertThatThrownBy(
            () -> service.login(new LoginRequest("user@vinfast.vn", "ChangeMe@123"), null, null))
        .isInstanceOf(ApiException.class)
        .extracting(exception -> ((ApiException) exception).getErrorCode())
        .isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED);
  }

  @Test
  void forgotPassword_MissingEmail_ReturnsGenericResponseWithoutMail() {
    when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("missing@vinfast.vn"))
        .thenReturn(Optional.empty());

    var response =
        service.forgotPassword(
            new ForgotPasswordRequest("missing@vinfast.vn"), "127.0.0.1", "JUnit");

    assertThat(response.message()).contains("If the email exists");
    verifyNoInteractions(authEmailTokenService, eventPublisher);
  }

  @Test
  void resetPassword_ValidToken_UpdatesPasswordAndRevokesRefreshTokens() {
    var user = user("user@vinfast.vn");
    when(authEmailTokenService.consumeWithPurpose(eq("raw-token"), any()))
        .thenReturn(new AuthEmailTokenConsumption(user, AuthEmailTokenPurpose.PASSWORD_RESET));

    var response = service.resetPassword(new ResetPasswordRequest("raw-token", "NewPassword@123"));

    assertThat(response.message()).contains("Password reset");
    assertThat(passwordEncoder.matches("NewPassword@123", user.getPasswordHash())).isTrue();
    assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    verify(refreshTokenService).revokeActiveForUser(user.getId());
    verify(authEmailTokenService).revokeActiveForUser(user.getId());
  }

  @Test
  void resetPassword_AdminInviteToken_EnsuresPersonalWorkspace() {
    var user = user("invited@vinfast.vn");
    when(authEmailTokenService.consumeWithPurpose(eq("raw-token"), any()))
        .thenReturn(new AuthEmailTokenConsumption(user, AuthEmailTokenPurpose.ADMIN_INVITE));

    service.resetPassword(new ResetPasswordRequest("raw-token", "NewPassword@123"));

    verify(userOnboardingService).ensurePersonalWorkspace(user);
  }

  @Test
  void verifyEmail_PendingUser_ActivatesAndEnsuresPersonalWorkspace() {
    var user = user("verify@vinfast.vn");
    user.setStatus(UserStatus.PENDING_EMAIL_VERIFICATION);
    when(authEmailTokenService.consume(eq("verify-token"), any())).thenReturn(user);

    service.verifyEmail(new VerifyEmailRequest("verify-token"));

    assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    verify(userOnboardingService).ensurePersonalWorkspace(user);
  }

  private AppUser user(String email) {
    return AppUser.builder()
        .id(UUID.randomUUID())
        .email(email)
        .displayName("Test User")
        .systemRole(SystemRole.USER)
        .status(UserStatus.ACTIVE)
        .createdAt(OffsetDateTime.now(CLOCK))
        .updatedAt(OffsetDateTime.now(CLOCK))
        .build();
  }
}
