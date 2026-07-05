package vn.vinfast.vfqc.api.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.vinfast.vfqc.api.auth.entity.AuthEmailToken;
import vn.vinfast.vfqc.api.auth.enums.AuthEmailTokenPurpose;
import vn.vinfast.vfqc.api.auth.enums.AuthEmailTokenStatus;
import vn.vinfast.vfqc.api.auth.repository.AuthEmailTokenRepository;
import vn.vinfast.vfqc.api.common.error.ApiException;
import vn.vinfast.vfqc.api.common.error.ErrorCode;
import vn.vinfast.vfqc.api.user.entity.AppUser;
import vn.vinfast.vfqc.api.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthEmailTokenServiceImplTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-05T10:00:00Z"), ZoneOffset.UTC);

  @Mock private AuthEmailTokenRepository repository;
  @Mock private UserRepository userRepository;

  private AuthEmailTokenServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new AuthEmailTokenServiceImpl(repository, userRepository, CLOCK);
  }

  @Test
  void issue_UserAndPurpose_StoresHashOnly() {
    var user = AppUser.builder().id(UUID.randomUUID()).build();
    var captor = ArgumentCaptor.forClass(AuthEmailToken.class);

    var result =
        service.issue(
            user,
            AuthEmailTokenPurpose.PASSWORD_RESET,
            Duration.ofMinutes(15),
            "127.0.0.1",
            "JUnit");

    verify(repository).save(captor.capture());
    var stored = captor.getValue();
    assertThat(result.token()).isNotBlank();
    assertThat(stored.getTokenHash()).isNotEqualTo(result.token());
    assertThat(stored.getPurpose()).isEqualTo(AuthEmailTokenPurpose.PASSWORD_RESET);
    assertThat(stored.getStatus()).isEqualTo(AuthEmailTokenStatus.ACTIVE);
    assertThat(stored.getExpiresAt()).isEqualTo(OffsetDateTime.now(CLOCK).plusMinutes(15));
  }

  @Test
  void consume_ExpiredToken_MarksExpiredAndThrows() {
    var token =
        AuthEmailToken.builder()
            .tokenHash("hash")
            .purpose(AuthEmailTokenPurpose.EMAIL_VERIFICATION)
            .status(AuthEmailTokenStatus.ACTIVE)
            .expiresAt(OffsetDateTime.now(CLOCK).minusSeconds(1))
            .build();
    when(repository.findByTokenHashAndStatus(any(), any())).thenReturn(Optional.of(token));

    assertThatThrownBy(
            () -> service.consume("raw", Set.of(AuthEmailTokenPurpose.EMAIL_VERIFICATION)))
        .isInstanceOf(ApiException.class)
        .extracting(exception -> ((ApiException) exception).getErrorCode())
        .isEqualTo(ErrorCode.EMAIL_TOKEN_EXPIRED);
    assertThat(token.getStatus()).isEqualTo(AuthEmailTokenStatus.EXPIRED);
  }

  @Test
  void consume_ValidToken_ReturnsUserAndMarksUsed() {
    var userId = UUID.randomUUID();
    var user = AppUser.builder().id(userId).build();
    var token =
        AuthEmailToken.builder()
            .userId(userId)
            .purpose(AuthEmailTokenPurpose.ADMIN_INVITE)
            .status(AuthEmailTokenStatus.ACTIVE)
            .expiresAt(OffsetDateTime.now(CLOCK).plusMinutes(15))
            .build();
    when(repository.findByTokenHashAndStatus(any(), any())).thenReturn(Optional.of(token));
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    var result = service.consume("raw", Set.of(AuthEmailTokenPurpose.ADMIN_INVITE));

    assertThat(result).isEqualTo(user);
    assertThat(token.getStatus()).isEqualTo(AuthEmailTokenStatus.USED);
    assertThat(token.getUsedAt()).isEqualTo(OffsetDateTime.now(CLOCK));
  }
}
