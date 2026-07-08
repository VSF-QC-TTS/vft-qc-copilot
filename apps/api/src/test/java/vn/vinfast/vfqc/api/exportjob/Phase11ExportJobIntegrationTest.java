package vn.vinfast.vfqc.api.exportjob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import vn.vinfast.vfqc.api.dataset.enums.DatasetVersionStatus;
import vn.vinfast.vfqc.api.exportjob.enums.ExportJobStatus;
import vn.vinfast.vfqc.api.exportjob.enums.ExportResultMode;
import vn.vinfast.vfqc.api.support.PostgresIntegrationTestBase;
import vn.vinfast.vfqc.api.testrun.entity.TestCaseResult;
import vn.vinfast.vfqc.api.testrun.entity.TestCaseResultReview;
import vn.vinfast.vfqc.api.testrun.entity.TestRun;
import vn.vinfast.vfqc.api.testrun.entity.TestRunTarget;
import vn.vinfast.vfqc.api.testrun.enums.ReviewStatus;
import vn.vinfast.vfqc.api.testrun.enums.TestCaseResultStatus;
import vn.vinfast.vfqc.api.testrun.enums.TestRunStatus;
import vn.vinfast.vfqc.api.testrun.enums.TestRunTargetType;
import vn.vinfast.vfqc.api.testrun.enums.TestRunType;

@RequiredArgsConstructor
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class Phase11ExportJobIntegrationTest extends PostgresIntegrationTestBase {

  private final MockMvc mockMvc;
  private final ObjectMapper objectMapper;

  @Test
  void datasetVersionExport_WritesXlsxAndSanitizesFormulaAndSecrets() throws Exception {
    var user = user("phase11-dataset-owner@vinfast.vn");
    var workspace = workspace(user);
    var projectId = createProject(user, workspace.getId(), "Phase 11 Dataset Export");
    var datasetId = createDataset(user, projectId, "Formula Dataset");
    var draft = datasetVersionRepository.findByDatasetIdOrderByVersionDesc(datasetId).getFirst();

    mockMvc
        .perform(
            post("/api/v1/dataset-versions/" + draft.getId() + "/rows")
                .with(bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "cells",
                            Map.of(
                                "question",
                                "=HYPERLINK(\"https://evil.test\")",
                                "expected",
                                "Bearer dataset-secret-token")))))
        .andExpect(status().isOk());
    mockMvc
        .perform(post("/api/v1/dataset-versions/" + draft.getId() + "/publish").with(bearer(user)))
        .andExpect(status().isOk());
    var activeVersion =
        datasetVersionRepository
            .findByDatasetIdAndStatus(datasetId, DatasetVersionStatus.ACTIVE)
            .orElseThrow();

    var jobId =
        createDatasetExport(user, activeVersion.getId());
    var job = awaitCompleted(jobId);

    assertThat(job.getFilePath()).isNotBlank();
    var getResponse =
        mockMvc
            .perform(get("/api/v1/export-jobs/" + jobId).with(bearer(user)))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(getResponse.getResponse().getContentAsString()).doesNotContain("filePath");

    var download =
        mockMvc
            .perform(get("/api/v1/export-jobs/" + jobId + "/download").with(bearer(user)))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(download.getResponse().getHeader("Content-Disposition"))
        .contains("dataset-version-" + jobId + ".xlsx");
    try (var workbook =
        new XSSFWorkbook(new ByteArrayInputStream(download.getResponse().getContentAsByteArray()))) {
      var sheet = workbook.getSheet("Dataset");
      assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Question");
      assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).startsWith("'=");
      assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).contains("****");
      assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).doesNotContain("dataset-secret-token");
    }
  }

  @Test
  void testRunResultExport_DefaultsToFinalModeAndDownloadRechecksPermission() throws Exception {
    var user = user("phase11-results-owner@vinfast.vn");
    var outsider = user("phase11-results-outsider@vinfast.vn");
    var workspace = workspace(user);
    var projectId = createProject(user, workspace.getId(), "Phase 11 Result Export");
    var datasetId = createDataset(user, projectId, "Result Dataset");
    var draft = datasetVersionRepository.findByDatasetIdOrderByVersionDesc(datasetId).getFirst();
    mockMvc
        .perform(
            post("/api/v1/dataset-versions/" + draft.getId() + "/rows")
                .with(bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("cells", Map.of("question", "Q1", "expected", "A1")))))
        .andExpect(status().isOk());
    mockMvc
        .perform(post("/api/v1/dataset-versions/" + draft.getId() + "/publish").with(bearer(user)))
        .andExpect(status().isOk());
    var version =
        datasetVersionRepository
            .findByDatasetIdAndStatus(datasetId, DatasetVersionStatus.ACTIVE)
            .orElseThrow();
    var row = datasetRowRepository.findByDatasetVersionIdOrderByRowIndexAsc(version.getId()).getFirst();
    var testRunId = seedCompletedRun(projectId, datasetId, version.getId(), version.getSchemaId(), user.getId());
    var target = seedRunTarget(testRunId);
    var result = seedResult(testRunId, target.getId(), row.getId());
    seedReview(user.getId(), result.getId());

    var jobId = createTestRunExport(user, testRunId, Map.of());
    awaitCompleted(jobId);

    mockMvc
        .perform(get("/api/v1/export-jobs/" + jobId + "/download").with(bearer(outsider)))
        .andExpect(status().isNotFound());
    var download =
        mockMvc
            .perform(get("/api/v1/export-jobs/" + jobId + "/download").with(bearer(user)))
            .andExpect(status().isOk())
            .andReturn();

    try (var workbook =
        new XSSFWorkbook(new ByteArrayInputStream(download.getResponse().getContentAsByteArray()))) {
      var sheet = workbook.getSheet("Results");
      assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("FAILED");
      assertThat(sheet.getRow(1).getCell(4).getStringCellValue()).isEqualTo("PASSED");
      assertThat(sheet.getRow(1).getCell(8).getStringCellValue()).doesNotContain("result-secret-token");
      assertThat(sheet.getRow(1).getCell(10).getStringCellValue()).doesNotContain("review-secret-token");
    }
  }

  @Test
  void exportDownload_ExpiredCompletedJob_ReturnsNotFound() throws Exception {
    var user = user("phase11-expired-owner@vinfast.vn");
    var workspace = workspace(user);
    var projectId = createProject(user, workspace.getId(), "Phase 11 Expired Export");
    var datasetId = createDataset(user, projectId, "Expired Export Dataset");
    var draft = datasetVersionRepository.findByDatasetIdOrderByVersionDesc(datasetId).getFirst();
    mockMvc
        .perform(post("/api/v1/dataset-versions/" + draft.getId() + "/publish").with(bearer(user)))
        .andExpect(status().isOk());
    var activeVersion =
        datasetVersionRepository
            .findByDatasetIdAndStatus(datasetId, DatasetVersionStatus.ACTIVE)
            .orElseThrow();
    var jobId = createDatasetExport(user, activeVersion.getId());
    var job = awaitCompleted(jobId);
    job.setExpiresAt(OffsetDateTime.now().minusMinutes(1));
    exportJobRepository.saveAndFlush(job);

    mockMvc
        .perform(get("/api/v1/export-jobs/" + jobId + "/download").with(bearer(user)))
        .andExpect(status().isNotFound());
  }

  @Test
  void exportDownload_MissingStoredFile_ReturnsNotFound() throws Exception {
    var user = user("phase11-missing-file-owner@vinfast.vn");
    var workspace = workspace(user);
    var projectId = createProject(user, workspace.getId(), "Phase 11 Missing File Export");
    var datasetId = createDataset(user, projectId, "Missing File Dataset");
    var draft = datasetVersionRepository.findByDatasetIdOrderByVersionDesc(datasetId).getFirst();
    mockMvc
        .perform(post("/api/v1/dataset-versions/" + draft.getId() + "/publish").with(bearer(user)))
        .andExpect(status().isOk());
    var activeVersion =
        datasetVersionRepository
            .findByDatasetIdAndStatus(datasetId, DatasetVersionStatus.ACTIVE)
            .orElseThrow();
    var jobId = createDatasetExport(user, activeVersion.getId());
    var job = awaitCompleted(jobId);
    job.setFilePath("missing/" + jobId + ".xlsx");
    exportJobRepository.saveAndFlush(job);

    mockMvc
        .perform(get("/api/v1/export-jobs/" + jobId + "/download").with(bearer(user)))
        .andExpect(status().isNotFound());
    var getResponse =
        mockMvc
            .perform(get("/api/v1/export-jobs/" + jobId).with(bearer(user)))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(getResponse.getResponse().getContentAsString()).doesNotContain("filePath");
    assertThat(exportJobRepository.findById(jobId).orElseThrow().getStatus())
        .isEqualTo(ExportJobStatus.COMPLETED);
  }

  private UUID createProject(vn.vinfast.vfqc.api.user.entity.AppUser user, UUID workspaceId, String name)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/v1/workspaces/" + workspaceId + "/projects")
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("name", name))))
            .andExpect(status().isOk())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  private UUID createDataset(vn.vinfast.vfqc.api.user.entity.AppUser user, UUID projectId, String name)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/v1/projects/" + projectId + "/datasets")
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("name", name))))
            .andExpect(status().isOk())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  private UUID createDatasetExport(vn.vinfast.vfqc.api.user.entity.AppUser user, UUID datasetVersionId)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/v1/dataset-versions/" + datasetVersionId + "/export-jobs")
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  private UUID createTestRunExport(
      vn.vinfast.vfqc.api.user.entity.AppUser user, UUID testRunId, Map<String, Object> body)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/v1/test-runs/" + testRunId + "/export-jobs")
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andReturn();
    var json = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(json.get("resultMode").asText()).isEqualTo(ExportResultMode.FINAL.name());
    return UUID.fromString(json.get("id").asText());
  }

  private vn.vinfast.vfqc.api.exportjob.entity.ExportJob awaitCompleted(UUID jobId)
      throws InterruptedException {
    for (int attempt = 0; attempt < 50; attempt++) {
      var job = exportJobRepository.findById(jobId).orElseThrow();
      if (job.getStatus() == ExportJobStatus.COMPLETED) {
        return job;
      }
      if (job.getStatus() == ExportJobStatus.FAILED) {
        throw new AssertionError("Export job failed: " + job.getErrorMessage());
      }
      Thread.sleep(100);
    }
    throw new AssertionError("Export job did not complete");
  }

  private UUID seedCompletedRun(
      UUID projectId, UUID datasetId, UUID datasetVersionId, UUID schemaId, UUID userId) {
    var now = OffsetDateTime.now();
    return testRunRepository
        .save(
            TestRun.builder()
                .projectId(projectId)
                .runType(TestRunType.SINGLE_TARGET)
                .datasetId(datasetId)
                .datasetVersionId(datasetVersionId)
                .datasetSchemaId(schemaId)
                .datasetSchemaVersion(1)
                .status(TestRunStatus.COMPLETED)
                .progressCompleted(1)
                .progressTotal(1)
                .domainConfigSnapshotJson(Map.of())
                .promptfooConfigSnapshotJson(Map.of())
                .domainConfigHash("a".repeat(64))
                .promptfooConfigHash("b".repeat(64))
                .startedBy(userId)
                .startedAt(now)
                .finishedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build())
        .getId();
  }

  private TestRunTarget seedRunTarget(UUID testRunId) {
    return testRunTargetRepository.save(
        TestRunTarget.builder()
            .testRunId(testRunId)
            .targetType(TestRunTargetType.LLM_BASELINE)
            .model("test-model")
            .name("Baseline")
            .orderIndex(0)
            .configJson(Map.of())
            .createdAt(OffsetDateTime.now())
            .build());
  }

  private TestCaseResult seedResult(UUID testRunId, UUID targetId, Long datasetRowId) {
    var now = OffsetDateTime.now();
    return testCaseResultRepository.save(
        TestCaseResult.builder()
            .testRunId(testRunId)
            .testRunTargetId(targetId)
            .datasetRowId(datasetRowId)
            .status(TestCaseResultStatus.FAILED)
            .score(BigDecimal.valueOf(0.1))
            .passed(false)
            .errorCategory("ASSERTION")
            .machineSummary("Bearer result-secret-token")
            .extractedOutputJson(Map.of("answer", "Bearer extracted-secret-token"))
            .latencyMs(123)
            .createdAt(now)
            .updatedAt(now)
            .build());
  }

  private void seedReview(UUID userId, Long resultId) {
    var now = OffsetDateTime.now();
    testCaseResultReviewRepository.save(
        TestCaseResultReview.builder()
            .testCaseResultId(resultId)
            .reviewerId(userId)
            .reviewStatus(ReviewStatus.OVERRIDDEN)
            .overrideStatus(TestCaseResultStatus.PASSED)
            .overrideScore(BigDecimal.valueOf(0.9))
            .note("Bearer review-secret-token")
            .createdAt(now)
            .updatedAt(now)
            .build());
  }
}
