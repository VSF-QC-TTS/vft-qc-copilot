package vn.vinfast.vfqc.api.auth.service.impl;

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

  @Override
  public String issueAccessToken(AppUser user) {
    var now = Instant.now();
    var expiresAt = now.plusSeconds(properties.accessExpiration() * 60);
    var claims =
        JwtClaimsSet.builder()
            .issuer("vfqc-api")
            .issuedAt(now)
            .expiresAt(expiresAt)
            .subject(user.getId().toString())
            .claim("uid", user.getId().toString())
            .claim("email", user.getEmail())
            .claim("name", user.getDisplayName())
            .claim("systemRole", user.getSystemRole().name())
            .claim("status", user.getStatus().name())
            .build();
    var header = JwsHeader.with(MacAlgorithm.HS256).build();
    log.debug("Access token issued: userId={} expiresAt={}", user.getId(), expiresAt);
    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  @Override
  public long accessExpiresInSeconds() {
    return properties.accessExpiration() * 60;
  }
}
