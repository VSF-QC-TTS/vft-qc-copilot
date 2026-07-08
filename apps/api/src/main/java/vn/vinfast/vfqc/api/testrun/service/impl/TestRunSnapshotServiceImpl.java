package vn.vinfast.vfqc.api.testrun.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vinfast.vfqc.api.common.error.ErrorCode;
import vn.vinfast.vfqc.api.common.masking.VfqcSensitiveDataMasker;
import vn.vinfast.vfqc.api.common.permission.PermissionAction;
import vn.vinfast.vfqc.api.common.permission.ProjectPermissionService;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.dataset.enums.DatasetVersionStatus;
import vn.vinfast.vfqc.api.dataset.repository.DatasetRepository;
import vn.vinfast.vfqc.api.dataset.repository.DatasetRowRepository;
import vn.vinfast.vfqc.api.dataset.repository.DatasetSchemaColumnRepository;
import vn.vinfast.vfqc.api.dataset.repository.DatasetVersionRepository;
import vn.vinfast.vfqc.api.testrun.dto.CreateTestRunRequest;
import vn.vinfast.vfqc.api.testrun.dto.PreflightMessageResponse;
import vn.vinfast.vfqc.api.testrun.dto.RunEvaluatorVersionRequest;
import vn.vinfast.vfqc.api.testrun.dto.RunPreflightResponse;
import vn.vinfast.vfqc.api.testrun.dto.RunTargetRequest;
import vn.vinfast.vfqc.api.testrun.enums.TestRunType;
import vn.vinfast.vfqc.api.testrun.model.LockedRunConfig;
import vn.vinfast.vfqc.api.testrun.model.LockedRunConfig.LockedRunTargetConfig;
import vn.vinfast.vfqc.api.testrun.service.TestRunSnapshotService;

@Slf4j
@Service
@RequiredArgsConstructor
public class TestRunSnapshotServiceImpl implements TestRunSnapshotService {

  private static final String KEY_RESPONSE_EXTRACTION = "responseExtraction";
  private static final String KEY_FIELDS = "fields";
  private static final String KEY_FIELD_NAME = "fieldName";

  private final ProjectPermissionService projectPermissionService;
  private final DatasetRepository datasetRepository;
  private final DatasetVersionRepository datasetVersionRepository;
  private final DatasetRowRepository datasetRowRepository;
  private final DatasetSchemaColumnRepository schemaColumnRepository;
  private final TestRunTargetValidator targetValidator;
  private final TestRunEvaluatorValidator evaluatorValidator;
  private final VfqcSensitiveDataMasker masker;

  @Override
  @Transactional(readOnly = true)
  public LockedRunConfig lock(CurrentUser user, UUID projectId, CreateTestRunRequest request) {
    var inspection = inspect(user, projectId, request);
    if (!inspection.blockers().isEmpty()) {
      var firstBlocker = inspection.blockers().getFirst();
      log.warn(
          "Test run snapshot lock rejected. projectId={}, blockerCount={}, firstBlockerCode={}, firstResourceId={}",
          projectId,
          inspection.blockers().size(),
          firstBlocker.code(),
          firstBlocker.resourceId());
      throw ErrorCode.INVALID_STATE.throwIt(firstBlocker.message());
    }
    return inspection.lockedConfig();
  }

  @Override
  @Transactional(readOnly = true)
  public Inspection inspect(CurrentUser user, UUID projectId, CreateTestRunRequest request) {
    projectPermissionService.requireProject(user, projectId, PermissionAction.VIEW);

    var blockers = new ArrayList<PreflightMessageResponse>();
    var warnings = new ArrayList<PreflightMessageResponse>();

    if (request == null) {
      addBlocker(blockers, "REQUEST_MISSING", "Create test run request is required", null);
      return emptyInspection(projectId, blockers, warnings);
    }

    log.debug(
        "Inspecting test run snapshot. projectId={}, datasetId={}, targetCount={}, evaluatorCount={}",
        projectId,
        request.datasetId(),
        request.targets() == null ? 0 : request.targets().size(),
        request.evaluators() == null ? 0 : request.evaluators().size());

    if (request.datasetId() == null) {
      addBlocker(blockers, "DATASET_ID_MISSING", "Dataset id is required", null);
      return emptyInspection(projectId, blockers, warnings);
    }

    var targets = request.targets() == null ? List.<RunTargetRequest>of() : request.targets();
    var evaluators =
        request.evaluators() == null ? List.<RunEvaluatorVersionRequest>of() : request.evaluators();

    var dataset = datasetRepository.findByIdAndDeletedAtIsNull(request.datasetId()).orElse(null);
    if (dataset == null || !projectId.equals(dataset.getProjectId())) {
      addBlocker(
          blockers,
          "DATASET_NOT_FOUND",
          "Dataset is not available for this project",
          request.datasetId());
      return emptyInspection(projectId, blockers, warnings);
    }

    if (dataset.getCurrentVersionId() == null) {
      addBlocker(
          blockers,
          "DATASET_ACTIVE_VERSION_MISSING",
          "Dataset has no active version",
          dataset.getId());
      return emptyInspection(projectId, blockers, warnings);
    }

    var datasetVersion =
        datasetVersionRepository.findById(dataset.getCurrentVersionId()).orElse(null);
    if (datasetVersion == null || datasetVersion.getStatus() != DatasetVersionStatus.ACTIVE) {
      addBlocker(
          blockers,
          "DATASET_ACTIVE_VERSION_MISSING",
          "Dataset has no active version",
          dataset.getId());
      return emptyInspection(projectId, blockers, warnings);
    }

    var rowCount = datasetRowRepository.countByDatasetVersionId(datasetVersion.getId());
    if (rowCount < 1) {
      addBlocker(
          blockers,
          "DATASET_ROWS_MISSING",
          "Dataset active version has no rows",
          datasetVersion.getId());
    }

    validateMaxRows(request, blockers);

    var lockedTargets = targetValidator.lockTargets(user, projectId, targets, blockers);
    var availableFieldSources = availableFieldSources(datasetVersion.getSchemaId(), lockedTargets);
    var lockedEvaluators =
        evaluatorValidator.lockEvaluators(user, projectId, evaluators, availableFieldSources, blockers);

    var runType = request.runType() == null ? defaultRunType(lockedTargets) : request.runType();
    validateRunType(runType, lockedTargets.size(), blockers);

    var limitedRowCount = limitedRowCount(rowCount, request);

    var domainSnapshot =
        snapshot(
            "projectId", projectId.toString(),
            "datasetId", dataset.getId().toString(),
            "datasetVersionId", datasetVersion.getId().toString(),
            "datasetSchemaId", datasetVersion.getSchemaId().toString(),
            "datasetSchemaVersion", datasetVersion.getSchemaVersion(),
            "rowCount", limitedRowCount,
            "targets", lockedTargets.stream().map(LockedRunTargetConfig::config).toList(),
            "evaluators", lockedEvaluators.stream().map(LockedRunConfig.LockedEvaluatorConfig::config).toList());

    var promptfooSnapshot =
        snapshot(
            "version", "phase8-mvp",
            "datasetVersionId", datasetVersion.getId().toString(),
            "targetCount", lockedTargets.size(),
            "evaluatorCount", lockedEvaluators.size());

    var judgeProviderId = evaluatorValidator.firstJudgeProviderId(lockedEvaluators);
    var judgeModel = evaluatorValidator.firstJudgeModel(lockedEvaluators);

    log.debug(
        "Finished test run snapshot inspection. projectId={}, datasetVersionId={}, rowCount={}, limitedRowCount={}, lockedTargetCount={}, lockedEvaluatorCount={}, blockerCount={}",
        projectId,
        datasetVersion.getId(),
        rowCount,
        limitedRowCount,
        lockedTargets.size(),
        lockedEvaluators.size(),
        blockers.size());

    return new Inspection(
        blockers,
        warnings,
        new RunPreflightResponse.Counts(
            limitedRowCount, lockedTargets.size(), lockedEvaluators.size()),
        new LockedRunConfig(
            projectId,
            runType,
            dataset.getId(),
            datasetVersion.getId(),
            datasetVersion.getSchemaId(),
            datasetVersion.getSchemaVersion(),
            limitedRowCount,
            judgeProviderId,
            judgeModel,
            lockedTargets,
            lockedEvaluators,
            masker.sanitizeMap(domainSnapshot).value(),
            masker.sanitizeMap(promptfooSnapshot).value()));
  }

  // -------------------------------------------------------------------------
  // Field source resolution
  // -------------------------------------------------------------------------

  private Set<String> availableFieldSources(UUID schemaId, List<LockedRunTargetConfig> targets) {
    var sources = new LinkedHashSet<String>();

    sources.add("response.statusCode");
    sources.add("latencyMs");

    schemaColumnRepository.findBySchemaIdOrderByOrderIndexAsc(schemaId).stream()
        .map(column -> "dataset:$dataset." + column.getName())
        .forEach(sources::add);

    targets.stream()
        .flatMap(target -> extractedFieldSources(target.config()).stream())
        .forEach(sources::add);

    return sources;
  }

  private List<String> extractedFieldSources(Map<String, Object> targetConfig) {
    if (!(targetConfig.get(KEY_RESPONSE_EXTRACTION) instanceof Map<?, ?> extraction)) {
      return List.of();
    }

    if (!(extraction.get(KEY_FIELDS) instanceof List<?> fields)) {
      return List.of();
    }

    return fields.stream()
        .map(field -> field instanceof Map<?, ?> map ? map.get(KEY_FIELD_NAME) : null)
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .map(String::trim)
        .filter(fieldName -> !fieldName.isBlank())
        .map(fieldName -> "extracted." + fieldName)
        .toList();
  }

  // -------------------------------------------------------------------------
  // Run type & row count
  // -------------------------------------------------------------------------

  private Inspection emptyInspection(
      UUID projectId,
      List<PreflightMessageResponse> blockers,
      List<PreflightMessageResponse> warnings) {
    return new Inspection(
        blockers,
        warnings,
        new RunPreflightResponse.Counts(0, 0, 0),
        new LockedRunConfig(
            projectId,
            TestRunType.SINGLE_TARGET,
            null,
            null,
            null,
            0,
            0,
            null,
            null,
            List.of(),
            List.of(),
            Map.of(),
            Map.of()));
  }

  private TestRunType defaultRunType(List<LockedRunTargetConfig> targets) {
    return targets.size() > 1 ? TestRunType.COMPARISON : TestRunType.SINGLE_TARGET;
  }

  private void validateRunType(
      TestRunType runType, int targetCount, List<PreflightMessageResponse> blockers) {
    if (targetCount < 1 || runType == null) {
      return;
    }

    if (runType == TestRunType.COMPARISON && targetCount < 2) {
      addBlocker(
          blockers,
          "RUN_TYPE_TARGET_COUNT_INVALID",
          "Comparison run requires at least two targets",
          null);
    }

    if (runType == TestRunType.SINGLE_TARGET && targetCount != 1) {
      addBlocker(
          blockers,
          "RUN_TYPE_TARGET_COUNT_INVALID",
          "Single target run requires exactly one target",
          null);
    }
  }

  private void validateMaxRows(
      CreateTestRunRequest request, List<PreflightMessageResponse> blockers) {
    var maxRows = request.options() == null ? null : request.options().maxRows();
    if (maxRows != null && maxRows < 1) {
      addBlocker(
          blockers, "RUN_MAX_ROWS_INVALID", "Max rows must be greater than or equal to 1", null);
    }
  }

  private int limitedRowCount(long rowCount, CreateTestRunRequest request) {
    if (rowCount < 1) {
      return 0;
    }

    var maxRows = request.options() == null ? null : request.options().maxRows();
    var effectiveRowCount = maxRows == null || maxRows < 1 ? rowCount : Math.min(rowCount, maxRows);

    return effectiveRowCount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) effectiveRowCount;
  }

  // -------------------------------------------------------------------------
  // Utilities
  // -------------------------------------------------------------------------

  private void addBlocker(
      List<PreflightMessageResponse> blockers, String code, String message, UUID resourceId) {
    blockers.add(
        new PreflightMessageResponse(
            code, message, resourceId == null ? null : resourceId.toString()));
  }

  private Map<String, Object> snapshot(Object... keyValues) {
    if (keyValues.length % 2 != 0) {
      throw new IllegalArgumentException("Snapshot key-value arguments must be even");
    }

    var result = new LinkedHashMap<String, Object>();
    for (int i = 0; i < keyValues.length; i += 2) {
      if (!(keyValues[i] instanceof String key)) {
        throw new IllegalArgumentException("Snapshot key must be a string");
      }
      result.put(key, keyValues[i + 1]);
    }
    return result;
  }

  public record Inspection(
      List<PreflightMessageResponse> blockers,
      List<PreflightMessageResponse> warnings,
      RunPreflightResponse.Counts counts,
      LockedRunConfig lockedConfig) {}
}
