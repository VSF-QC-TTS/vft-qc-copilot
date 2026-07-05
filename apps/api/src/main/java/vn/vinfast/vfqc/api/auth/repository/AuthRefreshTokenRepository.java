package vn.vinfast.vfqc.api.auth.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.vinfast.vfqc.api.auth.entity.AuthRefreshToken;
import vn.vinfast.vfqc.api.auth.enums.AuthRefreshTokenStatus;

public interface AuthRefreshTokenRepository extends JpaRepository<AuthRefreshToken, UUID> {

  Optional<AuthRefreshToken> findByTokenHash(String tokenHash);

  List<AuthRefreshToken> findByUserIdAndStatus(UUID userId, AuthRefreshTokenStatus status);
}
