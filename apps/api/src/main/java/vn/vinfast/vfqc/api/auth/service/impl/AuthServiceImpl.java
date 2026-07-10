package vn.vinfast.vfqc.api.auth.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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
import vn.vinfast.vfqc.api.common.config.UserProperties;
import vn.vinfast.vfqc.api.common.error.ErrorCode;
import vn.vinfast.vfqc.api.common.mail.event.MailSendEvent;
import vn.vinfast.vfqc.api.common.mail.model.EmailVerificationMailModel;
import vn.vinfast.vfqc.api.common.mail.model.PasswordResetMailModel;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.common.util.AuthLinkBuilder;
import vn.vinfast.vfqc.api.user.entity.AppUser;
import vn.vinfast.vfqc.api.user.enums.SystemRole;
import vn.vinfast.vfqc.api.user.enums.UserStatus;
import vn.vinfast.vfqc.api.user.repository.UserRepository;
import vn.vinfast.vfqc.api.user.service.UserOnboardingService;

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
  private final ApplicationEventPublisher eventPublisher;
  private final AuthProperties authProperties;
  private final UserProperties userProperties;
  private final UserOnboardingService userOnboardingService;
  private final AuthLinkBuilder linkBuilder;
  private final AuthMapper authMapper;
  private final Clock clock;

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
                .avatarUrl(userProperties.defaultAvatarUrl())
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
    eventPublisher.publishEvent(
        new MailSendEvent(
            new EmailVerificationMailModel(
                user.getEmail(),
                user.getDisplayName(),
                linkBuilder.verificationLink(token.token()))));
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
    var normalizedEmail = normalizeEmail(request.email());

    var user =
        userRepository
            .findByEmailIgnoreCaseAndDeletedAtIsNull(normalizedEmail)
            .orElseThrow(ErrorCode.INVALID_CREDENTIALS::throwIt);

    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw ErrorCode.INVALID_CREDENTIALS.throwIt();
    }

    if (user.getStatus() == UserStatus.PENDING_EMAIL_VERIFICATION) {
      throw ErrorCode.EMAIL_NOT_VERIFIED.throwIt();
    }

    if (!user.active()) {
      throw ErrorCode.USER_DISABLED.throwIt();
    }

    if (passwordEncoder.upgradeEncoding(user.getPasswordHash())) {
      user.setPasswordHash(passwordEncoder.encode(request.password()));
    }

    var refreshToken = refreshTokenService.issue(user, ip, userAgent);

    var response =
        new LoginResponse(
            jwtTokenService.issueAccessToken(user),
            "Bearer",
            jwtTokenService.accessExpiresInSeconds(),
            authMapper.toCurrentUserResponse(user));

    log.info("User login succeeded: userId={}", user.getId());

    return new LoginResult(response, refreshToken);
  }

  @Transactional
  @Override
  public RefreshResult refresh(String refreshToken, String ip, String userAgent) {
    var rotation = refreshTokenService.rotate(refreshToken);
    var user = rotation.user();

    if (user.getDeletedAt() != null) {
      throw ErrorCode.USER_DISABLED.throwIt();
    }

    if (user.getStatus() == UserStatus.PENDING_EMAIL_VERIFICATION) {
      throw ErrorCode.EMAIL_NOT_VERIFIED.throwIt();
    }

    if (!user.active()) {
      throw ErrorCode.USER_DISABLED.throwIt();
    }

    var newRefreshToken = refreshTokenService.issue(user, rotation.familyId(), ip, userAgent);

    log.info("Access token refreshed: userId={}", user.getId());

    return new RefreshResult(
        new RefreshAccessTokenResponse(
            jwtTokenService.issueAccessToken(user),
            "Bearer",
            jwtTokenService.accessExpiresInSeconds()),
        newRefreshToken);
  }

  @Transactional
  @Override
  public AuthMessageResponse verifyEmail(VerifyEmailRequest request) {
    var user =
        authEmailTokenService.consume(
            request.token(), Set.of(AuthEmailTokenPurpose.EMAIL_VERIFICATION));
    if (user.getDeletedAt() != null || user.getStatus() == UserStatus.DISABLED) {
      throw ErrorCode.USER_DISABLED.throwIt();
    }
    if (user.getStatus() == UserStatus.PENDING_EMAIL_VERIFICATION) {
      user.setStatus(UserStatus.ACTIVE);
      user.setUpdatedAt(OffsetDateTime.now(clock));
    }
    userOnboardingService.ensurePersonalWorkspace(user);
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
    var consumption =
        authEmailTokenService.consumeWithPurpose(
            request.token(),
            Set.of(AuthEmailTokenPurpose.PASSWORD_RESET, AuthEmailTokenPurpose.ADMIN_INVITE));
    var user = consumption.user();
    var purpose = consumption.purpose();

    if (user.getStatus() == UserStatus.DISABLED) {
      throw ErrorCode.USER_DISABLED.throwIt();
    }

    switch (purpose) {
      case PASSWORD_RESET -> {
        if (!user.active()) {
          throw ErrorCode.USER_DISABLED.throwIt();
        }
      }
      case ADMIN_INVITE -> user.setStatus(UserStatus.ACTIVE);
    }

    user.setPasswordHash(passwordEncoder.encode(request.password()));
    user.setUpdatedAt(OffsetDateTime.now(clock));
    refreshTokenService.revokeActiveForUser(user.getId());
    authEmailTokenService.revokeActiveForUser(user.getId());
    if (consumption.purpose() == AuthEmailTokenPurpose.ADMIN_INVITE) {
      userOnboardingService.ensurePersonalWorkspace(user);
    }
    log.info("User password updated: userId={}, purpose={}", user.getId(), purpose);
    return new AuthMessageResponse("Password reset successfully.");
  }

  @Transactional(readOnly = true)
  @Override
  public CurrentUserResponse currentUser(CurrentUser currentUser) {
    var persistedUser =
        userRepository
            .findByIdAndDeletedAtIsNull(currentUser.id())
            .orElseThrow(ErrorCode.INVALID_CREDENTIALS::throwIt);

    if (persistedUser.getDeletedAt() != null) {
      throw ErrorCode.INVALID_CREDENTIALS.throwIt();
    }

    if (!persistedUser.active()) {
      throw ErrorCode.USER_DISABLED.throwIt();
    }

    return authMapper.toCurrentUserResponse(persistedUser);
  }

  private void sendPasswordReset(AppUser user, String ip, String userAgent) {
    var token =
        authEmailTokenService.issue(
            user,
            AuthEmailTokenPurpose.PASSWORD_RESET,
            Duration.ofMinutes(authProperties.emailToken().passwordResetExpirationMinutes()),
            ip,
            userAgent);
    eventPublisher.publishEvent(
        new MailSendEvent(
            new PasswordResetMailModel(
                user.getEmail(), user.getDisplayName(), linkBuilder.resetLink(token.token()))));
    log.info("Password reset email requested: userId={}", user.getId());
  }

  private String normalizeEmail(String email) {
    return AuthLinkBuilder.normalizeEmail(email);
  }
}
