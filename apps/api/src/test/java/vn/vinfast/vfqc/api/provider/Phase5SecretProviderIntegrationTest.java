package vn.vinfast.vfqc.api.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import vn.vinfast.vfqc.api.provider.enums.ProviderStatus;
import vn.vinfast.vfqc.api.provider.enums.ProviderTestStatus;
import vn.vinfast.vfqc.api.provider.enums.ProviderType;
import vn.vinfast.vfqc.api.provider.repository.LlmProviderTestLogRepository;
import vn.vinfast.vfqc.api.secret.repository.SecretRepository;
import vn.vinfast.vfqc.api.support.FakeLlmProviderClientFactory;
import vn.vinfast.vfqc.api.support.PostgresIntegrationTestBase;

@Import(FakeLlmProviderClientFactory.class)
@RequiredArgsConstructor
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class Phase5SecretProviderIntegrationTest extends PostgresIntegrationTestBase {

  private final MockMvc mockMvc;
  private final ObjectMapper objectMapper;
  private final FakeLlmProviderClientFactory fakeClientFactory;
  private final SecretRepository secrets;
  private final LlmProviderTestLogRepository testLogs;

  @Test
  void meSecrets_Unauthenticated_ReturnsUnauthorized() throws Exception {
    mockMvc.perform(get("/api/v1/me/secrets")).andExpect(status().isUnauthorized());
  }

  @Test
  void createPersonalSecret_PersistsEncryptedValueAndReturnsMaskedResponse() throws Exception {
    var user = user("secret-owner@vinfast.vn");
    var body = Map.of("name", "Gemini Key", "value", "sk-plain-secret");

    var result =
        mockMvc
            .perform(
                post("/api/v1/me/secrets")
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andReturn();

    var json = objectMapper.readTree(result.getResponse().getContentAsString());
    var secretId = UUID.fromString(json.get("id").asText());
    var stored = secrets.findById(secretId).orElseThrow();
    assertThat(json.toString()).doesNotContain("sk-plain-secret", stored.getEncryptedValue());
    assertThat(json.get("maskedPreview").asText()).isEqualTo("****");
    assertThat(stored.getEncryptedValue()).startsWith("v1:").doesNotContain("sk-plain-secret");
  }

  @Test
  void createWorkspaceProvider_WithSecretFromDifferentWorkspace_ReturnsScopeMismatch() throws Exception {
    var user = user("workspace-owner@vinfast.vn");
    var sourceWorkspace = workspace(user);
    var targetWorkspace = workspace(user);
    var secretId = createWorkspaceSecret(user, sourceWorkspace.getId(), "Workspace API Key");
    var body =
        Map.of(
            "name",
            "Workspace Provider",
            "providerType",
            ProviderType.OPENAI_COMPATIBLE.name(),
            "secretId",
            secretId.toString(),
            "baseUrl",
            "https://example.test/v1",
            "defaultModel",
            "gpt-test",
            "config",
            Map.of("apiKey", "sk-plain-secret"));

    var result =
        mockMvc
            .perform(
                post("/api/v1/workspaces/" + targetWorkspace.getId() + "/llm-providers")
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isBadRequest())
            .andReturn();

    assertThat(result.getResponse().getContentAsString()).contains("SECRET_SCOPE_MISMATCH");
  }

  @Test
  void providerTest_Failure_PersistsSanitizedLogAndFailedStatus() throws Exception {
    var user = user("provider-owner@vinfast.vn");
    var secretId = createPersonalSecret(user, "Provider API Key", "sk-plain-secret");
    var providerId = createPersonalProvider(user, secretId);
    fakeClientFactory.failWith("provider rejected Bearer sk-plain-secret");

    var result =
        mockMvc
            .perform(
                post("/api/v1/llm-providers/" + providerId + "/test")
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("model", "gpt-test"))))
            .andExpect(status().isOk())
            .andReturn();

    var response = result.getResponse().getContentAsString();
    assertThat(response).contains(ProviderTestStatus.FAILED.name());
    assertThat(response).doesNotContain("sk-plain-secret");
    var provider = providerRepository.findById(providerId).orElseThrow();
    assertThat(provider.getStatus()).isEqualTo(ProviderStatus.TEST_FAILED);
    var log = testLogs.findByLlmProviderIdOrderByTestedAtDesc(providerId, org.springframework.data.domain.PageRequest.of(0, 1)).getFirst();
    assertThat(log.getErrorMessage()).isEqualTo("provider rejected Bearer ****");
  }

  @Test
  void providerTest_SuccessHistory_ReturnsSanitizedLog() throws Exception {
    var user = user("history-owner@vinfast.vn");
    var secretId = createPersonalSecret(user, "History API Key", "sk-plain-secret");
    var providerId = createPersonalProvider(user, secretId);
    fakeClientFactory.succeedWith("ok Bearer sk-plain-secret");

    mockMvc
        .perform(
            post("/api/v1/llm-providers/" + providerId + "/test")
                .with(bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("model", "gpt-test"))))
        .andExpect(status().isOk());

    var result =
        mockMvc
            .perform(
                get("/api/v1/llm-providers/" + providerId + "/test-logs?limit=50")
                    .with(bearer(user)))
            .andExpect(status().isOk())
            .andReturn();

    var response = result.getResponse().getContentAsString();
    assertThat(response).contains("ok Bearer ****").doesNotContain("sk-plain-secret");
    assertThat(providerRepository.findById(providerId).orElseThrow().getStatus())
        .isEqualTo(ProviderStatus.ACTIVE);
  }

  private UUID createPersonalSecret(vn.vinfast.vfqc.api.user.entity.AppUser user, String name, String value)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/v1/me/secrets")
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("name", name, "value", value))))
            .andExpect(status().isOk())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  private UUID createWorkspaceSecret(
      vn.vinfast.vfqc.api.user.entity.AppUser user, UUID workspaceId, String name) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/v1/workspaces/" + workspaceId + "/secrets")
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of("name", name, "value", "sk-workspace-secret"))))
            .andExpect(status().isOk())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  private UUID createPersonalProvider(vn.vinfast.vfqc.api.user.entity.AppUser user, UUID secretId)
      throws Exception {
    var body =
        Map.of(
            "name",
            "Personal Provider " + UUID.randomUUID(),
            "providerType",
            ProviderType.OPENAI_COMPATIBLE.name(),
            "secretId",
            secretId.toString(),
            "baseUrl",
            "https://example.test/v1",
            "defaultModel",
            "gpt-test",
            "config",
            Map.of("apiKey", "sk-plain-secret"));
    var result =
        mockMvc
            .perform(
                post("/api/v1/me/llm-providers")
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }
}
