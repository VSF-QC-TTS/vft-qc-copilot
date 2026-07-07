package vn.vinfast.vfqc.api.dataset.service.impl;

import java.io.IOException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.vinfast.vfqc.api.common.error.ErrorCode;
import vn.vinfast.vfqc.api.common.naming.ResourceNameNormalizer;
import vn.vinfast.vfqc.api.common.permission.PermissionAction;
import vn.vinfast.vfqc.api.common.permission.ProjectPermissionService;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.dataset.assembler.DatasetResponseAssembler;
import vn.vinfast.vfqc.api.dataset.dto.ConfirmDatasetImportJobRequest;
import vn.vinfast.vfqc.api.dataset.dto.DatasetImportJobResponse;
import vn.vinfast.vfqc.api.dataset.dto.DatasetImportMappingDto;
import vn.vinfast.vfqc.api.dataset.dto.DatasetResponse;
import vn.vinfast.vfqc.api.dataset.entity.Dataset;
import vn.vinfast.vfqc.api.dataset.entity.DatasetImportJob;
import vn.vinfast.vfqc.api.dataset.entity.DatasetRow;
import vn.vinfast.vfqc.api.dataset.entity.DatasetSchema;
import vn.vinfast.vfqc.api.dataset.entity.DatasetSchemaColumn;
import vn.vinfast.vfqc.api.dataset.entity.DatasetVersion;
import vn.vinfast.vfqc.api.dataset.enums.DatasetColumnDataType;
import vn.vinfast.vfqc.api.dataset.enums.DatasetImportMappingAction;
import vn.vinfast.vfqc.api.dataset.enums.DatasetImportSchemaCompatibilityStatus;
import vn.vinfast.vfqc.api.dataset.enums.DatasetImportStatus;
import vn.vinfast.vfqc.api.dataset.enums.DatasetImportTargetSchemaMode;
import vn.vinfast.vfqc.api.dataset.enums.DatasetSchemaScope;
import vn.vinfast.vfqc.api.dataset.enums.DatasetScope;
import vn.vinfast.vfqc.api.dataset.enums.DatasetSourceType;
import vn.vinfast.vfqc.api.dataset.enums.DatasetVersionStatus;
import vn.vinfast.vfqc.api.dataset.repository.DatasetImportJobRepository;
import vn.vinfast.vfqc.api.dataset.repository.DatasetRepository;
import vn.vinfast.vfqc.api.dataset.repository.DatasetRowRepository;
import vn.vinfast.vfqc.api.dataset.repository.DatasetSchemaColumnRepository;
import vn.vinfast.vfqc.api.dataset.repository.DatasetSchemaRepository;
import vn.vinfast.vfqc.api.dataset.repository.DatasetVersionRepository;
import vn.vinfast.vfqc.api.dataset.service.DatasetImportService;
import vn.vinfast.vfqc.api.dataset.service.DatasetRowHashService;
import vn.vinfast.vfqc.api.dataset.validator.DatasetCellValidator;
import vn.vinfast.vfqc.api.user.dto.UserSummaryResponse;
import vn.vinfast.vfqc.api.user.service.UserSummaryService;

@Service
@RequiredArgsConstructor
@Slf4j
public class DatasetImportServiceImpl implements DatasetImportService {

  private static final int DEFAULT_PREVIEW_ROWS = 10;
  private static final int MAX_PREVIEW_ROWS = 20;
  private static final int MAX_IMPORT_ROWS = 500;
  private static final Pattern COLUMN_NAME = Pattern.compile("[a-z][a-z0-9_]*");

  private final DatasetImportJobRepository importJobRepository;
  private final DatasetRepository datasetRepository;
  private final DatasetVersionRepository versionRepository;
  private final DatasetRowRepository rowRepository;
  private final DatasetSchemaRepository schemaRepository;
  private final DatasetSchemaColumnRepository columnRepository;
  private final ProjectPermissionService projectPermissionService;
  private final ResourceNameNormalizer nameNormalizer;
  private final DatasetCellValidator cellValidator;
  private final DatasetRowHashService rowHashService;
  private final DatasetResponseAssembler datasetAssembler;
  private final UserSummaryService userSummaryService;
  private final Clock clock;

  @Override
  @Transactional
  public DatasetImportJobResponse createExcelPreview(
      CurrentUser user,
      UUID projectId,
      DatasetSourceType sourceType,
      MultipartFile file,
      String sheetName,
      Integer previewRowCount) {
    if (sourceType != DatasetSourceType.EXCEL) {
      throw ErrorCode.VALIDATION_ERROR.throwIt("Only EXCEL import is supported in this MVP");
    }
    var project = projectPermissionService.requireProject(user, projectId, PermissionAction.EDIT);
    var schema =
        schemaRepository
            .findTopByProjectIdOrderByVersionDesc(projectId)
            .orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    var columns = columnRepository.findBySchemaIdOrderByOrderIndexAsc(schema.getId());
    var workbook = readWorkbook(file, sheetName, safePreviewRowCount(previewRowCount));
    var compatibility = compatibility(workbook.headers(), columns);
    var suggestedMapping = suggestedMapping(workbook.headers(), columns);
    var now = OffsetDateTime.now(clock);
    var job =
        importJobRepository.save(
            DatasetImportJob.builder()
                .targetProjectId(projectId)
                .sourceType(DatasetSourceType.EXCEL)
                .sourceFileName(safeFileName(file.getOriginalFilename()))
                .status(DatasetImportStatus.PREVIEWED)
                .previewJson(
                    Map.of(
                        "sheetName",
                        workbook.sheetName(),
                        "availableSheets",
                        workbook.availableSheets(),
                        "columns",
                        workbook.headers(),
                        "rows",
                        workbook.previewRows(),
                        "totalRows",
                        workbook.allRows().size(),
                        "previewRowCount",
                        workbook.previewRows().size()))
                .sourceDataJson(Map.of("rows", workbook.allRows()))
                .mappingJson(Map.of("items", suggestedMapping))
                .createdBy(user.id())
                .createdAt(now)
                .build());
    log.info(
        "Dataset import preview created: importJobId={} projectId={} workspaceId={} actorId={}",
        job.getId(),
        projectId,
        project.getWorkspaceId(),
        user.id());
    return toResponse(job, compatibility);
  }

  @Override
  @Transactional(readOnly = true)
  public DatasetImportJobResponse get(CurrentUser user, UUID importJobId) {
    var job =
        importJobRepository.findById(importJobId).orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    projectPermissionService.requireProject(user, job.getTargetProjectId(), PermissionAction.VIEW);
    var schema =
        schemaRepository
            .findTopByProjectIdOrderByVersionDesc(job.getTargetProjectId())
            .orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    var columns = columnRepository.findBySchemaIdOrderByOrderIndexAsc(schema.getId());
    return toResponse(job, compatibility(sourceColumns(job), columns));
  }

  @Override
  @Transactional
  public DatasetResponse confirm(
      CurrentUser user, UUID importJobId, ConfirmDatasetImportJobRequest request) {
    var job =
        importJobRepository.findById(importJobId).orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    var project =
        projectPermissionService.requireProject(
            user, job.getTargetProjectId(), PermissionAction.EDIT);
    if (job.getStatus() != DatasetImportStatus.PREVIEWED) {
      throw ErrorCode.DATASET_IMPORT_JOB_INVALID_STATE.throwIt();
    }
    var normalized = nameNormalizer.normalize(request.datasetName(), 160);
    if (datasetRepository.existsByProjectIdAndNameLookupAndDeletedAtIsNull(
        job.getTargetProjectId(), normalized.lookupName())) {
      throw ErrorCode.DATASET_NAME_ALREADY_EXISTS.throwIt();
    }
    var sourceColumns = sourceColumns(job);
    var mappings = validateMappings(sourceColumns, request.mapping());
    var baseSchema =
        schemaRepository
            .findTopByProjectIdOrderByVersionDesc(job.getTargetProjectId())
            .orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    var targetSchema =
        schemaForImport(
            user, job.getTargetProjectId(), baseSchema, mappings, schemaMode(request));
    var sourceRows = sourceRows(job);
    var importedRows = buildImportedRows(sourceRows, mappings);
    importedRows.forEach(cells -> cellValidator.validate(targetSchema.getId(), cells));
    var now = OffsetDateTime.now(clock);
    var dataset =
        datasetRepository.save(
            Dataset.builder()
                .scope(DatasetScope.PROJECT)
                .workspaceId(project.getWorkspaceId())
                .projectId(job.getTargetProjectId())
                .name(normalized.displayName())
                .nameLookup(normalized.lookupName())
                .description(request.datasetDescription())
                .sourceType(DatasetSourceType.EXCEL)
                .createdBy(user.id())
                .createdAt(now)
                .updatedAt(now)
                .build());
    var version =
        versionRepository.save(
            DatasetVersion.builder()
                .datasetId(dataset.getId())
                .version(1)
                .schemaId(targetSchema.getId())
                .schemaVersion(targetSchema.getVersion())
                .importJobId(job.getId())
                .importMappingJson(mappingAudit(mappings, schemaMode(request)))
                .rowCount(importedRows.size())
                .status(DatasetVersionStatus.ACTIVE)
                .createdBy(user.id())
                .createdAt(now)
                .publishedAt(now)
                .build());
    rowRepository.saveAll(
        indexedRows(version.getId(), importedRows, now));
    dataset.setCurrentVersionId(version.getId());
    job.setTargetDatasetId(dataset.getId());
    job.setStatus(DatasetImportStatus.COMPLETED);
    job.setMappingJson(mappingAudit(mappings, schemaMode(request)));
    job.setFinishedAt(now);
    log.info(
        "Dataset import completed: importJobId={} datasetId={} projectId={} actorId={}",
        job.getId(),
        dataset.getId(),
        job.getTargetProjectId(),
        user.id());
    return datasetAssembler.toResponse(dataset);
  }

  private ExcelImportWorkbook readWorkbook(
      MultipartFile file, String sheetName, int previewRowCount) {
    if (file == null || file.isEmpty()) {
      throw ErrorCode.VALIDATION_ERROR.throwIt("Excel file is required");
    }
    try (var workbook = new XSSFWorkbook(file.getInputStream())) {
      if (workbook.getNumberOfSheets() == 0) {
        throw ErrorCode.VALIDATION_ERROR.throwIt("Excel workbook has no sheets");
      }
      var availableSheets = new ArrayList<String>();
      for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
        availableSheets.add(workbook.getSheetName(index));
      }
      var sheet =
          sheetName == null || sheetName.isBlank()
              ? workbook.getSheetAt(0)
              : workbook.getSheet(sheetName);
      if (sheet == null) {
        throw ErrorCode.VALIDATION_ERROR.throwIt("Excel sheet does not exist");
      }
      var formatter = new DataFormatter();
      var headerRow = sheet.getRow(sheet.getFirstRowNum());
      var headers = readHeaders(headerRow, formatter);
      var allRows = new ArrayList<Map<String, Object>>();
      for (int rowIndex = sheet.getFirstRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
        if (allRows.size() >= MAX_IMPORT_ROWS) {
          throw ErrorCode.VALIDATION_ERROR.throwIt("Excel import row limit exceeded");
        }
        var row = sheet.getRow(rowIndex);
        if (row == null) {
          continue;
        }
        var data = readRow(row, headers, formatter);
        if (!data.isEmpty()) {
          allRows.add(Map.of("rowIndex", rowIndex, "data", data));
        }
      }
      return new ExcelImportWorkbook(
          sheet.getSheetName(),
          availableSheets,
          headers,
          allRows.stream().limit(previewRowCount).toList(),
          allRows);
    } catch (IOException exception) {
      throw ErrorCode.VALIDATION_ERROR.throwIt("Excel file cannot be read");
    }
  }

  private List<String> readHeaders(Row headerRow, DataFormatter formatter) {
    if (headerRow == null) {
      throw ErrorCode.VALIDATION_ERROR.throwIt("Excel header row is required");
    }
    var headers = new ArrayList<String>();
    var seen = new java.util.HashSet<String>();
    for (int cellIndex = 0; cellIndex < headerRow.getLastCellNum(); cellIndex++) {
      var raw = formatter.formatCellValue(headerRow.getCell(cellIndex)).trim();
      if (raw.isBlank()) {
        continue;
      }
      var header = normalizeColumnName(raw);
      if (!seen.add(header)) {
        throw ErrorCode.VALIDATION_ERROR.throwIt("Duplicate Excel column: " + header);
      }
      headers.add(header);
    }
    if (headers.isEmpty()) {
      throw ErrorCode.VALIDATION_ERROR.throwIt("Excel header row must contain at least one column");
    }
    return List.copyOf(headers);
  }

  private Map<String, Object> readRow(Row row, List<String> headers, DataFormatter formatter) {
    var data = new LinkedHashMap<String, Object>();
    for (int cellIndex = 0; cellIndex < headers.size(); cellIndex++) {
      var value = formatter.formatCellValue(row.getCell(cellIndex)).trim();
      if (!value.isBlank()) {
        data.put(headers.get(cellIndex), value);
      }
    }
    return data;
  }

  private DatasetSchema schemaForImport(
      CurrentUser user,
      UUID projectId,
      DatasetSchema baseSchema,
      List<ImportMapping> mappings,
      DatasetImportTargetSchemaMode mode) {
    var addMappings =
        mappings.stream()
            .filter(mapping -> mapping.action() == DatasetImportMappingAction.ADD_AS_NEW_COLUMN)
            .toList();
    if (addMappings.isEmpty()) {
      return baseSchema;
    }
    if (mode == DatasetImportTargetSchemaMode.USE_CURRENT_SCHEMA) {
      throw ErrorCode.DATASET_IMPORT_MAPPING_INVALID.throwIt(
          "Cannot add columns when targetSchemaMode is USE_CURRENT_SCHEMA");
    }
    var existingColumns = columnRepository.findBySchemaIdOrderByOrderIndexAsc(baseSchema.getId());
    var existingNames =
        existingColumns.stream().map(DatasetSchemaColumn::getName).collect(Collectors.toSet());
    for (var mapping : addMappings) {
      if (!existingNames.add(mapping.targetColumn())) {
        throw ErrorCode.DATASET_IMPORT_MAPPING_INVALID.throwIt(
            "Target column already exists: " + mapping.targetColumn());
      }
    }
    var now = OffsetDateTime.now(clock);
    var schema =
        schemaRepository.save(
            DatasetSchema.builder()
                .scope(DatasetSchemaScope.PROJECT)
                .projectId(projectId)
                .version(baseSchema.getVersion() + 1)
                .name(baseSchema.getName())
                .createdBy(user.id())
                .createdAt(now)
                .build());
    var nextOrder = new java.util.concurrent.atomic.AtomicInteger(0);
    var copied =
        existingColumns.stream()
            .map(
                column ->
                    DatasetSchemaColumn.builder()
                        .schemaId(schema.getId())
                        .name(column.getName())
                        .displayName(column.getDisplayName())
                        .dataType(column.getDataType())
                        .required(column.isRequired())
                        .description(column.getDescription())
                        .orderIndex(nextOrder.getAndIncrement())
                        .createdAt(now)
                        .updatedAt(now)
                        .build())
            .collect(Collectors.toCollection(ArrayList::new));
    for (var mapping : addMappings) {
      copied.add(
          DatasetSchemaColumn.builder()
              .schemaId(schema.getId())
              .name(mapping.targetColumn())
              .displayName(mapping.displayName())
              .dataType(mapping.dataType())
              .required(mapping.required())
              .description(mapping.description())
              .orderIndex(nextOrder.getAndIncrement())
              .createdAt(now)
              .updatedAt(now)
              .build());
    }
    columnRepository.saveAll(copied);
    return schema;
  }

  private List<DatasetRow> indexedRows(
      UUID versionId, List<Map<String, Object>> importedRows, OffsetDateTime now) {
    var rows = new ArrayList<DatasetRow>();
    for (int index = 0; index < importedRows.size(); index++) {
      var cells = importedRows.get(index);
      rows.add(
          DatasetRow.builder()
              .datasetVersionId(versionId)
              .rowIndex(index)
              .cellsJson(cells)
              .revision(0)
              .rowHash(rowHashService.hash(cells))
              .createdAt(now)
              .updatedAt(now)
              .build());
    }
    return rows;
  }

  private List<Map<String, Object>> buildImportedRows(
      List<Map<String, Object>> sourceRows, List<ImportMapping> mappings) {
    var activeMappings =
        mappings.stream()
            .filter(mapping -> mapping.action() != DatasetImportMappingAction.IGNORE)
            .toList();
    var imported = new ArrayList<Map<String, Object>>();
    for (var sourceRow : sourceRows) {
      var data = new LinkedHashMap<String, Object>();
      for (var mapping : activeMappings) {
        var value = sourceRow.get(mapping.sourceColumn());
        if (value != null) {
          data.put(mapping.targetColumn(), value);
        }
      }
      imported.add(data);
    }
    return imported;
  }

  private List<ImportMapping> validateMappings(
      List<String> sourceColumns, List<DatasetImportMappingDto> requestMappings) {
    var sourceSet = Set.copyOf(sourceColumns);
    var mappedSources = new java.util.HashSet<String>();
    var targetColumns = new java.util.HashSet<String>();
    var mappings = new ArrayList<ImportMapping>();
    for (var request : requestMappings) {
      var sourceColumn = normalizeColumnName(request.sourceColumn());
      if (!sourceSet.contains(sourceColumn)) {
        throw ErrorCode.DATASET_IMPORT_MAPPING_INVALID.throwIt(
            "Unknown source column: " + sourceColumn);
      }
      if (!mappedSources.add(sourceColumn)) {
        throw ErrorCode.DATASET_IMPORT_MAPPING_INVALID.throwIt(
            "Duplicate source mapping: " + sourceColumn);
      }
      var action =
          request.action() == null ? DatasetImportMappingAction.MAP_TO_EXISTING_COLUMN : request.action();
      if (action == DatasetImportMappingAction.IGNORE) {
        mappings.add(
            new ImportMapping(
                sourceColumn, action, null, null, DatasetColumnDataType.TEXT, false, null));
        continue;
      }
      var targetColumn = normalizeColumnName(request.targetColumn());
      if (!targetColumns.add(targetColumn)) {
        throw ErrorCode.DATASET_IMPORT_MAPPING_INVALID.throwIt(
            "Duplicate target mapping: " + targetColumn);
      }
      mappings.add(
          new ImportMapping(
              sourceColumn,
              action,
              targetColumn,
              request.displayName() == null ? targetColumn : request.displayName(),
              request.dataType() == null ? DatasetColumnDataType.TEXT : request.dataType(),
              Boolean.TRUE.equals(request.required()),
              request.description()));
    }
    if (!mappedSources.containsAll(sourceSet)) {
      throw ErrorCode.DATASET_IMPORT_MAPPING_INVALID.throwIt("Mapping must cover every source column");
    }
    return List.copyOf(mappings);
  }

  private Map<String, Object> mappingAudit(
      List<ImportMapping> mappings, DatasetImportTargetSchemaMode mode) {
    return Map.of(
        "targetSchemaMode",
        mode.name(),
        "items",
        mappings.stream()
            .map(
                mapping -> {
                  var item = new LinkedHashMap<String, Object>();
                  item.put("sourceColumn", mapping.sourceColumn());
                  item.put("action", mapping.action().name());
                  if (mapping.targetColumn() != null) {
                    item.put("targetColumn", mapping.targetColumn());
                  }
                  if (mapping.action() == DatasetImportMappingAction.ADD_AS_NEW_COLUMN) {
                    item.put("displayName", mapping.displayName());
                    item.put("dataType", mapping.dataType().name());
                    item.put("required", mapping.required());
                    if (mapping.description() != null) {
                      item.put("description", mapping.description());
                    }
                  }
                  return item;
                })
            .toList());
  }

  @SuppressWarnings("unchecked")
  private List<String> sourceColumns(DatasetImportJob job) {
    var columns = job.getPreviewJson() == null ? null : job.getPreviewJson().get("columns");
    if (!(columns instanceof List<?> list)) {
      return List.of();
    }
    return list.stream().map(String::valueOf).toList();
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> sourceRows(DatasetImportJob job) {
    var rows = job.getSourceDataJson() == null ? null : job.getSourceDataJson().get("rows");
    if (!(rows instanceof List<?> list)) {
      return List.of();
    }
    return list.stream()
        .map(
            item -> {
              var row = (Map<String, Object>) item;
              var data = row.get("data");
              return data instanceof Map<?, ?> map
                  ? (Map<String, Object>) map
                  : Map.<String, Object>of();
            })
        .toList();
  }

  private Map<String, Object> compatibility(
      List<String> sourceColumns, List<DatasetSchemaColumn> targetColumns) {
    var sourceSet = Set.copyOf(sourceColumns);
    var targetRequired =
        targetColumns.stream().filter(DatasetSchemaColumn::isRequired).map(DatasetSchemaColumn::getName).toList();
    var missingRequired =
        targetRequired.stream().filter(required -> !sourceSet.contains(required)).toList();
    var targetNames = targetColumns.stream().map(DatasetSchemaColumn::getName).collect(Collectors.toSet());
    var extraSource = sourceColumns.stream().filter(source -> !targetNames.contains(source)).toList();
    var status =
        missingRequired.isEmpty() && extraSource.isEmpty()
            ? DatasetImportSchemaCompatibilityStatus.COMPATIBLE
            : DatasetImportSchemaCompatibilityStatus.REQUIRES_MAPPING;
    return Map.of(
        "status",
        status.name(),
        "requiresMapping",
        status != DatasetImportSchemaCompatibilityStatus.COMPATIBLE,
        "sourceColumnCount",
        sourceColumns.size(),
        "targetColumnCount",
        targetColumns.size(),
        "missingRequiredColumns",
        missingRequired,
        "extraSourceColumns",
        extraSource);
  }

  private List<Map<String, Object>> suggestedMapping(
      List<String> sourceColumns, List<DatasetSchemaColumn> targetColumns) {
    var targetNames = targetColumns.stream().map(DatasetSchemaColumn::getName).collect(Collectors.toSet());
    return sourceColumns.stream()
        .map(
            sourceColumn -> {
              Map<String, Object> item = new LinkedHashMap<>();
              item.put("sourceColumn", sourceColumn);
              if (targetNames.contains(sourceColumn)) {
                item.put("action", DatasetImportMappingAction.MAP_TO_EXISTING_COLUMN.name());
                item.put("targetColumn", sourceColumn);
              } else {
                item.put("action", DatasetImportMappingAction.IGNORE.name());
              }
              return item;
            })
        .toList();
  }

  private DatasetImportJobResponse toResponse(
      DatasetImportJob job, Map<String, Object> compatibility) {
    var createdByUser = userSummary(job.getCreatedBy());
    return new DatasetImportJobResponse(
        job.getId(),
        job.getTargetProjectId(),
        job.getTargetDatasetId(),
        job.getSourceType(),
        job.getSourceFileName(),
        job.getStatus(),
        compatibility,
        job.getPreviewJson(),
        job.getMappingJson(),
        job.getErrorMessage(),
        job.getCreatedBy(),
        createdByUser,
        job.getCreatedAt(),
        job.getFinishedAt());
  }

  private UserSummaryResponse userSummary(UUID userId) {
    return userId == null ? null : userSummaryService.toSummaryById(userId);
  }

  private String normalizeColumnName(String input) {
    if (input == null) {
      throw ErrorCode.DATASET_IMPORT_MAPPING_INVALID.throwIt("Column name is required");
    }
    var normalized =
        input.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("_+", "_");
    normalized = normalized.replaceAll("^_+|_+$", "");
    if (!COLUMN_NAME.matcher(normalized).matches()) {
      throw ErrorCode.DATASET_IMPORT_MAPPING_INVALID.throwIt("Invalid column name: " + input);
    }
    return normalized;
  }

  private int safePreviewRowCount(Integer requested) {
    if (requested == null) {
      return DEFAULT_PREVIEW_ROWS;
    }
    return Math.clamp(requested, 1, MAX_PREVIEW_ROWS);
  }

  private DatasetImportTargetSchemaMode schemaMode(ConfirmDatasetImportJobRequest request) {
    return request.targetSchemaMode() == null
        ? DatasetImportTargetSchemaMode.USE_CURRENT_SCHEMA
        : request.targetSchemaMode();
  }

  private String safeFileName(String originalFilename) {
    if (originalFilename == null || originalFilename.isBlank()) {
      return "upload.xlsx";
    }
    return originalFilename.replaceAll("[\\\\/\\p{Cntrl}]", "_");
  }

  private record ExcelImportWorkbook(
      String sheetName,
      List<String> availableSheets,
      List<String> headers,
      List<Map<String, Object>> previewRows,
      List<Map<String, Object>> allRows) {}

  private record ImportMapping(
      String sourceColumn,
      DatasetImportMappingAction action,
      String targetColumn,
      String displayName,
      DatasetColumnDataType dataType,
      boolean required,
      String description) {}
}
