package vn.vinfast.vfqc.api.provider.assembler;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.vinfast.vfqc.api.provider.dto.LlmProviderResponse;
import vn.vinfast.vfqc.api.provider.entity.LlmProvider;
import vn.vinfast.vfqc.api.provider.mapper.LlmProviderMapper;
import vn.vinfast.vfqc.api.secret.dto.SecretSummaryResponse;
import vn.vinfast.vfqc.api.secret.mapper.SecretMapper;
import vn.vinfast.vfqc.api.secret.repository.SecretRepository;

@Component
@RequiredArgsConstructor
public class LlmProviderResponseAssembler {

  private final SecretRepository secretRepository;
  private final SecretMapper secretMapper;

  private final LlmProviderMapper llmProviderMapper;

  public LlmProviderResponse toResponse(LlmProvider provider) {
    var secret =
        provider.getSecretId() == null
            ? null
            : secretRepository
                .findById(provider.getSecretId())
                .map(secretMapper::toSummary)
                .orElse(null);
    return llmProviderMapper.toResponse(provider, secret);
  }

  public List<LlmProviderResponse> toResponses(List<LlmProvider> providers) {
    var secretIds =
        providers.stream()
            .map(LlmProvider::getSecretId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    var secretsById =
        secretRepository.findAllById(secretIds).stream()
            .map(secretMapper::toSummary)
            .collect(Collectors.toMap(SecretSummaryResponse::id, Function.identity()));

    return providers.stream()
        .map(
            provider ->
                llmProviderMapper.toResponse(provider, secretsById.get(provider.getSecretId())))
        .toList();
  }
}
