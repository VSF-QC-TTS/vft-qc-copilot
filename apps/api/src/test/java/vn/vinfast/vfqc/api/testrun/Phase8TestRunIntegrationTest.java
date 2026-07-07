package vn.vinfast.vfqc.api.testrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
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
import vn.vinfast.vfqc.api.testrun.entity.TestCaseResult;
import vn.vinfast.vfqc.api.testrun.enums.TestCaseResultStatus;
import vn.vinfast.vfqc.api.testrun.enums.TestRunStatus;
import vn.vinfast.vfqc.api.workspace.enums.WorkspaceRole;

@RequiredArgsConstructor
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class Phase8TestRunIntegrationTest extends PostgresIntegrationTestBase {

  private final MockMvc mockMvc;
  private final ObjectMapper objectMapper;

  @Test
  void preflight_WithMissingActiveDataset_ReturnsBlockerWithoutCreatingRun() throws Exception {
    var user = user("testrun-preflight-owner@vinfast.vn");
    var workspace = workspace(user);
    var projectId = createProject(user, workspace.getId());
    var datasetId = createDataset(user, projectId);
    var targetId = activeTarget(projectId, user.getId(), "answer");
    var evaluatorVersionId = createRuleBasedEvaluator(user, projectId, "Preflight evaluator", "extracted.answer");

    var result =
        mockMvc
            .perform(
                post("/api/v1/projects/" + projectId + "/test-runs/preflight")
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(runBody(datasetId, targetId, evaluatorVersionId))))
            .andExpect(status().isOk())
            .andReturn();

    var json = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(json.get("runnable").asBoolean()).isFalse();
    assertThat(json.get("blockers").get(0).get("code").asText()).isEqualTo("DATASET_ACTIVE_VERSION_MISSING");
    assertThat(testRunRepository.count()).isZero();
  }

  @Test
  void createTestRun_LocksSanitizedSnapshotsAndExactVersions() throws Exception {
    var user = user("testrun-create-owner@vinfast.vn");
    var workspace = workspace(user);
    var projectId = createProject(user, workspace.getId());
    var datasetId = createActiveDataset(user, projectId);
    var targetId = activeTarget(projectId, user.getId(), "answer");
    var evaluatorVersionId = createRuleBasedEvaluator(user, projectId, "Snapshot evaluator", "extracted.answer");

    var result =
        mockMvc
            .perform(
                post("/api/v1/projects/" + projectId + "/test-runs")
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(runBody(datasetId, targetId, evaluatorVersionId))))
            .andExpect(status().isCreated())
            .andReturn();

    var json = objectMapper.readTree(result.getResponse().getContentAsString());
    var testRunId = UUID.fromString(json.get("id").asText());
    var run = testRunRepository.findById(testRunId).orElseThrow();
    assertThat(run.getStatus()).isEqualTo(TestRunStatus.CREATED);
    assertThat(run.getDatasetId()).isEqualTo(datasetId);
    assertThat(run.getDomainConfigHash()).hasSize(64);
    assertThat(run.getPromptfooConfigHash()).hasSize(64);
    assertThat(testRunTargetRepository.findByTestRunIdOrderByOrderIndexAsc(testRunId)).hasSize(1);
    assertThat(testRunEvaluatorVersionRepository.findByTestRunIdOrderByOrderIndexAsc(testRunId))
        .singleElement()
        .satisfies(version -> assertThat(version.getEvaluatorVersionId()).isEqualTo(evaluatorVersionId));
    assertThat(objectMapper.writeValueAsString(run.getDomainConfigSnapshotJson()))
        .doesNotContain("super-secret-token")
        .contains("****");
  }

  @Test
  void createTestRun_WorkspaceViewer_ReturnsForbidden() throws Exception {
    var owner = user("testrun-viewer-owner@vinfast.vn");
    var viewer = user("testrun-viewer@vinfast.vn");
    var workspace = workspace(owner);
    workspaceMember(workspace, viewer, WorkspaceRole.VIEWER, owner);
    var projectId = createProject(owner, workspace.getId());
    var datasetId = createActiveDataset(owner, projectId);
    var targetId = activeTarget(projectId, owner.getId(), "answer");
    var evaluatorVersionId = createRuleBasedEvaluator(owner, projectId, "Viewer evaluator", "extracted.answer");

    mockMvc
        .perform(
            post("/api/v1/projects/" + projectId + "/test-runs")
                .with(bearer(viewer))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(runBody(datasetId, targetId, evaluatorVersionId))))
        .andExpect(status().isForbidden());
  }

  @Test
  void cancelCreatedTestRun_ReturnsInvalidState() throws Exception {
    var user = user("testrun-cancel-owner@vinfast.vn");
    var workspace = workspace(user);
    var projectId = createProject(user, workspace.getId());
    var datasetId = createActiveDataset(user, projectId);
    var targetId = activeTarget(projectId, user.getId(), "answer");
    var evaluatorVersionId = createRuleBasedEvaluator(user, projectId, "Cancel evaluator", "extracted.answer");
    var testRunId = createRun(user, projectId, datasetId, targetId, evaluatorVersionId);

    mockMvc
        .perform(
            post("/api/v1/test-runs/" + testRunId + "/cancel")
                .with(bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", "No longer needed"))))
        .andExpect(status().isConflict());
  }

  @Test
  void reviewOverride_ChangesFinalResultButNotMachineResult_AndMatrixUsesFinal() throws Exception {
    var user = user("testrun-review-owner@vinfast.vn");
    var workspace = workspace(user);
    var projectId = createProject(user, workspace.getId());
    var datasetId = createActiveDataset(user, projectId);
    var targetId = activeTarget(projectId, user.getId(), "answer");
    var evaluatorVersionId = createRuleBasedEvaluator(user, projectId, "Review evaluator", "extracted.answer");
    var testRunId = createRun(user, projectId, datasetId, targetId, evaluatorVersionId);
    var runTarget = testRunTargetRepository.findByTestRunIdOrderByOrderIndexAsc(testRunId).getFirst();
    var row = datasetRowRepository.findByDatasetVersionIdOrderByRowIndexAsc(
            testRunRepository.findById(testRunId).orElseThrow().getDatasetVersionId())
        .getFirst();
    var resultId = seedResult(testRunId, runTarget.getId(), row.getId());

    mockMvc
        .perform(
            put("/api/v1/test-case-results/" + resultId + "/review")
                .with(bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "reviewStatus",
                            "OVERRIDDEN",
                            "overrideStatus",
                            "PASSED",
                            "overrideScore",
                            0.95,
                            "note",
                            "Token sk-super-secret should be masked"))))
        .andExpect(status().isOk());

    var detail =
        mockMvc
            .perform(get("/api/v1/test-case-results/" + resultId).with(bearer(user)))
            .andExpect(status().isOk())
            .andReturn();
    var detailJson = objectMapper.readTree(detail.getResponse().getContentAsString());
    assertThat(detailJson.get("machineStatus").asText()).isEqualTo("FAILED");
    assertThat(detailJson.get("finalStatus").asText()).isEqualTo("PASSED");
    assertThat(detailJson.get("review").get("note").asText()).doesNotContain("sk-super-secret");
    assertThat(testCaseResultRepository.findById(resultId).orElseThrow().getStatus()).isEqualTo(TestCaseResultStatus.FAILED);

    var matrix =
        mockMvc
            .perform(get("/api/v1/test-runs/" + testRunId + "/comparison-matrix").with(bearer(user)))
            .andExpect(status().isOk())
            .andReturn();
    var matrixJson = objectMapper.readTree(matrix.getResponse().getContentAsString());
    assertThat(matrixJson.get("rows").get(0).get("cells").get(0).get("finalStatus").asText()).isEqualTo("PASSED");
    assertThat(matrix.getResponse().getContentAsString()).doesNotContain("raw secret response");
  }

  private UUID createProject(vn.vinfast.vfqc.api.user.entity.AppUser user, UUID workspaceId)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/v1/workspaces/" + workspaceId + "/projects")
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("name", "TestRun Project " + UUID.randomUUID()))))
            .andExpect(status().isOk())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  private UUID createDataset(vn.vinfast.vfqc.api.user.entity.AppUser user, UUID projectId) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/v1/projects/" + projectId + "/datasets")
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("name", "Run Dataset " + UUID.randomUUID()))))
            .andExpect(status().isOk())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  private UUID createActiveDataset(vn.vinfast.vfqc.api.user.entity.AppUser user, UUID projectId) throws Exception {
    var datasetId = createDataset(user, projectId);
    var draftId = datasetVersionRepository.findByDatasetIdOrderByVersionDesc(datasetId).getFirst().getId();
    mockMvc
        .perform(
            post("/api/v1/dataset-versions/" + draftId + "/rows")
                .with(bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("cells", Map.of("question", "What is VF 8?", "expected", "An electric SUV")))))
        .andExpect(status().isOk());
    mockMvc
        .perform(post("/api/v1/dataset-versions/" + draftId + "/publish").with(bearer(user)))
        .andExpect(status().isOk());
    return datasetId;
  }

  private UUID createRuleBasedEvaluator(
      vn.vinfast.vfqc.api.user.entity.AppUser user, UUID projectId, String name, String fieldSource)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/v1/projects/" + projectId + "/evaluators")
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "name",
                                name,
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
                                        fieldSource,
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
    return evaluatorVersionRepository.findByEvaluatorIdOrderByVersionDesc(evaluatorId).getFirst().getId();
  }

  private UUID activeTarget(UUID projectId, UUID createdBy, String extractedField) {
    var now = OffsetDateTime.now();
    return chatbotTargetRepository
        .save(
            ChatbotTarget.builder()
                .projectId(projectId)
                .name("Run Target " + UUID.randomUUID())
                .nameLookup("run target " + UUID.randomUUID())
                .method("POST")
                .url("https://chatbot.example.test/api")
                .headersJson(Map.of("Authorization", "Bearer super-secret-token"))
                .responseExtractionConfigJson(
                    Map.of(
                        "fields",
                        List.of(
                            Map.of(
                                "fieldName",
                                extractedField,
                                "source",
                                "JSON_PATH",
                                "path",
                                "$." + extractedField,
                                "required",
                                true))))
                .status(ChatbotTargetStatus.ACTIVE)
                .createdBy(createdBy)
                .createdAt(now)
                .updatedAt(now)
                .build())
        .getId();
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
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(runBody(datasetId, targetId, evaluatorVersionId))))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  private Long seedResult(UUID testRunId, UUID testRunTargetId, Long datasetRowId) {
    var now = OffsetDateTime.now();
    return testCaseResultRepository
        .save(
            TestCaseResult.builder()
                .testRunId(testRunId)
                .testRunTargetId(testRunTargetId)
                .datasetRowId(datasetRowId)
                .status(TestCaseResultStatus.FAILED)
                .score(new BigDecimal("0.2000"))
                .passed(false)
                .errorCategory("ASSERTION")
                .requestJson(Map.of("prompt", "question"))
                .responseJson(Map.of("body", "raw secret response"))
                .extractedOutputJson(Map.of("answer", "wrong"))
                .gradingResultJson(Map.of("reason", "mismatch"))
                .machineSummary("Machine failed")
                .createdAt(now)
                .updatedAt(now)
                .build())
        .getId();
  }
}
