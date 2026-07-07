package vn.vinfast.vfqc.api.secret.assembler;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.vinfast.vfqc.api.secret.dto.SecretResponse;
import vn.vinfast.vfqc.api.secret.dto.SecretSummaryResponse;
import vn.vinfast.vfqc.api.secret.entity.Secret;
import vn.vinfast.vfqc.api.secret.mapper.SecretMapper;
import vn.vinfast.vfqc.api.user.dto.UserSummaryResponse;
import vn.vinfast.vfqc.api.user.service.UserSummaryService;

@Component
@RequiredArgsConstructor
public class SecretResponseAssembler {

  private final SecretMapper secretMapper;
  private final UserSummaryService userSummaryService;

  public SecretResponse toResponse(Secret secret) {
    return withOwnerUser(secretMapper.toResponse(secret), summaryMap(secret.getOwnerUserId()));
  }

  public List<SecretResponse> toResponses(List<Secret> secrets) {
    var usersById =
        userSummaryService.summariesById(secrets.stream().map(Secret::getOwnerUserId).toList());
    return secrets.stream()
        .map(secret -> withOwnerUser(secretMapper.toResponse(secret), usersById))
        .toList();
  }

  public SecretSummaryResponse toSummary(Secret secret) {
    return withOwnerUser(secretMapper.toSummary(secret), summaryMap(secret.getOwnerUserId()));
  }

  public List<SecretSummaryResponse> toSummaries(List<Secret> secrets) {
    var usersById =
        userSummaryService.summariesById(secrets.stream().map(Secret::getOwnerUserId).toList());
    return secrets.stream()
        .map(secret -> withOwnerUser(secretMapper.toSummary(secret), usersById))
        .toList();
  }

  private SecretResponse withOwnerUser(
      SecretResponse response, java.util.Map<UUID, UserSummaryResponse> usersById) {
    return new SecretResponse(
        response.id(),
        response.scope(),
        response.ownerUserId(),
        userOrNull(usersById, response.ownerUserId()),
        response.workspaceId(),
        response.name(),
        response.maskedPreview(),
        response.status(),
        response.createdAt(),
        response.updatedAt());
  }

  private SecretSummaryResponse withOwnerUser(
      SecretSummaryResponse response, java.util.Map<UUID, UserSummaryResponse> usersById) {
    return new SecretSummaryResponse(
        response.id(),
        response.scope(),
        response.ownerUserId(),
        userOrNull(usersById, response.ownerUserId()),
        response.workspaceId(),
        response.name(),
        response.status());
  }

  private java.util.Map<UUID, UserSummaryResponse> summaryMap(UUID userId) {
    var summary = userSummaryService.toSummaryById(userId);
    return summary == null ? java.util.Map.of() : java.util.Map.of(userId, summary);
  }

  private UserSummaryResponse userOrNull(
      java.util.Map<UUID, UserSummaryResponse> usersById, UUID userId) {
    return userId == null ? null : usersById.get(userId);
  }
}
