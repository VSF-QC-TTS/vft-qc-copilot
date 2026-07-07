package vn.vinfast.vfqc.api.user.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import vn.vinfast.vfqc.api.auth.enums.AuthEmailTokenPurpose;
import vn.vinfast.vfqc.api.auth.service.AuthEmailTokenIssue;
import vn.vinfast.vfqc.api.auth.service.AuthEmailTokenService;
import vn.vinfast.vfqc.api.common.config.AuthProperties;
import vn.vinfast.vfqc.api.common.config.AuthProperties.RegistrationMode;
import vn.vinfast.vfqc.api.common.config.ClientProperties;
import vn.vinfast.vfqc.api.common.config.UserProperties;
import vn.vinfast.vfqc.api.common.mail.event.MailSendEvent;
import vn.vinfast.vfqc.api.common.mail.model.AdminInviteMailModel;
import vn.vinfast.vfqc.api.common.util.AuthLinkBuilder;
import vn.vinfast.vfqc.api.user.dto.AdminCreateUserRequest;
import vn.vinfast.vfqc.api.user.entity.AppUser;
import vn.vinfast.vfqc.api.user.enums.SystemRole;
import vn.vinfast.vfqc.api.user.enums.UserStatus;
import vn.vinfast.vfqc.api.user.repository.UserRepository;
import vn.vinfast.vfqc.api.user.service.UserSummaryService;

@ExtendWith(MockitoExtension.class)
class UserAdministrationServiceImplTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-05T10:00:00Z"), ZoneOffset.UTC);

  @Mock private UserRepository userRepository;
  @Mock private AuthEmailTokenService authEmailTokenService;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private UserSummaryService userSummaryService;

  private UserAdministrationServiceImpl service;

  @BeforeEach
  void setUp() {
    var linkBuilder = new AuthLinkBuilder(new ClientProperties("http://localhost:3000"));
    service =
        new UserAdministrationServiceImpl(
            userRepository,
            PasswordEncoderFactories.createDelegatingPasswordEncoder(),
            authEmailTokenService,
            eventPublisher,
            new AuthProperties(
                new AuthProperties.Registration(RegistrationMode.ADMIN_ONLY),
                new AuthProperties.EmailToken(1440, 15)),
            new UserProperties("https://example.com/avatar.png"),
            userSummaryService,
            linkBuilder,
            CLOCK);
  }

  @Test
  void createUser_NewEmail_CreatesPendingUserAndSendsInviteMail() {
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
            any(), eq(AuthEmailTokenPurpose.ADMIN_INVITE), any(), eq("127.0.0.1"), eq("JUnit")))
        .thenReturn(new AuthEmailTokenIssue("raw-token", OffsetDateTime.now(CLOCK).plusDays(1)));
    var userCaptor = ArgumentCaptor.forClass(AppUser.class);
    var eventCaptor = ArgumentCaptor.forClass(MailSendEvent.class);

    var response =
        service.createUser(
            new AdminCreateUserRequest("USER@vinfast.vn", "Test User"), "127.0.0.1", "JUnit");

    verify(userRepository).save(userCaptor.capture());
    assertThat(response.id()).isEqualTo(userId);
    assertThat(userCaptor.getValue().getEmail()).isEqualTo("user@vinfast.vn");
    assertThat(userCaptor.getValue().getSystemRole()).isEqualTo(SystemRole.USER);
    assertThat(userCaptor.getValue().getStatus()).isEqualTo(UserStatus.PENDING_EMAIL_VERIFICATION);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().model()).isInstanceOf(AdminInviteMailModel.class);
    assertThat(((AdminInviteMailModel) eventCaptor.getValue().model()).inviteLink())
        .contains("/reset-password?token=");
  }
}
