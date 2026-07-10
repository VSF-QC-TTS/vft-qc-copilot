package vn.vinfast.vfqc.api.testrun.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vn.vinfast.vfqc.api.dataset.entity.Dataset;
import vn.vinfast.vfqc.api.dataset.entity.DatasetSchema;
import vn.vinfast.vfqc.api.dataset.entity.DatasetVersion;
import vn.vinfast.vfqc.api.dataset.enums.DatasetSchemaScope;
import vn.vinfast.vfqc.api.dataset.enums.DatasetScope;
import vn.vinfast.vfqc.api.dataset.enums.DatasetSourceType;
import vn.vinfast.vfqc.api.dataset.enums.DatasetVersionStatus;
import vn.vinfast.vfqc.api.project.entity.Project;
import vn.vinfast.vfqc.api.runner.entity.RunnerEvent;
import vn.vinfast.vfqc.api.runner.enums.RunnerEventStatus;
import vn.vinfast.vfqc.api.runner.enums.RunnerEventType;
import vn.vinfast.vfqc.api.support.PostgresIntegrationTestBase;
import vn.vinfast.vfqc.api.testrun.entity.TestRun;
import vn.vinfast.vfqc.api.testrun.enums.TestRunStatus;
import vn.vinfast.vfqc.api.testrun.enums.TestRunType;
import vn.vinfast.vfqc.api.workspace.enums.WorkspaceRole;

@RequiredArgsConstructor
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class Phase10SseIntegrationTest extends PostgresIntegrationTestBase {

  private final MockMvc mockMvc;
  private final InMemoryTestRunSseEmitterRegistry registry;
  private final TestRunSseService sseService;

  @AfterEach
  void closeSseEmitters() {
    registry.completeAll();
  }

  @Test
  void subscribe_RequiresAuthenticatedUserAndProjectViewPermission() throws Exception {
    var owner = user("phase10-owner@vinfast.vn");
    var workspace = workspace(owner);
    var projectId = createProject(owner, workspace.getId());
    var testRunId = createRun(owner, projectId);
    var outsider = user("phase10-outsider@vinfast.vn");

    mockMvc.perform(get("/api/v1/test-runs/" + testRunId + "/events")).andExpect(status().isUnauthorized());

    mockMvc
        .perform(get("/api/v1/test-runs/" + testRunId + "/events").with(bearer(outsider)))
        .andExpect(status().isNotFound());
  }

  @Test
  void subscribe_AllRolesWithViewPermissionCanOpenStream() throws Exception {
    var owner = user("phase10-roles-owner@vinfast.vn");
    var workspace = workspace(owner);
    var projectId = createProject(owner, workspace.getId());
    var testRunId = createRun(owner, projectId);
    var admin = user("phase10-admin@vinfast.vn");
    var member = user("phase10-member@vinfast.vn");
    var viewer = user("phase10-viewer@vinfast.vn");
    workspaceMember(workspace, admin, WorkspaceRole.ADMIN, owner);
    workspaceMember(workspace, member, WorkspaceRole.MEMBER, owner);
    workspaceMember(workspace, viewer, WorkspaceRole.VIEWER, owner);

    assertCanSubscribe(owner, testRunId);
    assertCanSubscribe(admin, testRunId);
    assertCanSubscribe(member, testRunId);
    assertCanSubscribe(viewer, testRunId);
  }

  @Test
  void subscribe_ReplaysPersistedEventsAfterCursorAndKeepsPayloadUiSafe() throws Exception {
    var owner = user("phase10-replay@vinfast.vn");
    var workspace = workspace(owner);
    var projectId = createProject(owner, workspace.getId());
    var testRunId = createRun(owner, projectId);
    createEvent(testRunId, 1, RunnerEventType.RUN_STARTED, Map.of("status", "RUNNING"));
    createEvent(
        testRunId,
        2,
        RunnerEventType.TESTCASE_COMPLETED,
        Map.of(
            "datasetRowId",
            1001,
            "status",
            "FAILED",
            "request",
            Map.of("Authorization", "Bearer should-not-stream"),
            "response",
            Map.of("body", "sk-raw-response-secret"),
            "headers",
            Map.of("Cookie", "secret-cookie"),
            "machineSummary",
            "Bearer safe-summary-secret"));

    var result =
        mockMvc
            .perform(
                get("/api/v1/test-runs/" + testRunId + "/events")
                    .with(bearer(owner))
                    .header("Last-Event-ID", "1")
                    .accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(status().isOk())
            .andExpect(request().asyncStarted())
            .andReturn();

    var content = waitForContent(result, "TESTCASE_COMPLETED");
    assertThat(content).doesNotContain("id:1").contains("id:2").contains("event:TESTCASE_COMPLETED");
    assertThat(content)
        .contains("\"datasetRowId\":1001")
        .doesNotContain("should-not-stream")
        .doesNotContain("sk-raw-response-secret")
        .doesNotContain("secret-cookie")
        .doesNotContain("\"request\"")
        .doesNotContain("\"response\"")
        .doesNotContain("\"headers\"")
        .contains("Bearer ****");
  }

  @Test
  void subscribe_WithoutCursorReplaysCappedHistoryFromStart() throws Exception {
    var owner = user("phase10-initial@vinfast.vn");
    var workspace = workspace(owner);
    var projectId = createProject(owner, workspace.getId());
    var testRunId = createRun(owner, projectId);
    createEvent(testRunId, 1, RunnerEventType.RUN_STARTED, Map.of("status", "RUNNING"));
    createEvent(testRunId, 2, RunnerEventType.RUN_PROGRESS, Map.of("completed", 1, "total", 2));

    var result =
        mockMvc
            .perform(
                get("/api/v1/test-runs/" + testRunId + "/events")
                    .with(bearer(owner))
                    .accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(status().isOk())
            .andExpect(request().asyncStarted())
            .andReturn();

    var content = waitForContent(result, "RUN_PROGRESS");
    assertThat(content).contains("id:1").contains("id:2").contains("event:RUN_STARTED").contains("event:RUN_PROGRESS");
  }

  @Test
  void subscribe_MoreThanReplayPage_ReplaysAllPersistedEvents() throws Exception {
    var owner = user("phase10-large-replay@vinfast.vn");
    var workspace = workspace(owner);
    var projectId = createProject(owner, workspace.getId());
    var testRunId = createRun(owner, projectId);
    var now = OffsetDateTime.now();
    var events =
        IntStream.rangeClosed(1, 501)
            .mapToObj(
                sequenceNo ->
                    RunnerEvent.builder()
                        .eventId("phase10-large-" + testRunId + "-" + sequenceNo)
                        .testRunId(testRunId)
                        .eventType(RunnerEventType.RUN_PROGRESS)
                        .sequenceNo(sequenceNo)
                        .status(RunnerEventStatus.APPLIED)
                        .attempts(1)
                        .payloadJson(Map.of("completed", sequenceNo, "total", 501))
                        .occurredAt(now)
                        .createdAt(now)
                        .updatedAt(now)
                        .build())
            .toList();
    runnerEventRepository.saveAll(events);

    var result =
        mockMvc
            .perform(
                get("/api/v1/test-runs/" + testRunId + "/events")
                    .with(bearer(owner))
                    .accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(status().isOk())
            .andExpect(request().asyncStarted())
            .andReturn();

    var content = waitForContent(result, "\"completed\":501");
    assertThat(content).contains("id:501").contains("\"completed\":501");
  }

  @Test
  void subscribe_RejectsInvalidReplayCursor() throws Exception {
    var owner = user("phase10-invalid-cursor@vinfast.vn");
    var workspace = workspace(owner);
    var projectId = createProject(owner, workspace.getId());
    var testRunId = createRun(owner, projectId);

    mockMvc
        .perform(
            get("/api/v1/test-runs/" + testRunId + "/events")
                .with(bearer(owner))
                .queryParam("afterSequenceNo", "abc")
                .accept(MediaType.TEXT_EVENT_STREAM))
        .andExpect(status().isBadRequest());
  }

  @Test
  void heartbeat_DoesNotPersistRunnerEventOrAllocateSequence() {
    var testRunId = UUID.randomUUID();
    var failingEmitter = new FailingSseEmitter();
    registry.register(testRunId, failingEmitter);

    sseService.sendHeartbeat();

    assertThat(runnerEventRepository.count()).isZero();
    assertThat(runnerEventRepository.maxSequenceNo(testRunId)).isZero();
    assertThat(registry.activeEmitterCount(testRunId)).isZero();
  }

  private void assertCanSubscribe(vn.vinfast.vfqc.api.user.entity.AppUser user, UUID testRunId)
      throws Exception {
    mockMvc
        .perform(
            get("/api/v1/test-runs/" + testRunId + "/events")
                .with(bearer(user))
                .accept(MediaType.TEXT_EVENT_STREAM))
        .andExpect(status().isOk())
        .andExpect(request().asyncStarted());
  }

  private String waitForContent(MvcResult result, String marker) throws Exception {
    for (var i = 0; i < 20; i++) {
      var content = result.getResponse().getContentAsString();
      if (content.contains(marker)) {
        return content;
      }
      Thread.sleep(50);
    }
    return result.getResponse().getContentAsString();
  }

  private UUID createProject(vn.vinfast.vfqc.api.user.entity.AppUser owner, UUID workspaceId) {
    var now = OffsetDateTime.now();
    return projectRepository
        .save(
            Project.builder()
                .workspaceId(workspaceId)
                .name("Phase10 Project " + UUID.randomUUID())
                .nameLookup("phase10 project " + UUID.randomUUID())
                .createdBy(owner.getId())
                .createdAt(now)
                .updatedAt(now)
                .build())
        .getId();
  }

  private UUID createRun(vn.vinfast.vfqc.api.user.entity.AppUser owner, UUID projectId) {
    var now = OffsetDateTime.now();
    var schema =
        datasetSchemaRepository.save(
            DatasetSchema.builder()
                .scope(DatasetSchemaScope.PROJECT)
                .projectId(projectId)
                .version(1)
                .name("Phase10 Schema")
                .createdBy(owner.getId())
                .createdAt(now)
                .build());
    var dataset =
        datasetRepository.save(
            Dataset.builder()
                .scope(DatasetScope.PROJECT)
                .projectId(projectId)
                .ownerUserId(owner.getId())
                .name("Phase10 Dataset " + UUID.randomUUID())
                .nameLookup("phase10 dataset " + UUID.randomUUID())
                .sourceType(DatasetSourceType.MANUAL)
                .createdBy(owner.getId())
                .createdAt(now)
                .updatedAt(now)
                .build());
    var version =
        datasetVersionRepository.save(
            DatasetVersion.builder()
                .datasetId(dataset.getId())
                .version(1)
                .schemaId(schema.getId())
                .schemaVersion(1)
                .importMappingJson(Map.of())
                .rowCount(0)
                .status(DatasetVersionStatus.ACTIVE)
                .createdBy(owner.getId())
                .createdAt(now)
                .publishedAt(now)
                .build());
    dataset.setCurrentVersionId(version.getId());
    datasetRepository.save(dataset);
    return testRunRepository
        .save(
            TestRun.builder()
                .projectId(projectId)
                .runType(TestRunType.SINGLE_TARGET)
                .datasetId(dataset.getId())
                .datasetVersionId(version.getId())
                .datasetSchemaId(schema.getId())
                .datasetSchemaVersion(1)
                .status(TestRunStatus.RUNNING)
                .progressCompleted(0)
                .progressTotal(0)
                .summaryMetricsJson(Map.of())
                .domainConfigSnapshotJson(Map.of())
                .promptfooConfigSnapshotJson(Map.of())
                .domainConfigHash("domain")
                .promptfooConfigHash("promptfoo")
                .startedBy(owner.getId())
                .startedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build())
        .getId();
  }

  private void createEvent(
      UUID testRunId, long sequenceNo, RunnerEventType eventType, Map<String, Object> payload) {
    var now = OffsetDateTime.now();
    runnerEventRepository.save(
        RunnerEvent.builder()
            .eventId("phase10-" + testRunId + "-" + sequenceNo)
            .testRunId(testRunId)
            .eventType(eventType)
            .sequenceNo(sequenceNo)
            .status(RunnerEventStatus.APPLIED)
            .attempts(1)
            .payloadJson(payload)
            .occurredAt(now)
            .createdAt(now)
            .updatedAt(now)
            .build());
  }

  private static final class FailingSseEmitter extends SseEmitter {

    @Override
    public void send(SseEventBuilder builder) throws IOException {
      throw new IOException("simulated disconnect");
    }
  }
}
