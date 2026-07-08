package vn.vinfast.vfqc.api.testrun.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import vn.vinfast.vfqc.api.testrun.dto.RunEvaluatorVersionRequest;
import vn.vinfast.vfqc.api.testrun.dto.RunPreflightResponse;
import vn.vinfast.vfqc.api.testrun.dto.RunTargetRequest;
import vn.vinfast.vfqc.api.testrun.enums.TestRunTargetType;
import vn.vinfast.vfqc.api.testrun.enums.TestRunType;
import vn.vinfast.vfqc.api.testrun.model.LockedRunConfig;
import vn.vinfast.vfqc.api.testrun.model.LockedRunConfig.LockedEvaluatorConfig;
import vn.vinfast.vfqc.api.testrun.model.LockedRunConfig.LockedRunTargetConfig;
import vn.vinfast.vfqc.api.testrun.service.TestRunSnapshotService;

@Slf4j
@Service
@RequiredArgsConstructor
public class TestRunSnapshotServiceImpl implements TestRunSnapshotService {

  private static final String KEY_LLM_JUDGE = "llmJudge";
  private static final String KEY_DEFAULT_JUDGE_PROVIDER_ID = "defaultJudgeProviderId";
  private static final String KEY_DEFAULT_JUDGE_MODEL = "defaultJudgeModel";
  private static final String KEY_RESPONSE_EXTRACTION = "responseExtraction";
  private static final String KEY_FIELDS = "fields";
  private static final String KEY_FIELD_NAME = "fieldName";
  private static final String KEY_SECRET_BINDINGS = "secretBindings";
  private static final String KEY_SECRET_ID = "secretId";
  private static final String KEY_RUNTIME_PROVIDER_REF_ID = "runtimeProviderRefId";
  private static final String KEY_RUNTIME_BINDINGS = "runtimeBindings";

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

    var lockedTargets = lockTargets(user, projectId, targets, blockers);
    var availableFieldSources = availableFieldSources(datasetVersion.getSchemaId(), lockedTargets);
    var lockedEvaluators =
        lockEvaluators(user, projectId, evaluators, availableFieldSources, blockers);

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
            "evaluators", lockedEvaluators.stream().map(LockedEvaluatorConfig::config).toList());

    var promptfooSnapshot =
        snapshot(
            "version", "phase8-mvp",
            "datasetVersionId", datasetVersion.getId().toString(),
            "targetCount", lockedTargets.size(),
            "evaluatorCount", lockedEvaluators.size());

    var judgeProviderId = firstJudgeProviderId(lockedEvaluators);
    var judgeModel = firstJudgeModel(lockedEvaluators);

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

  private List<LockedRunTargetConfig> lockTargets(
      CurrentUser user,
      UUID projectId,
      List<RunTargetRequest> targets,
      List<PreflightMessageResponse> blockers) {
    var locked = new ArrayList<LockedRunTargetConfig>();

    for (var i = 0; i < targets.size(); i++) {
      var request = targets.get(i);
      var orderIndex = i + 1;

      if (request == null) {
        addBlocker(blockers, "TARGET_REQUEST_MISSING", "Target request is required", null);
        continue;
      }

      if (request.targetType() == null) {
        addBlocker(blockers, "TARGET_TYPE_MISSING", "Target type is required", null);
        continue;
      }

      if (request.targetType() == TestRunTargetType.CHATBOT_TARGET) {
        lockChatbotTarget(projectId, request, orderIndex, locked, blockers);
      } else if (request.targetType() == TestRunTargetType.LLM_BASELINE) {
        lockBaselineTarget(user, request, orderIndex, locked, blockers);
      } else {
        addBlocker(blockers, "TARGET_TYPE_UNSUPPORTED", "Target type is not supported", null);
      }
    }

    if (locked.isEmpty()) {
      addBlocker(blockers, "TARGET_MISSING", "At least one runnable target is required", null);
    }

    return locked;
  }

  private void lockChatbotTarget(
      UUID projectId,
      RunTargetRequest request,
      int orderIndex,
      List<LockedRunTargetConfig> locked,
      List<PreflightMessageResponse> blockers) {
    var blockerCountBefore = blockers.size();

    if (request.chatbotTargetId() == null) {
      addBlocker(blockers, "TARGET_ID_MISSING", "Chatbot target id is required", null);
      return;
    }

    var target =
        targetRepository.findByIdAndDeletedAtIsNull(request.chatbotTargetId()).orElse(null);
    if (target == null || !projectId.equals(target.getProjectId())) {
      addBlocker(
          blockers,
          "TARGET_NOT_FOUND",
          "Chatbot target is not available",
          request.chatbotTargetId());
      return;
    }

    if (target.getStatus() != ChatbotTargetStatus.ACTIVE) {
      addBlocker(blockers, "TARGET_NOT_READY", "Chatbot target must be ACTIVE", target.getId());
    }

    if (target.getResponseExtractionConfigJson() == null
        || target.getResponseExtractionConfigJson().isEmpty()) {
      addBlocker(
          blockers,
          "TARGET_EXTRACTION_MISSING",
          "Chatbot target response extraction is required",
          target.getId());
    }

    if (blockers.size() > blockerCountBefore) {
      return;
    }

    var config =
        snapshot(
            "type",
            "CHATBOT_TARGET",
            "chatbotTargetId",
            target.getId().toString(),
            "name",
            target.getName(),
            "method",
            target.getMethod(),
            "url",
            target.getUrl(),
            "headers",
            safeMap(target.getHeadersJson()),
            "queryParams",
            safeMap(target.getQueryParamsJson()),
            "body",
            safeMap(target.getBodyJson()),
            "auth",
            safeMap(target.getAuthConfigJson()),
            "datasetMapping",
            safeMap(target.getDatasetMappingJson()),
            KEY_RESPONSE_EXTRACTION,
            safeMap(target.getResponseExtractionConfigJson()));

    var sanitizedConfig = new LinkedHashMap<>(masker.sanitizeMap(config).value());
    sanitizedConfig.put(KEY_RUNTIME_BINDINGS, runtimeBindings(target.getAuthConfigJson()));

    locked.add(
        new LockedRunTargetConfig(
            TestRunTargetType.CHATBOT_TARGET,
            target.getId(),
            null,
            null,
            displayName(request.name(), target.getName()),
            orderIndex,
            sanitizedConfig));
  }

  private void lockBaselineTarget(
      CurrentUser user,
      RunTargetRequest request,
      int orderIndex,
      List<LockedRunTargetConfig> locked,
      List<PreflightMessageResponse> blockers) {
    var blockerCountBefore = blockers.size();

    if (request.providerId() == null) {
      addBlocker(blockers, "BASELINE_PROVIDER_MISSING", "Baseline provider id is required", null);
      return;
    }

    try {
      var provider = providerLookupService.requireVisible(user, request.providerId());

      if (provider.getStatus() != ProviderStatus.ACTIVE) {
        addBlocker(
            blockers,
            "BASELINE_PROVIDER_INVALID",
            "Baseline provider must be ACTIVE",
            provider.getId());
      }

      var model =
          request.model() == null || request.model().isBlank()
              ? provider.getDefaultModel()
              : request.model().trim();

      if (model == null || model.isBlank()) {
        addBlocker(
            blockers, "BASELINE_MODEL_MISSING", "Baseline model is required", provider.getId());
      }

      if (blockers.size() > blockerCountBefore) {
        return;
      }

      var config =
          snapshot(
              "type", "LLM_BASELINE",
              "providerId", provider.getId().toString(),
              "providerType", provider.getProviderType().name(),
              "model", model,
              "baseUrl", provider.getBaseUrl(),
              "config", safeMap(provider.getConfigJson()));

      var sanitizedConfig = new LinkedHashMap<>(masker.sanitizeMap(config).value());
      if (provider.getSecretId() != null) {
        sanitizedConfig.put(KEY_RUNTIME_PROVIDER_REF_ID, provider.getSecretId().toString());
      }

      locked.add(
          new LockedRunTargetConfig(
              TestRunTargetType.LLM_BASELINE,
              null,
              provider.getId(),
              model,
              displayName(request.name(), provider.getName()),
              orderIndex,
              sanitizedConfig));
    } catch (ApiException exception) {
      log.debug(
          "Baseline provider lookup failed during test run snapshot inspection. providerId={}, exceptionType={}",
          request.providerId(),
          exception.getClass().getSimpleName());
      addBlocker(
          blockers,
          "BASELINE_PROVIDER_INVALID",
          "Baseline provider is not available",
          request.providerId());
    }
  }

  private List<LockedEvaluatorConfig> lockEvaluators(
      CurrentUser user,
      UUID projectId,
      List<RunEvaluatorVersionRequest> evaluators,
      Set<String> availableFieldSources,
      List<PreflightMessageResponse> blockers) {
    var locked = new ArrayList<LockedEvaluatorConfig>();

    for (var i = 0; i < evaluators.size(); i++) {
      var request = evaluators.get(i);
      var blockerCountBefore = blockers.size();

      if (request == null) {
        addBlocker(
            blockers, "EVALUATOR_REQUEST_MISSING", "Evaluator version request is required", null);
        continue;
      }

      if (request.evaluatorVersionId() == null) {
        addBlocker(
            blockers, "EVALUATOR_VERSION_ID_MISSING", "Evaluator version id is required", null);
        continue;
      }

      var version = evaluatorVersionRepository.findById(request.evaluatorVersionId()).orElse(null);
      if (version == null) {
        addBlocker(
            blockers,
            "EVALUATOR_VERSION_NOT_FOUND",
            "Evaluator version is not available",
            request.evaluatorVersionId());
        continue;
      }

      var evaluator =
          evaluatorRepository.findByIdAndDeletedAtIsNull(version.getEvaluatorId()).orElse(null);
      if (evaluator == null || !projectId.equals(evaluator.getProjectId())) {
        addBlocker(
            blockers,
            "EVALUATOR_NOT_FOUND",
            "Evaluator is not available",
            version.getEvaluatorId());
        continue;
      }

      var rules =
          evaluatorRuleRepository.findByEvaluatorVersionIdOrderByOrderIndexAsc(version.getId());

      validateEvaluatorRules(rules, availableFieldSources, blockers, version.getId());

      var runtimeProviderRefId =
          resolveRuntimeJudgeProviderSecretId(
              user, version.getConfigJson(), blockers, version.getId());

      if (blockers.size() > blockerCountBefore) {
        continue;
      }

      var sanitizedConfig =
          new LinkedHashMap<>(masker.sanitizeMap(safeMap(version.getConfigJson())).value());

      if (runtimeProviderRefId != null) {
        sanitizedConfig.put(KEY_RUNTIME_PROVIDER_REF_ID, runtimeProviderRefId.toString());
      }

      locked.add(
          new LockedEvaluatorConfig(
              evaluator.getId(),
              version.getId(),
              evaluator.getType(),
              version.getVersion(),
              i + 1,
              sanitizedConfig,
              rules.stream().map(this::ruleSnapshot).toList()));
    }

    if (locked.isEmpty()) {
      addBlocker(blockers, "EVALUATOR_MISSING", "At least one evaluator version is required", null);
    }

    return locked;
  }

  private void validateEvaluatorRules(
      List<EvaluatorRule> rules,
      Set<String> availableFieldSources,
      List<PreflightMessageResponse> blockers,
      UUID evaluatorVersionId) {
    for (var rule : rules) {
      var fieldSource = rule.getFieldSource();

      if (fieldSource == null || fieldSource.isBlank()) {
        addBlocker(
            blockers,
            "EVALUATOR_FIELD_SOURCE_MISSING",
            "Evaluator field source is required",
            evaluatorVersionId);
        continue;
      }

      if (!availableFieldSources.isEmpty()
          && !availableFieldSources.contains(fieldSource)
          && !fieldSource.startsWith("response.body:")
          && !fieldSource.startsWith("response.headers:")) {
        addBlocker(
            blockers,
            "EVALUATOR_FIELD_SOURCE_INVALID",
            "Evaluator field source is not available",
            evaluatorVersionId);
      }
    }
  }

  private UUID resolveRuntimeJudgeProviderSecretId(
      CurrentUser user,
      Map<String, Object> config,
      List<PreflightMessageResponse> blockers,
      UUID evaluatorVersionId) {
    var providerIdValue = llmJudgeValue(config, KEY_DEFAULT_JUDGE_PROVIDER_ID);
    if (providerIdValue == null) {
      return null;
    }

    var providerId = parseUuidOrNull(providerIdValue);
    if (providerId == null) {
      addBlocker(
          blockers,
          "EVALUATOR_PROVIDER_INVALID",
          "Judge provider id is invalid",
          evaluatorVersionId);
      return null;
    }

    try {
      var provider = providerLookupService.requireVisible(user, providerId);
      if (provider.getStatus() != ProviderStatus.ACTIVE) {
        addBlocker(
            blockers, "EVALUATOR_PROVIDER_INVALID", "Judge provider must be ACTIVE", providerId);
        return null;
      }
      return provider.getSecretId();
    } catch (ApiException exception) {
      log.debug(
          "Judge provider lookup failed during test run snapshot inspection. evaluatorVersionId={}, providerId={}, exceptionType={}",
          evaluatorVersionId,
          providerId,
          exception.getClass().getSimpleName());
      addBlocker(
          blockers,
          "EVALUATOR_PROVIDER_INVALID",
          "Judge provider is not available",
          evaluatorVersionId);
      return null;
    }
  }

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

  private Map<String, Object> ruleSnapshot(EvaluatorRule rule) {
    return masker
        .sanitizeMap(
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

  private List<Map<String, Object>> runtimeBindings(Map<String, Object> authConfig) {
    if (authConfig == null || !(authConfig.get(KEY_SECRET_BINDINGS) instanceof List<?> bindings)) {
      return List.of();
    }

    return bindings.stream()
        .map(binding -> binding instanceof Map<?, ?> map ? runtimeBinding(map) : null)
        .filter(Objects::nonNull)
        .toList();
  }

  private Map<String, Object> runtimeBinding(Map<?, ?> binding) {
    var secretId = binding.get(KEY_SECRET_ID);
    if (secretId == null || secretId.toString().isBlank()) {
      return null;
    }

    return snapshot(
        "location", stringOrNull(binding.get("location")),
        "keyOrPath", stringOrNull(binding.get("keyOrPath")),
        "refId", secretId.toString());
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

  private UUID firstJudgeProviderId(List<LockedEvaluatorConfig> evaluators) {
    for (var evaluator : evaluators) {
      var providerId =
          parseUuidOrNull(llmJudgeValue(evaluator.config(), KEY_DEFAULT_JUDGE_PROVIDER_ID));
      if (providerId != null) {
        return providerId;
      }
    }
    return null;
  }

  private String firstJudgeModel(List<LockedEvaluatorConfig> evaluators) {
    for (var evaluator : evaluators) {
      var model = llmJudgeValue(evaluator.config(), KEY_DEFAULT_JUDGE_MODEL);
      if (model != null && !model.toString().isBlank()) {
        return model.toString().trim();
      }
    }
    return null;
  }

  private Object llmJudgeValue(Map<String, Object> config, String key) {
    if (config == null || !(config.get(KEY_LLM_JUDGE) instanceof Map<?, ?> llmJudge)) {
      return null;
    }
    return llmJudge.get(key);
  }

  private UUID parseUuidOrNull(Object value) {
    if (value instanceof UUID uuid) {
      return uuid;
    }

    if (value == null) {
      return null;
    }

    var text = value.toString().trim();
    if (text.isBlank()) {
      return null;
    }

    try {
      return UUID.fromString(text);
    } catch (IllegalArgumentException exception) {
      return null;
    }
  }

  private String displayName(String requestedName, String fallbackName) {
    return requestedName == null || requestedName.isBlank() ? fallbackName : requestedName.trim();
  }

  private String stringOrNull(Object value) {
    return value == null ? null : value.toString();
  }

  private void addBlocker(
      List<PreflightMessageResponse> blockers, String code, String message, UUID resourceId) {
    blockers.add(blocker(code, message, resourceId));
  }

  private PreflightMessageResponse blocker(String code, String message, UUID resourceId) {
    return new PreflightMessageResponse(
        code, message, resourceId == null ? null : resourceId.toString());
  }

  private Map<String, Object> safeMap(Map<String, Object> value) {
    return value == null ? Map.of() : value;
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
