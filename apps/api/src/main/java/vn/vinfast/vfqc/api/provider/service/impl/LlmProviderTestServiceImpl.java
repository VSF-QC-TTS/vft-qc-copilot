package vn.vinfast.vfqc.api.provider.service.impl;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vinfast.vfqc.api.common.masking.VfqcSensitiveDataMasker;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.provider.client.ProviderClientTestRequest;
import vn.vinfast.vfqc.api.provider.dto.LlmProviderTestLogResponse;
import vn.vinfast.vfqc.api.provider.dto.LlmProviderTestResponse;
import vn.vinfast.vfqc.api.provider.dto.TestLlmProviderRequest;
import vn.vinfast.vfqc.api.provider.entity.LlmProvider;
import vn.vinfast.vfqc.api.provider.enums.ProviderStatus;
import vn.vinfast.vfqc.api.provider.enums.ProviderTestStatus;
import vn.vinfast.vfqc.api.provider.mapper.LlmProviderTestLogMapper;
import vn.vinfast.vfqc.api.provider.repository.LlmProviderTestLogRepository;
import vn.vinfast.vfqc.api.provider.service.LlmProviderClientFactory;
import vn.vinfast.vfqc.api.provider.service.LlmProviderLookupService;
import vn.vinfast.vfqc.api.provider.service.LlmProviderTestService;
import vn.vinfast.vfqc.api.secret.service.RuntimeSecretResolver;

@Service
@RequiredArgsConstructor
@Slf4j
public class LlmProviderTestServiceImpl implements LlmProviderTestService {

  private static final String DEFAULT_PROMPT = "Return the word ok.";

  private final LlmProviderLookupService providerLookupService;
  private final RuntimeSecretResolver runtimeSecretResolver;
  private final LlmProviderClientFactory clientFactory;
  private final LlmProviderTestLogPersister testLogPersister;
  private final LlmProviderTestLogRepository testLogRepository;
  private final LlmProviderTestLogMapper testLogMapper;
  private final VfqcSensitiveDataMasker masker;

  @Override
  public LlmProviderTestResponse test(
      CurrentUser user, UUID providerId, TestLlmProviderRequest request) {
    var provider = providerLookupService.requireVisible(user, providerId);
    var model = chooseModel(provider, request);
    var prompt =
        request.prompt() == null || request.prompt().isBlank() ? DEFAULT_PROMPT : request.prompt();
    var workspaceId = provider.getWorkspaceId();
    var plaintextSecret = runtimeSecretResolver.resolve(user, provider.getSecretId(), workspaceId);
    var sanitizedRequest =
        masker
            .sanitizeMap(
                Map.of(
                    "providerId",
                    provider.getId().toString(),
                    "providerType",
                    provider.getProviderType().name(),
                    "model",
                    model,
                    "prompt",
                    prompt,
                    "overrides",
                    request.overrides() == null ? Map.of() : request.overrides()))
            .value();
    try {
      var result =
          clientFactory
              .clientFor(provider.getProviderType())
              .test(
                  new ProviderClientTestRequest(
                      provider, plaintextSecret, model, prompt, request.overrides()));
      var sanitizedPreview = masker.sanitizeText(result.responsePreview()).value();
      var testLog =
          testLogPersister.persistTestResult(
              provider,
              user,
              ProviderStatus.ACTIVE,
              ProviderTestStatus.SUCCESS,
              model,
              sanitizedRequest,
              sanitizedPreview,
              null,
              null,
              result.latencyMs(),
              result.tokenUsage());
      return testLogMapper.toTestResponse(testLog);
    } catch (RuntimeException exception) {
      var sanitizedError = masker.sanitizeText(exception.getMessage()).value();
      var testLog =
          testLogPersister.persistTestResult(
              provider,
              user,
              ProviderStatus.TEST_FAILED,
              ProviderTestStatus.FAILED,
              model,
              sanitizedRequest,
              null,
              exception.getClass().getSimpleName(),
              sanitizedError,
              null,
              Map.of());
      log.warn(
          "LLM provider test failed: providerId={} actorId={} errorType={}",
          providerId,
          user.id(),
          exception.getClass().getSimpleName());
      return testLogMapper.toTestResponse(testLog);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public List<LlmProviderTestLogResponse> history(CurrentUser user, UUID providerId, int limit) {
    providerLookupService.requireVisible(user, providerId);
    var safeLimit = Math.clamp(limit, 1, 100);
    return testLogRepository
        .findByLlmProviderIdOrderByTestedAtDesc(providerId, PageRequest.of(0, safeLimit))
        .stream()
        .map(testLogMapper::toResponse)
        .toList();
  }

  private String chooseModel(LlmProvider provider, TestLlmProviderRequest request) {
    if (request.model() != null && !request.model().isBlank()) {
      return request.model();
    }
    return provider.getDefaultModel();
  }
}
