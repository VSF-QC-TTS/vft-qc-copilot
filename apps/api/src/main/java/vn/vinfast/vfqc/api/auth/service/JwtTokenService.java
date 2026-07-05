package vn.vinfast.vfqc.api.auth.service;

import vn.vinfast.vfqc.api.user.entity.AppUser;

public interface JwtTokenService {

  /**
   * Issues a short-lived JWT access token for an active user.
   *
   * @param user active authenticated user
   * @return signed JWT token value
   */
  String issueAccessToken(AppUser user);

  /**
   * Returns the configured access-token lifetime in seconds.
   *
   * @return access-token TTL in seconds
   */
  long accessExpiresInSeconds();
}
