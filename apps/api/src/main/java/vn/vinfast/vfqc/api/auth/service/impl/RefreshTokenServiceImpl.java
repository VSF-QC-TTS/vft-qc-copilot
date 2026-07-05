package vn.vinfast.vfqc.api.auth.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vinfast.vfqc.api.auth.entity.AuthRefreshToken;
import vn.vinfast.vfqc.api.auth.enums.AuthRefreshTokenStatus;
import vn.vinfast.vfqc.api.auth.repository.AuthRefreshTokenRepository;
import vn.vinfast.vfqc.api.auth.service.RefreshTokenIssue;
import vn.vinfast.vfqc.api.auth.service.RefreshTokenService;
import vn.vinfast.vfqc.api.common.config.SecurityProperties;
import vn.vinfast.vfqc.api.common.error.ErrorCode;
import vn.vinfast.vfqc.api.user.entity.AppUser;
import vn.vinfast.vfqc.api.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenServiceImpl implements RefreshTokenService {

  private final AuthRefreshTokenRepository repository;
  private final UserRepository userRepository;
  private final SecurityProperties properties;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  @Transactional
  @Override
  public RefreshTokenIssue issue(AppUser user, String ip, String userAgent) {
    return issue(user, UUID.randomUUID(), ip, userAgent);
  }

  @Transactional
  @Override
  public RefreshRotation rotate(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      throw ErrorCode.REFRESH_TOKEN_MISSING.throwIt();
    }
    var now = OffsetDateTime.now(clock);
    var existing =
        repository
            .findByTokenHash(hash(rawToken))
            .orElseThrow(ErrorCode.REFRESH_TOKEN_INVALID::throwIt);
    if (existing.getStatus() != AuthRefreshTokenStatus.ACTIVE) {
      throw ErrorCode.REFRESH_TOKEN_INVALID.throwIt();
    }
    if (existing.getExpiresAt().isBefore(now)) {
      existing.setStatus(AuthRefreshTokenStatus.EXPIRED);
      throw ErrorCode.REFRESH_TOKEN_EXPIRED.throwIt();
    }
    existing.setStatus(AuthRefreshTokenStatus.REVOKED);
    existing.setRotatedAt(now);
    existing.setRevokedAt(now);
    var user =
        userRepository
            .findById(existing.getUserId())
            .orElseThrow(ErrorCode.REFRESH_TOKEN_INVALID::throwIt);
    if (!user.active()) {
      throw ErrorCode.USER_DISABLED.throwIt();
    }
    return new RefreshRotation(user, existing.getFamilyId());
  }

  @Transactional
  @Override
  public void revoke(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      return;
    }
    repository
        .findByTokenHash(hash(rawToken))
        .ifPresent(
            token -> {
              token.setStatus(AuthRefreshTokenStatus.REVOKED);
              token.setRevokedAt(OffsetDateTime.now(clock));
              log.info(
                  "Refresh token revoked: tokenId={} userId={}", token.getId(), token.getUserId());
            });
  }

  @Transactional
  @Override
  public void revokeActiveForUser(UUID userId) {
    var now = OffsetDateTime.now(clock);
    repository
        .findByUserIdAndStatus(userId, AuthRefreshTokenStatus.ACTIVE)
        .forEach(
            token -> {
              token.setStatus(AuthRefreshTokenStatus.REVOKED);
              token.setRevokedAt(now);
              log.info(
                  "Refresh token revoked after password change: tokenId={} userId={}",
                  token.getId(),
                  token.getUserId());
            });
  }

  @Transactional
  @Override
  public RefreshTokenIssue issue(AppUser user, UUID familyId, String ip, String userAgent) {
    var rawToken = randomToken();
    var now = OffsetDateTime.now(clock);
    var maxAge = properties.refreshExpiration() * 60;
    var refreshToken =
        repository.save(
            AuthRefreshToken.builder()
                .userId(user.getId())
                .tokenHash(hash(rawToken))
                .familyId(familyId)
                .status(AuthRefreshTokenStatus.ACTIVE)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(maxAge))
                .createdIp(ip)
                .userAgent(userAgent)
                .build());
    log.debug(
        "Refresh token issued: tokenId={} userId={} familyId={}",
        refreshToken.getId(),
        user.getId(),
        familyId);
    return new RefreshTokenIssue(rawToken, maxAge);
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
