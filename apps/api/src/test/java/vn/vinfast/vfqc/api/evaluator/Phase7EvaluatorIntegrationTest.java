package vn.vinfast.vfqc.api.evaluator;

import static org.assertj.core.api.Assertions.assertThat;
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
}
