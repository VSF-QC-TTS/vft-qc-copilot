package vn.vinfast.vfqc.api.auth.service;

import java.util.UUID;
import vn.vinfast.vfqc.api.user.entity.AppUser;

public interface RefreshTokenService {

  /**
   * Creates and persists a refresh token for a user. Only the token hash may be stored.
   *
   * @param user token owner
   * @param ip client IP address for audit metadata
   * @param userAgent client user-agent for audit metadata
   * @return plaintext token for one-time cookie writing and its max age
   */
  RefreshTokenIssue issue(AppUser user, String ip, String userAgent);

  /**
   * Creates and persists a rotated refresh token in the existing token family.
   *
   * @param user token owner
   * @param familyId refresh-token family preserved across rotation
   * @param ip client IP address for audit metadata
   * @param userAgent client user-agent for audit metadata
   * @return plaintext token for one-time cookie writing and its max age
   */
  RefreshTokenIssue issue(AppUser user, UUID familyId, String ip, String userAgent);

  /**
   * Validates and revokes an existing refresh token, returning the user and token family for
   * rotation. The implementation must reject inactive, expired, or missing tokens.
   *
   * @param rawToken raw refresh token from the cookie
   * @return rotation context containing the active user and token family id
   */
  RefreshRotation rotate(String rawToken);

  /**
   * Revokes a refresh token when present. Missing cookies are treated as already logged out.
   *
   * @param rawToken raw refresh token from the cookie
   */
  void revoke(String rawToken);

  /**
   * Revokes all active refresh tokens for a user. Used after password reset so old sessions cannot
   * continue with previously issued refresh tokens.
   *
   * @param userId token owner id
   */
  void revokeActiveForUser(UUID userId);

  record RefreshRotation(AppUser user, UUID familyId) {}
}
