package vn.vinfast.vfqc.api.user.service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import vn.vinfast.vfqc.api.user.dto.UserSummaryResponse;
import vn.vinfast.vfqc.api.user.entity.AppUser;

public interface UserSummaryService {

  UserSummaryResponse toSummary(AppUser user);

  UserSummaryResponse toSummaryById(UUID userId);

  Map<UUID, UserSummaryResponse> summariesById(Collection<UUID> userIds);
}
