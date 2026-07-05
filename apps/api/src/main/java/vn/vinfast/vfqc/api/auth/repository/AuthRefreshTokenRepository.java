package vn.vinfast.vfqc.api.auth.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import vn.vinfast.vfqc.api.auth.entity.AuthRefreshToken;
import vn.vinfast.vfqc.api.auth.enums.AuthRefreshTokenStatus;

public interface AuthRefreshTokenRepository extends JpaRepository<AuthRefreshToken, UUID> {

  Optional<AuthRefreshToken> findByTokenHash(String tokenHash);

  List<AuthRefreshToken> findByUserIdAndStatus(UUID userId, AuthRefreshTokenStatus status);

  @Modifying
  @Query("""
      UPDATE AuthRefreshToken t
      SET t.status = :newStatus, t.revokedAt = :now
      WHERE t.userId = :userId AND t.status = :currentStatus
      """)
  int revokeAllByUserIdAndStatus(
      UUID userId,
      AuthRefreshTokenStatus currentStatus,
      AuthRefreshTokenStatus newStatus,
      OffsetDateTime now);
}
