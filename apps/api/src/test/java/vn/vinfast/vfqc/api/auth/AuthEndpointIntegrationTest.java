package vn.vinfast.vfqc.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import vn.vinfast.vfqc.api.auth.enums.AuthEmailTokenPurpose;
import vn.vinfast.vfqc.api.auth.enums.AuthEmailTokenStatus;
import vn.vinfast.vfqc.api.auth.service.AuthEmailTokenService;
import vn.vinfast.vfqc.api.support.PostgresIntegrationTestBase;
import vn.vinfast.vfqc.api.user.entity.AppUser;
import vn.vinfast.vfqc.api.user.enums.SystemRole;
import vn.vinfast.vfqc.api.user.enums.UserStatus;
import vn.vinfast.vfqc.api.workspace.enums.WorkspaceMemberStatus;
import vn.vinfast.vfqc.api.workspace.enums.WorkspaceRole;
import vn.vinfast.vfqc.api.workspace.enums.WorkspaceType;

@RequiredArgsConstructor
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@TestPropertySource(properties = "vfqc.auth.registration.mode=SELF_VERIFY")
class AuthEndpointIntegrationTest extends PostgresIntegrationTestBase {

  private static final String REFRESH_COOKIE = "vfqc_refresh_token";

  private final MockMvc mockMvc;
  private final ObjectMapper objectMapper;
  private final AuthEmailTokenService authEmailTokenService;

  @Test
  void register_SelfVerify_CreatesPendingUserAndRejectsLoginUntilVerified() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "email", "new.user@vinfast.vn",
                            "displayName", "New User",
                            "password", "ChangeMe@123"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("new.user@vinfast.vn"))
        .andExpect(jsonPath("$.status").value("PENDING_EMAIL_VERIFICATION"));

    var user =
        userRepository
            .findByEmailIgnoreCaseAndDeletedAtIsNull("new.user@vinfast.vn")
            .orElseThrow();
    assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_EMAIL_VERIFICATION);
    assertThat(emailTokenRepository.findAll())
        .singleElement()
        .satisfies(
            token -> {
              assertThat(token.getUserId()).isEqualTo(user.getId());
              assertThat(token.getPurpose()).isEqualTo(AuthEmailTokenPurpose.EMAIL_VERIFICATION);
              assertThat(token.getStatus()).isEqualTo(AuthEmailTokenStatus.ACTIVE);
            });
    assertThat(workspaceRepository.findAll()).isEmpty();

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "new.user@vinfast.vn", "password", "ChangeMe@123"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void verifyEmail_ValidToken_ActivatesUserCreatesPersonalWorkspaceAndSupportsSessionLifecycle()
      throws Exception {
    var pending = pendingUser("verify.user@vinfast.vn", "Verify User", "{noop}OldPassword@123");
    var token =
        authEmailTokenService.issue(
            pending,
            AuthEmailTokenPurpose.EMAIL_VERIFICATION,
            Duration.ofMinutes(15),
            "127.0.0.1",
            "integration-test");

    mockMvc
        .perform(
            post("/api/v1/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("token", token.token()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("Email verified."));

    var activated = userRepository.findById(pending.getId()).orElseThrow();
    assertThat(activated.getStatus()).isEqualTo(UserStatus.ACTIVE);
    var personalWorkspace =
        workspaceRepository.findAll().stream()
            .filter(workspace -> workspace.getCreatedBy().equals(activated.getId()))
            .filter(workspace -> workspace.getType() == WorkspaceType.PERSONAL)
            .findFirst()
            .orElseThrow();
    assertThat(
            workspaceMemberRepository.findByWorkspaceIdAndUserIdAndStatus(
                personalWorkspace.getId(), activated.getId(), WorkspaceMemberStatus.ACTIVE))
        .hasValueSatisfying(member -> assertThat(member.getRole()).isEqualTo(WorkspaceRole.OWNER));

    var loginResult =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json(Map.of("email", "verify.user@vinfast.vn", "password", "OldPassword@123"))))
            .andExpect(status().isOk())
            .andExpect(cookie().exists(REFRESH_COOKIE))
            .andExpect(jsonPath("$.user.email").value("verify.user@vinfast.vn"))
            .andReturn();
    var refreshCookie = loginResult.getResponse().getCookie(REFRESH_COOKIE);
    assertThat(refreshCookie).isNotNull();

    var refreshResult =
        mockMvc
            .perform(post("/api/v1/auth/refresh").cookie(refreshCookie))
            .andExpect(status().isOk())
            .andExpect(cookie().exists(REFRESH_COOKIE))
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andReturn();
    var rotatedRefreshCookie = refreshResult.getResponse().getCookie(REFRESH_COOKIE);
    assertThat(rotatedRefreshCookie).isNotNull();

    mockMvc
        .perform(post("/api/v1/auth/logout").cookie(rotatedRefreshCookie))
        .andExpect(status().isNoContent())
        .andExpect(cookie().maxAge(REFRESH_COOKIE, 0));
  }

  @Test
  void forgotPassword_KnownAndUnknownEmail_ReturnSameGenericMessage() throws Exception {
    user("forgot.user@vinfast.vn");

    var unknown =
        mockMvc
            .perform(
                post("/api/v1/auth/password/forgot")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("email", "missing@vinfast.vn"))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    var known =
        mockMvc
            .perform(
                post("/api/v1/auth/password/forgot")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("email", "forgot.user@vinfast.vn"))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(known).isEqualTo(unknown);
    assertThat(emailTokenRepository.findAll())
        .singleElement()
        .satisfies(
            token -> {
              assertThat(token.getPurpose()).isEqualTo(AuthEmailTokenPurpose.PASSWORD_RESET);
              assertThat(token.getStatus()).isEqualTo(AuthEmailTokenStatus.ACTIVE);
            });
  }

  @Test
  void resetPassword_AdminInviteToken_ActivatesUserAndCreatesPersonalWorkspace() throws Exception {
    var invited = pendingUser("invite.user@vinfast.vn", "Invite User", "{noop}Temporary@123");
    var token =
        authEmailTokenService.issue(
            invited,
            AuthEmailTokenPurpose.ADMIN_INVITE,
            Duration.ofMinutes(15),
            "127.0.0.1",
            "integration-test");

    mockMvc
        .perform(
            post("/api/v1/auth/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("token", token.token(), "password", "NewPassword@123"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("Password reset successfully."));

    assertThat(userRepository.findById(invited.getId()).orElseThrow().getStatus())
        .isEqualTo(UserStatus.ACTIVE);
    assertThat(
            workspaceRepository.existsByCreatedByAndTypeAndDeletedAtIsNull(
                invited.getId(), WorkspaceType.PERSONAL))
        .isTrue();
    assertThat(emailTokenRepository.findByUserIdAndStatus(invited.getId(), AuthEmailTokenStatus.ACTIVE))
        .isEmpty();

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "invite.user@vinfast.vn", "password", "NewPassword@123"))))
        .andExpect(status().isOk());
  }

  private AppUser pendingUser(String email, String displayName, String passwordHash) {
    var now = OffsetDateTime.now();
    return userRepository.save(
        AppUser.builder()
            .email(email)
            .displayName(displayName)
            .passwordHash(passwordHash)
            .systemRole(SystemRole.USER)
            .status(UserStatus.PENDING_EMAIL_VERIFICATION)
            .createdAt(now)
            .updatedAt(now)
            .build());
  }

  private String json(Object value) throws Exception {
    return objectMapper.writeValueAsString(value);
  }
}
