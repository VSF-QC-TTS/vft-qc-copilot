package vn.vinfast.vfqc.api.user.service.impl;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vinfast.vfqc.api.user.dto.UserSummaryResponse;
import vn.vinfast.vfqc.api.user.entity.AppUser;
import vn.vinfast.vfqc.api.user.repository.UserRepository;
import vn.vinfast.vfqc.api.user.service.UserSummaryService;

@Service
@RequiredArgsConstructor
public class UserSummaryServiceImpl implements UserSummaryService {

  private final UserRepository userRepository;

  @Override
  public UserSummaryResponse toSummary(AppUser user) {
    if (user == null || user.getDeletedAt() != null) {
      return null;
    }
    return new UserSummaryResponse(
        user.getId(), user.getEmail(), user.getDisplayName(), user.getAvatarUrl());
  }

  @Override
  @Transactional(readOnly = true)
  public UserSummaryResponse toSummaryById(UUID userId) {
    if (userId == null) {
      return null;
    }
    return userRepository.findById(userId).map(this::toSummary).orElse(null);
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, UserSummaryResponse> summariesById(Collection<UUID> userIds) {
    var ids =
        userIds == null
            ? Set.<UUID>of()
            : userIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
    if (ids.isEmpty()) {
      return Map.of();
    }
    return userRepository.findByIdInAndDeletedAtIsNull(ids).stream()
        .map(this::toSummary)
        .collect(Collectors.toMap(UserSummaryResponse::id, Function.identity()));
  }
}
