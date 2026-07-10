package vn.vinfast.vfqc.api.auth.service.impl;

import static vn.vinfast.vfqc.api.common.constant.JwtConstant.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import vn.vinfast.vfqc.api.auth.service.JwtTokenService;
import vn.vinfast.vfqc.api.common.config.SecurityProperties;
import vn.vinfast.vfqc.api.user.entity.AppUser;

@Service
@RequiredArgsConstructor
@Slf4j
public class JwtTokenServiceImpl implements JwtTokenService {

  private final JwtEncoder jwtEncoder;
  private final SecurityProperties properties;
  private final Clock clock;

  @Override
  public String issueAccessToken(AppUser user) {
    var now = Instant.now(clock);
    var expiresAt = now.plus(accessTokenDuration());

    var claims =
        JwtClaimsSet.builder()
            .issuer(ISSUER)
            .issuedAt(now)
            .expiresAt(expiresAt)
            .subject(user.getId().toString())
            .claim(EMAIL_CLAIM, user.getEmail())
            .claim(NAME_CLAIM, user.getDisplayName())
            .claim(SYSTEM_ROLE_CLAIM, user.getSystemRole().name())
            .claim(STATUS_CLAIM, user.getStatus().name())
            .build();

    var header = JwsHeader.with(MacAlgorithm.HS256).build();

    var token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

    log.debug("Access token issued: userId={} expiresAt={}", user.getId(), expiresAt);

    return token;
  }

  @Override
  public long accessExpiresInSeconds() {
    return accessTokenDuration().toSeconds();
  }

  private Duration accessTokenDuration() {
    return Duration.ofMinutes(properties.accessExpirationMinutes());
  }
}
