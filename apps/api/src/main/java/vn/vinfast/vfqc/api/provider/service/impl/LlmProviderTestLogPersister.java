package vn.vinfast.vfqc.api.provider.service.impl;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.provider.entity.LlmProvider;
import vn.vinfast.vfqc.api.provider.entity.LlmProviderTestLog;
import vn.vinfast.vfqc.api.provider.enums.ProviderStatus;
import vn.vinfast.vfqc.api.provider.enums.ProviderTestStatus;
import vn.vinfast.vfqc.api.provider.repository.LlmProviderRepository;
import vn.vinfast.vfqc.api.provider.repository.LlmProviderTestLogRepository;

/**
 * Separate bean to handle transactional persistence of test logs and provider status updates. This
 * avoids the Spring @Transactional self-invocation problem that occurs when a bean calls its own
 * transactional methods.
 */
@Component
@RequiredArgsConstructor
public class LlmProviderTestLogPersister {

  private final LlmProviderRepository providerRepository;
  private final LlmProviderTestLogRepository testLogRepository;
  private final Clock clock;

  /**
   * Atomically persists a test log and updates the provider status within a single transaction.
   *
   * @return the persisted test log
   */
  @Transactional
  public LlmProviderTestLog persistTestResult(
      LlmProvider provider,
      CurrentUser user,
      ProviderStatus providerStatus,
      ProviderTestStatus testStatus,
      String model,
      Map<String, Object> requestJsonSanitized,
      String responsePreview,
      String errorCode,
      String errorMessage,
      Integer latencyMs,
      Map<String, Object> tokenUsage) {
    var now = OffsetDateTime.now(clock);
    var testLog =
        testLogRepository.save(
            LlmProviderTestLog.builder()
                .llmProviderId(provider.getId())
                .status(testStatus)
                .model(model)
                .requestJsonSanitized(requestJsonSanitized)
                .responsePreview(responsePreview)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .latencyMs(latencyMs)
                .tokenUsageJson(tokenUsage == null ? Map.of() : tokenUsage)
                .testedBy(user.id())
                .testedAt(now)
                .createdAt(now)
                .build());

    provider.setStatus(providerStatus);
    providerRepository.save(provider);

    return testLog;
  }
}
