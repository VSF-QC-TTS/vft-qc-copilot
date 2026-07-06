package vn.vinfast.vfqc.api.target.validator;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.vinfast.vfqc.api.common.error.ErrorCode;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.dataset.repository.DatasetSchemaColumnRepository;
import vn.vinfast.vfqc.api.dataset.repository.DatasetSchemaRepository;
import vn.vinfast.vfqc.api.project.entity.Project;
import vn.vinfast.vfqc.api.secret.service.SecretLookupService;
import vn.vinfast.vfqc.api.target.dto.DatasetMappingDto;
import vn.vinfast.vfqc.api.target.dto.ResponseExtractionConfigDto;
import vn.vinfast.vfqc.api.target.dto.SecretBindingDto;
import vn.vinfast.vfqc.api.target.dto.TargetRequestConfigDto;
import vn.vinfast.vfqc.api.target.enums.ChatbotTargetStatus;
import vn.vinfast.vfqc.api.target.enums.SecretBindingLocation;

@Component
@RequiredArgsConstructor
public class ChatbotTargetConfigValidator {

  private static final Set<String> SENSITIVE_KEYS =
      Set.of("authorization", "cookie", "set-cookie", "x-api-key", "api-key");
  private static final Pattern SENSITIVE_JSON_FIELD =
      Pattern.compile(
          "\"([^\"]*(?:password|token|api[_-]?key|secret|authorization|cookie)[^\"]*)\"\\s*:",
          Pattern.CASE_INSENSITIVE);
  private final TargetUrlValidator targetUrlValidator;
  private final SecretLookupService secretLookupService;
  private final DatasetSchemaRepository schemaRepository;
  private final DatasetSchemaColumnRepository columnRepository;

  public void validateRequestConfig(
      CurrentUser user, Project project, TargetRequestConfigDto requestConfig) {
    if (requestConfig == null) {
      throw ErrorCode.VALIDATION_ERROR.throwIt("Target request config is required");
    }
    if (requestConfig.method() == null || requestConfig.method().isBlank()) {
      throw ErrorCode.VALIDATION_ERROR.throwIt("Target method is required");
    }
    if (requestConfig.url() == null || requestConfig.url().isBlank()) {
      throw ErrorCode.VALIDATION_ERROR.throwIt("Target URL is required");
    }
    targetUrlValidator.validateForOutboundRequest(requestConfig.url());
    validateSecretBindings(user, project, requestConfig.safeSecretBindings());
    validateSensitiveLocations(requestConfig);
    validateDatasetMappings(project, requestConfig.safeDatasetMappings());
  }

  public void validateExtractionConfig(ResponseExtractionConfigDto config) {
    if (config == null || config.safeFields().isEmpty()) {
      throw ErrorCode.VALIDATION_ERROR.throwIt("Response extraction config is required");
    }
    for (var field : config.safeFields()) {
      if (field.fieldName() == null || field.fieldName().isBlank()) {
        throw ErrorCode.VALIDATION_ERROR.throwIt("Extraction field name is required");
      }
      if (field.source() == null) {
        throw ErrorCode.VALIDATION_ERROR.throwIt("Extraction source is required");
      }
      if (field.source().name().equals("JSON_PATH")
          && (field.path() == null || field.path().isBlank())) {
        throw ErrorCode.VALIDATION_ERROR.throwIt("JSON extraction path is required");
      }
    }
  }

  public void validateStatusTransition(
      ChatbotTargetStatus status,
      TargetRequestConfigDto requestConfig,
      ResponseExtractionConfigDto extractionConfig) {
    if (status == ChatbotTargetStatus.ACTIVE || status == ChatbotTargetStatus.READY) {
      validateExtractionConfig(extractionConfig);
      if (requestConfig == null || requestConfig.url() == null || requestConfig.url().isBlank()) {
        throw ErrorCode.INVALID_STATE.throwIt("Target request config is required");
      }
    }
  }

  private void validateSecretBindings(
      CurrentUser user, Project project, List<SecretBindingDto> secretBindings) {
    for (var binding : secretBindings) {
      if (binding.location() == null
          || binding.keyOrPath() == null
          || binding.keyOrPath().isBlank()) {
        throw ErrorCode.VALIDATION_ERROR.throwIt(
            "Secret binding location and key/path are required");
      }
      secretLookupService.requireActiveForUse(user, binding.secretId(), project.getWorkspaceId());
    }
  }

  private void validateSensitiveLocations(TargetRequestConfigDto requestConfig) {
    for (var entry : requestConfig.safeHeaders().entrySet()) {
      if (isSensitiveKey(entry.getKey())
          && hasBinding(
              requestConfig.safeSecretBindings(), SecretBindingLocation.HEADER, entry.getKey())) {
        throw ErrorCode.VALIDATION_ERROR.throwIt("Sensitive header requires a secret binding");
      }
    }
    for (var entry : requestConfig.safeQueryParams().entrySet()) {
      if (isSensitiveKey(entry.getKey())
          && hasBinding(
              requestConfig.safeSecretBindings(), SecretBindingLocation.QUERY, entry.getKey())) {
        throw ErrorCode.VALIDATION_ERROR.throwIt(
            "Sensitive query parameter requires a secret binding");
      }
    }
    validateSensitiveBodyFields(requestConfig);
  }

  private void validateSensitiveBodyFields(TargetRequestConfigDto requestConfig) {
    if (requestConfig.body() == null || requestConfig.body().isBlank()) {
      return;
    }
    var matcher = SENSITIVE_JSON_FIELD.matcher(requestConfig.body());
    while (matcher.find()) {
      var key = matcher.group(1);
      if (hasBinding(requestConfig.safeSecretBindings(), SecretBindingLocation.BODY_JSON, key)) {
        throw ErrorCode.VALIDATION_ERROR.throwIt("Sensitive body field requires a secret binding");
      }
    }
  }

  private boolean hasBinding(
      List<SecretBindingDto> bindings, SecretBindingLocation location, String keyOrPath) {
    return bindings.stream()
        .noneMatch(
            binding ->
                binding.location() == location && binding.keyOrPath().equalsIgnoreCase(keyOrPath));
  }

  private boolean isSensitiveKey(String key) {
    var normalized = key.toLowerCase(Locale.ROOT);
    return SENSITIVE_KEYS.contains(normalized)
        || normalized.contains("token")
        || normalized.contains("secret")
        || normalized.contains("password")
        || normalized.contains("apikey")
        || normalized.contains("api_key");
  }

  private void validateDatasetMappings(Project project, List<DatasetMappingDto> mappings) {
    if (mappings.isEmpty()) {
      return;
    }
    var schema =
        schemaRepository
            .findTopByProjectIdOrderByVersionDesc(project.getId())
            .orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    var columns =
        columnRepository.findBySchemaIdOrderByOrderIndexAsc(schema.getId()).stream()
            .map(column -> column.getName().toLowerCase(Locale.ROOT))
            .collect(java.util.stream.Collectors.toSet());
    for (var mapping : mappings) {
      if (!columns.contains(mapping.columnName().toLowerCase(Locale.ROOT))) {
        throw ErrorCode.DATASET_UNKNOWN_COLUMN.throwIt();
      }
    }
  }
}
