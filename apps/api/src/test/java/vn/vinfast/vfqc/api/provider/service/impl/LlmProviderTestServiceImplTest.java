package vn.vinfast.vfqc.api.provider.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
import vn.vinfast.vfqc.api.provider.dto.TestLlmProviderRequest;
import vn.vinfast.vfqc.api.provider.entity.LlmProvider;
import vn.vinfast.vfqc.api.provider.entity.LlmProviderTestLog;
import vn.vinfast.vfqc.api.provider.enums.ProviderScope;
import vn.vinfast.vfqc.api.provider.enums.ProviderStatus;
import vn.vinfast.vfqc.api.provider.enums.ProviderTestStatus;
import vn.vinfast.vfqc.api.provider.enums.ProviderType;
import vn.vinfast.vfqc.api.provider.mapper.LlmProviderTestLogMapper;
import vn.vinfast.vfqc.api.provider.repository.LlmProviderRepository;
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
  @Mock private LlmProviderRepository providerRepository;
  @Mock private LlmProviderTestLogRepository testLogRepository;
  @Mock private LlmProviderTestLogMapper testLogMapper;

  @Test
  void test_ClientFailure_SavesSanitizedLogWithoutPlaintextSecret() {
    // Arrange
    var user =
        new CurrentUser(
            UUID.randomUUID(), "owner@vinfast.vn", "Owner", SystemRole.USER, UserStatus.ACTIVE);
    var providerId = UUID.randomUUID();
    var provider =
        new LlmProvider()
            .setId(providerId)
            .setScope(ProviderScope.PERSONAL)
            .setOwnerUserId(user.id())
            .setProviderType(ProviderType.OPENAI_COMPATIBLE)
            .setSecretId(UUID.randomUUID())
            .setDefaultModel("gpt-test")
            .setStatus(ProviderStatus.ACTIVE);
    var savedLog = ArgumentCaptor.forClass(LlmProviderTestLog.class);
    var clock = Clock.fixed(Instant.parse("2026-07-05T03:30:00Z"), ZoneOffset.UTC);
    var service =
        new LlmProviderTestServiceImpl(
            providerLookupService,
            runtimeSecretResolver,
            clientFactory,
            providerRepository,
            testLogRepository,
            testLogMapper,
            new DefaultVfqcSensitiveDataMasker(),
            clock);
    when(providerLookupService.requireVisible(user, providerId)).thenReturn(provider);
    when(runtimeSecretResolver.resolve(user, provider.getSecretId(), null))
        .thenReturn("sk-plain-secret");
    when(clientFactory.clientFor(ProviderType.OPENAI_COMPATIBLE)).thenReturn(client);
    when(testLogRepository.save(savedLog.capture()))
        .thenAnswer(invocation -> invocation.getArgument(0, LlmProviderTestLog.class).setId(1L));
    when(client.test(any())).thenThrow(new RuntimeException("provider rejected sk-plain-secret"));

    // Act
    var response = service.test(user, providerId, new TestLlmProviderRequest(null, null, Map.of()));

    // Assert
    assertThat(response.status()).isEqualTo(ProviderTestStatus.FAILED);
    assertThat(savedLog.getValue().getErrorMessage()).isEqualTo("provider rejected ****");
    assertThat(savedLog.getValue().toString()).doesNotContain("sk-plain-secret");
    assertThat(provider.getStatus()).isEqualTo(ProviderStatus.TEST_FAILED);
    verify(providerRepository).save(provider);
  }

  @Test
  void history_LimitAboveMaximum_ClampsToOneHundredAndChecksPermission() {
    // Arrange
    var user =
        new CurrentUser(
            UUID.randomUUID(), "owner@vinfast.vn", "Owner", SystemRole.USER, UserStatus.ACTIVE);
    var providerId = UUID.randomUUID();
    var provider =
        new LlmProvider()
            .setId(providerId)
            .setScope(ProviderScope.PERSONAL)
            .setOwnerUserId(user.id())
            .setProviderType(ProviderType.OPENAI_COMPATIBLE)
            .setSecretId(UUID.randomUUID())
            .setDefaultModel("gpt-test");
    var pageable = ArgumentCaptor.forClass(Pageable.class);
    var log =
        new LlmProviderTestLog()
            .setId(10L)
            .setLlmProviderId(providerId)
            .setStatus(ProviderTestStatus.SUCCESS)
            .setModel("gpt-test")
            .setRequestJsonSanitized(Map.of("model", "gpt-test"))
            .setTestedAt(OffsetDateTime.parse("2026-07-05T03:30:00Z"));
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
    var service =
        new LlmProviderTestServiceImpl(
            providerLookupService,
            runtimeSecretResolver,
            clientFactory,
            providerRepository,
            testLogRepository,
            testLogMapper,
            new DefaultVfqcSensitiveDataMasker(),
            Clock.fixed(Instant.parse("2026-07-05T03:30:00Z"), ZoneOffset.UTC));
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
        new LlmProvider()
            .setId(providerId)
            .setScope(ProviderScope.PERSONAL)
            .setOwnerUserId(user.id())
            .setProviderType(ProviderType.OPENAI_COMPATIBLE)
            .setSecretId(UUID.randomUUID())
            .setDefaultModel("gpt-test")
            .setStatus(ProviderStatus.TEST_FAILED);
    var savedLog = ArgumentCaptor.forClass(LlmProviderTestLog.class);
    var service =
        new LlmProviderTestServiceImpl(
            providerLookupService,
            runtimeSecretResolver,
            clientFactory,
            providerRepository,
            testLogRepository,
            testLogMapper,
            new DefaultVfqcSensitiveDataMasker(),
            Clock.fixed(Instant.parse("2026-07-05T03:30:00Z"), ZoneOffset.UTC));
    when(providerLookupService.requireVisible(user, providerId)).thenReturn(provider);
    when(runtimeSecretResolver.resolve(user, provider.getSecretId(), null))
        .thenReturn("sk-plain-secret");
    when(clientFactory.clientFor(ProviderType.OPENAI_COMPATIBLE)).thenReturn(client);
    when(client.test(any()))
        .thenReturn(new ProviderClientTestResult("provider returned Bearer sk-plain-secret", 22, Map.of()));
    when(testLogRepository.save(savedLog.capture()))
        .thenAnswer(invocation -> invocation.getArgument(0, LlmProviderTestLog.class).setId(2L));

    // Act
    var response = service.test(user, providerId, new TestLlmProviderRequest(null, null, Map.of()));

    // Assert
    assertThat(response.status()).isEqualTo(ProviderTestStatus.SUCCESS);
    assertThat(savedLog.getValue().getResponsePreview()).isEqualTo("provider returned Bearer ****");
    assertThat(provider.getStatus()).isEqualTo(ProviderStatus.ACTIVE);
    verify(providerRepository).save(provider);
  }
}
