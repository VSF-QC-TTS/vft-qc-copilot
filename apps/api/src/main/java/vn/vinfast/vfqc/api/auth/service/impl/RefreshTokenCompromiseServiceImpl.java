package vn.vinfast.vfqc.api.auth.service.impl;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.vinfast.vfqc.api.auth.enums.AuthRefreshTokenStatus;
import vn.vinfast.vfqc.api.auth.repository.AuthRefreshTokenRepository;
import vn.vinfast.vfqc.api.auth.service.RefreshTokenCompromiseService;

/**
 * @author nghlong3004 (Long Nguyen Hoang)
 * @since 7/10/2026
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenCompromiseServiceImpl implements RefreshTokenCompromiseService {

  private final AuthRefreshTokenRepository repository;
  private final Clock clock;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @Override
  public void revokeFamily(UUID familyId) {
    repository.revokeAllByFamilyIdAndStatus(
        familyId,
        AuthRefreshTokenStatus.ACTIVE,
        AuthRefreshTokenStatus.REVOKED,
        OffsetDateTime.now(clock));
  }
}
