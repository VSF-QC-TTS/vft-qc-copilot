package vn.vinfast.vfqc.api.auth.service;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import vn.vinfast.vfqc.api.auth.enums.AuthEmailTokenPurpose;
import vn.vinfast.vfqc.api.user.entity.AppUser;

public interface AuthEmailTokenService {

  /**
   * Issues a one-time email token and stores only its SHA-256 hash. Existing active tokens for
   * the same user and purpose are revoked before the new token is persisted.
   *
   * @param user token owner
   * @param purpose token purpose
   * @param ttl validity duration
   * @param ip client IP address for audit metadata
   * @param userAgent client user-agent for audit metadata
   * @return raw token for one-time email link creation and its expiry timestamp
   */
  AuthEmailTokenIssue issue(
      AppUser user, AuthEmailTokenPurpose purpose, Duration ttl, String ip, String userAgent);

  /**
   * Consumes an active token for one of the allowed purposes and returns the owning user. Invalid,
   * missing, expired, reused, or wrong-purpose tokens fail with stable auth error codes.
   *
   * @param rawToken raw token from a public auth request
   * @param allowedPurposes purposes accepted by the caller
   * @return token owner
   */
  AppUser consume(String rawToken, Set<AuthEmailTokenPurpose> allowedPurposes);

  /**
   * Revokes all active email tokens for a user.
   *
   * @param userId token owner id
   */
  void revokeActiveForUser(UUID userId);
}
