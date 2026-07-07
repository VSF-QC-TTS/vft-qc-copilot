package vn.vinfast.vfqc.api.secret.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.vinfast.vfqc.api.common.naming.ResourceNameNormalizer;
import vn.vinfast.vfqc.api.common.permission.WorkspacePermissionService;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.secret.dto.CreateSecretRequest;
import vn.vinfast.vfqc.api.secret.dto.SecretResponse;
import vn.vinfast.vfqc.api.secret.assembler.SecretResponseAssembler;
import vn.vinfast.vfqc.api.secret.entity.Secret;
import vn.vinfast.vfqc.api.secret.enums.SecretScope;
import vn.vinfast.vfqc.api.secret.enums.SecretStatus;
import vn.vinfast.vfqc.api.secret.repository.SecretRepository;
import vn.vinfast.vfqc.api.secret.service.SecretEncryptionService;
import vn.vinfast.vfqc.api.user.enums.SystemRole;
import vn.vinfast.vfqc.api.user.enums.UserStatus;

@ExtendWith(MockitoExtension.class)
class SecretServiceImplTest {

  @Mock private SecretRepository secretRepository;
  @Mock private WorkspacePermissionService workspacePermissionService;
  @Mock private SecretEncryptionService encryptionService;
  @Mock private SecretResponseAssembler secretAssembler;

  @Test
  void createPersonal_PlaintextSecret_StoresEncryptedValueOnly() {
    // Arrange
    var user =
        new CurrentUser(
            UUID.randomUUID(), "owner@vinfast.vn", "Owner", SystemRole.USER, UserStatus.ACTIVE);
    var request = new CreateSecretRequest("Gemini Key", "sk-plain-secret");
    var clock = Clock.fixed(Instant.parse("2026-07-05T03:00:00Z"), ZoneOffset.UTC);
    var service =
        new SecretServiceImpl(
            secretRepository,
            workspacePermissionService,
            new ResourceNameNormalizer(),
            encryptionService,
            secretAssembler,
            clock);
    var saved = ArgumentCaptor.forClass(Secret.class);
    when(encryptionService.encrypt("sk-plain-secret")).thenReturn("encrypted-payload");
    when(secretRepository.existsByOwnerUserIdAndNameLookupAndDeletedAtIsNull(user.id(), "gemini key"))
        .thenReturn(false);
    when(secretRepository.save(saved.capture()))
        .thenAnswer(invocation -> {
          var s = invocation.getArgument(0, Secret.class);
          s.setId(UUID.randomUUID());
          return s;
        });
    when(secretAssembler.toResponse(any(Secret.class)))
        .thenAnswer(
            invocation -> {
              var secret = invocation.getArgument(0, Secret.class);
              return new SecretResponse(
                  secret.getId(),
                  secret.getScope(),
                  secret.getOwnerUserId(),
                  null,
                  secret.getWorkspaceId(),
                  secret.getName(),
                  secret.getMaskedPreview(),
                  secret.getStatus(),
                  secret.getCreatedAt(),
                  secret.getUpdatedAt());
            });

    // Act
    var response = service.createPersonal(user, request);

    // Assert
    assertThat(saved.getValue().getScope()).isEqualTo(SecretScope.PERSONAL);
    assertThat(saved.getValue().getOwnerUserId()).isEqualTo(user.id());
    assertThat(saved.getValue().getEncryptedValue()).isEqualTo("encrypted-payload");
    assertThat(saved.getValue().getEncryptedValue()).doesNotContain("sk-plain-secret");
    assertThat(saved.getValue().getMaskedPreview()).isEqualTo("****");
    assertThat(saved.getValue().getStatus()).isEqualTo(SecretStatus.ACTIVE);
    assertThat(response.maskedPreview()).isEqualTo("****");
    assertThat(response.toString()).doesNotContain("sk-plain-secret", "encrypted-payload");
  }

  @Test
  void createPersonal_NonTokenPlaintextSecret_DoesNotUsePlaintextAsPreview() {
    // Arrange
    var user =
        new CurrentUser(
            UUID.randomUUID(), "owner@vinfast.vn", "Owner", SystemRole.USER, UserStatus.ACTIVE);
    var request = new CreateSecretRequest("Internal Password", "my-internal-password");
    var clock = Clock.fixed(Instant.parse("2026-07-05T03:00:00Z"), ZoneOffset.UTC);
    var service =
        new SecretServiceImpl(
            secretRepository,
            workspacePermissionService,
            new ResourceNameNormalizer(),
            encryptionService,
            secretAssembler,
            clock);
    var saved = ArgumentCaptor.forClass(Secret.class);
    when(encryptionService.encrypt("my-internal-password")).thenReturn("encrypted-password");
    when(secretRepository.existsByOwnerUserIdAndNameLookupAndDeletedAtIsNull(
            user.id(), "internal password"))
        .thenReturn(false);
    when(secretRepository.save(saved.capture()))
        .thenAnswer(invocation -> {
          var s = invocation.getArgument(0, Secret.class);
          s.setId(UUID.randomUUID());
          return s;
        });
    when(secretAssembler.toResponse(any(Secret.class)))
        .thenAnswer(
            invocation -> {
              var secret = invocation.getArgument(0, Secret.class);
              return new SecretResponse(
                  secret.getId(),
                  secret.getScope(),
                  secret.getOwnerUserId(),
                  null,
                  secret.getWorkspaceId(),
                  secret.getName(),
                  secret.getMaskedPreview(),
                  secret.getStatus(),
                  secret.getCreatedAt(),
                  secret.getUpdatedAt());
            });

    // Act
    var response = service.createPersonal(user, request);

    // Assert
    assertThat(saved.getValue().getMaskedPreview()).isEqualTo("****");
    assertThat(response.toString()).doesNotContain("my-internal-password", "encrypted-password");
  }
}
