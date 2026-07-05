package vn.vinfast.vfqc.api.user.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vinfast.vfqc.api.auth.enums.AuthEmailTokenPurpose;
import vn.vinfast.vfqc.api.auth.service.AuthEmailTokenService;
import vn.vinfast.vfqc.api.common.config.AuthProperties;
import vn.vinfast.vfqc.api.common.config.UserProperties;
import vn.vinfast.vfqc.api.common.error.ErrorCode;
import vn.vinfast.vfqc.api.common.mail.event.MailSendEvent;
import vn.vinfast.vfqc.api.common.mail.model.AdminInviteMailModel;
import vn.vinfast.vfqc.api.common.util.AuthLinkBuilder;
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
  private final ApplicationEventPublisher eventPublisher;
  private final AuthProperties authProperties;
  private final UserProperties userProperties;
  private final AuthLinkBuilder linkBuilder;
  private final Clock clock;

  @Transactional
  @Override
  public AdminCreateUserResponse createUser(
      AdminCreateUserRequest request, String ip, String userAgent) {
    var email = AuthLinkBuilder.normalizeEmail(request.email());
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
                .avatarUrl(userProperties.defaultAvatarUrl())
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
    eventPublisher.publishEvent(
        new MailSendEvent(
            new AdminInviteMailModel(
                user.getEmail(), user.getDisplayName(), linkBuilder.inviteLink(token.token()))));
    log.info("Admin created user invite: userId={}", user.getId());
    return new AdminCreateUserResponse(
        user.getId(), user.getEmail(), user.getStatus(), "User created. Invite email sent.");
  }
}
