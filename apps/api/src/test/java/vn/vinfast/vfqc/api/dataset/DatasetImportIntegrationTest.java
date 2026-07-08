package vn.vinfast.vfqc.api.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import vn.vinfast.vfqc.api.dataset.enums.DatasetColumnDataType;
import vn.vinfast.vfqc.api.dataset.enums.DatasetImportMappingAction;
import vn.vinfast.vfqc.api.dataset.enums.DatasetImportStatus;
import vn.vinfast.vfqc.api.dataset.enums.DatasetImportTargetSchemaMode;
import vn.vinfast.vfqc.api.dataset.enums.DatasetSourceType;
import vn.vinfast.vfqc.api.dataset.enums.DatasetVersionStatus;
import vn.vinfast.vfqc.api.support.PostgresIntegrationTestBase;
import vn.vinfast.vfqc.api.workspace.enums.WorkspaceRole;

@RequiredArgsConstructor
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class DatasetImportIntegrationTest extends PostgresIntegrationTestBase {

  private final MockMvc mockMvc;
  private final ObjectMapper objectMapper;

  @Test
  void excelImport_PreviewAndConfirm_ExtendsSchemaCreatesActiveDataset() throws Exception {
    var user = user("dataset-import-owner@vinfast.vn");
    var workspace = workspace(user);
    var projectId = createProject(user, workspace.getId(), "Dataset Import Project");

    var importJobId =
        createImportJob(
            user,
            projectId,
            excel(
                new String[] {"question", "expected", "context"},
                new String[][] {
                  {"VF 8 price?", "Expected answer", "Sales"},
                  {"VF 9 warranty?", "Warranty answer", "After-sales"}
                }),
            1);

    var job = datasetImportJobRepository.findById(importJobId).orElseThrow();
    assertThat(job.getStatus()).isEqualTo(DatasetImportStatus.PREVIEWED);
    assertThat(job.getPreviewJson().get("totalRows")).isEqualTo(2);
    assertThat((List<?>) job.getPreviewJson().get("rows")).hasSize(1);

    var datasetId =
        confirmImport(
            user,
            importJobId,
            "Imported Regression Dataset",
            DatasetImportTargetSchemaMode.EXTEND_CURRENT_SCHEMA,
            List.of(
                mapping("question", DatasetImportMappingAction.MAP_TO_EXISTING_COLUMN, "question"),
                mapping("expected", DatasetImportMappingAction.MAP_TO_EXISTING_COLUMN, "expected"),
                addColumn("context", "context")));

    var dataset = datasetRepository.findById(datasetId).orElseThrow();
    assertThat(dataset.getSourceType()).isEqualTo(DatasetSourceType.EXCEL);
    assertThat(dataset.getCurrentVersionId()).isNotNull();
    var version = datasetVersionRepository.findById(dataset.getCurrentVersionId()).orElseThrow();
    assertThat(version.getStatus()).isEqualTo(DatasetVersionStatus.ACTIVE);
    assertThat(version.getImportJobId()).isEqualTo(importJobId);
    assertThat(version.getRowCount()).isEqualTo(2);
    assertThat(version.getSchemaVersion()).isEqualTo(2);
    assertThat(datasetSchemaColumnRepository.findBySchemaIdOrderByOrderIndexAsc(version.getSchemaId()))
        .extracting("name")
        .containsExactly("question", "expected", "context");
    assertThat(datasetRowRepository.findByDatasetVersionIdOrderByRowIndexAsc(version.getId()))
        .hasSize(2)
        .allSatisfy(row -> assertThat(row.getRowHash()).isNotBlank())
        .extracting(row -> row.getCellsJson().get("context"))
        .containsExactly("Sales", "After-sales");
    assertThat(datasetImportJobRepository.findById(importJobId).orElseThrow().getStatus())
        .isEqualTo(DatasetImportStatus.COMPLETED);
  }

  @Test
  void excelImportPreview_WorkspaceViewer_ReturnsForbidden() throws Exception {
    var owner = user("dataset-import-viewer-owner@vinfast.vn");
    var viewer = user("dataset-import-viewer@vinfast.vn");
    var workspace = workspace(owner);
    workspaceMember(workspace, viewer, WorkspaceRole.VIEWER, owner);
    var projectId = createProject(owner, workspace.getId(), "Dataset Import Viewer Project");

    mockMvc
        .perform(
            multipart("/api/v1/projects/" + projectId + "/dataset-import-jobs")
                .file(excel(new String[] {"question"}, new String[][] {{"Q1"}}))
                .param("sourceType", DatasetSourceType.EXCEL.name())
                .with(bearer(viewer)))
        .andExpect(status().isForbidden());

    assertThat(datasetImportJobRepository.findAll()).isEmpty();
  }

  @Test
  void excelImportPreview_EmptySheet_ReturnsBadRequestAndDoesNotCreateJob() throws Exception {
    var user = user("dataset-import-empty-owner@vinfast.vn");
    var workspace = workspace(user);
    var projectId = createProject(user, workspace.getId(), "Dataset Import Empty Project");

    mockMvc
        .perform(
            multipart("/api/v1/projects/" + projectId + "/dataset-import-jobs")
                .file(emptyExcel())
                .param("sourceType", DatasetSourceType.EXCEL.name())
                .with(bearer(user)))
        .andExpect(status().isBadRequest());

    assertThat(datasetImportJobRepository.findAll()).isEmpty();
  }

  @Test
  void excelImportPreview_DuplicatedHeaders_ReturnsBadRequestAndDoesNotCreateJob()
      throws Exception {
    var user = user("dataset-import-duplicate-owner@vinfast.vn");
    var workspace = workspace(user);
    var projectId = createProject(user, workspace.getId(), "Dataset Import Duplicate Project");

    mockMvc
        .perform(
            multipart("/api/v1/projects/" + projectId + "/dataset-import-jobs")
                .file(excel(new String[] {"question", "Question"}, new String[][] {{"Q1", "Q2"}}))
                .param("sourceType", DatasetSourceType.EXCEL.name())
                .with(bearer(user)))
        .andExpect(status().isBadRequest());

    assertThat(datasetImportJobRepository.findAll()).isEmpty();
  }

  @Test
  void excelImportPreview_LargeWorkbookWithinLimit_StoresAllRowsAndClampsPreview()
      throws Exception {
    var user = user("dataset-import-large-owner@vinfast.vn");
    var workspace = workspace(user);
    var projectId = createProject(user, workspace.getId(), "Dataset Import Large Project");

    var importJobId =
        createImportJob(
            user,
            projectId,
            excel(new String[] {"question", "expected"}, largeRows(120)),
            500);

    var job = datasetImportJobRepository.findById(importJobId).orElseThrow();
    assertThat(job.getPreviewJson().get("totalRows")).isEqualTo(120);
    assertThat((List<?>) job.getPreviewJson().get("rows")).hasSize(20);

    var datasetId =
        confirmImport(
            user,
            importJobId,
            "Imported Large Dataset",
            DatasetImportTargetSchemaMode.USE_CURRENT_SCHEMA,
            List.of(
                mapping("question", DatasetImportMappingAction.MAP_TO_EXISTING_COLUMN, "question"),
                mapping("expected", DatasetImportMappingAction.MAP_TO_EXISTING_COLUMN, "expected")));

    var dataset = datasetRepository.findById(datasetId).orElseThrow();
    var version = datasetVersionRepository.findById(dataset.getCurrentVersionId()).orElseThrow();
    assertThat(version.getRowCount()).isEqualTo(120);
    assertThat(datasetRowRepository.findByDatasetVersionIdOrderByRowIndexAsc(version.getId()))
        .hasSize(120);
  }

  @Test
  void confirmImport_WhenJobAlreadyCompleted_ReturnsConflict() throws Exception {
    var user = user("dataset-import-state-owner@vinfast.vn");
    var workspace = workspace(user);
    var projectId = createProject(user, workspace.getId(), "Dataset Import State Project");
    var importJobId =
        createImportJob(
            user,
            projectId,
            excel(
                new String[] {"question", "expected"},
                new String[][] {{"Question", "Expected"}}),
            10);
    var mappings =
        List.of(
            mapping("question", DatasetImportMappingAction.MAP_TO_EXISTING_COLUMN, "question"),
            mapping("expected", DatasetImportMappingAction.MAP_TO_EXISTING_COLUMN, "expected"));

    confirmImport(user, importJobId, "Imported Once", DatasetImportTargetSchemaMode.USE_CURRENT_SCHEMA, mappings);

    mockMvc
        .perform(
            post("/api/v1/dataset-import-jobs/" + importJobId + "/confirm")
                .with(bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        confirmBody(
                            "Imported Twice",
                            DatasetImportTargetSchemaMode.USE_CURRENT_SCHEMA,
                            mappings))))
        .andExpect(status().isConflict());
  }

  @Test
  void confirmImport_MissingRequiredQuestion_ReturnsBadRequestAndDoesNotCreateDataset()
      throws Exception {
    var user = user("dataset-import-validation-owner@vinfast.vn");
    var workspace = workspace(user);
    var projectId = createProject(user, workspace.getId(), "Dataset Import Validation Project");
    var importJobId =
        createImportJob(
            user,
            projectId,
            excel(new String[] {"expected"}, new String[][] {{"Expected only"}}),
            10);

    mockMvc
        .perform(
            post("/api/v1/dataset-import-jobs/" + importJobId + "/confirm")
                .with(bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        confirmBody(
                            "Invalid Import",
                            DatasetImportTargetSchemaMode.USE_CURRENT_SCHEMA,
                            List.of(
                                mapping(
                                    "expected",
                                    DatasetImportMappingAction.MAP_TO_EXISTING_COLUMN,
                                    "expected"))))))
        .andExpect(status().isBadRequest());

    assertThat(datasetRepository.findByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(projectId))
        .isEmpty();
    assertThat(datasetImportJobRepository.findById(importJobId).orElseThrow().getStatus())
        .isEqualTo(DatasetImportStatus.PREVIEWED);
  }

  private UUID createProject(
      vn.vinfast.vfqc.api.user.entity.AppUser user, UUID workspaceId, String name)
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
    return UUID.fromString(
        objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  private UUID createImportJob(
      vn.vinfast.vfqc.api.user.entity.AppUser user,
      UUID projectId,
      MockMultipartFile file,
      int previewRowCount)
      throws Exception {
    var result =
        mockMvc
            .perform(
                multipart("/api/v1/projects/" + projectId + "/dataset-import-jobs")
                    .file(file)
                    .param("sourceType", DatasetSourceType.EXCEL.name())
                    .param("previewRowCount", String.valueOf(previewRowCount))
                    .with(bearer(user)))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(
        objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  private UUID confirmImport(
      vn.vinfast.vfqc.api.user.entity.AppUser user,
      UUID importJobId,
      String datasetName,
      DatasetImportTargetSchemaMode mode,
      List<Map<String, Object>> mappings)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/v1/dataset-import-jobs/" + importJobId + "/confirm")
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(confirmBody(datasetName, mode, mappings))))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(
        objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  private Map<String, Object> confirmBody(
      String datasetName, DatasetImportTargetSchemaMode mode, List<Map<String, Object>> mappings) {
    return Map.of(
        "datasetName",
        datasetName,
        "datasetDescription",
        "Imported by integration test",
        "targetSchemaMode",
        mode.name(),
        "mapping",
        mappings);
  }

  private Map<String, Object> mapping(
      String sourceColumn, DatasetImportMappingAction action, String targetColumn) {
    return Map.of(
        "sourceColumn", sourceColumn,
        "action", action.name(),
        "targetColumn", targetColumn);
  }

  private Map<String, Object> addColumn(String sourceColumn, String targetColumn) {
    return Map.of(
        "sourceColumn",
        sourceColumn,
        "action",
        DatasetImportMappingAction.ADD_AS_NEW_COLUMN.name(),
        "targetColumn",
        targetColumn,
        "displayName",
        targetColumn,
        "dataType",
        DatasetColumnDataType.TEXT.name(),
        "required",
        false);
  }

  private String[][] largeRows(int rowCount) {
    var rows = new String[rowCount][2];
    for (int index = 0; index < rowCount; index++) {
      rows[index][0] = "Question " + index;
      rows[index][1] = "Expected " + index;
    }
    return rows;
  }

  private MockMultipartFile emptyExcel() throws Exception {
    try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
      workbook.createSheet("Regression");
      workbook.write(output);
      return new MockMultipartFile(
          "file",
          "empty-dataset.xlsx",
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
          output.toByteArray());
    }
  }

  private MockMultipartFile excel(String[] headers, String[][] rows) throws Exception {
    try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
      var sheet = workbook.createSheet("Regression");
      var header = sheet.createRow(0);
      for (int index = 0; index < headers.length; index++) {
        header.createCell(index).setCellValue(headers[index]);
      }
      for (int rowIndex = 0; rowIndex < rows.length; rowIndex++) {
        var row = sheet.createRow(rowIndex + 1);
        for (int cellIndex = 0; cellIndex < rows[rowIndex].length; cellIndex++) {
          row.createCell(cellIndex).setCellValue(rows[rowIndex][cellIndex]);
        }
      }
      workbook.write(output);
      return new MockMultipartFile(
          "file",
          "dataset.xlsx",
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
          output.toByteArray());
    }
  }
}
