package vn.vinfast.vfqc.api.provider.assembler;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.vinfast.vfqc.api.provider.dto.LlmProviderResponse;
import vn.vinfast.vfqc.api.provider.entity.LlmProvider;
import vn.vinfast.vfqc.api.provider.mapper.LlmProviderMapper;
import vn.vinfast.vfqc.api.secret.dto.SecretSummaryResponse;
import vn.vinfast.vfqc.api.secret.entity.Secret;
import vn.vinfast.vfqc.api.secret.mapper.SecretMapper;
import vn.vinfast.vfqc.api.secret.repository.SecretRepository;
import vn.vinfast.vfqc.api.user.dto.UserSummaryResponse;
import vn.vinfast.vfqc.api.user.service.UserSummaryService;

@Component
@RequiredArgsConstructor
public class LlmProviderResponseAssembler {

  private final SecretRepository secretRepository;
  private final SecretMapper secretMapper;
  private final UserSummaryService userSummaryService;

  private final LlmProviderMapper llmProviderMapper;

  public LlmProviderResponse toResponse(LlmProvider provider) {
    var secretEntity =
        provider.getSecretId() == null
            ? null
            : secretRepository
                .findById(provider.getSecretId())
                .orElse(null);
    var userIds =
        Stream.of(
                provider.getOwnerUserId(), secretEntity == null ? null : secretEntity.getOwnerUserId())
            .filter(Objects::nonNull)
            .toList();
    var usersById = userSummaryService.summariesById(userIds);
    return llmProviderMapper.toResponse(
        provider,
        secretEntity == null ? null : toSecretSummary(secretEntity, usersById),
        userOrNull(usersById, provider.getOwnerUserId()));
  }

  public List<LlmProviderResponse> toResponses(List<LlmProvider> providers) {
    var secretIds =
        providers.stream()
            .map(LlmProvider::getSecretId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    var secretEntitiesById =
        secretRepository.findAllById(secretIds).stream()
            .collect(Collectors.toMap(Secret::getId, Function.identity()));
    var userIds =
        Stream.concat(
                providers.stream().map(LlmProvider::getOwnerUserId),
                secretEntitiesById.values().stream().map(Secret::getOwnerUserId))
            .filter(Objects::nonNull)
            .toList();
    var usersById = userSummaryService.summariesById(userIds);
    var secretsById =
        secretEntitiesById.values().stream()
            .map(secret -> toSecretSummary(secret, usersById))
            .collect(Collectors.toMap(SecretSummaryResponse::id, Function.identity()));

    return providers.stream()
        .map(
            provider ->
                llmProviderMapper.toResponse(
                    provider,
                    secretsById.get(provider.getSecretId()),
                    userOrNull(usersById, provider.getOwnerUserId())))
        .toList();
  }

  private SecretSummaryResponse toSecretSummary(
      Secret secret, Map<UUID, UserSummaryResponse> usersById) {
    var summary = secretMapper.toSummary(secret);
    return new SecretSummaryResponse(
        summary.id(),
        summary.scope(),
        summary.ownerUserId(),
        userOrNull(usersById, summary.ownerUserId()),
        summary.workspaceId(),
        summary.name(),
        summary.status());
  }

  private UserSummaryResponse userOrNull(
      Map<UUID, UserSummaryResponse> usersById,
      UUID userId) {
    return userId == null ? null : usersById.get(userId);
  }
}
