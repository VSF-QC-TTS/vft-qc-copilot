package vn.vinfast.vfqc.api.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vfqc.security")
public record SecurityProperties(
    long accessExpiration,
    long refreshExpiration,
    Jwt jwt,
    String encryptionKey,
    Cookie cookie,
    String runnerToken) {

  public SecurityProperties {
    if (accessExpiration <= 0) {
      accessExpiration = 15;
    }
    if (refreshExpiration <= 0) {
      refreshExpiration = 10080;
    }
    if (jwt == null) {
      jwt = new Jwt("");
    }
    if (cookie == null) {
      cookie = new Cookie(false, "lax");
    }
    if (runnerToken == null || runnerToken.isBlank()) {
      runnerToken = "dev-runner-token";
    }
  }

  public record Jwt(String secretKey) {}

  public record Cookie(boolean secure, String sameSite) {}
}
