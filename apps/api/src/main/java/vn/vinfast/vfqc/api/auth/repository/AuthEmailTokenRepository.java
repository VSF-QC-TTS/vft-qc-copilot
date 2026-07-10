package vn.vinfast.vfqc.api.auth.repository;

import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.vinfast.vfqc.api.auth.entity.AuthEmailToken;
import vn.vinfast.vfqc.api.auth.enums.AuthEmailTokenPurpose;
import vn.vinfast.vfqc.api.auth.enums.AuthEmailTokenStatus;

public interface AuthEmailTokenRepository extends JpaRepository<AuthEmailToken, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
    select token
    from AuthEmailToken token
    where token.tokenHash = :tokenHash
      and token.status = :status
    """)
  Optional<AuthEmailToken> findForUpdateByTokenHashAndStatus(
      @Param("tokenHash") String tokenHash, @Param("status") AuthEmailTokenStatus status);

  List<AuthEmailToken> findByUserIdAndStatus(UUID userId, AuthEmailTokenStatus status);

  @Modifying
  @Query(
      """
      UPDATE AuthEmailToken t
      SET t.status = :newStatus, t.revokedAt = :now
      WHERE t.userId = :userId AND t.status = :currentStatus
      """)
  void revokeAllByUserIdAndStatus(
      UUID userId,
      AuthEmailTokenStatus currentStatus,
      AuthEmailTokenStatus newStatus,
      OffsetDateTime now);

  @Modifying
  @Query(
      """
      UPDATE AuthEmailToken t
      SET t.status = :newStatus, t.revokedAt = :now
      WHERE t.userId = :userId AND t.purpose IN :purposes AND t.status = :currentStatus
      """)
  void revokeAllByUserIdAndPurposesAndStatus(
      UUID userId,
      Collection<AuthEmailTokenPurpose> purposes,
      AuthEmailTokenStatus currentStatus,
      AuthEmailTokenStatus newStatus,
      OffsetDateTime now);
}
