package vn.vinfast.vfqc.api.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import vn.vinfast.vfqc.api.dataset.enums.DatasetVersionStatus;
import vn.vinfast.vfqc.api.support.PostgresIntegrationTestBase;

@RequiredArgsConstructor
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class Phase4DatasetIntegrationTest extends PostgresIntegrationTestBase {

  private final MockMvc mockMvc;
  private final ObjectMapper objectMapper;

  @Test
  void createProject_CreatesDefaultDatasetSchemaTransactionally() throws Exception {
    var user = user("project-schema-owner@vinfast.vn");
    var workspace = workspace(user);

    var projectId = createProject(user, workspace.getId(), "Dataset Bootstrap Project");

    var schema = datasetSchemaRepository.findTopByProjectIdOrderByVersionDesc(projectId);
    assertThat(schema).isPresent();
    assertThat(datasetSchemaColumnRepository.findBySchemaIdOrderByOrderIndexAsc(schema.orElseThrow().getId()))
        .extracting("name")
        .containsExactly("question", "expected");
  }

  @Test
  void addAndPatchDraftRows_MaintainsDeterministicRowHash() throws Exception {
    var user = user("dataset-rowhash-owner@vinfast.vn");
    var workspace = workspace(user);
    var projectId = createProject(user, workspace.getId(), "Dataset RowHash Project");
    var datasetId = createDataset(user, projectId, "RowHash Dataset");
    var draftId = draftVersionId(datasetId);
    var rowId = addRow(user, draftId, Map.of("question", "What is VF 8?", "expected", "Answer"));

    var stored = datasetRowRepository.findById(rowId).orElseThrow();
    assertThat(stored.getRowHash()).isNotBlank();

    patchRow(user, rowId, Map.of("question", "What is VF 9?", "expected", "Answer"), 0);

    var patched = datasetRowRepository.findById(rowId).orElseThrow();
    assertThat(patched.getRowHash()).isNotBlank().isNotEqualTo(stored.getRowHash());
  }

  @Test
  void patchRow_WithStaleRevision_ReturnsConflictAndPreservesCells() throws Exception {
    var user = user("dataset-conflict-owner@vinfast.vn");
    var workspace = workspace(user);
    var projectId = createProject(user, workspace.getId(), "Dataset Conflict Project");
    var datasetId = createDataset(user, projectId, "Conflict Dataset");
    var draftId = draftVersionId(datasetId);
    var rowId = addRow(user, draftId, Map.of("question", "Q1", "expected", "A1"));

    patchRow(user, rowId, Map.of("question", "Q2", "expected", "A2"), 0);

    mockMvc
        .perform(
            patch("/api/v1/dataset-rows/" + rowId)
                .with(bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "cells",
                            Map.of("question", "Q3", "expected", "A3"),
                            "expectedRevision",
                            0))))
        .andExpect(status().isConflict());

    var stored = datasetRowRepository.findById(rowId).orElseThrow();
    assertThat(stored.getRevision()).isEqualTo(1);
    assertThat(stored.getCellsJson()).containsEntry("question", "Q2");
  }

  @Test
  void publishDraft_ArchivesOldActiveAndRejectsActiveMutation() throws Exception {
    var user = user("dataset-publish-owner@vinfast.vn");
    var workspace = workspace(user);
    var projectId = createProject(user, workspace.getId(), "Dataset Publish Project");
    var datasetId = createDataset(user, projectId, "Publish Dataset");
    var firstDraftId = draftVersionId(datasetId);
    addRow(user, firstDraftId, Map.of("question", "Q1", "expected", "A1"));

    publish(user, firstDraftId);

    var active = datasetVersionRepository.findById(firstDraftId).orElseThrow();
    assertThat(active.getStatus()).isEqualTo(DatasetVersionStatus.ACTIVE);
    mockMvc
        .perform(
            post("/api/v1/dataset-versions/" + firstDraftId + "/rows")
                .with(bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("cells", Map.of("question", "Q2")))))
        .andExpect(status().isConflict());

    mockMvc.perform(post("/api/v1/datasets/" + datasetId + "/drafts").with(bearer(user)))
        .andExpect(status().isOk());
    var secondDraftId = draftVersionId(datasetId);
    addRow(user, secondDraftId, Map.of("question", "Q3", "expected", "A3"));
    publish(user, secondDraftId);

    assertThat(datasetVersionRepository.findById(firstDraftId).orElseThrow().getStatus())
        .isEqualTo(DatasetVersionStatus.ARCHIVED);
    assertThat(datasetRepository.findById(datasetId).orElseThrow().getCurrentVersionId())
        .isEqualTo(secondDraftId);
  }

  @Test
  void createDraft_WhenDraftAlreadyExists_ReturnsConflict() throws Exception {
    var user = user("dataset-duplicate-draft-owner@vinfast.vn");
    var workspace = workspace(user);
    var projectId = createProject(user, workspace.getId(), "Dataset Duplicate Draft Project");
    var datasetId = createDataset(user, projectId, "Duplicate Draft Dataset");

    mockMvc.perform(post("/api/v1/datasets/" + datasetId + "/drafts").with(bearer(user)))
        .andExpect(status().isConflict());

    assertThat(datasetVersionRepository.findByDatasetIdOrderByVersionDesc(datasetId))
        .hasSize(1)
        .singleElement()
        .satisfies(version -> assertThat(version.getStatus()).isEqualTo(DatasetVersionStatus.DRAFT));
  }

  @Test
  void discardDraft_MarksDraftDiscardedRejectsMutationAndAllowsNewDraft() throws Exception {
    var user = user("dataset-discard-owner@vinfast.vn");
    var workspace = workspace(user);
    var projectId = createProject(user, workspace.getId(), "Dataset Discard Project");
    var datasetId = createDataset(user, projectId, "Discard Dataset");
    var draftId = draftVersionId(datasetId);
    addRow(user, draftId, Map.of("question", "Q1", "expected", "A1"));

    discard(user, draftId);

    var discarded = datasetVersionRepository.findById(draftId).orElseThrow();
    assertThat(discarded.getStatus()).isEqualTo(DatasetVersionStatus.DISCARDED);
    assertThat(discarded.getDiscardedAt()).isNotNull();
    assertThat(datasetRepository.findById(datasetId).orElseThrow().getCurrentVersionId()).isNull();
    mockMvc
        .perform(
            post("/api/v1/dataset-versions/" + draftId + "/rows")
                .with(bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("cells", Map.of("question", "Q2")))))
        .andExpect(status().isConflict());

    mockMvc.perform(post("/api/v1/datasets/" + datasetId + "/drafts").with(bearer(user)))
        .andExpect(status().isOk());

    var replacementDraft = draftVersion(datasetId);
    assertThat(replacementDraft.getVersion()).isEqualTo(2);
    assertThat(replacementDraft.getStatus()).isEqualTo(DatasetVersionStatus.DRAFT);
    assertThat(replacementDraft.getRowCount()).isZero();
  }

  @Test
  void createDraft_FromActiveVersionCopiesRowsAndPublishRecountsRows() throws Exception {
    var user = user("dataset-rowcount-owner@vinfast.vn");
    var workspace = workspace(user);
    var projectId = createProject(user, workspace.getId(), "Dataset RowCount Project");
    var datasetId = createDataset(user, projectId, "RowCount Dataset");
    var firstDraftId = draftVersionId(datasetId);
    addRow(user, firstDraftId, Map.of("question", "Q1", "expected", "A1"));
    addRow(user, firstDraftId, Map.of("question", "Q2", "expected", "A2"));

    publish(user, firstDraftId);

    var firstActive = datasetVersionRepository.findById(firstDraftId).orElseThrow();
    assertThat(firstActive.getRowCount()).isEqualTo(2);
    var activeRows = datasetRowRepository.findByDatasetVersionIdOrderByRowIndexAsc(firstDraftId);
    assertThat(activeRows).hasSize(2).extracting("rowIndex").containsExactly(0, 1);

    mockMvc.perform(post("/api/v1/datasets/" + datasetId + "/drafts").with(bearer(user)))
        .andExpect(status().isOk());

    var copiedDraft = draftVersion(datasetId);
    assertThat(copiedDraft.getVersion()).isEqualTo(2);
    assertThat(copiedDraft.getRowCount()).isEqualTo(2);
    var copiedRows = datasetRowRepository.findByDatasetVersionIdOrderByRowIndexAsc(copiedDraft.getId());
    assertThat(copiedRows).hasSize(2).extracting("rowHash").containsExactly(
        activeRows.get(0).getRowHash(), activeRows.get(1).getRowHash());

    addRow(user, copiedDraft.getId(), Map.of("question", "Q3", "expected", "A3"));
    assertThat(datasetVersionRepository.findById(copiedDraft.getId()).orElseThrow().getRowCount())
        .isEqualTo(3);

    publish(user, copiedDraft.getId());

    assertThat(datasetVersionRepository.findById(copiedDraft.getId()).orElseThrow().getRowCount())
        .isEqualTo(3);
    assertThat(datasetRepository.findById(datasetId).orElseThrow().getCurrentVersionId())
        .isEqualTo(copiedDraft.getId());
  }

  @Test
  void addRow_InvalidCells_ReturnsBadRequestAndDoesNotChangeRowCount() throws Exception {
    var user = user("dataset-validation-owner@vinfast.vn");
    var workspace = workspace(user);
    var projectId = createProject(user, workspace.getId(), "Dataset Validation Project");
    var datasetId = createDataset(user, projectId, "Validation Dataset");
    var draftId = draftVersionId(datasetId);

    mockMvc
        .perform(
            post("/api/v1/dataset-versions/" + draftId + "/rows")
                .with(bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("cells", Map.of("expected", "A1")))))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            post("/api/v1/dataset-versions/" + draftId + "/rows")
                .with(bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("cells", Map.of("question", "Q1", "unknown", "bad")))))
        .andExpect(status().isBadRequest());

    var draft = datasetVersionRepository.findById(draftId).orElseThrow();
    assertThat(draft.getRowCount()).isZero();
    assertThat(datasetRowRepository.countByDatasetVersionId(draftId)).isZero();
  }

  private UUID createProject(vn.vinfast.vfqc.api.user.entity.AppUser user, UUID workspaceId, String name)
      throws Exception {
    var body = Map.of("name", name, "description", "Integration test project");
    var result =
        mockMvc
            .perform(
                post("/api/v1/workspaces/" + workspaceId + "/projects")
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  private UUID createDataset(vn.vinfast.vfqc.api.user.entity.AppUser user, UUID projectId, String name)
      throws Exception {
    var body = Map.of("name", name, "description", "Integration test dataset");
    var result =
        mockMvc
            .perform(
                post("/api/v1/projects/" + projectId + "/datasets")
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  private UUID draftVersionId(UUID datasetId) {
    return draftVersion(datasetId).getId();
  }

  private vn.vinfast.vfqc.api.dataset.entity.DatasetVersion draftVersion(UUID datasetId) {
    return datasetVersionRepository
        .findByDatasetIdAndStatus(datasetId, DatasetVersionStatus.DRAFT)
        .orElseThrow();
  }

  private long addRow(
      vn.vinfast.vfqc.api.user.entity.AppUser user, UUID versionId, Map<String, Object> cells)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/v1/dataset-versions/" + versionId + "/rows")
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("cells", cells))))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
  }

  private void patchRow(
      vn.vinfast.vfqc.api.user.entity.AppUser user,
      long rowId,
      Map<String, Object> cells,
      int expectedRevision)
      throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/dataset-rows/" + rowId)
                .with(bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("cells", cells, "expectedRevision", expectedRevision))))
        .andExpect(status().isOk());
  }

  private void publish(vn.vinfast.vfqc.api.user.entity.AppUser user, UUID versionId)
      throws Exception {
    mockMvc.perform(post("/api/v1/dataset-versions/" + versionId + "/publish").with(bearer(user)))
        .andExpect(status().isOk());
  }

  private void discard(vn.vinfast.vfqc.api.user.entity.AppUser user, UUID versionId)
      throws Exception {
    mockMvc.perform(post("/api/v1/dataset-versions/" + versionId + "/discard").with(bearer(user)))
        .andExpect(status().isOk());
  }
}
