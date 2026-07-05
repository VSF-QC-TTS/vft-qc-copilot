package vn.vinfast.vfqc.api.secret.service.impl;

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
import vn.vinfast.vfqc.api.secret.dto.CreateSecretRequest;
import vn.vinfast.vfqc.api.secret.dto.RotateSecretRequest;
import vn.vinfast.vfqc.api.secret.dto.SecretResponse;
import vn.vinfast.vfqc.api.secret.dto.UpdateSecretRequest;
import vn.vinfast.vfqc.api.secret.entity.Secret;
import vn.vinfast.vfqc.api.secret.enums.SecretScope;
import vn.vinfast.vfqc.api.secret.enums.SecretStatus;
import vn.vinfast.vfqc.api.secret.mapper.SecretMapper;
import vn.vinfast.vfqc.api.secret.repository.SecretRepository;
import vn.vinfast.vfqc.api.secret.service.SecretEncryptionService;
import vn.vinfast.vfqc.api.secret.service.SecretService;

@Service
@RequiredArgsConstructor
@Slf4j
public class SecretServiceImpl implements SecretService {

  private final SecretRepository secretRepository;
  private final WorkspacePermissionService workspacePermissionService;
  private final ResourceNameNormalizer nameNormalizer;
  private final SecretEncryptionService encryptionService;
  private final SecretMapper secretMapper;
  private final Clock clock;

  @Override
  @Transactional(readOnly = true)
  public List<SecretResponse> listWorkspace(CurrentUser user, UUID workspaceId) {
    workspacePermissionService.require(user, workspaceId, PermissionAction.VIEW);
    return secretRepository
        .findByWorkspaceIdAndDeletedAtIsNullOrderByCreatedAtDesc(workspaceId)
        .stream()
        .map(secretMapper::toResponse)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<SecretResponse> listPersonal(CurrentUser user) {
    return secretRepository
        .findByOwnerUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(user.id())
        .stream()
        .map(secretMapper::toResponse)
        .toList();
  }

  @Override
  @Transactional
  public SecretResponse createWorkspace(
      CurrentUser user, UUID workspaceId, CreateSecretRequest request) {
    workspacePermissionService.require(user, workspaceId, PermissionAction.EDIT);
    var normalized = nameNormalizer.normalize(request.name(), 160);
    if (secretRepository.existsByWorkspaceIdAndNameLookupAndDeletedAtIsNull(
        workspaceId, normalized.lookupName())) {
      throw ErrorCode.DUPLICATE_RESOURCE.throwIt("Secret name already exists");
    }
    return create(
        user,
        request.value(),
        new Secret()
            .setScope(SecretScope.WORKSPACE)
            .setWorkspaceId(workspaceId)
            .setName(normalized.displayName())
            .setNameLookup(normalized.lookupName()));
  }

  @Override
  @Transactional
  public SecretResponse createPersonal(CurrentUser user, CreateSecretRequest request) {
    var normalized = nameNormalizer.normalize(request.name(), 160);
    if (secretRepository.existsByOwnerUserIdAndNameLookupAndDeletedAtIsNull(
        user.id(), normalized.lookupName())) {
      throw ErrorCode.DUPLICATE_RESOURCE.throwIt("Secret name already exists");
    }
    return create(
        user,
        request.value(),
        new Secret()
            .setScope(SecretScope.PERSONAL)
            .setOwnerUserId(user.id())
            .setName(normalized.displayName())
            .setNameLookup(normalized.lookupName()));
  }

  @Override
  @Transactional(readOnly = true)
  public SecretResponse get(CurrentUser user, UUID secretId) {
    return secretMapper.toResponse(requireOwnedOrVisible(user, secretId, PermissionAction.VIEW));
  }

  @Override
  @Transactional
  public SecretResponse update(CurrentUser user, UUID secretId, UpdateSecretRequest request) {
    var secret = requireOwnedOrVisible(user, secretId, PermissionAction.EDIT);
    if (request.name() != null) {
      var normalized = nameNormalizer.normalize(request.name(), 160);
      validateUniqueName(secret, normalized.lookupName());
      secret.setName(normalized.displayName()).setNameLookup(normalized.lookupName());
    }
    secret.setUpdatedAt(OffsetDateTime.now(clock));
    log.info("Secret metadata updated: secretId={} actorId={}", secretId, user.id());
    return secretMapper.toResponse(secret);
  }

  @Override
  @Transactional
  public SecretResponse rotate(CurrentUser user, UUID secretId, RotateSecretRequest request) {
    var secret = requireOwnedOrVisible(user, secretId, PermissionAction.EDIT);
    applySecretValue(secret, request.value());
    secret.setUpdatedAt(OffsetDateTime.now(clock));
    log.info("Secret rotated: secretId={} actorId={}", secretId, user.id());
    return secretMapper.toResponse(secret);
  }

  @Override
  @Transactional
  public SecretResponse disable(CurrentUser user, UUID secretId) {
    var secret = requireOwnedOrVisible(user, secretId, PermissionAction.EDIT);
    secret.setStatus(SecretStatus.DISABLED).setUpdatedAt(OffsetDateTime.now(clock));
    log.info("Secret disabled: secretId={} actorId={}", secretId, user.id());
    return secretMapper.toResponse(secret);
  }

  @Override
  @Transactional
  public void delete(CurrentUser user, UUID secretId) {
    var secret = requireOwnedOrVisible(user, secretId, PermissionAction.EDIT);
    secret.setDeletedAt(OffsetDateTime.now(clock));
    log.info("Secret soft-deleted: secretId={} actorId={}", secretId, user.id());
  }

  private SecretResponse create(CurrentUser user, String plaintext, Secret secret) {
    var now = OffsetDateTime.now(clock);
    applySecretValue(secret, plaintext);
    var saved =
        secretRepository.save(
            secret
                .setStatus(SecretStatus.ACTIVE)
                .setCreatedBy(user.id())
                .setCreatedAt(now)
                .setUpdatedAt(now));
    log.info("Secret created: secretId={} actorId={}", saved.getId(), user.id());
    return secretMapper.toResponse(saved);
  }

  private void applySecretValue(Secret secret, String plaintext) {
    secret.setEncryptedValue(encryptionService.encrypt(plaintext)).setMaskedPreview("****");
  }

  private Secret requireOwnedOrVisible(CurrentUser user, UUID secretId, PermissionAction action) {
    var secret =
        secretRepository
            .findByIdAndDeletedAtIsNull(secretId)
            .orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    if (secret.getScope() == SecretScope.WORKSPACE) {
      workspacePermissionService.require(user, secret.getWorkspaceId(), action);
      return secret;
    }
    if (user.platformAdmin() || user.id().equals(secret.getOwnerUserId())) {
      return secret;
    }
    throw ErrorCode.RESOURCE_NOT_FOUND.throwIt();
  }

  private void validateUniqueName(Secret secret, String lookupName) {
    if (secret.getNameLookup().equals(lookupName)) {
      return;
    }
    var duplicate =
        secret.getScope() == SecretScope.WORKSPACE
            ? secretRepository.existsByWorkspaceIdAndNameLookupAndDeletedAtIsNull(
                secret.getWorkspaceId(), lookupName)
            : secretRepository.existsByOwnerUserIdAndNameLookupAndDeletedAtIsNull(
                secret.getOwnerUserId(), lookupName);
    if (duplicate) {
      throw ErrorCode.DUPLICATE_RESOURCE.throwIt("Secret name already exists");
    }
  }
}
