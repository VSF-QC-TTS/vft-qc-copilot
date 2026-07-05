package vn.vinfast.vfqc.api.provider.service.impl;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vinfast.vfqc.api.common.error.ErrorCode;
import vn.vinfast.vfqc.api.common.naming.ResourceNameNormalizer;
import vn.vinfast.vfqc.api.common.permission.PermissionAction;
import vn.vinfast.vfqc.api.common.permission.WorkspacePermissionService;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.provider.assembler.LlmProviderResponseAssembler;
import vn.vinfast.vfqc.api.provider.dto.CreateLlmProviderRequest;
import vn.vinfast.vfqc.api.provider.dto.LlmProviderResponse;
import vn.vinfast.vfqc.api.provider.dto.UpdateLlmProviderRequest;
import vn.vinfast.vfqc.api.provider.entity.LlmProvider;
import vn.vinfast.vfqc.api.provider.enums.ProviderScope;
import vn.vinfast.vfqc.api.provider.enums.ProviderStatus;
import vn.vinfast.vfqc.api.provider.repository.LlmProviderRepository;
import vn.vinfast.vfqc.api.provider.service.LlmProviderService;
import vn.vinfast.vfqc.api.provider.validator.ProviderConfigValidator;
import vn.vinfast.vfqc.api.secret.service.SecretLookupService;

@Service
@RequiredArgsConstructor
@Slf4j
public class LlmProviderServiceImpl implements LlmProviderService {

  private final LlmProviderRepository providerRepository;
  private final WorkspacePermissionService workspacePermissionService;
  private final SecretLookupService secretLookupService;
  private final ResourceNameNormalizer nameNormalizer;
  private final ProviderConfigValidator configValidator;
  private final LlmProviderResponseAssembler assembler;
  private final Clock clock;

  @Override
  @Transactional(readOnly = true)
  public List<LlmProviderResponse> listWorkspace(CurrentUser user, UUID workspaceId) {
    workspacePermissionService.require(user, workspaceId, PermissionAction.VIEW);
    return assembler.toResponses(
        providerRepository.findByWorkspaceIdAndDeletedAtIsNullOrderByCreatedAtDesc(workspaceId));
  }

  @Override
  @Transactional(readOnly = true)
  public List<LlmProviderResponse> listPersonal(CurrentUser user) {
    return assembler.toResponses(
        providerRepository.findByOwnerUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(user.id()));
  }

  @Override
  @Transactional
  public LlmProviderResponse createWorkspace(
      CurrentUser user, UUID workspaceId, CreateLlmProviderRequest request) {
    workspacePermissionService.require(user, workspaceId, PermissionAction.EDIT);
    secretLookupService.requireActiveForUse(user, request.secretId(), workspaceId);
    var normalized = nameNormalizer.normalize(request.name(), 160);
    if (providerRepository.existsByWorkspaceIdAndNameLookupAndDeletedAtIsNull(
        workspaceId, normalized.lookupName())) {
      throw ErrorCode.DUPLICATE_RESOURCE.throwIt("Provider name already exists");
    }
    var provider =
        LlmProvider.builder()
            .scope(ProviderScope.WORKSPACE)
            .workspaceId(workspaceId)
            .name(normalized.displayName())
            .nameLookup(normalized.lookupName())
            .build();
    return saveNew(user, provider, request);
  }

  @Override
  @Transactional
  public LlmProviderResponse createPersonal(CurrentUser user, CreateLlmProviderRequest request) {
    secretLookupService.requireActiveForUse(user, request.secretId(), null);
    var normalized = nameNormalizer.normalize(request.name(), 160);
    if (providerRepository.existsByOwnerUserIdAndNameLookupAndDeletedAtIsNull(
        user.id(), normalized.lookupName())) {
      throw ErrorCode.DUPLICATE_RESOURCE.throwIt("Provider name already exists");
    }
    var provider =
        LlmProvider.builder()
            .scope(ProviderScope.PERSONAL)
            .ownerUserId(user.id())
            .name(normalized.displayName())
            .nameLookup(normalized.lookupName())
            .build();
    return saveNew(user, provider, request);
  }

  @Override
  @Transactional(readOnly = true)
  public LlmProviderResponse get(CurrentUser user, UUID providerId) {
    return assembler.toResponse(requireWritableOrVisible(user, providerId, PermissionAction.VIEW));
  }

  @Override
  @Transactional
  public LlmProviderResponse update(
      CurrentUser user, UUID providerId, UpdateLlmProviderRequest request) {
    var provider = requireWritableOrVisible(user, providerId, PermissionAction.EDIT);
    if (request.name() != null) {
      var normalized = nameNormalizer.normalize(request.name(), 160);
      validateUniqueName(provider, normalized.lookupName());
      provider.setName(normalized.displayName());
      provider.setNameLookup(normalized.lookupName());
    }
    if (request.secretId() != null) {
      secretLookupService.requireActiveForUse(
          user,
          request.secretId(),
          provider.getScope() == ProviderScope.WORKSPACE ? provider.getWorkspaceId() : null);
      provider.setSecretId(request.secretId());
    }
    if (request.baseUrl() != null) {
      provider.setBaseUrl(request.baseUrl());
    }
    if (request.defaultModel() != null) {
      provider.setDefaultModel(request.defaultModel());
    }
    if (request.config() != null) {
      provider.setConfigJson(
          configValidator
              .validateAndSanitize(provider.getProviderType(), request.config())
              .value());
    }
    provider.setUpdatedAt(OffsetDateTime.now(clock));
    log.info("LLM provider updated: providerId={} actorId={}", providerId, user.id());
    return assembler.toResponse(provider);
  }

  @Override
  @Transactional
  public void delete(CurrentUser user, UUID providerId) {
    var provider = requireWritableOrVisible(user, providerId, PermissionAction.EDIT);
    provider.setDeletedAt(OffsetDateTime.now(clock));
    log.info("LLM provider soft-deleted: providerId={} actorId={}", providerId, user.id());
  }

  private LlmProviderResponse saveNew(
      CurrentUser user, LlmProvider provider, CreateLlmProviderRequest request) {
    var now = OffsetDateTime.now(clock);
    var config =
        configValidator.validateAndSanitize(request.providerType(), request.config()).value();
    provider.setProviderType(request.providerType());
    provider.setSecretId(request.secretId());
    provider.setBaseUrl(request.baseUrl());
    provider.setDefaultModel(request.defaultModel());
    provider.setAvailableModelsJson(null);
    provider.setConfigJson(config);
    provider.setStatus(ProviderStatus.ACTIVE);
    provider.setCreatedBy(user.id());
    provider.setCreatedAt(now);
    provider.setUpdatedAt(now);
    var saved = providerRepository.save(provider);
    log.info("LLM provider created: providerId={} actorId={}", saved.getId(), user.id());
    return assembler.toResponse(saved);
  }

  private LlmProvider requireWritableOrVisible(
      CurrentUser user, UUID providerId, PermissionAction action) {
    var provider =
        providerRepository
            .findByIdAndDeletedAtIsNull(providerId)
            .orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    if (provider.getScope() == ProviderScope.WORKSPACE) {
      workspacePermissionService.require(user, provider.getWorkspaceId(), action);
      return provider;
    }
    if (user.platformAdmin() || user.id().equals(provider.getOwnerUserId())) {
      return provider;
    }
    throw ErrorCode.RESOURCE_NOT_FOUND.throwIt();
  }

  private void validateUniqueName(LlmProvider provider, String lookupName) {
    if (provider.getNameLookup().equals(lookupName)) {
      return;
    }
    var duplicate =
        provider.getScope() == ProviderScope.WORKSPACE
            ? providerRepository.existsByWorkspaceIdAndNameLookupAndDeletedAtIsNull(
                provider.getWorkspaceId(), lookupName)
            : providerRepository.existsByOwnerUserIdAndNameLookupAndDeletedAtIsNull(
                provider.getOwnerUserId(), lookupName);
    if (duplicate) {
      throw ErrorCode.DUPLICATE_RESOURCE.throwIt("Provider name already exists");
    }
  }
}
