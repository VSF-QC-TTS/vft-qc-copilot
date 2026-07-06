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

    patchRow(user, rowId, Map.of("question", "What is VF 9?", "expected", "Answer"));

    var patched = datasetRowRepository.findById(rowId).orElseThrow();
    assertThat(patched.getRowHash()).isNotBlank().isNotEqualTo(stored.getRowHash());
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
    return datasetVersionRepository
        .findByDatasetIdAndStatus(datasetId, DatasetVersionStatus.DRAFT)
        .orElseThrow()
        .getId();
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
      vn.vinfast.vfqc.api.user.entity.AppUser user, long rowId, Map<String, Object> cells)
      throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/dataset-rows/" + rowId)
                .with(bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("cells", cells))))
        .andExpect(status().isOk());
  }

  private void publish(vn.vinfast.vfqc.api.user.entity.AppUser user, UUID versionId)
      throws Exception {
    mockMvc.perform(post("/api/v1/dataset-versions/" + versionId + "/publish").with(bearer(user)))
        .andExpect(status().isOk());
  }
}
