package vn.vinfast.vfqc.api.auth.service.impl;

import java.time.Clock;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.vinfast.vfqc.api.common.config.AdminSeedProperties;
import vn.vinfast.vfqc.api.user.entity.AppUser;
import vn.vinfast.vfqc.api.user.enums.SystemRole;
import vn.vinfast.vfqc.api.user.enums.UserStatus;
import vn.vinfast.vfqc.api.user.repository.UserRepository;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminSeedRunner implements ApplicationRunner {

  private final AdminSeedProperties properties;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;

  @Override
  @Transactional
  public void run(@NonNull ApplicationArguments args) {
    if (properties.email() == null
        || properties.email().isBlank()
        || properties.password() == null
        || properties.password().isBlank()) {
      return;
    }
    if (userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull(properties.email())) {
      log.info("Admin seed skipped because user already exists: email={}", properties.email());
      return;
    }
    var now = OffsetDateTime.now(clock);
    userRepository.save(
        AppUser.builder()
            .email(properties.email().trim().toLowerCase())
            .displayName(properties.displayName())
            .avatarUrl(properties.avatarUrl())
            .passwordHash(passwordEncoder.encode(properties.password()))
            .systemRole(SystemRole.PLATFORM_ADMIN)
            .status(UserStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .build());
    log.info("Admin seed created platform admin: email={}", properties.email());
  }
}
