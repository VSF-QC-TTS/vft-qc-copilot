package vn.vinfast.vfqc.api.testrun.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.vinfast.vfqc.api.common.error.ApiException;
import vn.vinfast.vfqc.api.common.masking.VfqcSensitiveDataMasker;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.provider.enums.ProviderStatus;
import vn.vinfast.vfqc.api.provider.service.LlmProviderLookupService;
import vn.vinfast.vfqc.api.target.enums.ChatbotTargetStatus;
import vn.vinfast.vfqc.api.target.repository.ChatbotTargetRepository;
import vn.vinfast.vfqc.api.testrun.dto.PreflightMessageResponse;
import vn.vinfast.vfqc.api.testrun.dto.RunTargetRequest;
import vn.vinfast.vfqc.api.testrun.enums.TestRunTargetType;
import vn.vinfast.vfqc.api.testrun.model.LockedRunConfig.LockedRunTargetConfig;

/**
 * Validates and locks test run targets during snapshot inspection.
 *
 * <p>Handles both CHATBOT_TARGET and LLM_BASELINE target types. Collects blockers into a shared
 * list instead of failing fast, enabling full preflight feedback to the user.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class TestRunTargetValidator {

  private static final String KEY_RESPONSE_EXTRACTION = "responseExtraction";
  private static final String KEY_SECRET_BINDINGS = "secretBindings";
  private static final String KEY_SECRET_ID = "secretId";
  private static final String KEY_RUNTIME_PROVIDER_REF_ID = "runtimeProviderRefId";
  private static final String KEY_RUNTIME_BINDINGS = "runtimeBindings";

  private final ChatbotTargetRepository targetRepository;
  private final LlmProviderLookupService providerLookupService;
  private final VfqcSensitiveDataMasker masker;

  List<LockedRunTargetConfig> lockTargets(
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

  // -------------------------------------------------------------------------
  // Runtime binding helpers
  // -------------------------------------------------------------------------

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

  // -------------------------------------------------------------------------
  // Shared utilities (duplicated from SnapshotServiceImpl to keep validators self-contained)
  // -------------------------------------------------------------------------

  private String displayName(String requestedName, String fallbackName) {
    return requestedName == null || requestedName.isBlank() ? fallbackName : requestedName.trim();
  }

  private String stringOrNull(Object value) {
    return value == null ? null : value.toString();
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
