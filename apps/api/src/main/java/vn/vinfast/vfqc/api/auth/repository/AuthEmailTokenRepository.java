package vn.vinfast.vfqc.api.auth.repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import vn.vinfast.vfqc.api.auth.entity.AuthEmailToken;
import vn.vinfast.vfqc.api.auth.enums.AuthEmailTokenPurpose;
import vn.vinfast.vfqc.api.auth.enums.AuthEmailTokenStatus;

public interface AuthEmailTokenRepository extends JpaRepository<AuthEmailToken, UUID> {

  Optional<AuthEmailToken> findByTokenHashAndStatus(
      String tokenHash, AuthEmailTokenStatus status);

  List<AuthEmailToken> findByUserIdAndPurposeInAndStatus(
      UUID userId, Collection<AuthEmailTokenPurpose> purposes, AuthEmailTokenStatus status);

  List<AuthEmailToken> findByUserIdAndStatus(UUID userId, AuthEmailTokenStatus status);

  @Modifying
  @Query("""
      UPDATE AuthEmailToken t
      SET t.status = :newStatus, t.revokedAt = :now
      WHERE t.userId = :userId AND t.status = :currentStatus
      """)
  int revokeAllByUserIdAndStatus(
      UUID userId,
      AuthEmailTokenStatus currentStatus,
      AuthEmailTokenStatus newStatus,
      OffsetDateTime now);

  @Modifying
  @Query("""
      UPDATE AuthEmailToken t
      SET t.status = :newStatus, t.revokedAt = :now
      WHERE t.userId = :userId AND t.purpose IN :purposes AND t.status = :currentStatus
      """)
  int revokeAllByUserIdAndPurposesAndStatus(
      UUID userId,
      Collection<AuthEmailTokenPurpose> purposes,
      AuthEmailTokenStatus currentStatus,
      AuthEmailTokenStatus newStatus,
      OffsetDateTime now);
}
