package vn.vinfast.vfqc.api.user.service.impl;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vinfast.vfqc.api.auth.enums.AuthEmailTokenPurpose;
import vn.vinfast.vfqc.api.auth.service.AuthEmailTokenService;
import vn.vinfast.vfqc.api.common.config.AuthProperties;
import vn.vinfast.vfqc.api.common.config.ClientProperties;
import vn.vinfast.vfqc.api.common.error.ErrorCode;
import vn.vinfast.vfqc.api.common.mail.MailService;
import vn.vinfast.vfqc.api.common.mail.model.AdminInviteMailModel;
import vn.vinfast.vfqc.api.user.dto.AdminCreateUserRequest;
import vn.vinfast.vfqc.api.user.dto.AdminCreateUserResponse;
import vn.vinfast.vfqc.api.user.entity.AppUser;
import vn.vinfast.vfqc.api.user.enums.SystemRole;
import vn.vinfast.vfqc.api.user.enums.UserStatus;
import vn.vinfast.vfqc.api.user.repository.UserRepository;
import vn.vinfast.vfqc.api.user.service.UserAdministrationService;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserAdministrationServiceImpl implements UserAdministrationService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final AuthEmailTokenService authEmailTokenService;
  private final MailService mailService;
  private final AuthProperties authProperties;
  private final ClientProperties clientProperties;
  private final Clock clock;

  @Value("${vfqc.user.default-avatar-url}")
  private String avatarUrl;

  @Transactional
  @Override
  public AdminCreateUserResponse createUser(
      AdminCreateUserRequest request, String ip, String userAgent) {
    var email = request.email().trim().toLowerCase();
    if (userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull(email)) {
      throw ErrorCode.DUPLICATE_RESOURCE.throwIt("Email already exists");
    }
    var now = OffsetDateTime.now(clock);
    var user =
        userRepository.save(
            AppUser.builder()
                .email(email)
                .displayName(request.displayName().trim())
                .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                .avatarUrl(avatarUrl)
                .systemRole(SystemRole.USER)
                .status(UserStatus.PENDING_EMAIL_VERIFICATION)
                .createdAt(now)
                .updatedAt(now)
                .build());
    var token =
        authEmailTokenService.issue(
            user,
            AuthEmailTokenPurpose.ADMIN_INVITE,
            Duration.ofMinutes(authProperties.emailToken().verificationExpirationMinutes()),
            ip,
            userAgent);
    mailService.send(
        new AdminInviteMailModel(
            user.getEmail(), user.getDisplayName(), inviteLink(token.token())));
    log.info("Admin created user invite: userId={}", user.getId());
    return new AdminCreateUserResponse(
        user.getId(), user.getEmail(), user.getStatus(), "User created. Invite email sent.");
  }

  private String inviteLink(String token) {
    return clientProperties.baseUrl() + "/reset-password?token=" + encode(token);
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
