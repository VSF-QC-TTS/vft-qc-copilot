package vn.vinfast.vfqc.api.auth.repository;

import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.vinfast.vfqc.api.auth.entity.AuthRefreshToken;
import vn.vinfast.vfqc.api.auth.enums.AuthRefreshTokenStatus;

public interface AuthRefreshTokenRepository extends JpaRepository<AuthRefreshToken, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
					SELECT token
					FROM AuthRefreshToken token
					WHERE token.tokenHash = :tokenHash
					""")
  Optional<AuthRefreshToken> findForUpdateByTokenHash(@Param("tokenHash") String tokenHash);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
					UPDATE AuthRefreshToken token
					SET token.status = :newStatus,
							token.revokedAt = :now
					WHERE token.userId = :userId
						AND token.status = :currentStatus
					""")
  int revokeAllByUserIdAndStatus(
      @Param("userId") UUID userId,
      @Param("currentStatus") AuthRefreshTokenStatus currentStatus,
      @Param("newStatus") AuthRefreshTokenStatus newStatus,
      @Param("now") OffsetDateTime now);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
					UPDATE AuthRefreshToken token
					SET token.status = :newStatus,
							token.revokedAt = :now
					WHERE token.familyId = :familyId
						AND token.status = :currentStatus
					""")
  int revokeAllByFamilyIdAndStatus(
      @Param("familyId") UUID familyId,
      @Param("currentStatus") AuthRefreshTokenStatus currentStatus,
      @Param("newStatus") AuthRefreshTokenStatus newStatus,
      @Param("now") OffsetDateTime now);
}
