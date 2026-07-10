package vn.vinfast.vfqc.api.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Range;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import vn.vinfast.vfqc.api.evaluator.enums.EvaluatorType;
import vn.vinfast.vfqc.api.runner.config.RunnerRedisStreams;
import vn.vinfast.vfqc.api.runner.enums.RunnerCommandStatus;
import vn.vinfast.vfqc.api.runner.enums.RunnerCommandType;
import vn.vinfast.vfqc.api.runner.enums.RunnerEventStatus;
import vn.vinfast.vfqc.api.runner.service.RunnerRedisEventConsumer;
import vn.vinfast.vfqc.api.secret.entity.Secret;
import vn.vinfast.vfqc.api.secret.enums.SecretScope;
import vn.vinfast.vfqc.api.secret.enums.SecretStatus;
import vn.vinfast.vfqc.api.secret.service.SecretEncryptionService;
import vn.vinfast.vfqc.api.support.PostgresIntegrationTestBase;
import vn.vinfast.vfqc.api.target.entity.ChatbotTarget;
import vn.vinfast.vfqc.api.target.enums.ChatbotTargetStatus;
import vn.vinfast.vfqc.api.testrun.enums.TestCaseResultStatus;
import vn.vinfast.vfqc.api.testrun.enums.TestRunStatus;

@RequiredArgsConstructor
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class Phase9RunnerIntegrationTest extends PostgresIntegrationTestBase {

  private final MockMvc mockMvc;
  private final ObjectMapper objectMapper;
  private final SecretEncryptionService encryptionService;
  private final RunnerRedisEventConsumer runnerRedisEventConsumer;

  @Test
  void createRun_PublishesCompactStartCommandAndInternalRunnerFlowIngestsEvents() throws Exception {
    var user = user("phase9-owner@vinfast.vn");
    var workspace = workspace(user);
    var projectId = createProject(user, workspace.getId());
    var secretId = createWorkspaceSecret(user.getId(), workspace.getId(), "runner-plaintext");
    var datasetId = createActiveDataset(user, projectId);
    var targetId = activeTarget(projectId, user.getId(), secretId, "answer");
    var evaluatorVersionId = createRuleBasedEvaluator(user, projectId, "Phase9 evaluator", "extracted.answer");

    var createResult =
        mockMvc
            .perform(
                post("/api/v1/projects/" + projectId + "/test-runs")
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(runBody(datasetId, targetId, evaluatorVersionId))))
            .andExpect(status().isCreated())
            .andReturn();

    var testRunId = UUID.fromString(objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText());
    var run = testRunRepository.findById(testRunId).orElseThrow();
    assertThat(run.getStatus()).isEqualTo(TestRunStatus.QUEUED);
    var startCommand = waitForPublishedCommand(testRunId, RunnerCommandType.START_RUN);
    assertThat(startCommand.getCommandType()).isEqualTo(RunnerCommandType.START_RUN);
    assertThat(startCommand.getStatus()).isEqualTo(RunnerCommandStatus.PUBLISHED);
    var commandPayload = redisTemplate.opsForStream().range(RunnerRedisStreams.COMMANDS, Range.unbounded()).getFirst().getValue();
    assertThat(commandPayload.keySet()).containsExactlyInAnyOrder("commandId", "testRunId", "commandType", "createdAt");
    assertThat(commandPayload.toString()).doesNotContain("runner-plaintext").doesNotContain("chatbot.example.test/api");

    var target = chatbotTargetRepository.findById(targetId).orElseThrow();
    target.setUrl("https://mutated.example.test/api");
    chatbotTargetRepository.saveAndFlush(target);

    mockMvc
        .perform(get("/internal/runner/test-runs/" + testRunId + "/config").with(bearer(user)))
        .andExpect(status().isForbidden());
    var config =
        mockMvc
            .perform(get("/internal/runner/test-runs/" + testRunId + "/config").header("Authorization", "Bearer dev-runner-token"))
            .andExpect(status().isOk())
            .andReturn();
    var configJson = objectMapper.readTree(config.getResponse().getContentAsString());
    assertThat(configJson.get("targets").get(0).get("config").get("url").asText())
        .isEqualTo("https://chatbot.example.test/api");
    assertThat(config.getResponse().getContentAsString()).contains(secretId.toString()).doesNotContain("runner-plaintext");

    var secrets =
        mockMvc
            .perform(
                post("/internal/runner/test-runs/" + testRunId + "/runtime-secrets")
                    .header("Authorization", "Bearer dev-runner-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("secretIds", List.of(secretId)))))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(secrets.getResponse().getContentAsString()).contains("runner-plaintext");
    mockMvc
        .perform(
            post("/internal/runner/test-runs/" + testRunId + "/runtime-secrets")
                .header("Authorization", "Bearer dev-runner-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("secretIds", List.of(UUID.randomUUID())))))
        .andExpect(status().isForbidden());

    ingest("evt-start", testRunId, "RUN_STARTED", Map.of());
    ingest("evt-start", testRunId, "RUN_STARTED", Map.of());
    assertThat(runnerEventRepository.findByEventId("evt-start").orElseThrow().getSequenceNo()).isEqualTo(1);
    assertThat(runnerEventRepository.count()).isEqualTo(1);

    var runTarget = testRunTargetRepository.findByTestRunIdOrderByOrderIndexAsc(testRunId).getFirst();
    var row = datasetRowRepository.findByDatasetVersionIdOrderByRowIndexAsc(run.getDatasetVersionId()).getFirst();
    ingest(
        "evt-case",
        testRunId,
        "TESTCASE_COMPLETED",
        Map.of(
            "testRunTargetId",
            runTarget.getId().toString(),
            "datasetRowId",
            row.getId(),
            "status",
            "FAILED",
            "passed",
            false,
            "request",
            Map.of("Authorization", "Bearer super-secret-token"),
            "response",
            Map.of("body", "sk-super-secret-response"),
            "machineSummary",
            "sk-super-secret-response"));
    var result = testCaseResultRepository.findByTestRunIdAndTestRunTargetIdAndDatasetRowId(testRunId, runTarget.getId(), row.getId()).orElseThrow();
    assertThat(result.getStatus()).isEqualTo(TestCaseResultStatus.FAILED);
    assertThat(objectMapper.writeValueAsString(result.getRequestJson())).doesNotContain("super-secret-token").contains("****");
    assertThat(result.getMachineSummary()).doesNotContain("sk-super-secret-response");

    ingest("evt-complete", testRunId, "RUN_COMPLETED", Map.of());
    assertThat(testRunRepository.findById(testRunId).orElseThrow().getStatus()).isEqualTo(TestRunStatus.COMPLETED);
    assertThat(runnerEventRepository.findByEventId("evt-complete").orElseThrow().getSequenceNo()).isEqualTo(3);
  }

  @Test
  void cancelRunningRun_PublishesCancelCommand() throws Exception {
    var user = user("phase9-cancel@vinfast.vn");
    var workspace = workspace(user);
    var projectId = createProject(user, workspace.getId());
    var secretId = createWorkspaceSecret(user.getId(), workspace.getId(), "cancel-plaintext");
    var datasetId = createActiveDataset(user, projectId);
    var targetId = activeTarget(projectId, user.getId(), secretId, "answer");
    var evaluatorVersionId = createRuleBasedEvaluator(user, projectId, "Cancel evaluator", "extracted.answer");
    var testRunId = createRun(user, projectId, datasetId, targetId, evaluatorVersionId);
    ingest("evt-cancel-start", testRunId, "RUN_STARTED", Map.of());

    mockMvc
        .perform(
            post("/api/v1/test-runs/" + testRunId + "/cancel")
                .with(bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", "stop"))))
        .andExpect(status().isOk());

    assertThat(testRunRepository.findById(testRunId).orElseThrow().getStatus())
        .isEqualTo(TestRunStatus.CANCEL_REQUESTED);
    assertThat(runnerCommandRepository.findByTestRunIdOrderByCreatedAtAsc(testRunId))
        .extracting(command -> command.getCommandType())
        .containsExactly(RunnerCommandType.START_RUN, RunnerCommandType.CANCEL_RUN);
  }

  @Test
  void runFailedAndDuplicateEventMismatch_AreHandledSafely() throws Exception {
    var user = user("phase9-failed@vinfast.vn");
    var workspace = workspace(user);
    var projectId = createProject(user, workspace.getId());
    var secretId = createWorkspaceSecret(user.getId(), workspace.getId(), "failed-plaintext");
    var datasetId = createActiveDataset(user, projectId);
    var targetId = activeTarget(projectId, user.getId(), secretId, "answer");
    var evaluatorVersionId = createRuleBasedEvaluator(user, projectId, "Failed evaluator", "extracted.answer");
    var testRunId = createRun(user, projectId, datasetId, targetId, evaluatorVersionId);

    ingest("evt-failed-start", testRunId, "RUN_STARTED", Map.of());
    ingest("evt-failed-terminal", testRunId, "RUN_FAILED", Map.of("error", "Bearer failed-secret-token"));

    var run = testRunRepository.findById(testRunId).orElseThrow();
    assertThat(run.getStatus()).isEqualTo(TestRunStatus.FAILED);
    assertThat(run.getFinishedAt()).isNotNull();
    var failedEvent = runnerEventRepository.findByEventId("evt-failed-terminal").orElseThrow();
    assertThat(objectMapper.writeValueAsString(failedEvent.getPayloadJson())).doesNotContain("failed-secret-token").contains("****");

    var otherRunId = createRun(user, projectId, datasetId, targetId, evaluatorVersionId);
    mockMvc
        .perform(
            post("/internal/runner/events")
                .header("Authorization", "Bearer dev-runner-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "eventId",
                            "evt-failed-terminal",
                            "testRunId",
                            otherRunId,
                            "eventType",
                            "RUN_STARTED",
                            "payload",
                            Map.of()))))
        .andExpect(status().isConflict());
  }

  @Test
  void redisEventConsumer_RetriesThenDlqsSanitizedPoisonMessage() {
    redisTemplate
        .opsForStream()
        .add(
            RunnerRedisStreams.EVENTS,
            Map.of(
                "eventId",
                "evt-poison",
                "testRunId",
                UUID.randomUUID().toString(),
                "eventType",
                "RUN_STARTED",
                "payload",
                "{\"Authorization\":\"Bearer redis-secret-token\""));

    assertThat(runnerRedisEventConsumer.consumeOnce()).isZero();
    assertThat(runnerRedisEventConsumer.consumeOnce()).isZero();
    assertThat(runnerRedisEventConsumer.consumeOnce()).isEqualTo(1);

    var dlqEntry = redisTemplate.opsForStream().range(RunnerRedisStreams.EVENTS_DLQ, Range.unbounded()).getFirst();
    assertThat(dlqEntry).isNotNull();
    assertThat(dlqEntry.getValue().toString()).doesNotContain("redis-secret-token").contains("****");
    assertThat(redisTemplate.opsForStream().range(RunnerRedisStreams.EVENTS, Range.unbounded())).isEmpty();
  }

  @Test
  void runnerToken_CannotCallFrontendApi() throws Exception {
    var user = user("phase9-boundary@vinfast.vn");
    var workspace = workspace(user);
    var projectId = createProject(user, workspace.getId());

    mockMvc
        .perform(get("/api/v1/projects/" + projectId).header("Authorization", "Bearer dev-runner-token"))
        .andExpect(status().isUnauthorized());
  }

  private void ingest(String eventId, UUID testRunId, String eventType, Map<String, Object> payload) throws Exception {
    var response =
        mockMvc
            .perform(
                post("/internal/runner/events")
                    .header("Authorization", "Bearer dev-runner-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "eventId", eventId,
                                "testRunId", testRunId,
                                "eventType", eventType,
                                "payload", payload))))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(objectMapper.readTree(response.getResponse().getContentAsString()).get("status").asText())
        .isEqualTo(RunnerEventStatus.APPLIED.name());
  }

  private vn.vinfast.vfqc.api.runner.entity.RunnerCommand waitForPublishedCommand(
      UUID testRunId, RunnerCommandType type) throws InterruptedException {
    for (var i = 0; i < 20; i++) {
      var command =
          runnerCommandRepository.findByTestRunIdOrderByCreatedAtAsc(testRunId).stream()
              .filter(candidate -> candidate.getCommandType() == type)
              .findFirst()
              .orElse(null);
      if (command != null && command.getStatus() == RunnerCommandStatus.PUBLISHED) {
        return command;
      }
      Thread.sleep(50);
    }
    return runnerCommandRepository.findByTestRunIdOrderByCreatedAtAsc(testRunId).stream()
        .filter(candidate -> candidate.getCommandType() == type)
        .findFirst()
        .orElseThrow();
  }

  private UUID createProject(vn.vinfast.vfqc.api.user.entity.AppUser user, UUID workspaceId) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/v1/workspaces/" + workspaceId + "/projects")
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("name", "Phase9 Project " + UUID.randomUUID()))))
            .andExpect(status().isOk())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  private UUID createActiveDataset(vn.vinfast.vfqc.api.user.entity.AppUser user, UUID projectId) throws Exception {
    var dataset =
        mockMvc
            .perform(
                post("/api/v1/projects/" + projectId + "/datasets")
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("name", "Phase9 Dataset " + UUID.randomUUID()))))
            .andExpect(status().isOk())
            .andReturn();
    var datasetId = UUID.fromString(objectMapper.readTree(dataset.getResponse().getContentAsString()).get("id").asText());
    var draftId = datasetVersionRepository.findByDatasetIdOrderByVersionDesc(datasetId).getFirst().getId();
    mockMvc
        .perform(
            post("/api/v1/dataset-versions/" + draftId + "/rows")
                .with(bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("cells", Map.of("question", "What is VF 8?")))))
        .andExpect(status().isOk());
    mockMvc.perform(post("/api/v1/dataset-versions/" + draftId + "/publish").with(bearer(user))).andExpect(status().isOk());
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

  private UUID activeTarget(UUID projectId, UUID createdBy, UUID secretId, String extractedField) {
    var now = OffsetDateTime.now();
    return chatbotTargetRepository
        .save(
            ChatbotTarget.builder()
                .projectId(projectId)
                .name("Phase9 Target " + UUID.randomUUID())
                .nameLookup("phase9 target " + UUID.randomUUID())
                .method("POST")
                .url("https://chatbot.example.test/api")
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

  private UUID createWorkspaceSecret(UUID userId, UUID workspaceId, String plaintext) {
    var now = OffsetDateTime.now();
    return secretRepository
        .save(
            Secret.builder()
                .scope(SecretScope.WORKSPACE)
                .workspaceId(workspaceId)
                .name("Phase9 Secret " + UUID.randomUUID())
                .nameLookup("phase9-secret-" + UUID.randomUUID())
                .encryptedValue(encryptionService.encrypt(plaintext))
                .maskedPreview("****")
                .status(SecretStatus.ACTIVE)
                .createdBy(userId)
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
}
