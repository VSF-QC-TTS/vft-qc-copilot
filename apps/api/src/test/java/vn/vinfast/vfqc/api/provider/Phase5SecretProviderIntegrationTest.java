package vn.vinfast.vfqc.api.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import vn.vinfast.vfqc.api.secret.enums.SecretStatus;
import vn.vinfast.vfqc.api.secret.repository.SecretRepository;
import vn.vinfast.vfqc.api.support.FakeLlmProviderClientFactory;
import vn.vinfast.vfqc.api.support.PostgresIntegrationTestBase;
import vn.vinfast.vfqc.api.workspace.enums.WorkspaceRole;

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
    assertThat(stored.getEncryptedValue())
        .startsWith("spring-crypto:aes-gcm:v1")
        .doesNotContain("sk-plain-secret");
  }

  @Test
  void rotatePersonalSecret_PersistsNewEncryptedValueAndNeverReturnsPlaintext() throws Exception {
    var user = user("rotate-owner@vinfast.vn");
    var secretId = createPersonalSecret(user, "Rotating API Key", "sk-old-secret");
    var before = secrets.findById(secretId).orElseThrow().getEncryptedValue();
    var body = Map.of("value", "sk-new-secret");

    var result =
        mockMvc
            .perform(
                post("/api/v1/secrets/" + secretId + "/rotate")
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andReturn();

    var response = result.getResponse().getContentAsString();
    var after = secrets.findById(secretId).orElseThrow();
    assertThat(response)
        .doesNotContain("sk-old-secret", "sk-new-secret", before, after.getEncryptedValue())
        .contains("\"maskedPreview\":\"****\"");
    assertThat(after.getEncryptedValue())
        .startsWith("spring-crypto:aes-gcm:v1")
        .isNotEqualTo(before)
        .doesNotContain("sk-old-secret", "sk-new-secret");
  }

  @Test
  void deletePersonalSecret_HidesSecretAndMarksDeleted() throws Exception {
    var user = user("delete-owner@vinfast.vn");
    var secretId = createPersonalSecret(user, "Deleted API Key", "sk-delete-secret");

    mockMvc
        .perform(delete("/api/v1/secrets/" + secretId).with(bearer(user)))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/v1/secrets/" + secretId).with(bearer(user)))
        .andExpect(status().isNotFound());
    var stored = secrets.findById(secretId).orElseThrow();
    assertThat(stored.getDeletedAt()).isNotNull();
    assertThat(stored.getEncryptedValue()).doesNotContain("sk-delete-secret");
  }

  @Test
  void disableSecret_BlocksProviderTestWithoutPersistingLog() throws Exception {
    var user = user("disabled-provider-owner@vinfast.vn");
    var secretId = createPersonalSecret(user, "Disabled API Key", "sk-disabled-secret");
    var providerId = createPersonalProvider(user, secretId);

    mockMvc
        .perform(post("/api/v1/secrets/" + secretId + "/disable").with(bearer(user)))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/llm-providers/" + providerId + "/test")
                .with(bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("model", "gpt-test"))))
        .andExpect(status().isNotFound());
    assertThat(secrets.findById(secretId).orElseThrow().getStatus())
        .isEqualTo(SecretStatus.DISABLED);
    assertThat(providerRepository.findById(providerId).orElseThrow().getStatus())
        .isEqualTo(ProviderStatus.ACTIVE);
    assertThat(
            testLogs.findByLlmProviderIdOrderByTestedAtDesc(
                providerId, org.springframework.data.domain.PageRequest.of(0, 1)))
        .isEmpty();
  }

  @Test
  void createPersonalProvider_WithDisabledSecret_ReturnsNotFound() throws Exception {
    var user = user("disabled-create-owner@vinfast.vn");
    var secretId = createPersonalSecret(user, "Disabled Create Key", "sk-disabled-create");

    mockMvc
        .perform(post("/api/v1/secrets/" + secretId + "/disable").with(bearer(user)))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/me/llm-providers")
                .with(bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(providerBody(secretId, "sk-disabled-create"))))
        .andExpect(status().isNotFound());
    assertThat(providerRepository.findAll()).isEmpty();
  }

  @Test
  void updatePersonalProvider_WithDeletedSecret_ReturnsNotFoundAndKeepsOldSecret()
      throws Exception {
    var user = user("deleted-update-owner@vinfast.vn");
    var activeSecretId = createPersonalSecret(user, "Active Update Key", "sk-active-update");
    var deletedSecretId = createPersonalSecret(user, "Deleted Update Key", "sk-deleted-update");
    var providerId = createPersonalProvider(user, activeSecretId);

    mockMvc
        .perform(delete("/api/v1/secrets/" + deletedSecretId).with(bearer(user)))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            patch("/api/v1/llm-providers/" + providerId)
                .with(bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("secretId", deletedSecretId.toString()))))
        .andExpect(status().isNotFound());
    assertThat(providerRepository.findById(providerId).orElseThrow().getSecretId())
        .isEqualTo(activeSecretId);
  }

  @Test
  void createWorkspaceProvider_WithSecretFromDifferentWorkspace_ReturnsScopeMismatch()
      throws Exception {
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
  void updateWorkspaceProvider_WithPersonalSecret_ReturnsScopeMismatchAndKeepsOldSecret()
      throws Exception {
    var user = user("provider-update-owner@vinfast.vn");
    var workspace = workspace(user);
    var workspaceSecretId = createWorkspaceSecret(user, workspace.getId(), "Workspace Update Key");
    var providerId = createWorkspaceProvider(user, workspace.getId(), workspaceSecretId);
    var personalSecretId = createPersonalSecret(user, "Personal Update Key", "sk-personal-secret");
    var body = Map.of("secretId", personalSecretId.toString());

    var result =
        mockMvc
            .perform(
                patch("/api/v1/llm-providers/" + providerId)
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isBadRequest())
            .andReturn();

    assertThat(result.getResponse().getContentAsString()).contains("SECRET_SCOPE_MISMATCH");
    assertThat(providerRepository.findById(providerId).orElseThrow().getSecretId())
        .isEqualTo(workspaceSecretId);
  }

  @Test
  void workspaceSecret_ForNonMember_ReturnsNotFound() throws Exception {
    var owner = user("workspace-secret-owner@vinfast.vn");
    var outsider = user("workspace-secret-outsider@vinfast.vn");
    var workspace = workspace(owner);
    var secretId = createWorkspaceSecret(owner, workspace.getId(), "Private Workspace Key");

    mockMvc
        .perform(get("/api/v1/workspaces/" + workspace.getId() + "/secrets").with(bearer(outsider)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/api/v1/secrets/" + secretId).with(bearer(outsider)))
        .andExpect(status().isNotFound());
  }

  @Test
  void workspaceProvider_ForNonMember_ReturnsHiddenNotFound() throws Exception {
    var owner = user("workspace-provider-owner@vinfast.vn");
    var outsider = user("workspace-provider-outsider@vinfast.vn");
    var workspace = workspace(owner);
    var secretId = createWorkspaceSecret(owner, workspace.getId(), "Private Provider Key");
    var providerId = createWorkspaceProvider(owner, workspace.getId(), secretId);

    mockMvc
        .perform(
            get("/api/v1/workspaces/" + workspace.getId() + "/llm-providers")
                .with(bearer(outsider)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/api/v1/llm-providers/" + providerId).with(bearer(outsider)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            post("/api/v1/llm-providers/" + providerId + "/test")
                .with(bearer(outsider))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("model", "gpt-test"))))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            get("/api/v1/llm-providers/" + providerId + "/test-logs")
                .with(bearer(outsider)))
        .andExpect(status().isNotFound());
  }

  @Test
  void workspaceViewer_CannotMutateWorkspaceSecretsOrProviders() throws Exception {
    var owner = user("workspace-viewer-owner@vinfast.vn");
    var viewer = user("workspace-viewer@vinfast.vn");
    var workspace = workspace(owner);
    workspaceMember(workspace, viewer, WorkspaceRole.VIEWER, owner);
    var secretId = createWorkspaceSecret(owner, workspace.getId(), "Viewer Boundary Key");
    var providerId = createWorkspaceProvider(owner, workspace.getId(), secretId);

    mockMvc
        .perform(
            post("/api/v1/workspaces/" + workspace.getId() + "/secrets")
                .with(bearer(viewer))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("name", "Viewer Secret", "value", "sk-viewer-denied"))))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/api/v1/workspaces/" + workspace.getId() + "/llm-providers")
                .with(bearer(viewer))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(providerBody(secretId, "sk-viewer-denied"))))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            patch("/api/v1/llm-providers/" + providerId)
                .with(bearer(viewer))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Viewer Rename"))))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(delete("/api/v1/llm-providers/" + providerId).with(bearer(viewer)))
        .andExpect(status().isForbidden());
    assertThat(providerRepository.findById(providerId).orElseThrow().getDeletedAt()).isNull();
  }

  @Test
  void deleteProvider_HidesProviderAndBlocksRuntimeTestEndpoints() throws Exception {
    var user = user("provider-delete-owner@vinfast.vn");
    var secretId = createPersonalSecret(user, "Provider Delete Key", "sk-provider-delete");
    var providerId = createPersonalProvider(user, secretId);

    mockMvc
        .perform(delete("/api/v1/llm-providers/" + providerId).with(bearer(user)))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/v1/llm-providers/" + providerId).with(bearer(user)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            post("/api/v1/llm-providers/" + providerId + "/test")
                .with(bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("model", "gpt-test"))))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/api/v1/llm-providers/" + providerId + "/test-logs").with(bearer(user)))
        .andExpect(status().isNotFound());
    assertThat(providerRepository.findById(providerId).orElseThrow().getDeletedAt()).isNotNull();
  }

  @Test
  void createProvider_SanitizesConfigInResponseAndPersistence() throws Exception {
    var user = user("provider-config-owner@vinfast.vn");
    var secretId = createPersonalSecret(user, "Config API Key", "sk-config-secret");

    var result =
        mockMvc
            .perform(
                post("/api/v1/me/llm-providers")
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(providerBody(secretId, "sk-config-secret"))))
            .andExpect(status().isOk())
            .andReturn();

    var response = result.getResponse().getContentAsString();
    var providerId = UUID.fromString(objectMapper.readTree(response).get("id").asText());
    var storedConfig = providerRepository.findById(providerId).orElseThrow().getConfigJson();
    assertThat(response).contains("\"apiKey\":\"****\"").doesNotContain("sk-config-secret");
    assertThat(storedConfig.toString()).doesNotContain("sk-config-secret");
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
    var log =
        testLogs
            .findByLlmProviderIdOrderByTestedAtDesc(
                providerId, org.springframework.data.domain.PageRequest.of(0, 1))
            .getFirst();
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

  private UUID createPersonalSecret(
      vn.vinfast.vfqc.api.user.entity.AppUser user, String name, String value) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/v1/me/secrets")
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("name", name, "value", value))))
            .andExpect(status().isOk())
            .andReturn();
    return UUID.fromString(
        objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  private UUID createWorkspaceSecret(
      vn.vinfast.vfqc.api.user.entity.AppUser user, UUID workspaceId, String name)
      throws Exception {
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
    return UUID.fromString(
        objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  private UUID createPersonalProvider(vn.vinfast.vfqc.api.user.entity.AppUser user, UUID secretId)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/v1/me/llm-providers")
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(providerBody(secretId, "sk-plain-secret"))))
            .andExpect(status().isOk())
            .andReturn();
    return UUID.fromString(
        objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  private UUID createWorkspaceProvider(
      vn.vinfast.vfqc.api.user.entity.AppUser user, UUID workspaceId, UUID secretId)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/v1/workspaces/" + workspaceId + "/llm-providers")
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(providerBody(secretId, "sk-workspace-secret"))))
            .andExpect(status().isOk())
            .andReturn();
    return UUID.fromString(
        objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  private Map<String, Object> providerBody(UUID secretId, String configSecret) {
    return Map.of(
        "name",
        "Provider " + UUID.randomUUID(),
        "providerType",
        ProviderType.OPENAI_COMPATIBLE.name(),
        "secretId",
        secretId.toString(),
        "baseUrl",
        "https://example.test/v1",
        "defaultModel",
        "gpt-test",
        "config",
        Map.of(
            "apiKey",
            configSecret,
            "headers",
            Map.of("Authorization", "Bearer " + configSecret)));
  }
}
