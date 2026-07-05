package vn.vinfast.vfqc.api.auth.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.vinfast.vfqc.api.auth.entity.AuthEmailToken;
import vn.vinfast.vfqc.api.auth.enums.AuthEmailTokenPurpose;
import vn.vinfast.vfqc.api.auth.enums.AuthEmailTokenStatus;

public interface AuthEmailTokenRepository extends JpaRepository<AuthEmailToken, UUID> {

  Optional<AuthEmailToken> findByTokenHashAndStatus(
      String tokenHash, AuthEmailTokenStatus status);

  List<AuthEmailToken> findByUserIdAndPurposeInAndStatus(
      UUID userId, Collection<AuthEmailTokenPurpose> purposes, AuthEmailTokenStatus status);

  List<AuthEmailToken> findByUserIdAndStatus(UUID userId, AuthEmailTokenStatus status);
}
