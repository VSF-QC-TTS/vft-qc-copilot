package vn.vinfast.vfqc.api.provider.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.vinfast.vfqc.api.common.masking.DefaultVfqcSensitiveDataMasker;
import vn.vinfast.vfqc.api.common.naming.ResourceNameNormalizer;
import vn.vinfast.vfqc.api.common.permission.WorkspacePermissionService;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.provider.assembler.LlmProviderResponseAssembler;
import vn.vinfast.vfqc.api.provider.dto.CreateLlmProviderRequest;
import vn.vinfast.vfqc.api.provider.dto.LlmProviderResponse;
import vn.vinfast.vfqc.api.provider.entity.LlmProvider;
import vn.vinfast.vfqc.api.provider.enums.ProviderScope;
import vn.vinfast.vfqc.api.provider.enums.ProviderStatus;
import vn.vinfast.vfqc.api.provider.enums.ProviderType;
import vn.vinfast.vfqc.api.provider.repository.LlmProviderRepository;
import vn.vinfast.vfqc.api.provider.validator.ProviderConfigValidator;
import vn.vinfast.vfqc.api.secret.entity.Secret;
import vn.vinfast.vfqc.api.secret.service.SecretLookupService;
import vn.vinfast.vfqc.api.user.enums.SystemRole;
import vn.vinfast.vfqc.api.user.enums.UserStatus;

@ExtendWith(MockitoExtension.class)
class LlmProviderServiceImplTest {

  @Mock private LlmProviderRepository providerRepository;
  @Mock private WorkspacePermissionService workspacePermissionService;
  @Mock private SecretLookupService secretLookupService;
  @Mock private LlmProviderResponseAssembler assembler;

  @Test
  void createPersonal_ConfigWithSensitiveValues_SanitizesBeforePersistence() {
    // Arrange
    var user =
        new CurrentUser(
            UUID.randomUUID(), "owner@vinfast.vn", "Owner", SystemRole.USER, UserStatus.ACTIVE);
    var secretId = UUID.randomUUID();
    var request =
        new CreateLlmProviderRequest(
            "OpenAI Judge",
            ProviderType.OPENAI_COMPATIBLE,
            secretId,
            "https://example.test/v1",
            "gpt-test",
            Map.of("apiKey", "sk-plain-secret", "temperature", 0));
    var saved = ArgumentCaptor.forClass(LlmProvider.class);
    var service =
        new LlmProviderServiceImpl(
            providerRepository,
            workspacePermissionService,
            secretLookupService,
            new ResourceNameNormalizer(),
            new ProviderConfigValidator(new DefaultVfqcSensitiveDataMasker()),
            assembler,
            Clock.fixed(Instant.parse("2026-07-05T03:40:00Z"), ZoneOffset.UTC));
    when(secretLookupService.requireActiveForUse(user, secretId, null)).thenReturn(new Secret());
    when(providerRepository.existsByOwnerUserIdAndNameLookupAndDeletedAtIsNull(
            user.id(), "openai judge"))
        .thenReturn(false);
    when(providerRepository.save(saved.capture()))
        .thenAnswer(invocation -> {
          var p = invocation.getArgument(0, LlmProvider.class);
          p.setId(UUID.randomUUID());
          return p;
        });
    when(assembler.toResponse(any(LlmProvider.class)))
        .thenAnswer(
            invocation -> {
              var provider = invocation.getArgument(0, LlmProvider.class);
              return new LlmProviderResponse(
                  provider.getId(),
                  provider.getScope(),
                  provider.getOwnerUserId(),
                  null,
                  provider.getWorkspaceId(),
                  provider.getName(),
                  provider.getProviderType(),
                  null,
                  provider.getBaseUrl(),
                  provider.getDefaultModel(),
                  Map.of(),
                  provider.getConfigJson(),
                  provider.getStatus(),
                  provider.getCreatedAt(),
                  provider.getUpdatedAt());
            });

    // Act
    var response = service.createPersonal(user, request);

    // Assert
    assertThat(saved.getValue().getScope()).isEqualTo(ProviderScope.PERSONAL);
    assertThat(saved.getValue().getOwnerUserId()).isEqualTo(user.id());
    assertThat(saved.getValue().getStatus()).isEqualTo(ProviderStatus.ACTIVE);
    assertThat(saved.getValue().getConfigJson()).containsEntry("apiKey", "****");
    assertThat(saved.getValue().getConfigJson()).doesNotContainValue("sk-plain-secret");
    assertThat(response.config()).containsEntry("apiKey", "****");
    verify(secretLookupService).requireActiveForUse(user, secretId, null);
  }
}
