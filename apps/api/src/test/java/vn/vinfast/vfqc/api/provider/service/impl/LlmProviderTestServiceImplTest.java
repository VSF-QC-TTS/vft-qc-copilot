package vn.vinfast.vfqc.api.provider.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.vinfast.vfqc.api.common.masking.DefaultVfqcSensitiveDataMasker;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.provider.client.LlmProviderClient;
import vn.vinfast.vfqc.api.provider.client.ProviderClientTestResult;
import vn.vinfast.vfqc.api.provider.dto.LlmProviderTestLogResponse;
import vn.vinfast.vfqc.api.provider.dto.LlmProviderTestResponse;
import vn.vinfast.vfqc.api.provider.dto.TestLlmProviderRequest;
import vn.vinfast.vfqc.api.provider.entity.LlmProvider;
import vn.vinfast.vfqc.api.provider.entity.LlmProviderTestLog;
import vn.vinfast.vfqc.api.provider.enums.ProviderScope;
import vn.vinfast.vfqc.api.provider.enums.ProviderStatus;
import vn.vinfast.vfqc.api.provider.enums.ProviderTestStatus;
import vn.vinfast.vfqc.api.provider.enums.ProviderType;
import vn.vinfast.vfqc.api.provider.mapper.LlmProviderTestLogMapper;
import vn.vinfast.vfqc.api.provider.repository.LlmProviderTestLogRepository;
import vn.vinfast.vfqc.api.provider.service.LlmProviderClientFactory;
import vn.vinfast.vfqc.api.provider.service.LlmProviderLookupService;
import vn.vinfast.vfqc.api.secret.service.RuntimeSecretResolver;
import vn.vinfast.vfqc.api.user.enums.SystemRole;
import vn.vinfast.vfqc.api.user.enums.UserStatus;

@ExtendWith(MockitoExtension.class)
class LlmProviderTestServiceImplTest {

  @Mock private LlmProviderLookupService providerLookupService;
  @Mock private RuntimeSecretResolver runtimeSecretResolver;
  @Mock private LlmProviderClientFactory clientFactory;
  @Mock private LlmProviderClient client;
  @Mock private LlmProviderTestLogPersister testLogPersister;
  @Mock private LlmProviderTestLogRepository testLogRepository;
  @Mock private LlmProviderTestLogMapper testLogMapper;

  private LlmProviderTestServiceImpl createService() {
    return new LlmProviderTestServiceImpl(
        providerLookupService,
        runtimeSecretResolver,
        clientFactory,
        testLogPersister,
        testLogRepository,
        testLogMapper,
        new DefaultVfqcSensitiveDataMasker());
  }

  @Test
  void test_ClientFailure_SavesSanitizedLogWithoutPlaintextSecret() {
    // Arrange
    var user =
        new CurrentUser(
            UUID.randomUUID(), "owner@vinfast.vn", "Owner", SystemRole.USER, UserStatus.ACTIVE);
    var providerId = UUID.randomUUID();
    var provider =
        LlmProvider.builder()
            .id(providerId)
            .scope(ProviderScope.PERSONAL)
            .ownerUserId(user.id())
            .providerType(ProviderType.OPENAI_COMPATIBLE)
            .secretId(UUID.randomUUID())
            .defaultModel("gpt-test")
            .status(ProviderStatus.ACTIVE)
            .build();
    var service = createService();

    var persistedLog =
        LlmProviderTestLog.builder()
            .id(1L)
            .llmProviderId(providerId)
            .status(ProviderTestStatus.FAILED)
            .model("gpt-test")
            .errorMessage("provider rejected ****")
            .build();

    when(providerLookupService.requireVisible(user, providerId)).thenReturn(provider);
    when(runtimeSecretResolver.resolve(user, provider.getSecretId(), null))
        .thenReturn("sk-plain-secret");
    when(clientFactory.clientFor(ProviderType.OPENAI_COMPATIBLE)).thenReturn(client);
    when(client.test(any())).thenThrow(new RuntimeException("provider rejected sk-plain-secret"));
    var errorCaptor = ArgumentCaptor.forClass(String.class);
    when(testLogPersister.persistTestResult(
            eq(provider),
            eq(user),
            eq(ProviderStatus.TEST_FAILED),
            eq(ProviderTestStatus.FAILED),
            eq("gpt-test"),
            any(),
            any(),
            any(),
            errorCaptor.capture(),
            any(),
            any()))
        .thenReturn(persistedLog);
    when(testLogMapper.toTestResponse(persistedLog))
        .thenReturn(
            new LlmProviderTestResponse(
                ProviderTestStatus.FAILED, "gpt-test", Map.of(), null,
                "RuntimeException", "provider rejected ****", null, Map.of()));

    // Act
    var response =
        service.test(user, providerId, new TestLlmProviderRequest(null, null, Map.of()));

    // Assert
    assertThat(response.status()).isEqualTo(ProviderTestStatus.FAILED);
    assertThat(errorCaptor.getValue()).isEqualTo("provider rejected ****");
    assertThat(errorCaptor.getValue()).doesNotContain("sk-plain-secret");
    verify(testLogPersister)
        .persistTestResult(
            eq(provider),
            eq(user),
            eq(ProviderStatus.TEST_FAILED),
            eq(ProviderTestStatus.FAILED),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any());
  }

  @Test
  void history_LimitAboveMaximum_ClampsToOneHundredAndChecksPermission() {
    // Arrange
    var user =
        new CurrentUser(
            UUID.randomUUID(), "owner@vinfast.vn", "Owner", SystemRole.USER, UserStatus.ACTIVE);
    var providerId = UUID.randomUUID();
    var provider =
        LlmProvider.builder()
            .id(providerId)
            .scope(ProviderScope.PERSONAL)
            .ownerUserId(user.id())
            .providerType(ProviderType.OPENAI_COMPATIBLE)
            .secretId(UUID.randomUUID())
            .defaultModel("gpt-test")
            .build();
    var pageable = ArgumentCaptor.forClass(Pageable.class);
    var log =
        LlmProviderTestLog.builder()
            .id(10L)
            .llmProviderId(providerId)
            .status(ProviderTestStatus.SUCCESS)
            .model("gpt-test")
            .requestJsonSanitized(Map.of("model", "gpt-test"))
            .testedAt(OffsetDateTime.parse("2026-07-05T03:30:00Z"))
            .build();
    var mapped =
        new LlmProviderTestLogResponse(
            10L,
            ProviderTestStatus.SUCCESS,
            "gpt-test",
            Map.of("model", "gpt-test"),
            "ok",
            null,
            null,
            12,
            Map.of(),
            log.getTestedAt());
    var service = createService();
    when(providerLookupService.requireVisible(user, providerId)).thenReturn(provider);
    when(testLogRepository.findByLlmProviderIdOrderByTestedAtDesc(
            org.mockito.ArgumentMatchers.eq(providerId), pageable.capture()))
        .thenReturn(List.of(log));
    when(testLogMapper.toResponse(log)).thenReturn(mapped);

    // Act
    var response = service.history(user, providerId, 500);

    // Assert
    assertThat(response).containsExactly(mapped);
    assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
    verify(providerLookupService).requireVisible(user, providerId);
  }

  @Test
  void test_ClientSuccess_SanitizesResponsePreviewAndMarksProviderActive() {
    // Arrange
    var user =
        new CurrentUser(
            UUID.randomUUID(), "owner@vinfast.vn", "Owner", SystemRole.USER, UserStatus.ACTIVE);
    var providerId = UUID.randomUUID();
    var provider =
        LlmProvider.builder()
            .id(providerId)
            .scope(ProviderScope.PERSONAL)
            .ownerUserId(user.id())
            .providerType(ProviderType.OPENAI_COMPATIBLE)
            .secretId(UUID.randomUUID())
            .defaultModel("gpt-test")
            .status(ProviderStatus.TEST_FAILED)
            .build();

    var persistedLog =
        LlmProviderTestLog.builder()
            .id(2L)
            .llmProviderId(providerId)
            .status(ProviderTestStatus.SUCCESS)
            .model("gpt-test")
            .responsePreview("provider returned Bearer ****")
            .build();

    var service = createService();
    when(providerLookupService.requireVisible(user, providerId)).thenReturn(provider);
    when(runtimeSecretResolver.resolve(user, provider.getSecretId(), null))
        .thenReturn("sk-plain-secret");
    when(clientFactory.clientFor(ProviderType.OPENAI_COMPATIBLE)).thenReturn(client);
    when(client.test(any()))
        .thenReturn(
            new ProviderClientTestResult(
                "provider returned Bearer sk-plain-secret", 22, Map.of()));
    var previewCaptor = ArgumentCaptor.forClass(String.class);
    when(testLogPersister.persistTestResult(
            eq(provider),
            eq(user),
            eq(ProviderStatus.ACTIVE),
            eq(ProviderTestStatus.SUCCESS),
            eq("gpt-test"),
            any(),
            previewCaptor.capture(),
            any(),
            any(),
            eq(22),
            any()))
        .thenReturn(persistedLog);
    when(testLogMapper.toTestResponse(persistedLog))
        .thenReturn(
            new LlmProviderTestResponse(
                ProviderTestStatus.SUCCESS, "gpt-test", Map.of(),
                "provider returned Bearer ****", null, null, 22, Map.of()));

    // Act
    var response =
        service.test(user, providerId, new TestLlmProviderRequest(null, null, Map.of()));

    // Assert
    assertThat(response.status()).isEqualTo(ProviderTestStatus.SUCCESS);
    assertThat(previewCaptor.getValue()).isEqualTo("provider returned Bearer ****");
    assertThat(previewCaptor.getValue()).doesNotContain("sk-plain-secret");
    verify(testLogPersister)
        .persistTestResult(
            eq(provider),
            eq(user),
            eq(ProviderStatus.ACTIVE),
            eq(ProviderTestStatus.SUCCESS),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any());
  }
}
