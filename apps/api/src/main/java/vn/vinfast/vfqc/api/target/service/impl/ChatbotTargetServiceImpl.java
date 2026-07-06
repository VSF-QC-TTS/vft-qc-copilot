package vn.vinfast.vfqc.api.target.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vinfast.vfqc.api.common.error.ErrorCode;
import vn.vinfast.vfqc.api.common.masking.VfqcSensitiveDataMasker;
import vn.vinfast.vfqc.api.common.naming.ResourceNameNormalizer;
import vn.vinfast.vfqc.api.common.permission.PermissionAction;
import vn.vinfast.vfqc.api.common.permission.ProjectPermissionService;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.secret.service.RuntimeSecretResolver;
import vn.vinfast.vfqc.api.target.assembler.ChatbotTargetResponseAssembler;
import vn.vinfast.vfqc.api.target.client.HttpTargetClient;
import vn.vinfast.vfqc.api.target.client.TargetHttpRequest;
import vn.vinfast.vfqc.api.target.dto.ChatbotTargetExampleResponse;
import vn.vinfast.vfqc.api.target.dto.ChatbotTargetReadinessResponse;
import vn.vinfast.vfqc.api.target.dto.ChatbotTargetResponse;
import vn.vinfast.vfqc.api.target.dto.CreateChatbotTargetRequest;
import vn.vinfast.vfqc.api.target.dto.ResponseExtractionConfigDto;
import vn.vinfast.vfqc.api.target.dto.TargetRequestConfigDto;
import vn.vinfast.vfqc.api.target.dto.TestChatbotTargetRequest;
import vn.vinfast.vfqc.api.target.dto.UpdateChatbotTargetRequest;
import vn.vinfast.vfqc.api.target.entity.ChatbotTarget;
import vn.vinfast.vfqc.api.target.entity.ChatbotTargetExample;
import vn.vinfast.vfqc.api.target.enums.ChatbotTargetStatus;
import vn.vinfast.vfqc.api.target.enums.SecretBindingLocation;
import vn.vinfast.vfqc.api.target.enums.TargetBodyType;
import vn.vinfast.vfqc.api.target.parser.CurlParser;
import vn.vinfast.vfqc.api.target.repository.ChatbotTargetExampleRepository;
import vn.vinfast.vfqc.api.target.repository.ChatbotTargetRepository;
import vn.vinfast.vfqc.api.target.service.ChatbotTargetReadinessService;
import vn.vinfast.vfqc.api.target.service.ChatbotTargetService;
import vn.vinfast.vfqc.api.target.service.ResponseExtractionService;
import vn.vinfast.vfqc.api.target.validator.ChatbotTargetConfigValidator;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatbotTargetServiceImpl implements ChatbotTargetService {

  private final ChatbotTargetRepository targetRepository;
  private final ChatbotTargetExampleRepository exampleRepository;
  private final ProjectPermissionService projectPermissionService;
  private final ResourceNameNormalizer nameNormalizer;
  private final ChatbotTargetConfigValidator configValidator;
  private final ChatbotTargetResponseAssembler assembler;
  private final ChatbotTargetReadinessService readinessService;
  private final ResponseExtractionService extractionService;
  private final RuntimeSecretResolver runtimeSecretResolver;
  private final HttpTargetClient httpTargetClient;
  private final VfqcSensitiveDataMasker masker;
  private final CurlParser curlParser;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  @Override
  @Transactional(readOnly = true)
  public List<ChatbotTargetResponse> list(CurrentUser user, UUID projectId) {
    projectPermissionService.requireProject(user, projectId, PermissionAction.VIEW);
    return assembler.toResponses(
        targetRepository.findByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(projectId));
  }

  @Override
  @Transactional
  public ChatbotTargetResponse create(
      CurrentUser user, UUID projectId, CreateChatbotTargetRequest request) {
    var project = projectPermissionService.requireProject(user, projectId, PermissionAction.EDIT);
    var normalized = nameNormalizer.normalize(request.name(), 160);
    if (targetRepository.existsByProjectIdAndNameLookupAndDeletedAtIsNull(
        projectId, normalized.lookupName())) {
      throw ErrorCode.DUPLICATE_RESOURCE.throwIt("Chatbot target name already exists");
    }
    var config = requestConfig(request);
    configValidator.validateRequestConfig(user, project, config);
    if (request.responseExtractionConfig() != null) {
      configValidator.validateExtractionConfig(request.responseExtractionConfig());
    }
    var now = OffsetDateTime.now(clock);
    var target =
        ChatbotTarget.builder()
            .projectId(projectId)
            .name(normalized.displayName())
            .nameLookup(normalized.lookupName())
            .description(request.description())
            .status(ChatbotTargetStatus.DRAFT)
            .createdBy(user.id())
            .createdAt(now)
            .updatedAt(now)
            .build();
    applyRawCurl(target, request.rawCurl());
    applyConfig(target, config);
    applyExtraction(target, request.responseExtractionConfig());
    var saved = targetRepository.save(target);
    log.info("Chatbot target created: targetId={} actorId={}", saved.getId(), user.id());
    return assembler.toResponse(saved);
  }

  @Override
  @Transactional(readOnly = true)
  public ChatbotTargetResponse get(CurrentUser user, UUID targetId) {
    return assembler.toResponse(requireTarget(user, targetId, PermissionAction.VIEW));
  }

  @Override
  @Transactional
  public ChatbotTargetResponse update(
      CurrentUser user, UUID targetId, UpdateChatbotTargetRequest request) {
    var target = requireTarget(user, targetId, PermissionAction.EDIT);
    var project =
        projectPermissionService.requireProject(user, target.getProjectId(), PermissionAction.EDIT);
    if (request.name() != null) {
      var normalized = nameNormalizer.normalize(request.name(), 160);
      validateUniqueName(target, normalized.lookupName());
      target.setName(normalized.displayName());
      target.setNameLookup(normalized.lookupName());
    }
    if (request.description() != null) {
      target.setDescription(request.description());
    }
    if (hasRequestPatch(request)) {
      var config = requestConfig(request, assembler.toRequestConfig(target));
      configValidator.validateRequestConfig(user, project, config);
      applyConfig(target, config);
    }
    if (request.rawCurl() != null) {
      applyRawCurl(target, request.rawCurl());
    }
    if (request.responseExtractionConfig() != null) {
      configValidator.validateExtractionConfig(request.responseExtractionConfig());
      applyExtraction(target, request.responseExtractionConfig());
    }
    if (request.status() != null) {
      configValidator.validateStatusTransition(
          request.status(),
          assembler.toRequestConfig(target),
          assembler.toExtractionConfig(target.getResponseExtractionConfigJson()));
      target.setStatus(request.status());
    }
    target.setUpdatedAt(OffsetDateTime.now(clock));
    log.info("Chatbot target updated: targetId={} actorId={}", targetId, user.id());
    return assembler.toResponse(target);
  }

  @Override
  @Transactional
  public void delete(CurrentUser user, UUID targetId) {
    var target = requireTarget(user, targetId, PermissionAction.EDIT);
    var now = OffsetDateTime.now(clock);
    target.setStatus(ChatbotTargetStatus.DELETED);
    target.setDeletedAt(now);
    target.setUpdatedAt(now);
    log.info("Chatbot target soft-deleted: targetId={} actorId={}", targetId, user.id());
  }

  @Override
  @Transactional
  public ChatbotTargetExampleResponse test(
      CurrentUser user, UUID targetId, TestChatbotTargetRequest request) {
    var target = requireTarget(user, targetId, PermissionAction.EDIT);
    if (target.getStatus() == ChatbotTargetStatus.DISABLED) {
      throw ErrorCode.INVALID_STATE.throwIt("Disabled target cannot be tested");
    }
    var project =
        projectPermissionService.requireProject(user, target.getProjectId(), PermissionAction.EDIT);
    var config = assembler.toRequestConfig(target);
    configValidator.validateRequestConfig(user, project, config);
    var targetRequest = runtimeRequest(user, project.getWorkspaceId(), config, request);
    var result = httpTargetClient.execute(targetRequest);
    var sanitizedBody = masker.sanitizeText(result.responseBody()).value();
    var extracted =
        result.errorMessage() == null && target.getResponseExtractionConfigJson() != null
            ? extractionService.extract(
                sanitizedBody,
                assembler.toExtractionConfig(target.getResponseExtractionConfigJson()))
            : Map.<String, Object>of();
    var now = OffsetDateTime.now(clock);
    var example =
        exampleRepository.save(
            ChatbotTargetExample.builder()
                .chatbotTargetId(targetId)
                .requestJson(sanitizeRequest(targetRequest))
                .responseStatusCode(result.statusCode() == 0 ? null : result.statusCode())
                .responseHeadersJson(masker.sanitizeMap(result.responseHeaders()).value())
                .responseBodyJson(Map.of("text", sanitizedBody))
                .extractedOutputJson(masker.sanitizeMap(extracted).value())
                .latencyMs(result.latencyMs())
                .errorMessage(masker.sanitizeText(result.errorMessage()).value())
                .testedBy(user.id())
                .createdAt(now)
                .build());
    target.setLatestExampleId(example.getId());
    target.setUpdatedAt(now);
    return assembler.toResponse(example);
  }

  @Override
  @Transactional(readOnly = true)
  public List<ChatbotTargetExampleResponse> examples(CurrentUser user, UUID targetId) {
    requireTarget(user, targetId, PermissionAction.VIEW);
    return exampleRepository.findByChatbotTargetIdOrderByCreatedAtDesc(targetId).stream()
        .map(assembler::toResponse)
        .toList();
  }

  @Override
  @Transactional
  public ChatbotTargetResponse updateExtraction(
      CurrentUser user, UUID targetId, ResponseExtractionConfigDto config) {
    var target = requireTarget(user, targetId, PermissionAction.EDIT);
    configValidator.validateExtractionConfig(config);
    applyExtraction(target, config);
    target.setUpdatedAt(OffsetDateTime.now(clock));
    return assembler.toResponse(target);
  }

  @Override
  @Transactional(readOnly = true)
  public ChatbotTargetReadinessResponse readiness(CurrentUser user, UUID targetId) {
    return readinessService.readiness(requireTarget(user, targetId, PermissionAction.VIEW));
  }

  private ChatbotTarget requireTarget(CurrentUser user, UUID targetId, PermissionAction action) {
    var target =
        targetRepository
            .findByIdAndDeletedAtIsNull(targetId)
            .orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    projectPermissionService.requireProject(user, target.getProjectId(), action);
    return target;
  }

  private TargetRequestConfigDto requestConfig(CreateChatbotTargetRequest request) {
    if (request.rawCurl() != null && request.url() == null) {
      var parsed = curlParser.parse(request.rawCurl());
      return new TargetRequestConfigDto(
          parsed.method(),
          parsed.url(),
          parsed.headers(),
          parsed.queryParams(),
          parsed.body().isBlank() ? TargetBodyType.NONE : TargetBodyType.JSON,
          parsed.body(),
          request.secretBindings(),
          request.datasetMappings());
    }
    return new TargetRequestConfigDto(
        request.method(),
        request.url(),
        request.headers(),
        request.queryParams(),
        request.bodyType(),
        request.body(),
        request.secretBindings(),
        request.datasetMappings());
  }

  private TargetRequestConfigDto requestConfig(
      UpdateChatbotTargetRequest request, TargetRequestConfigDto existing) {
    return new TargetRequestConfigDto(
        request.method() == null ? existing.method() : request.method(),
        request.url() == null ? existing.url() : request.url(),
        request.headers() == null ? existing.safeHeaders() : request.headers(),
        request.queryParams() == null ? existing.safeQueryParams() : request.queryParams(),
        request.bodyType() == null ? existing.safeBodyType() : request.bodyType(),
        request.body() == null ? existing.body() : request.body(),
        request.secretBindings() == null ? existing.safeSecretBindings() : request.secretBindings(),
        request.datasetMappings() == null
            ? existing.safeDatasetMappings()
            : request.datasetMappings());
  }

  private boolean hasRequestPatch(UpdateChatbotTargetRequest request) {
    return request.method() != null
        || request.url() != null
        || request.headers() != null
        || request.queryParams() != null
        || request.bodyType() != null
        || request.body() != null
        || request.secretBindings() != null
        || request.datasetMappings() != null;
  }

  private void applyConfig(ChatbotTarget target, TargetRequestConfigDto config) {
    target.setMethod(config.method().toUpperCase());
    target.setUrl(config.url());
    target.setHeadersJson(masker.sanitizeMap(toObjectMap(config.safeHeaders())).value());
    target.setQueryParamsJson(masker.sanitizeMap(toObjectMap(config.safeQueryParams())).value());
    target.setBodyJson(
        masker
            .sanitizeMap(
                Map.of(
                    "type",
                    config.safeBodyType().name(),
                    "value",
                    config.body() == null ? "" : config.body()))
            .value());
    target.setAuthConfigJson(Map.of("secretBindings", config.safeSecretBindings()));
    target.setDatasetMappingJson(Map.of("datasetMappings", config.safeDatasetMappings()));
  }

  private void applyExtraction(ChatbotTarget target, ResponseExtractionConfigDto config) {
    target.setResponseExtractionConfigJson(
        config == null ? null : objectMapper.convertValue(config, new TypeReference<>() {}));
  }

  private void applyRawCurl(ChatbotTarget target, String rawCurl) {
    if (rawCurl == null || rawCurl.isBlank()) {
      return;
    }
    var sanitized = masker.sanitizeCurl(rawCurl).value();
    target.setRawCurlSanitized(sanitized);
    target.setRawCurlHash(hash(sanitized));
  }

  private TargetHttpRequest runtimeRequest(
      CurrentUser user,
      UUID workspaceId,
      TargetRequestConfigDto config,
      TestChatbotTargetRequest request) {
    var headers = new LinkedHashMap<>(config.safeHeaders());
    var query = new LinkedHashMap<>(config.safeQueryParams());
    var body = config.body() == null ? "" : config.body();
    var datasetValues =
        request == null || request.datasetValues() == null
            ? Map.<String, String>of()
            : request.datasetValues();
    for (var mapping : config.safeDatasetMappings()) {
      var value = datasetValues.getOrDefault(mapping.columnName(), "");
      if (mapping.location() == SecretBindingLocation.HEADER) {
        headers.put(mapping.keyOrPath(), value);
      } else if (mapping.location() == SecretBindingLocation.QUERY) {
        query.put(mapping.keyOrPath(), value);
      } else {
        body = body.replace("$dataset." + mapping.columnName(), value);
      }
    }
    for (var binding : config.safeSecretBindings()) {
      var secret = runtimeSecretResolver.resolve(user, binding.secretId(), workspaceId);
      if (binding.location() == SecretBindingLocation.HEADER) {
        headers.put(binding.keyOrPath(), secret);
      } else if (binding.location() == SecretBindingLocation.QUERY) {
        query.put(binding.keyOrPath(), secret);
      } else {
        body = body.replace(binding.keyOrPath(), secret);
      }
    }
    return new TargetHttpRequest(config.method(), config.url(), headers, query, body);
  }

  private Map<String, Object> sanitizeRequest(TargetHttpRequest request) {
    return masker
        .sanitizeMap(
            Map.of(
                "method", request.method(),
                "url", request.url(),
                "headers", request.headers(),
                "queryParams", request.queryParams(),
                "body", request.body()))
        .value();
  }

  private Map<String, Object> toObjectMap(Map<String, String> value) {
    return new LinkedHashMap<>(value);
  }

  private String hash(String value) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw ErrorCode.INTERNAL_ERROR.throwIt();
    }
  }

  private void validateUniqueName(ChatbotTarget target, String lookupName) {
    if (target.getNameLookup().equals(lookupName)) {
      return;
    }
    if (targetRepository.existsByProjectIdAndNameLookupAndDeletedAtIsNull(
        target.getProjectId(), lookupName)) {
      throw ErrorCode.DUPLICATE_RESOURCE.throwIt("Chatbot target name already exists");
    }
  }
}
