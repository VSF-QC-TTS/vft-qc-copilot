package vn.vinfast.vfqc.api.auth.service.impl;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vinfast.vfqc.api.auth.dto.AuthMessageResponse;
import vn.vinfast.vfqc.api.auth.dto.CurrentUserResponse;
import vn.vinfast.vfqc.api.auth.dto.ForgotPasswordRequest;
import vn.vinfast.vfqc.api.auth.dto.LoginRequest;
import vn.vinfast.vfqc.api.auth.dto.LoginResponse;
import vn.vinfast.vfqc.api.auth.dto.RefreshAccessTokenResponse;
import vn.vinfast.vfqc.api.auth.dto.RegisterRequest;
import vn.vinfast.vfqc.api.auth.dto.RegisterResponse;
import vn.vinfast.vfqc.api.auth.dto.ResetPasswordRequest;
import vn.vinfast.vfqc.api.auth.dto.VerifyEmailRequest;
import vn.vinfast.vfqc.api.auth.enums.AuthEmailTokenPurpose;
import vn.vinfast.vfqc.api.auth.mapper.AuthMapper;
import vn.vinfast.vfqc.api.auth.service.AuthEmailTokenService;
import vn.vinfast.vfqc.api.auth.service.AuthService;
import vn.vinfast.vfqc.api.auth.service.JwtTokenService;
import vn.vinfast.vfqc.api.auth.service.RefreshTokenService;
import vn.vinfast.vfqc.api.common.config.AuthProperties;
import vn.vinfast.vfqc.api.common.config.AuthProperties.RegistrationMode;
import vn.vinfast.vfqc.api.common.config.ClientProperties;
import vn.vinfast.vfqc.api.common.error.ErrorCode;
import vn.vinfast.vfqc.api.common.mail.MailService;
import vn.vinfast.vfqc.api.common.mail.model.EmailVerificationMailModel;
import vn.vinfast.vfqc.api.common.mail.model.PasswordResetMailModel;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.user.entity.AppUser;
import vn.vinfast.vfqc.api.user.enums.SystemRole;
import vn.vinfast.vfqc.api.user.enums.UserStatus;
import vn.vinfast.vfqc.api.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

  private static final String FORGOT_PASSWORD_MESSAGE =
      "If the email exists, reset instructions have been sent.";

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenService jwtTokenService;
  private final RefreshTokenService refreshTokenService;
  private final AuthEmailTokenService authEmailTokenService;
  private final MailService mailService;
  private final AuthProperties authProperties;
  private final ClientProperties clientProperties;
  private final AuthMapper authMapper;
  private final Clock clock;

  @Value("${vfqc.user.default-avatar-url}")
  private String avatarUrl;

  @Transactional
  @Override
  public RegisterResponse register(RegisterRequest request, String ip, String userAgent) {
    if (authProperties.registration().mode() != RegistrationMode.SELF_VERIFY) {
      throw ErrorCode.REGISTRATION_DISABLED.throwIt();
    }
    var email = normalizeEmail(request.email());
    if (userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull(email)) {
      throw ErrorCode.DUPLICATE_RESOURCE.throwIt("Email already exists");
    }
    var now = OffsetDateTime.now(clock);
    var user =
        userRepository.save(
            AppUser.builder()
                .email(email)
                .displayName(request.displayName().trim())
                .passwordHash(passwordEncoder.encode(request.password()))
                .avatarUrl(avatarUrl)
                .systemRole(SystemRole.USER)
                .status(UserStatus.PENDING_EMAIL_VERIFICATION)
                .createdAt(now)
                .updatedAt(now)
                .build());
    var token =
        authEmailTokenService.issue(
            user,
            AuthEmailTokenPurpose.EMAIL_VERIFICATION,
            Duration.ofMinutes(authProperties.emailToken().verificationExpirationMinutes()),
            ip,
            userAgent);
    mailService.send(
        new EmailVerificationMailModel(
            user.getEmail(), user.getDisplayName(), verificationLink(token.token())));
    log.info("User registration accepted: userId={}", user.getId());
    return new RegisterResponse(
        user.getId(),
        user.getEmail(),
        user.getStatus(),
        "Registration accepted. Verify your email to activate the account.");
  }

  @Transactional
  @Override
  public LoginResult login(LoginRequest request, String ip, String userAgent) {
    var user =
        userRepository
            .findByEmailIgnoreCaseAndDeletedAtIsNull(normalizeEmail(request.email()))
            .orElseThrow(ErrorCode.INVALID_CREDENTIALS::throwIt);
    if (user.getStatus() == UserStatus.PENDING_EMAIL_VERIFICATION) {
      throw ErrorCode.EMAIL_NOT_VERIFIED.throwIt();
    }
    if (!user.active()) {
      throw ErrorCode.USER_DISABLED.throwIt();
    }
    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw ErrorCode.INVALID_CREDENTIALS.throwIt();
    }
    if (passwordEncoder.upgradeEncoding(user.getPasswordHash())) {
      user.setPasswordHash(passwordEncoder.encode(request.password()));
    }
    var refresh = refreshTokenService.issue(user, ip, userAgent);
    var response =
        new LoginResponse(
            jwtTokenService.issueAccessToken(user),
            "Bearer",
            jwtTokenService.accessExpiresInSeconds(),
            authMapper.toCurrentUserResponse(user));
    log.info("User login succeeded: userId={}", user.getId());
    return new LoginResult(response, refresh);
  }

  @Transactional
  @Override
  public RefreshResult refresh(String refreshToken, String ip, String userAgent) {
    var rotation = refreshTokenService.rotate(refreshToken);
    var refresh = refreshTokenService.issue(rotation.user(), rotation.familyId(), ip, userAgent);
    log.info("Access token refreshed: userId={}", rotation.user().getId());
    return new RefreshResult(
        new RefreshAccessTokenResponse(
            jwtTokenService.issueAccessToken(rotation.user()),
            "Bearer",
            jwtTokenService.accessExpiresInSeconds()),
        refresh);
  }

  @Transactional
  @Override
  public AuthMessageResponse verifyEmail(VerifyEmailRequest request) {
    var user =
        authEmailTokenService.consume(
            request.token(), Set.of(AuthEmailTokenPurpose.EMAIL_VERIFICATION));
    if (user.getStatus() == UserStatus.DISABLED) {
      throw ErrorCode.USER_DISABLED.throwIt();
    }
    if (user.getStatus() == UserStatus.PENDING_EMAIL_VERIFICATION) {
      user.setStatus(UserStatus.ACTIVE);
      user.setUpdatedAt(OffsetDateTime.now(clock));
    }
    log.info("User email verified: userId={}", user.getId());
    return new AuthMessageResponse("Email verified.");
  }

  @Transactional
  @Override
  public AuthMessageResponse forgotPassword(
      ForgotPasswordRequest request, String ip, String userAgent) {
    userRepository
        .findByEmailIgnoreCaseAndDeletedAtIsNull(normalizeEmail(request.email()))
        .filter(AppUser::active)
        .ifPresent(user -> sendPasswordReset(user, ip, userAgent));
    return new AuthMessageResponse(FORGOT_PASSWORD_MESSAGE);
  }

  @Transactional
  @Override
  public AuthMessageResponse resetPassword(ResetPasswordRequest request) {
    var user =
        authEmailTokenService.consume(
            request.token(),
            Set.of(AuthEmailTokenPurpose.PASSWORD_RESET, AuthEmailTokenPurpose.ADMIN_INVITE));
    if (user.getStatus() == UserStatus.DISABLED) {
      throw ErrorCode.USER_DISABLED.throwIt();
    }
    user.setPasswordHash(passwordEncoder.encode(request.password()));
    user.setStatus(UserStatus.ACTIVE);
    user.setUpdatedAt(OffsetDateTime.now(clock));
    refreshTokenService.revokeActiveForUser(user.getId());
    authEmailTokenService.revokeActiveForUser(user.getId());
    log.info("User password reset: userId={}", user.getId());
    return new AuthMessageResponse("Password reset successfully.");
  }

  @Override
  public CurrentUserResponse currentUser(CurrentUser user) {
    return authMapper.toCurrentUserResponse(user);
  }

  private void sendPasswordReset(AppUser user, String ip, String userAgent) {
    var token =
        authEmailTokenService.issue(
            user,
            AuthEmailTokenPurpose.PASSWORD_RESET,
            Duration.ofMinutes(authProperties.emailToken().passwordResetExpirationMinutes()),
            ip,
            userAgent);
    mailService.send(
        new PasswordResetMailModel(
            user.getEmail(), user.getDisplayName(), resetLink(token.token())));
    log.info("Password reset email requested: userId={}", user.getId());
  }

  private String normalizeEmail(String email) {
    return email.trim().toLowerCase();
  }

  private String verificationLink(String token) {
    return clientProperties.baseUrl() + "/verify-email?token=" + encode(token);
  }

  private String resetLink(String token) {
    return clientProperties.baseUrl() + "/reset-password?token=" + encode(token);
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
