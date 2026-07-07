package vn.vinfast.vfqc.api.evaluator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import vn.vinfast.vfqc.api.evaluator.enums.EvaluatorType;
import vn.vinfast.vfqc.api.provider.entity.LlmProvider;
import vn.vinfast.vfqc.api.provider.enums.ProviderScope;
import vn.vinfast.vfqc.api.provider.enums.ProviderStatus;
import vn.vinfast.vfqc.api.provider.enums.ProviderType;
import vn.vinfast.vfqc.api.support.PostgresIntegrationTestBase;
import vn.vinfast.vfqc.api.target.entity.ChatbotTarget;
import vn.vinfast.vfqc.api.target.enums.ChatbotTargetStatus;
import vn.vinfast.vfqc.api.workspace.enums.WorkspaceRole;

@RequiredArgsConstructor
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class Phase7EvaluatorIntegrationTest extends PostgresIntegrationTestBase {

  private final MockMvc mockMvc;
  private final ObjectMapper objectMapper;

  @Test
  void createRuleBasedEvaluator_AndCreateVersion_PreservesImmutableVersions() throws Exception {
    var user = user("evaluator-owner@vinfast.vn");
    var workspace = workspace(user);
    var projectId = createProject(user, workspace.getId());

    var evaluatorId = createRuleBasedEvaluator(user, projectId, "Basic evaluator");

    assertThat(evaluatorRepository.findById(evaluatorId).orElseThrow().getType())
        .isEqualTo(EvaluatorType.RULE_BASED);
    assertThat(evaluatorVersionRepository.findByEvaluatorIdOrderByVersionDesc(evaluatorId))
        .hasSize(1)
        .singleElement()
        .satisfies(version -> assertThat(version.getVersion()).isEqualTo(1));
    assertThat(evaluatorRuleRepository.findAll()).hasSize(1);

    mockMvc
        .perform(
            post("/api/v1/evaluators/" + evaluatorId + "/versions")
                .with(bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "config", ruleBasedConfig(),
                            "rules",
                            List.of(
                                rule(
                                    "response.statusCode",
                                    "NUMERIC_GTE",
                                    Map.of("type", "number", "value", 200)))))))
        .andExpect(status().isCreated());

    assertThat(evaluatorVersionRepository.findByEvaluatorIdOrderByVersionDesc(evaluatorId))
        .extracting("version")
        .containsExactly(2, 1);
    assertThat(evaluatorRuleRepository.findAll()).hasSize(2);
  }

  @Test
  void createEvaluator_WorkspaceViewer_ReturnsForbidden() throws Exception {
    var owner = user("evaluator-viewer-owner@vinfast.vn");
    var viewer = user("evaluator-viewer@vinfast.vn");
    var workspace = workspace(owner);
    workspaceMember(workspace, viewer, WorkspaceRole.VIEWER, owner);
    var projectId = createProject(owner, workspace.getId());

    mockMvc
        .perform(
            post("/api/v1/projects/" + projectId + "/evaluators")
                .with(bearer(viewer))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createBody("Viewer evaluator"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void createEvaluator_WithCustomJavaScript_ReturnsBadRequest() throws Exception {
    var user = user("evaluator-js-owner@vinfast.vn");
    var workspace = workspace(user);
    var projectId = createProject(user, workspace.getId());

    var body =
        Map.of(
            "name",
            "Unsafe evaluator",
            "type",
            "RULE_BASED",
            "config",
            ruleBasedConfig(),
            "rules",
            List.of(
                Map.of(
                    "fieldSource",
                    "response.statusCode",
                    "operator",
                    "NUMERIC_EQUALS",
                    "expectedValue",
                    Map.of("type", "number", "value", 200),
                    "required",
                    true,
                    "orderIndex",
                    1,
                    "config",
                    Map.of("javascript", "return process.env.SECRET"))));

    mockMvc
        .perform(
            post("/api/v1/projects/" + projectId + "/evaluators")
                .with(bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void validateEvaluator_WithTargetFieldSources_RejectsUnknownExtractedField() throws Exception {
    var user = user("evaluator-validation-owner@vinfast.vn");
    var workspace = workspace(user);
    var projectId = createProject(user, workspace.getId());
    var targetId = targetWithExtraction(projectId, user.getId());

    mockMvc
        .perform(
            post("/api/v1/projects/" + projectId + "/evaluators/validate")
                .with(bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(validateBody(targetId, "extracted.answer"))))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/projects/" + projectId + "/evaluators/validate")
                .with(bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(validateBody(targetId, "extracted.missing"))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void setupStatus_AfterEvaluatorCreate_ReportsActiveEvaluator() throws Exception {
    var user = user("evaluator-setup-owner@vinfast.vn");
    var workspace = workspace(user);
    var projectId = createProject(user, workspace.getId());

    createRuleBasedEvaluator(user, projectId, "Setup evaluator");

    var result =
        mockMvc
            .perform(get("/api/v1/projects/" + projectId + "/setup-status").with(bearer(user)))
            .andExpect(status().isOk())
            .andReturn();

    var json = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(json.get("steps").get("hasEvaluator").asBoolean()).isTrue();
    assertThat(json.get("counts").get("activeEvaluators").asLong()).isEqualTo(1);
  }

  @Test
  void updateEvaluatorMetadata_DoesNotChangeType() throws Exception {
    var user = user("evaluator-update-owner@vinfast.vn");
    var workspace = workspace(user);
    var projectId = createProject(user, workspace.getId());
    var evaluatorId = createRuleBasedEvaluator(user, projectId, "Original evaluator");

    mockMvc
        .perform(
            patch("/api/v1/evaluators/" + evaluatorId)
                .with(bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Renamed evaluator"))))
        .andExpect(status().isOk());

    var evaluator = evaluatorRepository.findById(evaluatorId).orElseThrow();
    assertThat(evaluator.getName()).isEqualTo("Renamed evaluator");
    assertThat(evaluator.getType()).isEqualTo(EvaluatorType.RULE_BASED);
  }

  @Test
  void createOrUpdateEvaluator_WithDuplicateName_ReturnsConflict() throws Exception {
    var user = user("evaluator-duplicate-owner@vinfast.vn");
    var workspace = workspace(user);
    var projectId = createProject(user, workspace.getId());
    var firstId = createRuleBasedEvaluator(user, projectId, "Duplicate evaluator");

    mockMvc
        .perform(
            post("/api/v1/projects/" + projectId + "/evaluators")
                .with(bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createBody(" duplicate   evaluator "))))
        .andExpect(status().isConflict());

    var secondId = createRuleBasedEvaluator(user, projectId, "Other evaluator");
    mockMvc
        .perform(
            patch("/api/v1/evaluators/" + secondId)
                .with(bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Duplicate evaluator"))))
        .andExpect(status().isConflict());

    assertThat(evaluatorRepository.findById(firstId)).isPresent();
  }

  @Test
  void deleteEvaluator_HidesEvaluatorAndVersions() throws Exception {
    var user = user("evaluator-delete-owner@vinfast.vn");
    var workspace = workspace(user);
    var projectId = createProject(user, workspace.getId());
    var evaluatorId = createRuleBasedEvaluator(user, projectId, "Deleted evaluator");
    var versionId =
        evaluatorVersionRepository.findByEvaluatorIdOrderByVersionDesc(evaluatorId).getFirst().getId();

    mockMvc
        .perform(delete("/api/v1/evaluators/" + evaluatorId).with(bearer(user)))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/v1/evaluators/" + evaluatorId).with(bearer(user)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/api/v1/evaluators/" + evaluatorId + "/versions").with(bearer(user)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/api/v1/evaluator-versions/" + versionId).with(bearer(user)))
        .andExpect(status().isNotFound());

    var listResult =
        mockMvc
            .perform(get("/api/v1/projects/" + projectId + "/evaluators").with(bearer(user)))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(objectMapper.readTree(listResult.getResponse().getContentAsString()).get("items")).isEmpty();
  }

  @Test
  void evaluatorVersionListAndDetail_ReturnRuleReadModels() throws Exception {
    var user = user("evaluator-version-owner@vinfast.vn");
    var workspace = workspace(user);
    var projectId = createProject(user, workspace.getId());
    var evaluatorId = createRuleBasedEvaluator(user, projectId, "Version read evaluator");

    var listResult =
        mockMvc
            .perform(get("/api/v1/evaluators/" + evaluatorId + "/versions").with(bearer(user)))
            .andExpect(status().isOk())
            .andReturn();
    var versionJson = objectMapper.readTree(listResult.getResponse().getContentAsString()).get("items").get(0);
    assertThat(versionJson.get("version").asInt()).isEqualTo(1);
    assertThat(versionJson.get("rules")).hasSize(1);

    var versionId = UUID.fromString(versionJson.get("id").asText());
    var detailResult =
        mockMvc
            .perform(get("/api/v1/evaluator-versions/" + versionId).with(bearer(user)))
            .andExpect(status().isOk())
            .andReturn();
    var detailJson = objectMapper.readTree(detailResult.getResponse().getContentAsString());
    assertThat(detailJson.get("evaluatorId").asText()).isEqualTo(evaluatorId.toString());
    assertThat(detailJson.get("rules").get(0).get("fieldSource").asText()).isEqualTo("response.statusCode");
  }

  @Test
  void createLlmJudgeEvaluator_WithDisabledProvider_ReturnsBadRequest() throws Exception {
    var user = user("evaluator-llm-owner@vinfast.vn");
    var workspace = workspace(user);
    var projectId = createProject(user, workspace.getId());
    var providerId = provider(workspace.getId(), user.getId(), ProviderStatus.DISABLED).getId();

    mockMvc
        .perform(
            post("/api/v1/projects/" + projectId + "/evaluators")
                .with(bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(llmJudgeBody("Disabled judge", providerId))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createMixedEvaluator_WithIncompleteConfig_ReturnsBadRequest() throws Exception {
    var user = user("evaluator-mixed-owner@vinfast.vn");
    var workspace = workspace(user);
    var projectId = createProject(user, workspace.getId());

    var body =
        Map.of(
            "name",
            "Incomplete mixed evaluator",
            "type",
            "MIXED",
            "config",
            ruleBasedConfig(),
            "rules",
            List.of(rule("response.statusCode", "NUMERIC_EQUALS", Map.of("type", "number", "value", 200))));

    mockMvc
        .perform(
            post("/api/v1/projects/" + projectId + "/evaluators")
                .with(bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
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
                            Map.of("name", "Evaluator Project " + UUID.randomUUID()))))
            .andExpect(status().isOk())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  private UUID createRuleBasedEvaluator(
      vn.vinfast.vfqc.api.user.entity.AppUser user, UUID projectId, String name) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/v1/projects/" + projectId + "/evaluators")
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createBody(name))))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  private Map<String, Object> createBody(String name) {
    return Map.of(
        "name",
        name,
        "type",
        "RULE_BASED",
        "config",
        ruleBasedConfig(),
        "rules",
        List.of(rule("response.statusCode", "NUMERIC_EQUALS", Map.of("type", "number", "value", 200))));
  }

  private Map<String, Object> llmJudgeBody(String name, UUID providerId) {
    return Map.of(
        "name",
        name,
        "type",
        "LLM_JUDGE",
        "config",
        Map.of(
            "llmJudge",
            Map.of(
                "rubric",
                "Judge answer quality",
                "threshold",
                0.7,
                "passCondition",
                "SCORE_AT_LEAST_THRESHOLD",
                "inputTemplate",
                "Actual: {{extracted.answer}}",
                "outputSchema",
                "VFQC_JUDGE_V1",
                "defaultJudgeProviderId",
                providerId.toString(),
                "defaultJudgeModel",
                "judge-model")));
  }

  private Map<String, Object> validateBody(UUID targetId, String fieldSource) {
    return Map.of(
        "type",
        "RULE_BASED",
        "config",
        ruleBasedConfig(),
        "rules",
        List.of(rule(fieldSource, "CONTAINS", Map.of("type", "literal", "value", "ok"))),
        "context",
        Map.of("chatbotTargetId", targetId));
  }

  private Map<String, Object> rule(
      String fieldSource, String operator, Map<String, Object> expectedValue) {
    return Map.of(
        "fieldSource",
        fieldSource,
        "operator",
        operator,
        "expectedValue",
        expectedValue,
        "required",
        true,
        "orderIndex",
        1,
        "config",
        Map.of());
  }

  private Map<String, Object> ruleBasedConfig() {
    return Map.of(
        "ruleBased",
        Map.of("aggregation", "ALL_PASS", "passCondition", Map.of("type", "ALL_RULE_PASS")));
  }

  private UUID targetWithExtraction(UUID projectId, UUID createdBy) {
    var now = OffsetDateTime.now();
    return chatbotTargetRepository
        .save(
            ChatbotTarget.builder()
                .projectId(projectId)
                .name("Target " + UUID.randomUUID())
                .nameLookup("target " + UUID.randomUUID())
                .method("POST")
                .url("https://chatbot.example.test/api")
                .responseExtractionConfigJson(
                    Map.of(
                        "fields",
                        List.of(
                            Map.of(
                                "fieldName",
                                "answer",
                                "source",
                                "JSON_PATH",
                                "path",
                                "$.answer",
                                "required",
                                true))))
                .status(ChatbotTargetStatus.ACTIVE)
                .createdBy(createdBy)
                .createdAt(now)
                .updatedAt(now)
                .build())
        .getId();
  }

  private LlmProvider provider(UUID workspaceId, UUID createdBy, ProviderStatus status) {
    var now = OffsetDateTime.now();
    return providerRepository.save(
        LlmProvider.builder()
            .scope(ProviderScope.WORKSPACE)
            .workspaceId(workspaceId)
            .name("Judge Provider " + UUID.randomUUID())
            .nameLookup("judge provider " + UUID.randomUUID())
            .providerType(ProviderType.OPENAI_COMPATIBLE)
            .defaultModel("judge-model")
            .status(status)
            .createdBy(createdBy)
            .createdAt(now)
            .updatedAt(now)
            .build());
  }
}
