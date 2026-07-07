package vn.vinfast.vfqc.api.testrun.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vinfast.vfqc.api.common.error.ApiException;
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
import vn.vinfast.vfqc.api.evaluator.entity.EvaluatorRule;
import vn.vinfast.vfqc.api.evaluator.repository.EvaluatorRepository;
import vn.vinfast.vfqc.api.evaluator.repository.EvaluatorRuleRepository;
import vn.vinfast.vfqc.api.evaluator.repository.EvaluatorVersionRepository;
import vn.vinfast.vfqc.api.provider.enums.ProviderStatus;
import vn.vinfast.vfqc.api.provider.service.LlmProviderLookupService;
import vn.vinfast.vfqc.api.target.enums.ChatbotTargetStatus;
import vn.vinfast.vfqc.api.target.repository.ChatbotTargetRepository;
import vn.vinfast.vfqc.api.testrun.dto.CreateTestRunRequest;
import vn.vinfast.vfqc.api.testrun.dto.PreflightMessageResponse;
import vn.vinfast.vfqc.api.testrun.dto.RunPreflightResponse;
import vn.vinfast.vfqc.api.testrun.dto.RunTargetRequest;
import vn.vinfast.vfqc.api.testrun.enums.TestRunTargetType;
import vn.vinfast.vfqc.api.testrun.enums.TestRunType;
import vn.vinfast.vfqc.api.testrun.model.LockedRunConfig;
import vn.vinfast.vfqc.api.testrun.model.LockedRunConfig.LockedEvaluatorConfig;
import vn.vinfast.vfqc.api.testrun.model.LockedRunConfig.LockedRunTargetConfig;
import vn.vinfast.vfqc.api.testrun.service.TestRunSnapshotService;

@Service
@RequiredArgsConstructor
public class TestRunSnapshotServiceImpl implements TestRunSnapshotService {

  private final ProjectPermissionService projectPermissionService;
  private final DatasetRepository datasetRepository;
  private final DatasetVersionRepository datasetVersionRepository;
  private final DatasetRowRepository datasetRowRepository;
  private final DatasetSchemaColumnRepository schemaColumnRepository;
  private final ChatbotTargetRepository targetRepository;
  private final EvaluatorRepository evaluatorRepository;
  private final EvaluatorVersionRepository evaluatorVersionRepository;
  private final EvaluatorRuleRepository evaluatorRuleRepository;
  private final LlmProviderLookupService providerLookupService;
  private final VfqcSensitiveDataMasker masker;

  @Override
  @Transactional(readOnly = true)
  public LockedRunConfig lock(CurrentUser user, UUID projectId, CreateTestRunRequest request) {
    var inspection = inspect(user, projectId, request);
    if (!inspection.blockers().isEmpty()) {
      throw ErrorCode.INVALID_STATE.throwIt(inspection.blockers().getFirst().message());
    }
    return inspection.lockedConfig();
  }

  @Transactional(readOnly = true)
  public Inspection inspect(CurrentUser user, UUID projectId, CreateTestRunRequest request) {
    projectPermissionService.requireProject(user, projectId, PermissionAction.VIEW);
    var blockers = new ArrayList<PreflightMessageResponse>();
    var warnings = new ArrayList<PreflightMessageResponse>();
    var targets = request.targets() == null ? List.<RunTargetRequest>of() : request.targets();
    var evaluators =
        request.evaluators() == null ? List.<vn.vinfast.vfqc.api.testrun.dto.RunEvaluatorVersionRequest>of()
            : request.evaluators();

    var dataset = datasetRepository.findByIdAndDeletedAtIsNull(request.datasetId()).orElse(null);
    if (dataset == null || !projectId.equals(dataset.getProjectId())) {
      blockers.add(blocker("DATASET_NOT_FOUND", "Dataset is not available for this project", request.datasetId()));
      return emptyInspection(blockers, warnings);
    }
    if (dataset.getCurrentVersionId() == null) {
      blockers.add(blocker("DATASET_ACTIVE_VERSION_MISSING", "Dataset has no active version", dataset.getId()));
      return emptyInspection(blockers, warnings);
    }
    var datasetVersion = datasetVersionRepository.findById(dataset.getCurrentVersionId()).orElse(null);
    if (datasetVersion == null || datasetVersion.getStatus() != DatasetVersionStatus.ACTIVE) {
      blockers.add(blocker("DATASET_ACTIVE_VERSION_MISSING", "Dataset has no active version", dataset.getId()));
      return emptyInspection(blockers, warnings);
    }
    var rowCount = datasetRowRepository.countByDatasetVersionId(datasetVersion.getId());
    if (rowCount < 1) {
      blockers.add(blocker("DATASET_ROWS_MISSING", "Dataset active version has no rows", datasetVersion.getId()));
    }

    var lockedTargets = lockTargets(user, projectId, targets, blockers);
    var availableFieldSources = availableFieldSources(datasetVersion.getSchemaId(), lockedTargets);
    var lockedEvaluators = lockEvaluators(user, projectId, evaluators, availableFieldSources, blockers);
    var runType = request.runType() == null ? defaultRunType(lockedTargets) : request.runType();
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
            "evaluators", lockedEvaluators.stream().map(LockedEvaluatorConfig::config).toList());
    var promptfooSnapshot =
        snapshot(
            "version", "phase8-mvp",
            "datasetVersionId", datasetVersion.getId().toString(),
            "targetCount", lockedTargets.size(),
            "evaluatorCount", lockedEvaluators.size());
    var judgeProviderId = firstJudgeProviderId(lockedEvaluators);
    var judgeModel = firstJudgeModel(lockedEvaluators);
    return new Inspection(
        blockers,
        warnings,
        new RunPreflightResponse.Counts(limitedRowCount, lockedTargets.size(), lockedEvaluators.size()),
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

  private Inspection emptyInspection(
      List<PreflightMessageResponse> blockers, List<PreflightMessageResponse> warnings) {
    return new Inspection(
        blockers,
        warnings,
        new RunPreflightResponse.Counts(0, 0, 0),
        new LockedRunConfig(
            null, TestRunType.SINGLE_TARGET, null, null, null, 0, 0, null, null, List.of(), List.of(), Map.of(), Map.of()));
  }

  private List<LockedRunTargetConfig> lockTargets(
      CurrentUser user,
      UUID projectId,
      List<RunTargetRequest> targets,
      List<PreflightMessageResponse> blockers) {
    var locked = new ArrayList<LockedRunTargetConfig>();
    for (var i = 0; i < targets.size(); i++) {
      var request = targets.get(i);
      var orderIndex = i + 1;
      if (request.targetType() == TestRunTargetType.CHATBOT_TARGET) {
        lockChatbotTarget(projectId, request, orderIndex, locked, blockers);
      } else if (request.targetType() == TestRunTargetType.LLM_BASELINE) {
        lockBaselineTarget(user, request, orderIndex, locked, blockers);
      }
    }
    if (locked.isEmpty()) {
      blockers.add(blocker("TARGET_MISSING", "At least one runnable target is required", null));
    }
    return locked;
  }

  private void lockChatbotTarget(
      UUID projectId,
      RunTargetRequest request,
      int orderIndex,
      List<LockedRunTargetConfig> locked,
      List<PreflightMessageResponse> blockers) {
    if (request.chatbotTargetId() == null) {
      blockers.add(blocker("TARGET_ID_MISSING", "Chatbot target id is required", null));
      return;
    }
    var target = targetRepository.findByIdAndDeletedAtIsNull(request.chatbotTargetId()).orElse(null);
    if (target == null || !projectId.equals(target.getProjectId())) {
      blockers.add(blocker("TARGET_NOT_FOUND", "Chatbot target is not available", request.chatbotTargetId()));
      return;
    }
    if (target.getStatus() != ChatbotTargetStatus.ACTIVE) {
      blockers.add(blocker("TARGET_NOT_READY", "Chatbot target must be ACTIVE", target.getId()));
    }
    if (target.getResponseExtractionConfigJson() == null || target.getResponseExtractionConfigJson().isEmpty()) {
      blockers.add(blocker("TARGET_EXTRACTION_MISSING", "Chatbot target response extraction is required", target.getId()));
    }
    var config =
        snapshot(
            "type", "CHATBOT_TARGET",
            "chatbotTargetId", target.getId().toString(),
            "name", target.getName(),
            "method", target.getMethod(),
            "url", target.getUrl(),
            "headers", safeMap(target.getHeadersJson()),
            "queryParams", safeMap(target.getQueryParamsJson()),
            "body", safeMap(target.getBodyJson()),
            "auth", safeMap(target.getAuthConfigJson()),
            "datasetMapping", safeMap(target.getDatasetMappingJson()),
            "responseExtraction", safeMap(target.getResponseExtractionConfigJson()));
    locked.add(
        new LockedRunTargetConfig(
            TestRunTargetType.CHATBOT_TARGET,
            target.getId(),
            null,
            null,
            request.name() == null || request.name().isBlank() ? target.getName() : request.name(),
            orderIndex,
            masker.sanitizeMap(config).value()));
  }

  private void lockBaselineTarget(
      CurrentUser user,
      RunTargetRequest request,
      int orderIndex,
      List<LockedRunTargetConfig> locked,
      List<PreflightMessageResponse> blockers) {
    if (request.providerId() == null) {
      blockers.add(blocker("BASELINE_PROVIDER_MISSING", "Baseline provider id is required", null));
      return;
    }
    try {
      var provider = providerLookupService.requireVisible(user, request.providerId());
      if (provider.getStatus() != ProviderStatus.ACTIVE) {
        blockers.add(blocker("BASELINE_PROVIDER_INVALID", "Baseline provider must be ACTIVE", provider.getId()));
      }
      var model = request.model() == null || request.model().isBlank() ? provider.getDefaultModel() : request.model();
      if (model == null || model.isBlank()) {
        blockers.add(blocker("BASELINE_MODEL_MISSING", "Baseline model is required", provider.getId()));
      }
      var config =
          snapshot(
              "type", "LLM_BASELINE",
              "providerId", provider.getId().toString(),
              "providerType", provider.getProviderType().name(),
              "model", model,
              "baseUrl", provider.getBaseUrl(),
              "config", safeMap(provider.getConfigJson()));
      locked.add(
          new LockedRunTargetConfig(
              TestRunTargetType.LLM_BASELINE,
              null,
              provider.getId(),
              model,
              request.name() == null || request.name().isBlank() ? provider.getName() : request.name(),
              orderIndex,
              masker.sanitizeMap(config).value()));
    } catch (ApiException exception) {
      blockers.add(blocker("BASELINE_PROVIDER_INVALID", "Baseline provider is not available", request.providerId()));
    }
  }

  private List<LockedEvaluatorConfig> lockEvaluators(
      CurrentUser user,
      UUID projectId,
      List<vn.vinfast.vfqc.api.testrun.dto.RunEvaluatorVersionRequest> evaluators,
      List<String> availableFieldSources,
      List<PreflightMessageResponse> blockers) {
    var locked = new ArrayList<LockedEvaluatorConfig>();
    for (var i = 0; i < evaluators.size(); i++) {
      var request = evaluators.get(i);
      var version = evaluatorVersionRepository.findById(request.evaluatorVersionId()).orElse(null);
      if (version == null) {
        blockers.add(blocker("EVALUATOR_VERSION_NOT_FOUND", "Evaluator version is not available", request.evaluatorVersionId()));
        continue;
      }
      var evaluator = evaluatorRepository.findByIdAndDeletedAtIsNull(version.getEvaluatorId()).orElse(null);
      if (evaluator == null || !projectId.equals(evaluator.getProjectId())) {
        blockers.add(blocker("EVALUATOR_NOT_FOUND", "Evaluator is not available", version.getEvaluatorId()));
        continue;
      }
      var rules = evaluatorRuleRepository.findByEvaluatorVersionIdOrderByOrderIndexAsc(version.getId());
      validateEvaluatorSnapshot(user, version.getConfigJson(), rules, availableFieldSources, blockers, version.getId());
      locked.add(
          new LockedEvaluatorConfig(
              evaluator.getId(),
              version.getId(),
              evaluator.getType(),
              version.getVersion(),
              i + 1,
              masker.sanitizeMap(safeMap(version.getConfigJson())).value(),
              rules.stream().map(this::ruleSnapshot).toList()));
    }
    if (locked.isEmpty()) {
      blockers.add(blocker("EVALUATOR_MISSING", "At least one evaluator version is required", null));
    }
    return locked;
  }

  @SuppressWarnings("unchecked")
  private void validateEvaluatorSnapshot(
      CurrentUser user,
      Map<String, Object> config,
      List<EvaluatorRule> rules,
      List<String> availableFieldSources,
      List<PreflightMessageResponse> blockers,
      UUID evaluatorVersionId) {
    for (var rule : rules) {
      if (!availableFieldSources.isEmpty()
          && !availableFieldSources.contains(rule.getFieldSource())
          && !rule.getFieldSource().startsWith("response.body:")
          && !rule.getFieldSource().startsWith("response.headers:")) {
        blockers.add(blocker("EVALUATOR_FIELD_SOURCE_INVALID", "Evaluator field source is not available", evaluatorVersionId));
      }
    }
    var llmJudge =
        config == null ? null : config.get("llmJudge") instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    if (llmJudge != null && llmJudge.get("defaultJudgeProviderId") != null) {
      try {
        var providerId = UUID.fromString(llmJudge.get("defaultJudgeProviderId").toString());
        var provider = providerLookupService.requireVisible(user, providerId);
        if (provider.getStatus() != ProviderStatus.ACTIVE) {
          blockers.add(blocker("EVALUATOR_PROVIDER_INVALID", "Judge provider must be ACTIVE", providerId));
        }
      } catch (IllegalArgumentException | ApiException exception) {
        blockers.add(blocker("EVALUATOR_PROVIDER_INVALID", "Judge provider is not available", evaluatorVersionId));
      }
    }
  }

  private List<String> availableFieldSources(UUID schemaId, List<LockedRunTargetConfig> targets) {
    var sources = new ArrayList<String>();
    sources.add("response.statusCode");
    sources.add("latencyMs");
    schemaColumnRepository.findBySchemaIdOrderByOrderIndexAsc(schemaId).stream()
        .map(column -> "dataset:$dataset." + column.getName())
        .forEach(sources::add);
    targets.stream().flatMap(target -> extractedFieldSources(target.config()).stream()).forEach(sources::add);
    return sources;
  }

  @SuppressWarnings("unchecked")
  private List<String> extractedFieldSources(Map<String, Object> targetConfig) {
    if (!(targetConfig.get("responseExtraction") instanceof Map<?, ?> extraction)) {
      return List.of();
    }
    if (!(extraction.get("fields") instanceof List<?> fields)) {
      return List.of();
    }
    return fields.stream()
        .filter(Map.class::isInstance)
        .map(field -> ((Map<String, Object>) field).get("fieldName"))
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .map(fieldName -> "extracted." + fieldName)
        .toList();
  }

  private Map<String, Object> ruleSnapshot(EvaluatorRule rule) {
    return masker.sanitizeMap(
            snapshot(
                "fieldSource", rule.getFieldSource(),
                "operator", rule.getOperator().name(),
                "expectedValue", safeMap(rule.getExpectedValueJson()),
                "weight", rule.getWeight(),
                "required", rule.isRequired(),
                "orderIndex", rule.getOrderIndex(),
                "config", safeMap(rule.getConfigJson())))
        .value();
  }

  private TestRunType defaultRunType(List<LockedRunTargetConfig> targets) {
    return targets.size() > 1 ? TestRunType.COMPARISON : TestRunType.SINGLE_TARGET;
  }

  private int limitedRowCount(long rowCount, CreateTestRunRequest request) {
    var maxRows = request.options() == null ? null : request.options().maxRows();
    return (int) Math.min(rowCount, maxRows == null ? rowCount : maxRows);
  }

  @SuppressWarnings("unchecked")
  private UUID firstJudgeProviderId(List<LockedEvaluatorConfig> evaluators) {
    for (var evaluator : evaluators) {
      var llmJudge = evaluator.config().get("llmJudge");
      if (llmJudge instanceof Map<?, ?> map && map.get("defaultJudgeProviderId") != null) {
        return UUID.fromString(map.get("defaultJudgeProviderId").toString());
      }
    }
    return null;
  }

  private String firstJudgeModel(List<LockedEvaluatorConfig> evaluators) {
    for (var evaluator : evaluators) {
      var llmJudge = evaluator.config().get("llmJudge");
      if (llmJudge instanceof Map<?, ?> map && map.get("defaultJudgeModel") != null) {
        return map.get("defaultJudgeModel").toString();
      }
    }
    return null;
  }

  private PreflightMessageResponse blocker(String code, String message, UUID resourceId) {
    return new PreflightMessageResponse(code, message, resourceId == null ? null : resourceId.toString());
  }

  private Map<String, Object> safeMap(Map<String, Object> value) {
    return value == null ? Map.of() : value;
  }

  private Map<String, Object> snapshot(Object... keyValues) {
    var result = new LinkedHashMap<String, Object>();
    for (var i = 0; i < keyValues.length; i += 2) {
      result.put((String) keyValues[i], keyValues[i + 1]);
    }
    return result;
  }

  public record Inspection(
      List<PreflightMessageResponse> blockers,
      List<PreflightMessageResponse> warnings,
      RunPreflightResponse.Counts counts,
      LockedRunConfig lockedConfig) {}
}
