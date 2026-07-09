package vn.vinfast.vfqc.api.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import vn.vinfast.vfqc.api.evaluator.enums.EvaluatorType;
import vn.vinfast.vfqc.api.secret.entity.Secret;
import vn.vinfast.vfqc.api.secret.enums.SecretScope;
import vn.vinfast.vfqc.api.secret.enums.SecretStatus;
import vn.vinfast.vfqc.api.secret.service.SecretEncryptionService;
import vn.vinfast.vfqc.api.target.entity.ChatbotTarget;
import vn.vinfast.vfqc.api.target.enums.ChatbotTargetStatus;

public class RunnerSmokeSeedFixture {

  public static final String FAKE_RUNTIME_SECRET = "vfqc_secret_1234567890abcdef";

  private final PostgresIntegrationTestBase base;
  private final MockMvc mockMvc;
  private final ObjectMapper objectMapper;
  private final SecretEncryptionService encryptionService;

  public RunnerSmokeSeedFixture(
      PostgresIntegrationTestBase base,
      MockMvc mockMvc,
      ObjectMapper objectMapper,
      SecretEncryptionService encryptionService) {
    this.base = base;
    this.mockMvc = mockMvc;
    this.objectMapper = objectMapper;
    this.encryptionService = encryptionService;
  }

  public SeededRun seedMinimalRun() throws Exception {
    var user = base.user("runner-smoke-owner@vinfast.vn");
    var workspace = base.workspace(user);
    var projectId = createProject(user, workspace.getId());
    var secretId = createWorkspaceSecret(user.getId(), workspace.getId());
    var datasetId = createActiveDataset(user, projectId);
    var datasetVersionId = base.datasetRepository.findById(datasetId).orElseThrow().getCurrentVersionId();
    var targetId = activeLocalTarget(projectId, user.getId(), secretId);
    var evaluatorVersionId = createRuleBasedEvaluator(user, projectId);
    var testRunId = createRun(user, projectId, datasetId, targetId, evaluatorVersionId);
    return new SeededRun(testRunId, projectId, datasetId, datasetVersionId, targetId, evaluatorVersionId, secretId);
  }

  public record SeededRun(
      UUID testRunId,
      UUID projectId,
      UUID datasetId,
      UUID datasetVersionId,
      UUID targetId,
      UUID evaluatorVersionId,
      UUID secretId) {}

  private UUID createProject(vn.vinfast.vfqc.api.user.entity.AppUser user, UUID workspaceId) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/v1/workspaces/" + workspaceId + "/projects")
                    .with(base.bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("name", "Runner Smoke Project " + UUID.randomUUID()))))
            .andExpect(status().isOk())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  private UUID createActiveDataset(vn.vinfast.vfqc.api.user.entity.AppUser user, UUID projectId) throws Exception {
    var dataset =
        mockMvc
            .perform(
                post("/api/v1/projects/" + projectId + "/datasets")
                    .with(base.bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("name", "Runner Smoke Dataset " + UUID.randomUUID()))))
            .andExpect(status().isOk())
            .andReturn();
    var datasetId = UUID.fromString(objectMapper.readTree(dataset.getResponse().getContentAsString()).get("id").asText());
    var draftId = base.datasetVersionRepository.findByDatasetIdOrderByVersionDesc(datasetId).getFirst().getId();
    mockMvc
        .perform(
            post("/api/v1/dataset-versions/" + draftId + "/rows")
                .with(base.bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("cells", Map.of("question", "Runner smoke question", "expected", "ok")))))
        .andExpect(status().isOk());
    mockMvc
        .perform(post("/api/v1/dataset-versions/" + draftId + "/publish").with(base.bearer(user)))
        .andExpect(status().isOk());
    return datasetId;
  }

  private UUID createRuleBasedEvaluator(vn.vinfast.vfqc.api.user.entity.AppUser user, UUID projectId) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/v1/projects/" + projectId + "/evaluators")
                    .with(base.bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "name",
                                "Runner Smoke Evaluator " + UUID.randomUUID(),
                                "type",
                                EvaluatorType.RULE_BASED.name(),
                                "config",
                                Map.of(
                                    "ruleBased",
                                    Map.of(
                                        "aggregation",
                                        "ALL_PASS",
                                        "passCondition",
                                        Map.of("type", "ALL_RULE_PASS"))),
                                "rules",
                                List.of(
                                    Map.of(
                                        "fieldSource",
                                        "extracted.answer",
                                        "operator",
                                        "CONTAINS",
                                        "expectedValue",
                                        Map.of("type", "literal", "value", "ok"),
                                        "required",
                                        true,
                                        "orderIndex",
                                        1,
                                        "config",
                                        Map.of()))))))
            .andExpect(status().isCreated())
            .andReturn();
    var evaluatorId = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    return base.evaluatorVersionRepository.findByEvaluatorIdOrderByVersionDesc(evaluatorId).getFirst().getId();
  }

  private UUID activeLocalTarget(UUID projectId, UUID createdBy, UUID secretId) {
    var now = OffsetDateTime.now();
    return base.chatbotTargetRepository
        .save(
            ChatbotTarget.builder()
                .projectId(projectId)
                .name("Runner Smoke Target " + UUID.randomUUID())
                .nameLookup("runner smoke target " + UUID.randomUUID())
                .method("POST")
                .url("http://127.0.0.1:9/vfqc-smoke")
                .headersJson(Map.of("Authorization", "Bearer ****"))
                .authConfigJson(
                    Map.of(
                        "secretBindings",
                        List.of(
                            Map.of(
                                "location",
                                "HEADER",
                                "keyOrPath",
                                "Authorization",
                                "secretId",
                                secretId.toString()))))
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

  private UUID createWorkspaceSecret(UUID userId, UUID workspaceId) {
    var now = OffsetDateTime.now();
    return base.secretRepository
        .save(
            Secret.builder()
                .scope(SecretScope.WORKSPACE)
                .workspaceId(workspaceId)
                .name("Runner Smoke Secret " + UUID.randomUUID())
                .nameLookup("runner-smoke-secret-" + UUID.randomUUID())
                .encryptedValue(encryptionService.encrypt(FAKE_RUNTIME_SECRET))
                .maskedPreview("****")
                .status(SecretStatus.ACTIVE)
                .createdBy(userId)
                .createdAt(now)
                .updatedAt(now)
                .build())
        .getId();
  }

  private UUID createRun(
      vn.vinfast.vfqc.api.user.entity.AppUser user,
      UUID projectId,
      UUID datasetId,
      UUID targetId,
      UUID evaluatorVersionId)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/v1/projects/" + projectId + "/test-runs")
                    .with(base.bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(runBody(datasetId, targetId, evaluatorVersionId))))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  private Map<String, Object> runBody(UUID datasetId, UUID targetId, UUID evaluatorVersionId) {
    return Map.of(
        "datasetId",
        datasetId,
        "targets",
        List.of(Map.of("targetType", "CHATBOT_TARGET", "chatbotTargetId", targetId)),
        "evaluators",
        List.of(Map.of("evaluatorVersionId", evaluatorVersionId)));
  }
}
