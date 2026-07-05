package vn.vinfast.vfqc.api.auth.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vinfast.vfqc.api.auth.entity.AuthEmailToken;
import vn.vinfast.vfqc.api.auth.enums.AuthEmailTokenPurpose;
import vn.vinfast.vfqc.api.auth.enums.AuthEmailTokenStatus;
import vn.vinfast.vfqc.api.auth.repository.AuthEmailTokenRepository;
import vn.vinfast.vfqc.api.auth.service.AuthEmailTokenIssue;
import vn.vinfast.vfqc.api.auth.service.AuthEmailTokenService;
import vn.vinfast.vfqc.api.common.error.ErrorCode;
import vn.vinfast.vfqc.api.user.entity.AppUser;
import vn.vinfast.vfqc.api.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class AuthEmailTokenServiceImpl implements AuthEmailTokenService {

  private final AuthEmailTokenRepository repository;
  private final UserRepository userRepository;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  @Transactional
  @Override
  public AuthEmailTokenIssue issue(
      AppUser user, AuthEmailTokenPurpose purpose, Duration ttl, String ip, String userAgent) {
    revokeActive(user.getId(), Set.of(purpose));
    var rawToken = randomToken();
    var now = OffsetDateTime.now(clock);
    var expiresAt = now.plus(ttl);
    repository.save(
        new AuthEmailToken()
            .setUserId(user.getId())
            .setTokenHash(hash(rawToken))
            .setPurpose(purpose)
            .setStatus(AuthEmailTokenStatus.ACTIVE)
            .setIssuedAt(now)
            .setExpiresAt(expiresAt)
            .setCreatedIp(ip)
            .setUserAgent(userAgent));
    return new AuthEmailTokenIssue(rawToken, expiresAt);
  }

  @Transactional
  @Override
  public AppUser consume(String rawToken, Set<AuthEmailTokenPurpose> allowedPurposes) {
    if (rawToken == null || rawToken.isBlank()) {
      throw ErrorCode.EMAIL_TOKEN_MISSING.throwIt();
    }
    var now = OffsetDateTime.now(clock);
    var token =
        repository
            .findByTokenHashAndStatus(hash(rawToken), AuthEmailTokenStatus.ACTIVE)
            .orElseThrow(ErrorCode.EMAIL_TOKEN_INVALID::throwIt);
    if (!allowedPurposes.contains(token.getPurpose())) {
      throw ErrorCode.EMAIL_TOKEN_INVALID.throwIt();
    }
    if (token.getExpiresAt().isBefore(now)) {
      token.setStatus(AuthEmailTokenStatus.EXPIRED);
      throw ErrorCode.EMAIL_TOKEN_EXPIRED.throwIt();
    }
    token.setStatus(AuthEmailTokenStatus.USED).setUsedAt(now);
    return userRepository
        .findById(token.getUserId())
        .orElseThrow(ErrorCode.EMAIL_TOKEN_INVALID::throwIt);
  }

  @Transactional
  @Override
  public void revokeActiveForUser(UUID userId) {
    repository.findByUserIdAndStatus(userId, AuthEmailTokenStatus.ACTIVE).forEach(this::revoke);
  }

  private void revokeActive(UUID userId, Set<AuthEmailTokenPurpose> purposes) {
    repository
        .findByUserIdAndPurposeInAndStatus(userId, purposes, AuthEmailTokenStatus.ACTIVE)
        .forEach(this::revoke);
  }

  private void revoke(AuthEmailToken token) {
    token.setStatus(AuthEmailTokenStatus.REVOKED).setRevokedAt(OffsetDateTime.now(clock));
  }

  private String randomToken() {
    var bytes = new byte[48];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private String hash(String token) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      var bytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}
