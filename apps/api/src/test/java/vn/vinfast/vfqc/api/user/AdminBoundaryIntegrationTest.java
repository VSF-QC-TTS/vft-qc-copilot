package vn.vinfast.vfqc.api.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import vn.vinfast.vfqc.api.support.PostgresIntegrationTestBase;
import vn.vinfast.vfqc.api.user.enums.UserStatus;

@RequiredArgsConstructor
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AdminBoundaryIntegrationTest extends PostgresIntegrationTestBase {

  private final MockMvc mockMvc;
  private final ObjectMapper objectMapper;

  @Test
  void createUser_RequiresPlatformAdminRole() throws Exception {
    var normalUser = user("normal.admin-boundary@vinfast.vn");
    var platformAdmin = platformAdmin("platform.admin-boundary@vinfast.vn");
    var request = json(Map.of("email", "created.by.admin@vinfast.vn", "displayName", "Created User"));

    mockMvc
        .perform(
            post("/api/v1/admin/users")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
        .andExpect(status().isUnauthorized());

    mockMvc
        .perform(
            post("/api/v1/admin/users")
                .with(bearer(normalUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            post("/api/v1/admin/users")
                .with(bearer(platformAdmin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("created.by.admin@vinfast.vn"))
        .andExpect(jsonPath("$.status").value("PENDING_EMAIL_VERIFICATION"))
        .andExpect(jsonPath("$.user.email").value("created.by.admin@vinfast.vn"));

    var created =
        userRepository
            .findByEmailIgnoreCaseAndDeletedAtIsNull("created.by.admin@vinfast.vn")
            .orElseThrow();
    assertThat(created.getStatus()).isEqualTo(UserStatus.PENDING_EMAIL_VERIFICATION);
    assertThat(emailTokenRepository.findAll()).hasSize(1);
  }

  private String json(Object value) throws Exception {
    return objectMapper.writeValueAsString(value);
  }
}
