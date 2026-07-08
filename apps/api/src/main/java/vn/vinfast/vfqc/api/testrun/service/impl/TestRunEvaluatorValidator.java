package vn.vinfast.vfqc.api.testrun.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.vinfast.vfqc.api.common.error.ApiException;
import vn.vinfast.vfqc.api.common.masking.VfqcSensitiveDataMasker;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.evaluator.entity.EvaluatorRule;
import vn.vinfast.vfqc.api.evaluator.repository.EvaluatorRepository;
import vn.vinfast.vfqc.api.evaluator.repository.EvaluatorRuleRepository;
import vn.vinfast.vfqc.api.evaluator.repository.EvaluatorVersionRepository;
import vn.vinfast.vfqc.api.provider.enums.ProviderStatus;
import vn.vinfast.vfqc.api.provider.service.LlmProviderLookupService;
import vn.vinfast.vfqc.api.testrun.dto.PreflightMessageResponse;
import vn.vinfast.vfqc.api.testrun.dto.RunEvaluatorVersionRequest;
import vn.vinfast.vfqc.api.testrun.model.LockedRunConfig.LockedEvaluatorConfig;

/**
 * Validates and locks evaluator versions during snapshot inspection.
 *
 * <p>Resolves evaluator rules, LLM judge provider references, and collects blockers into a shared
 * list for preflight feedback.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class TestRunEvaluatorValidator {

  private static final String KEY_LLM_JUDGE = "llmJudge";
  private static final String KEY_DEFAULT_JUDGE_PROVIDER_ID = "defaultJudgeProviderId";
  private static final String KEY_DEFAULT_JUDGE_MODEL = "defaultJudgeModel";
  private static final String KEY_RUNTIME_PROVIDER_REF_ID = "runtimeProviderRefId";

  private final EvaluatorRepository evaluatorRepository;
  private final EvaluatorVersionRepository evaluatorVersionRepository;
  private final EvaluatorRuleRepository evaluatorRuleRepository;
  private final LlmProviderLookupService providerLookupService;
  private final VfqcSensitiveDataMasker masker;

  List<LockedEvaluatorConfig> lockEvaluators(
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

  UUID firstJudgeProviderId(List<LockedEvaluatorConfig> evaluators) {
    for (var evaluator : evaluators) {
      var providerId =
          parseUuidOrNull(llmJudgeValue(evaluator.config(), KEY_DEFAULT_JUDGE_PROVIDER_ID));
      if (providerId != null) {
        return providerId;
      }
    }
    return null;
  }

  String firstJudgeModel(List<LockedEvaluatorConfig> evaluators) {
    for (var evaluator : evaluators) {
      var model = llmJudgeValue(evaluator.config(), KEY_DEFAULT_JUDGE_MODEL);
      if (model != null && !model.toString().isBlank()) {
        return model.toString().trim();
      }
    }
    return null;
  }

  // -------------------------------------------------------------------------
  // Rule validation
  // -------------------------------------------------------------------------

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

  // -------------------------------------------------------------------------
  // Judge provider resolution
  // -------------------------------------------------------------------------

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

  // -------------------------------------------------------------------------
  // LLM judge config helpers
  // -------------------------------------------------------------------------

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

  // -------------------------------------------------------------------------
  // Shared utilities
  // -------------------------------------------------------------------------

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

  private void addBlocker(
      List<PreflightMessageResponse> blockers, String code, String message, UUID resourceId) {
    blockers.add(
        new PreflightMessageResponse(
            code, message, resourceId == null ? null : resourceId.toString()));
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
}
