package vn.vinfast.vfqc.api.common.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import vn.vinfast.vfqc.api.support.PostgresIntegrationTestBase;

@RequiredArgsConstructor
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class SecurityBoundaryIntegrationTest extends PostgresIntegrationTestBase {

  private final MockMvc mockMvc;

  @Test
  void internalRunner_RejectsUserJwtAndAcceptsOnlyRunnerToken() throws Exception {
    var user = user("runner-boundary-user@vinfast.vn");

    mockMvc.perform(get("/internal/runner/probe").with(bearer(user)))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/internal/runner/probe").header("Authorization", "Bearer dev-runner-token"))
        .andExpect(status().isNotFound());
  }

  @Test
  void api_RejectsRunnerTokenAndAcceptsUserJwt() throws Exception {
    var user = user("api-boundary-user@vinfast.vn");

    mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer dev-runner-token"))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/v1/auth/me").with(bearer(user))).andExpect(status().isOk());
  }
}
